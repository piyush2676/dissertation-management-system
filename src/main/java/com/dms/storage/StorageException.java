package com.dms.storage;

/** A file could not be written or read back. Surfaced to the user, never swallowed. */
public class StorageException extends RuntimeException {

    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
