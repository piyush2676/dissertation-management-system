package com.dms.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * Interface seam at an external dependency. Local disk today; object storage later
 * without touching a caller. Callers deal in opaque storage paths and never in
 * filesystem concepts.
 */
public interface StorageService {

    /**
     * Writes the file under the given logical folder and returns what the caller
     * must persist to find and verify it again.
     *
     * @param folder logical grouping, e.g. {@code "12/3"} for allocation 12, milestone 3
     */
    StoredFile store(MultipartFile file, String folder);

    /** Reads a file back by the storage path returned from {@link #store}. */
    Resource load(String storagePath);

    /** Removes a stored file. Submissions never call this -- versions are append-only. */
    void delete(String storagePath);
}
