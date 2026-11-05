package dev.labellens.product;

import dev.labellens.ai.ImageInput;
import dev.labellens.allergens.AllergenDetector;
import dev.labellens.allergens.AllergenOutcome;
import dev.labellens.allergens.AllergenResultEntity;
import dev.labellens.allergens.AllergenResultRepository;
import dev.labellens.nutriscore.NutriScoreCalculator;
import dev.labellens.nutriscore.NutriScoreEntity;
import dev.labellens.nutriscore.NutriScoreFromReading;
import dev.labellens.nutriscore.NutriScoreRepository;
import dev.labellens.nutriscore.NutriScoreResult;
import dev.labellens.reading.LabelReader;
import dev.labellens.reading.LabelReadingEntity;
import dev.labellens.reading.LabelReadingRepository;
import dev.labellens.reading.ReadOutcome;
import dev.labellens.storage.PhotoStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    static final int MAX_PHOTOS = 3;
    static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png");

    private final ProductRepository repository;
    private final LabelReadingRepository readingRepository;
    private final NutriScoreRepository nutriScoreRepository;
    private final AllergenResultRepository allergenRepository;
    private final PhotoStorage storage;
    private final LabelReader reader;
    private final AllergenDetector allergenDetector;
    private final TransactionTemplate transaction;
    private final int dailyUploadLimit;

    public ProductService(ProductRepository repository,
                          LabelReadingRepository readingRepository,
                          NutriScoreRepository nutriScoreRepository,
                          AllergenResultRepository allergenRepository,
                          PhotoStorage storage,
                          LabelReader reader,
                          AllergenDetector allergenDetector,
                          TransactionTemplate transaction,
                          @Value("${labellens.uploads.daily-limit}") int dailyUploadLimit) {
        this.repository = repository;
        this.readingRepository = readingRepository;
        this.nutriScoreRepository = nutriScoreRepository;
        this.allergenRepository = allergenRepository;
        this.storage = storage;
        this.reader = reader;
        this.allergenDetector = allergenDetector;
        this.transaction = transaction;
        this.dailyUploadLimit = dailyUploadLimit;
    }

    /**
     * Saves the photos, asks the AI to read the label, computes the Nutri-Score, finds the
     * allergens, saves everything.
     *
     * Separate steps on purpose, NOT one big transaction: the AI call takes several
     * seconds, and a transaction would hold a database connection open the whole time.
     *   1. transaction: save the product as PENDING (so it exists even if step 2 crashes)
     *   2. no transaction: call the AI
     *   3. no transaction: compute the Nutri-Score (plain Java), find the allergens (word list + AI)
     *   4. transaction: save the reading, the score and the allergens, mark the product DONE or FAILED
     *
     * Any unexpected error in steps 2–4 marks the product FAILED (never stuck in PENDING) and
     * is logged with its stack trace; the user only sees a short reason.
     */
    public ProductResponse create(List<MultipartFile> photos) {
        validate(photos);
        checkDailyLimit();

        // Step 1. The images are collected here, inside the transaction: after it ends,
        // the product's photo list can no longer be loaded from the database (lazy loading).
        Created created = transaction.execute(status -> {
            Product p = Product.create();
            List<ImageInput> images = new ArrayList<>();
            for (int i = 0; i < photos.size(); i++) {
                MultipartFile photo = photos.get(i);
                int position = i + 1;
                // Known trade-off: files are written before the database commit.
                // If the commit fails, the files stay as orphans. Acceptable for v1.
                String key = storage.save(p.getId(), position, photo);
                p.addPhoto(position, key, photo.getContentType(), photo.getSize());
                images.add(new ImageInput(storage.load(key), photo.getContentType()));
            }
            repository.save(p);
            return new Created(p.getId(), images);
        });

        try {
            process(created);
        } catch (RuntimeException e) {
            log.error("Processing product {} failed unexpectedly", created.id(), e);
            transaction.executeWithoutResult(status -> repository.findById(created.id()).orElseThrow()
                    .markFailed("Unexpected error while processing the photos (" + e.getClass().getSimpleName()
                            + "). Details are in the server log."));
        }

        // Not "return get(...)" directly: a call from inside the same class skips @Transactional
        // (Spring's proxy is bypassed), so get() would run without a database session.
        return transaction.execute(status -> get(created.id()));
    }

    /** Steps 2–4. */
    private void process(Created created) {
        // Step 2.
        ReadOutcome outcome = reader.read(created.id(), created.images());

        // Step 3. Nutri-Score: plain Java, instant, no AI. Only computed if the reading succeeded.
        NutriScoreResult nutriScore = outcome.success()
                ? NutriScoreCalculator.score(NutriScoreFromReading.toInput(outcome.reading()))
                : null;

        // Step 3b. Allergens: never fails the product. If the AI fails, the word list result is
        // kept and flagged (AllergenDetector).
        AllergenOutcome allergens = outcome.success()
                ? allergenDetector.detect(created.id(), outcome.reading().ingredientsText(),
                        outcome.reading().mayContainText())
                : null;

        // Step 4.
        transaction.executeWithoutResult(status -> {
            Product p = repository.findById(created.id()).orElseThrow();
            if (outcome.success()) {
                readingRepository.save(LabelReadingEntity.from(p.getId(), outcome));
                nutriScoreRepository.save(NutriScoreEntity.from(
                        p.getId(), nutriScore, NutriScoreFromReading.fruitVegAssumedZero()));
                allergenRepository.save(AllergenResultEntity.from(p.getId(), allergens));
                // DONE even when "not scored" (drinks, oils...): processing is complete either way.
                p.markDone();
            } else {
                p.markFailed(outcome.failureReason());
            }
        });

        if (!outcome.success()) {
            log.warn("Product {}: reading failed: {}", created.id(), outcome.failureReason());
        } else if (!allergens.aiChecked() && allergens.ingredientsRead()) {
            log.warn("Product {}: allergens from the word list only: {}", created.id(), allergens.aiProblem());
        }
    }

    private record Created(UUID id, List<ImageInput> images) {
    }

    // readOnly + mapping inside the method: photos are loaded lazily,
    // so they must be read while the transaction (database session) is still open.
    @Transactional(readOnly = true)
    public ProductResponse get(UUID id) {
        Product product = repository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
        return toResponse(product);
    }

    private ProductResponse toResponse(Product product) {
        return ProductResponse.from(product,
                readingRepository.findById(product.getId()).orElse(null),
                nutriScoreRepository.findById(product.getId()).orElse(null),
                allergenRepository.findById(product.getId()).orElse(null));
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> list(int page, int size) {
        Page<Product> result = repository.findAll(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        List<ProductResponse> items = result.getContent().stream()
                .map(this::toResponse)
                .toList();
        return new PageResponse<>(
                items,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    /**
     * Counted over the last 24 hours, before any AI call. With one app copy (max-replicas 1),
     * two uploads at the same moment can go 1 over the limit: acceptable for a cost cap.
     */
    private void checkDailyLimit() {
        if (repository.countByCreatedAtAfter(Instant.now().minus(Duration.ofHours(24))) >= dailyUploadLimit) {
            log.warn("Daily upload limit of {} reached", dailyUploadLimit);
            throw new UploadLimitException(dailyUploadLimit);
        }
    }

    private void validate(List<MultipartFile> photos) {
        if (photos == null || photos.isEmpty()) {
            throw new InvalidUploadException("Send at least 1 photo.");
        }
        if (photos.size() > MAX_PHOTOS) {
            throw new InvalidUploadException("Send at most " + MAX_PHOTOS + " photos, got " + photos.size() + ".");
        }
        for (MultipartFile photo : photos) {
            if (photo.isEmpty()) {
                throw new InvalidUploadException("Photo '" + photo.getOriginalFilename() + "' is empty.");
            }
            if (!ALLOWED_TYPES.contains(photo.getContentType())) {
                throw new InvalidUploadException("Photo '" + photo.getOriginalFilename()
                        + "' must be JPEG or PNG, got " + photo.getContentType() + ".");
            }
        }
        // The 10 MB per file limit is enforced by Spring itself: see application.yml.
    }
}
