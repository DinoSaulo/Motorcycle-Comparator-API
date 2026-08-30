package com.motorcycle.comparison.exception;

/** Thrown when the file system rejects a read or a write that should have succeeded: an infrastructure failure, not a
 *  caller mistake, so it becomes HTTP 500; a rejected upload raises {@link DomainValidationException} instead. */
public class FileStorageException extends RuntimeException {

    public FileStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
