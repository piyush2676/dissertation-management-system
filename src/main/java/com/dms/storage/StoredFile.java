package com.dms.storage;

/**
 * What the caller persists after a successful store: where it went, what it was
 * called, and a digest so the bytes on disk can later be proved to be the bytes
 * that were submitted.
 */
public record StoredFile(
        String storagePath,
        String originalFilename,
        String contentType,
        String sha256,
        long sizeBytes) {
}
