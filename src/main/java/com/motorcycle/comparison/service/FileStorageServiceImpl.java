package com.motorcycle.comparison.service;

import com.motorcycle.comparison.exception.DomainValidationException;
import com.motorcycle.comparison.exception.FileStorageException;
import com.motorcycle.comparison.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Stores motorcycle images on the local file system. Every name it hands out is a UUID plus a known extension, which
 * is also the only shape it will read or delete — the client's own file name is never trusted, or even kept.
 */
@Service
@Slf4j
public class FileStorageServiceImpl implements FileStorageService {

    /** Long enough for the longest signature checked below: WebP's "WEBP" marker ends at byte 12. */
    private static final int SIGNATURE_BYTES = 12;

    private static final Pattern STORED_FILE_NAME = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png|webp)");

    private final Path storageRoot;
    private final DataSize maxFileSize;

    public FileStorageServiceImpl(
            @Value("${app.storage.images.location:./uploads/motorcycles}") String location,
            @Value("${app.storage.images.max-file-size:5MB}") DataSize maxFileSize) {
        this.storageRoot = Paths.get(location).toAbsolutePath().normalize();
        this.maxFileSize = maxFileSize;
        try {
            // Fail at startup rather than on the first upload, when an admin is already waiting on the response.
            Files.createDirectories(this.storageRoot);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot create the image storage directory " + this.storageRoot, ex);
        }
        log.info("Motorcycle images are stored in {} (max {})", this.storageRoot, maxFileSize);
    }

    @Override
    public String storeFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new DomainValidationException("An image file is required and cannot be empty");
        }
        if (file.getSize() > maxFileSize.toBytes()) {
            throw new DomainValidationException("Image exceeds the maximum size of " + describe(maxFileSize));
        }

        ImageFormat format = resolveFormat(file);
        String fileName = UUID.randomUUID() + format.extension;
        Path target = storageRoot.resolve(fileName);
        // No REPLACE_EXISTING: the name is a UUID nobody else holds, so a collision is a bug worth
        // failing on rather than a previous upload worth silently overwriting.
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target);
        } catch (IOException ex) {
            throw new FileStorageException("Could not store image " + fileName, ex);
        }

        log.info("Stored image {} ({} bytes, {})", fileName, file.getSize(), format.contentType);
        return fileName;
    }

    @Override
    public boolean deleteFile(String fileName) {
        Optional<Path> target = resolveStoredPath(fileName);
        if (target.isEmpty()) {
            log.warn("Refusing to delete image with an unexpected name: {}", fileName);
            return false;
        }
        try {
            boolean deleted = Files.deleteIfExists(target.get());
            if (!deleted) {
                log.warn("Image {} was already missing from {}", fileName, storageRoot);
            }
            return deleted;
        } catch (IOException ex) {
            // Never fatal: an orphan file on disk must not stop the motorcycle row from being deleted.
            log.warn("Could not delete image {}: {}", fileName, ex.getMessage());
            return false;
        }
    }

    @Override
    public Resource loadFileAsResource(String fileName) {
        Path target = resolveStoredPath(fileName).orElseThrow(() -> ResourceNotFoundException.of("Image", fileName));
        try {
            Resource resource = new UrlResource(target.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw ResourceNotFoundException.of("Image", fileName);
            }
            return resource;
        } catch (MalformedURLException ex) {
            throw new FileStorageException("Could not read image " + fileName, ex);
        }
    }

    /** Kilobytes below one megabyte: a limit configured as 512KB must not be reported to the client as "0 MB". */
    private static String describe(DataSize size) {
        return size.toBytes() >= DataSize.ofMegabytes(1).toBytes() ? size.toMegabytes() + " MB" : size.toKilobytes() + " KB";
    }

    /**
     * Accepting only the exact shape this service generates is what keeps a crafted name such as {@code ../../pom.xml}
     * out of the file system; the {@code startsWith} check below is the belt to that regex's braces.
     */
    private Optional<Path> resolveStoredPath(String fileName) {
        if (fileName == null || !STORED_FILE_NAME.matcher(fileName).matches()) {
            return Optional.empty();
        }
        Path target = storageRoot.resolve(fileName).normalize();
        return target.startsWith(storageRoot) ? Optional.of(target) : Optional.empty();
    }

    private ImageFormat resolveFormat(MultipartFile file) {
        ImageFormat declared = ImageFormat.ofContentType(file.getContentType())
                .orElseThrow(() -> new DomainValidationException("Unsupported image type " + file.getContentType() + ". Supported types: " + ImageFormat.supportedContentTypes()));

        // The declared content type is just another client-supplied header: confirm the bytes agree before
        // committing a name and an extension that the serving endpoint will later trust.
        if (!declared.signature.test(readSignature(file))) {
            throw new DomainValidationException("File content does not match its declared type " + declared.contentType);
        }
        return declared;
    }

    /**
     * Opens the part a second time — storeFile reads it again to copy it. Safe with the servlet multipart resolver
     * Spring Boot configures, whose getInputStream() hands out a fresh stream over the buffered part every call.
     */
    private static byte[] readSignature(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(SIGNATURE_BYTES);
        } catch (IOException ex) {
            throw new FileStorageException("Could not read the uploaded image", ex);
        }
    }

    /** The accepted formats, each pinned to the byte signature its content has to start with. */
    private enum ImageFormat {

        JPEG("image/jpeg", ".jpg", header -> startsWith(header, 0, 0xFF, 0xD8, 0xFF)),
        PNG("image/png", ".png", header -> startsWith(header, 0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)),
        // A WebP file is a RIFF container whose form type sits at byte 8; without it every .wav would pass.
        WEBP("image/webp", ".webp", header -> startsWith(header, 0, 0x52, 0x49, 0x46, 0x46) && startsWith(header, 8, 0x57, 0x45, 0x42, 0x50));

        private final String contentType;
        private final String extension;
        private final Predicate<byte[]> signature;

        ImageFormat(String contentType, String extension, Predicate<byte[]> signature) {
            this.contentType = contentType;
            this.extension = extension;
            this.signature = signature;
        }

        static Optional<ImageFormat> ofContentType(String contentType) {
            if (contentType == null) {
                return Optional.empty();
            }
            // Browsers may append parameters, and the casing of a media type is not significant.
            String type = contentType.split(";")[0].trim().toLowerCase(Locale.ROOT);
            return Arrays.stream(values()).filter(format -> format.contentType.equals(type)).findFirst();
        }

        static String supportedContentTypes() {
            return Arrays.stream(values()).map(format -> format.contentType).collect(Collectors.joining(", "));
        }

        private static boolean startsWith(byte[] header, int offset, int... expected) {
            if (header.length < offset + expected.length) {
                return false;
            }
            for (int i = 0; i < expected.length; i++) {
                if ((header[offset + i] & 0xFF) != expected[i]) {
                    return false;
                }
            }
            return true;
        }
    }
}
