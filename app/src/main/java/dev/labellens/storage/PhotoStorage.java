package dev.labellens.storage;

import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Saves photo files somewhere and returns a key to find them again.
 * An interface so the rest of the app doesn't care where:
 * LocalPhotoStorage now, an Azure Blob Storage version on deployment day.
 */
public interface PhotoStorage {

    /** Saves the file and returns its storage key, e.g. "3f2a.../1.jpg". */
    String save(UUID productId, int position, MultipartFile file);

    /** Reads a saved file back (needed on day 3 to send it to the AI). */
    byte[] load(String storageKey);
}
