package com.motorcycle.comparison.service;

import com.motorcycle.comparison.exception.FileStorageException;
import com.motorcycle.comparison.exception.ResourceNotFoundException;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * Binary storage for motorcycle images. Deals in file names only — turning a name into the public
 * {@code /api/v1/images/motorcycles/...} URL belongs to the caller, so swapping in an S3 adapter changes nothing above.
 */
public interface FileStorageService {

    /**
     * Validates and stores an upload under a freshly generated name.
     *
     * @return the stored file name, never the caller's own
     * @throws IllegalArgumentException if the file is empty, too large, or not a supported image (HTTP 400)
     * @throws FileStorageException     if the bytes could not be written (HTTP 500)
     */
    String storeFile(MultipartFile file);

    /**
     * Removes a stored file. Deliberately lenient: a name this service never issued, an already-deleted file or an
     * unreadable directory are logged and reported, never thrown, so an orphan file cannot block an entity deletion.
     *
     * @return {@code true} only when a file was actually deleted
     */
    boolean deleteFile(String fileName);

    /**
     * @throws ResourceNotFoundException if the name is not one this service issued, or the file is gone (HTTP 404)
     * @throws FileStorageException      if the file exists but could not be opened (HTTP 500)
     */
    Resource loadFileAsResource(String fileName);
}
