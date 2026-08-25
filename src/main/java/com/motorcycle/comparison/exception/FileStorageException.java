package com.motorcycle.comparison.exception;

/**
 * Thrown when the file system rejects a read or a write that should have succeeded. An infrastructure failure rather
 * than a caller mistake, so it is translated to HTTP 500 — a rejected upload raises {@link IllegalArgumentException}.
 */
public class FileStorageException extends RuntimeException {

    public FileStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
