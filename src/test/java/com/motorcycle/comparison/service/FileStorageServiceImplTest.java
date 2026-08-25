package com.motorcycle.comparison.service;

import com.motorcycle.comparison.exception.FileStorageException;
import com.motorcycle.comparison.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("FileStorageService")
class FileStorageServiceImplTest {

    /** Bytes rather than megabytes so the size boundary can be crossed with a real array instead of a huge one. */
    private static final long MAX_FILE_SIZE_BYTES = 1024;

    private static final String STORED_NAME_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png|webp)";
    private static final String ABSENT_STORED_NAME = "00000000-0000-0000-0000-000000000000.png";

    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] WEBP_SIGNATURE = {0x52, 0x49, 0x46, 0x46, 0x24, 0x00, 0x00, 0x00, 0x57, 0x45, 0x42, 0x50};
    /** A RIFF container like WebP, but carrying the WAVE form type at byte 8. */
    private static final byte[] WAV_SIGNATURE = {0x52, 0x49, 0x46, 0x46, 0x24, 0x00, 0x00, 0x00, 0x57, 0x41, 0x56, 0x45};
    private static final byte[] RIFF_ONLY = {0x52, 0x49, 0x46, 0x46};

    @TempDir
    Path tempDir;

    private Path storageRoot;
    private FileStorageService fileStorageService;

    @BeforeEach
    void setUp() {
        // Two levels below the temp root so a "../../pom.xml" name has a real file to aim at.
        storageRoot = tempDir.resolve("var").resolve("motorcycles");
        fileStorageService = new FileStorageServiceImpl(storageRoot.toString(), DataSize.ofBytes(MAX_FILE_SIZE_BYTES));
    }

    private static byte[] signatureFor(String contentType) {
        return switch (contentType.split(";")[0].trim().toLowerCase(Locale.ROOT)) {
            case "image/jpeg" -> JPEG_SIGNATURE;
            case "image/png" -> PNG_SIGNATURE;
            case "image/webp" -> WEBP_SIGNATURE;
            default -> throw new IllegalArgumentException("No fixture signature for " + contentType);
        };
    }

    private static byte[] concat(byte[] head, byte[] tail) {
        byte[] content = new byte[head.length + tail.length];
        System.arraycopy(head, 0, content, 0, head.length);
        System.arraycopy(tail, 0, content, head.length, tail.length);
        return content;
    }

    private static byte[] payload() {
        return "pixel data".getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] imageBytes(String contentType) {
        return concat(signatureFor(contentType), payload());
    }

    /** Resolves the {@code @CsvSource} keys naming the bytes actually uploaded, as opposed to the declared type. */
    private static byte[] fixtureNamed(String fixture) {
        return switch (fixture) {
            case "JPEG" -> imageBytes("image/jpeg");
            case "PNG" -> imageBytes("image/png");
            case "WEBP" -> imageBytes("image/webp");
            case "WAV" -> concat(WAV_SIGNATURE, payload());
            case "TEXT" -> "this file is plain text pretending to be an image".getBytes(StandardCharsets.UTF_8);
            default -> throw new IllegalArgumentException("No fixture named " + fixture);
        };
    }

    private static byte[] pngOfExactly(int totalBytes) {
        byte[] content = new byte[totalBytes];
        System.arraycopy(PNG_SIGNATURE, 0, content, 0, PNG_SIGNATURE.length);
        return content;
    }

    private static MultipartFile upload(String contentType, byte[] content) {
        return new MockMultipartFile("image", "chosen-by-the-client.png", contentType, content);
    }

    private static MultipartFile validUpload(String contentType) {
        return upload(contentType, imageBytes(contentType));
    }

    /** MockMultipartFile never fails, so an infrastructure failure has to be staged with a stream that refuses to be read. */
    private static InputStream unreadableStream() {
        return new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("the disk went away mid-write");
            }
        };
    }

    private static MultipartFile uploadDeclaredAsPng(long size) {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getSize()).thenReturn(size);
        when(file.getContentType()).thenReturn("image/png");
        return file;
    }

    private long storedFileCount() throws IOException {
        try (Stream<Path> files = Files.list(storageRoot)) {
            return files.count();
        }
    }

    @Nested
    @DisplayName("storage directory")
    class StorageDirectory {

        @Test
        @DisplayName("creates the configured directory when it does not exist yet")
        void createsTheStorageDirectory() {
            Path notYetCreated = tempDir.resolve("brand").resolve("new").resolve("images");

            new FileStorageServiceImpl(notYetCreated.toString(), DataSize.ofMegabytes(5));

            assertThat(notYetCreated).isDirectory();
        }

        @Test
        @DisplayName("reuses a storage directory that already exists")
        void acceptsAnExistingStorageDirectory() {
            assertThatCode(() -> new FileStorageServiceImpl(storageRoot.toString(), DataSize.ofMegabytes(5))).doesNotThrowAnyException();
            assertThat(storageRoot).isDirectory();
        }

        @Test
        @DisplayName("fails at construction time when the configured location is a regular file")
        void failsWhenTheLocationIsARegularFile() throws IOException {
            Path blocker = Files.writeString(tempDir.resolve("not-a-directory"), "occupied");

            assertThatThrownBy(() -> new FileStorageServiceImpl(blocker.toString(), DataSize.ofMegabytes(5)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("storage directory");
        }
    }

    @Nested
    @DisplayName("storing an upload")
    class StoreFile {

        @ParameterizedTest(name = "{0} is stored as {1}")
        @CsvSource({
                "image/jpeg, .jpg",
                "image/png, .png",
                "image/webp, .webp"
        })
        @DisplayName("writes every supported format under a generated name")
        void storesEverySupportedFormat(String contentType, String extension) throws IOException {
            byte[] content = imageBytes(contentType);

            String fileName = fileStorageService.storeFile(upload(contentType, content));

            assertThat(fileName).matches(STORED_NAME_PATTERN).endsWith(extension);
            assertThat(storageRoot.resolve(fileName)).exists().hasBinaryContent(content);
            assertThat(storedFileCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("never reuses the name the client sent")
        void discardsTheClientFileName() {
            String fileName = fileStorageService.storeFile(validUpload("image/png"));

            assertThat(fileName).doesNotContain("chosen-by-the-client");
        }

        @Test
        @DisplayName("rejects a missing file")
        void rejectsANullFile() {
            assertThatThrownBy(() -> fileStorageService.storeFile(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("required");
        }

        @Test
        @DisplayName("rejects an upload with no bytes")
        void rejectsAnEmptyFile() throws IOException {
            MultipartFile empty = upload("image/png", new byte[0]);

            assertThatThrownBy(() -> fileStorageService.storeFile(empty))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("empty");
            assertThat(storedFileCount()).isZero();
        }

        @Test
        @DisplayName("accepts an upload sitting exactly on the size limit")
        void acceptsAFileExactlyAtTheSizeLimit() {
            byte[] content = pngOfExactly((int) MAX_FILE_SIZE_BYTES);

            String fileName = fileStorageService.storeFile(upload("image/png", content));

            assertThat(storageRoot.resolve(fileName)).exists().hasBinaryContent(content);
        }

        @Test
        @DisplayName("rejects an upload one byte over the size limit")
        void rejectsAFileOneByteOverTheSizeLimit() throws IOException {
            MultipartFile tooBig = upload("image/png", pngOfExactly((int) MAX_FILE_SIZE_BYTES + 1));

            assertThatThrownBy(() -> fileStorageService.storeFile(tooBig))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("maximum size");
            assertThat(storedFileCount()).isZero();
        }

        @ParameterizedTest(name = "\"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = {
                "application/pdf",
                "text/plain",
                "image/gif",
                "image/svg+xml",
                "image/tiff",
                "application/octet-stream",
                "multipart/form-data"
        })
        @DisplayName("rejects a content type outside the supported image formats")
        void rejectsUnsupportedContentTypes(String contentType) throws IOException {
            MultipartFile file = upload(contentType, imageBytes("image/png"));

            assertThatThrownBy(() -> fileStorageService.storeFile(file))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unsupported image type");
            assertThat(storedFileCount()).isZero();
        }

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {
                "IMAGE/PNG",
                "Image/Png",
                "image/png; charset=binary",
                "image/png;charset=UTF-8",
                " image/png "
        })
        @DisplayName("accepts a declared type whose casing or parameters differ from the canonical form")
        void normalisesTheDeclaredContentType(String contentType) {
            String fileName = fileStorageService.storeFile(upload(contentType, imageBytes("image/png")));

            assertThat(fileName).matches(STORED_NAME_PATTERN).endsWith(".png");
        }

        @ParameterizedTest(name = "{0} declared over {1} bytes")
        @CsvSource({
                "image/png, JPEG",
                "image/png, WEBP",
                "image/png, TEXT",
                "image/jpeg, PNG",
                "image/jpeg, WEBP",
                "image/jpeg, TEXT",
                "image/webp, PNG",
                "image/webp, JPEG",
                "image/webp, TEXT"
        })
        @DisplayName("rejects an upload whose bytes contradict the declared type")
        void rejectsContentThatDoesNotMatchTheDeclaredType(String contentType, String fixture) throws IOException {
            MultipartFile file = upload(contentType, fixtureNamed(fixture));

            assertThatThrownBy(() -> fileStorageService.storeFile(file))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("does not match its declared type");
            assertThat(storedFileCount()).isZero();
        }

        @Test
        @DisplayName("rejects a RIFF container that is not WebP, such as a WAV file")
        void rejectsARiffContainerWithoutTheWebpMarker() throws IOException {
            MultipartFile wav = upload("image/webp", fixtureNamed("WAV"));

            assertThatThrownBy(() -> fileStorageService.storeFile(wav))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("does not match its declared type");
            assertThat(storedFileCount()).isZero();
        }

        @Test
        @DisplayName("rejects a RIFF header that stops before the form type can be read")
        void rejectsARiffHeaderTooShortToCarryTheFormType() {
            MultipartFile truncated = upload("image/webp", RIFF_ONLY);

            assertThatThrownBy(() -> fileStorageService.storeFile(truncated))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("does not match its declared type");
        }

        @Test
        @DisplayName("rejects an upload shorter than the signature it claims to have")
        void rejectsASignatureCutShort() {
            MultipartFile truncated = upload("image/png", new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47});

            assertThatThrownBy(() -> fileStorageService.storeFile(truncated))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("does not match its declared type");
        }

        @Test
        @DisplayName("rejects a signature that diverges only in its last byte")
        void rejectsASignatureDivergingInTheLastByte() {
            byte[] almostPng = concat(new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0B}, payload());

            assertThatThrownBy(() -> fileStorageService.storeFile(upload("image/png", almostPng)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("does not match its declared type");
        }

        @Test
        @DisplayName("raises a storage failure, not a rejection, when the signature cannot be read")
        void raisesAStorageFailureWhenTheSignatureCannotBeRead() throws IOException {
            MultipartFile file = uploadDeclaredAsPng(64);
            when(file.getInputStream()).thenThrow(new IOException("the disk went away"));

            assertThatThrownBy(() -> fileStorageService.storeFile(file))
                    .isInstanceOf(FileStorageException.class)
                    .hasMessageContaining("Could not read the uploaded image");
        }

        @Test
        @DisplayName("raises a storage failure, not a rejection, when the bytes cannot be written")
        void raisesAStorageFailureWhenTheBytesCannotBeWritten() throws IOException {
            byte[] content = imageBytes("image/png");
            MultipartFile file = uploadDeclaredAsPng(content.length);
            when(file.getInputStream()).thenReturn(new ByteArrayInputStream(content), unreadableStream());

            assertThatThrownBy(() -> fileStorageService.storeFile(file))
                    .isInstanceOf(FileStorageException.class)
                    .hasMessageContaining("Could not store image");
            // Current behaviour, pinned rather than endorsed: Files.copy leaves the partial file behind, so a
            // failed upload orphans a name that was never handed to any caller.
            assertThat(storedFileCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("gives each upload its own name even when the bytes are identical")
        void generatesADistinctNamePerUpload() throws IOException {
            byte[] content = imageBytes("image/png");

            String first = fileStorageService.storeFile(upload("image/png", content));
            String second = fileStorageService.storeFile(upload("image/png", content));

            assertThat(first).isNotEqualTo(second);
            assertThat(storageRoot.resolve(first)).exists().hasBinaryContent(content);
            assertThat(storageRoot.resolve(second)).exists().hasBinaryContent(content);
            assertThat(storedFileCount()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("deleting a stored image")
    class DeleteFile {

        @Test
        @DisplayName("removes the file and reports that it did")
        void deletesAStoredFile() throws IOException {
            String fileName = fileStorageService.storeFile(validUpload("image/jpeg"));

            assertThat(fileStorageService.deleteFile(fileName)).isTrue();
            assertThat(storageRoot.resolve(fileName)).doesNotExist();
            assertThat(storedFileCount()).isZero();
        }

        @Test
        @DisplayName("keeps the other stored images when one is deleted")
        void deletesOnlyTheRequestedFile() {
            String kept = fileStorageService.storeFile(validUpload("image/png"));
            String removed = fileStorageService.storeFile(validUpload("image/png"));

            fileStorageService.deleteFile(removed);

            assertThat(storageRoot.resolve(kept)).exists();
        }

        @Test
        @DisplayName("reports false instead of throwing when the file is already gone")
        void reportsFalseWhenTheFileIsAlreadyGone() {
            String fileName = fileStorageService.storeFile(validUpload("image/webp"));
            fileStorageService.deleteFile(fileName);

            assertThat(fileStorageService.deleteFile(fileName)).isFalse();
        }

        @Test
        @DisplayName("reports false instead of throwing for a well-formed name that was never issued")
        void reportsFalseForANameNeverIssued() {
            assertThat(fileStorageService.deleteFile(ABSENT_STORED_NAME)).isFalse();
        }

        @ParameterizedTest(name = "\"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = {
                "../../pom.xml",
                "..\\..\\pom.xml",
                "../motorcycles",
                "/etc/passwd",
                "C:\\Windows\\System32\\drivers\\etc\\hosts",
                "not-a-uuid.png",
                "00000000-0000-0000-0000-000000000000.gif",
                "00000000-0000-0000-0000-000000000000",
                "00000000-0000-0000-0000-000000000000.png.exe",
                "00000000-0000-0000-0000-00000000000g.png"
        })
        @DisplayName("refuses a name it never issued without throwing")
        void refusesNamesItNeverIssued(String fileName) {
            assertThat(fileStorageService.deleteFile(fileName)).isFalse();
        }

        @Test
        @DisplayName("reports false instead of throwing when the file system refuses the removal")
        void reportsFalseWhenTheFileSystemRefusesTheRemoval() throws IOException {
            // A non-empty directory wearing a stored name is the portable way to make deleteIfExists fail.
            Path stubborn = Files.createDirectory(storageRoot.resolve(ABSENT_STORED_NAME));
            Files.writeString(stubborn.resolve("blocker.txt"), "keeps the directory non-empty");

            assertThat(fileStorageService.deleteFile(ABSENT_STORED_NAME)).isFalse();
            assertThat(stubborn).exists();
        }

        @Test
        @DisplayName("leaves files outside the storage root untouched when the name climbs the tree")
        void leavesFilesOutsideTheStorageRootUntouched() throws IOException {
            Path decoyPom = Files.writeString(tempDir.resolve("pom.xml"), "<project/>");

            assertThat(fileStorageService.deleteFile("../../pom.xml")).isFalse();
            assertThat(fileStorageService.deleteFile("..\\..\\pom.xml")).isFalse();
            assertThat(fileStorageService.deleteFile(decoyPom.toAbsolutePath().toString())).isFalse();

            assertThat(decoyPom).exists().hasContent("<project/>");
        }
    }

    @Nested
    @DisplayName("loading a stored image")
    class LoadFileAsResource {

        @Test
        @DisplayName("returns a readable resource carrying the stored bytes")
        void returnsAReadableResource() throws IOException {
            byte[] content = imageBytes("image/webp");
            String fileName = fileStorageService.storeFile(upload("image/webp", content));

            Resource resource = fileStorageService.loadFileAsResource(fileName);

            assertThat(resource.exists()).isTrue();
            assertThat(resource.isReadable()).isTrue();
            assertThat(resource.getFilename()).isEqualTo(fileName);
            assertThat(resource.getContentAsByteArray()).isEqualTo(content);
        }

        @Test
        @DisplayName("reports the image as missing once it has been deleted")
        void reportsADeletedImageAsMissing() {
            String fileName = fileStorageService.storeFile(validUpload("image/png"));
            fileStorageService.deleteFile(fileName);

            assertThatThrownBy(() -> fileStorageService.loadFileAsResource(fileName))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(fileName);
        }

        @ParameterizedTest(name = "\"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = {
                "00000000-0000-0000-0000-000000000000.png",
                "not-a-uuid.png",
                "../../pom.xml",
                "..\\..\\pom.xml",
                "/etc/passwd",
                "00000000-0000-0000-0000-000000000000.gif"
        })
        @DisplayName("reports a name it never issued as missing rather than serving it")
        void reportsUnknownNamesAsMissing(String fileName) {
            assertThatThrownBy(() -> fileStorageService.loadFileAsResource(fileName))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Image");
        }

        @Test
        @DisplayName("reports a stored name that turns out to be a directory as missing")
        void reportsADirectoryAsMissing() throws IOException {
            Files.createDirectory(storageRoot.resolve(ABSENT_STORED_NAME));

            assertThatThrownBy(() -> fileStorageService.loadFileAsResource(ABSENT_STORED_NAME))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(ABSENT_STORED_NAME);
        }

        @Test
        @DisplayName("refuses to serve a readable file that sits outside the storage root")
        void refusesToServeAFileOutsideTheStorageRoot() throws IOException {
            Path decoyPom = Files.writeString(tempDir.resolve("pom.xml"), "<project/>");

            assertThatThrownBy(() -> fileStorageService.loadFileAsResource("../../pom.xml"))
                    .isInstanceOf(ResourceNotFoundException.class);
            assertThatThrownBy(() -> fileStorageService.loadFileAsResource(decoyPom.toAbsolutePath().toString()))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
