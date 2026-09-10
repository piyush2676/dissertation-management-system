package com.dms.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Writes uploads under a configured root directory.
 *
 * <p>The upload root is deliberately not a static resource directory. Every read
 * goes back through a controller that re-checks ownership, so knowing a storage
 * path is not enough to fetch a file.
 */
@Service
@Slf4j
public class LocalDiskStorageService implements StorageService {

    /** Dissertation deliverables only. Anything else is rejected before a byte is written. */
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "doc", "docx");

    private final Path root;

    public LocalDiskStorageService(@Value("${dms.storage.root:uploads}") String root) {
        this.root = Paths.get(root).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.root);
        } catch (IOException ex) {
            throw new StorageException("Could not create the upload directory at " + this.root, ex);
        }
        log.info("File uploads are stored under {}", this.root);
    }

    @Override
    public StoredFile store(MultipartFile file, String folder) {
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

        // The stored name is generated, never taken from the client, so a crafted
        // filename cannot escape the root or overwrite an existing version.
        Path directory = resolveInsideRoot(folder);
        String storedName = UUID.randomUUID() + "." + extension;
        Path target = directory.resolve(storedName);

        try {
            Files.createDirectories(directory);
        } catch (IOException ex) {
            throw new StorageException("Could not create the folder for this upload.", ex);
        }

        MessageDigest digest = sha256Digest();
        try (InputStream in = file.getInputStream();
             DigestInputStream digesting = new DigestInputStream(in, digest)) {
            Files.copy(digesting, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new StorageException("Could not save " + originalFilename + ".", ex);
        }

        long sizeBytes;
        try {
            sizeBytes = Files.size(target);
        } catch (IOException ex) {
            throw new StorageException("Could not measure the saved file.", ex);
        }
        if (sizeBytes == 0) {
            quietlyDelete(target);
            throw new StorageException("That file is empty.");
        }

        String storagePath = root.relativize(target).toString().replace('\\', '/');
        return new StoredFile(
                storagePath,
                originalFilename,
                contentType,
                HexFormat.of().formatHex(digest.digest()),
                sizeBytes);
    }

    @Override
    public Resource load(String storagePath) {
        Path file = resolveInsideRoot(storagePath);
        if (!Files.isReadable(file)) {
            throw new StorageException("That file is no longer on disk.");
        }
        try {
            return new UrlResource(file.toUri());
        } catch (IOException ex) {
            throw new StorageException("Could not read that file.", ex);
        }
    }

    @Override
    public void delete(String storagePath) {
        quietlyDelete(resolveInsideRoot(storagePath));
    }

    /**
     * Resolves a caller-supplied path against the root and refuses anything that
     * escapes it. This is the check that makes a stored path safe to round-trip
     * through the database.
     */
    private Path resolveInsideRoot(String relative) {
        Path resolved = root.resolve(relative == null ? "" : relative).normalize();
        if (!resolved.startsWith(root)) {
            throw new StorageException("Rejected a path outside the upload root.");
        }
        return resolved;
    }

    private static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required but unavailable", ex);
        }
    }

    private void quietlyDelete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            log.warn("Could not delete {}: {}", path, ex.getMessage());
        }
    }
}
