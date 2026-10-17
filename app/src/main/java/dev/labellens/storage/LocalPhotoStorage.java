package dev.labellens.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/** Stores photos in a folder on disk. Folder set by labellens.storage.local-dir. */
@Component
public class LocalPhotoStorage implements PhotoStorage {

    private final Path root;

    public LocalPhotoStorage(@Value("${labellens.storage.local-dir}") String rootDir) {
        this.root = Path.of(rootDir).toAbsolutePath().normalize();
    }

    @Override
    public String save(UUID productId, int position, MultipartFile file) {
        // The key is built from our own values, never from the uploaded file name,
        // so a malicious name like "../../etc/passwd" can't write outside the folder.
        String key = productId + "/" + position + extensionFor(file.getContentType());
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save photo " + key, e);
        }
        return key;
    }

    @Override
    public byte[] load(String storageKey) {
        try {
            return Files.readAllBytes(resolve(storageKey));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read photo " + storageKey, e);
        }
    }

    private Path resolve(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Invalid storage key: " + key);
        }
        return path;
    }

    private static String extensionFor(String contentType) {
        return "image/png".equals(contentType) ? ".png" : ".jpg";
    }
}
