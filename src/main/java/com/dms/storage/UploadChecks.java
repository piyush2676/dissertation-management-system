package com.dms.storage;

import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Set;

/**
 * The checks every upload passes before any backend keeps it. They used to live in
 * {@link LocalDiskStorageService}; they moved here unchanged when R2 became a second
 * backend, so local disk and object storage accept exactly the same files and the
 * rules cannot drift apart.
 */
final class UploadChecks {

    /** Dissertation deliverables only. Anything else is rejected before a byte is written. */
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "doc", "docx");

    private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F', '-'};
    /** A .docx is a zip archive. */
    private static final byte[] ZIP_MAGIC = {'P', 'K', 3, 4};
    /** A legacy .doc is an OLE2 compound file. */
    private static final byte[] OLE_MAGIC = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
            (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};

    private UploadChecks() {
    }

    /**
     * What an accepted upload turned out to be. The content type here is the one the
     * file's own bytes support, not the one the browser claimed.
     */
    record Accepted(String originalFilename, String extension, String contentType) {
    }

    /** Runs every check in order and either accepts the upload or throws a user-facing reason. */
    static Accepted inspect(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new StorageException("Choose a file to upload.");
        }

        String originalFilename = StringUtils.cleanPath(
                file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename());
        String extension = extensionOf(originalFilename);

        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new StorageException("Upload a PDF or Word document. Received: ." + extension);
        }
        String contentType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new StorageException("That file type is not accepted: " + contentType);
        }
        // The extension and the content type are both claims made by the browser. The first
        // bytes are the file's own account of itself, and they have to agree with the claim.
        return new Accepted(originalFilename, extension, verifiedContentType(file, extension));
    }

    static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required but unavailable", ex);
        }
    }

    /** The content type the file's own header supports for this extension, or a rejection. */
    private static String verifiedContentType(MultipartFile file, String extension) {
        byte[] head = new byte[8];
        int read;
        try (InputStream in = file.getInputStream()) {
            read = in.readNBytes(head, 0, head.length);
        } catch (IOException ex) {
            throw new StorageException("Could not read the uploaded file.", ex);
        }
        return switch (extension) {
            case "pdf" -> startsWith(head, read, PDF_MAGIC)
                    ? "application/pdf" : notA("PDF");
            case "docx" -> startsWith(head, read, ZIP_MAGIC)
                    ? "application/vnd.openxmlformats-officedocument.wordprocessingml.document" : notA("Word (.docx)");
            case "doc" -> startsWith(head, read, OLE_MAGIC)
                    ? "application/msword" : notA("Word (.doc)");
            default -> throw new StorageException("Upload a PDF or Word document. Received: ." + extension);
        };
    }

    private static boolean startsWith(byte[] head, int read, byte[] magic) {
        if (read < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (head[i] != magic[i]) {
                return false;
            }
        }
        return true;
    }

    private static String notA(String kind) {
        throw new StorageException("That file is not a real " + kind
                + " document, whatever its name says. Export it again and upload the result.");
    }

    private static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
