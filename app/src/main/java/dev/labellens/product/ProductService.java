package dev.labellens.product;

import dev.labellens.storage.PhotoStorage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class ProductService {

    static final int MAX_PHOTOS = 3;
    static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png");

    private final ProductRepository repository;
    private final PhotoStorage storage;

    public ProductService(ProductRepository repository, PhotoStorage storage) {
        this.repository = repository;
        this.storage = storage;
    }

    /**
     * Saves the photos and creates a PENDING product.
     * Day 3 adds the AI reading step after this.
     */
    @Transactional
    public ProductResponse create(List<MultipartFile> photos) {
        validate(photos);

        Product product = Product.create();
        for (int i = 0; i < photos.size(); i++) {
            MultipartFile photo = photos.get(i);
            int position = i + 1;
            // Known trade-off: files are written before the database commit.
            // If the commit fails, the files stay as orphans. Acceptable for v1.
            String key = storage.save(product.getId(), position, photo);
            product.addPhoto(position, key, photo.getContentType(), photo.getSize());
        }
        return ProductResponse.from(repository.save(product));
    }

    // readOnly + mapping inside the method: the photos are loaded lazily,
    // so they must be read while the transaction (database session) is still open.
    @Transactional(readOnly = true)
    public ProductResponse get(UUID id) {
        return repository.findById(id)
                .map(ProductResponse::from)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> list(int page, int size) {
        Page<Product> result = repository.findAll(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return new PageResponse<>(
                result.map(ProductResponse::from).getContent(),
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
