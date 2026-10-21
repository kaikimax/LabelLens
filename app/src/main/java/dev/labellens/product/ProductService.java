package dev.labellens.product;

import dev.labellens.ai.ImageInput;
import dev.labellens.reading.LabelReader;
import dev.labellens.reading.LabelReadingEntity;
import dev.labellens.reading.LabelReadingRepository;
import dev.labellens.reading.ReadOutcome;
import dev.labellens.storage.PhotoStorage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class ProductService {

    static final int MAX_PHOTOS = 3;
    static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png");

    private final ProductRepository repository;
    private final LabelReadingRepository readingRepository;
    private final PhotoStorage storage;
    private final LabelReader reader;
    private final TransactionTemplate transaction;

    public ProductService(ProductRepository repository,
                          LabelReadingRepository readingRepository,
                          PhotoStorage storage,
                          LabelReader reader,
                          TransactionTemplate transaction) {
        this.repository = repository;
        this.readingRepository = readingRepository;
        this.storage = storage;
        this.reader = reader;
        this.transaction = transaction;
    }

    /**
     * Saves the photos, asks the AI to read the label, saves the result.
     *
     * Three separate steps on purpose, NOT one big transaction: the AI call takes several
     * seconds, and a transaction would hold a database connection open the whole time.
     *   1. transaction: save the product as PENDING (so it exists even if step 2 crashes)
     *   2. no transaction: call the AI
     *   3. transaction: save the reading and mark the product EXTRACTED or FAILED
     */
    public ProductResponse create(List<MultipartFile> photos) {
        validate(photos);

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

        // Step 2.
        ReadOutcome outcome = reader.read(created.id(), created.images());

        // Step 3.
        transaction.executeWithoutResult(status -> {
            Product p = repository.findById(created.id()).orElseThrow();
            if (outcome.success()) {
                readingRepository.save(LabelReadingEntity.from(p.getId(), outcome));
                p.markExtracted();
            } else {
                p.markFailed(outcome.failureReason());
            }
        });

        return transaction.execute(status -> get(created.id()));
    }

    private record Created(UUID id, List<ImageInput> images) {
    }

    // readOnly + mapping inside the method: photos are loaded lazily,
    // so they must be read while the transaction (database session) is still open.
    @Transactional(readOnly = true)
    public ProductResponse get(UUID id) {
        Product product = repository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
        return ProductResponse.from(product, readingRepository.findById(id).orElse(null));
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> list(int page, int size) {
        Page<Product> result = repository.findAll(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        List<ProductResponse> items = result.getContent().stream()
                .map(p -> ProductResponse.from(p, readingRepository.findById(p.getId()).orElse(null)))
                .toList();
        return new PageResponse<>(
                items,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
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
