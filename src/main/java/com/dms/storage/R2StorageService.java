package com.dms.storage;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Keeps uploads in a Cloudflare R2 bucket, so they survive the restarts and redeploys
 * that wipe the disk on hosts like Render's free plan.
 *
 * <p>R2 speaks the S3 API, so this is the AWS SDK pointed at R2's endpoint. It runs only
 * with {@code dms.storage.type=r2}; local disk stays the default.
 *
 * <p>Storage paths are object keys with the same shape local disk returns
 * ({@code 12/3/<uuid>.pdf}). A database therefore moves between the two backends
 * unchanged, once the files are copied across under the same paths.
 *
 * <p>The bucket must stay private. Like the upload folder on disk it is never served
 * directly: every read comes back through {@code SubmissionDownloadController}, which
 * re-checks ownership first.
 */
@Service
@ConditionalOnProperty(name = "dms.storage.type", havingValue = "r2")
@Slf4j
public class R2StorageService implements StorageService {

    private final S3Client s3;
    private final String bucket;

    /** What Spring calls. Every setting is required, and a missing one stops startup by name. */
    @Autowired
    public R2StorageService(
            @Value("${dms.storage.r2.endpoint:}") String endpoint,
            @Value("${dms.storage.r2.bucket:}") String bucket,
            @Value("${dms.storage.r2.access-key-id:}") String accessKeyId,
            @Value("${dms.storage.r2.secret-access-key:}") String secretAccessKey) {
        this(clientFor(endpoint, bucket, accessKeyId, secretAccessKey), bucket);
    }

    /** Tests pass in a stubbed client here, so they never touch the network. */
    R2StorageService(S3Client s3, String bucket) {
        this.s3 = s3;
        this.bucket = bucket;
        log.info("File uploads are stored in the R2 bucket {}", bucket);
    }

    @Override
    public StoredFile store(MultipartFile file, String folder) {
        // The same checks local disk runs, from the same class, so switching backends
        // cannot quietly start accepting a file the other one refuses.
        UploadChecks.Accepted upload = UploadChecks.inspect(file);

        // The key is generated, never taken from the client's filename, so an upload can
        // never land on top of an earlier version.
        String prefix = (folder == null || folder.isBlank()) ? "" : requireCleanKey(folder) + "/";
        String key = prefix + UUID.randomUUID() + "." + upload.extension();

        // Pass one: hash and count. The upload has to declare its length before it starts,
        // and the digest is what later proves the stored bytes are the submitted ones.
        Fingerprint fingerprint = fingerprint(file, upload.originalFilename());

        // Pass two: send it. The provider opens a fresh stream each time it is asked, so if
        // the SDK retries after a dropped connection it re-reads from the first byte instead
        // of resending a stream that is already half consumed.
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(upload.contentType())
                .contentLength(fingerprint.sizeBytes())
                .build();
        try {
            s3.putObject(request, RequestBody.fromContentProvider(
                    () -> openStream(file), fingerprint.sizeBytes(), upload.contentType()));
        } catch (UncheckedIOException | SdkException ex) {
            throw new StorageException("Could not save " + upload.originalFilename() + ".", ex);
        }

        return new StoredFile(
                key,
                upload.originalFilename(),
                upload.contentType(),
                fingerprint.sha256(),
                fingerprint.sizeBytes());
    }

    @Override
    public Resource load(String storagePath) {
        String key = requireCleanKey(storagePath);

        // A HEAD request answers "is it there, and how big is it" without downloading it.
        // Asking now means a missing file fails here with a clear message, the same moment
        // local disk would say so, and not halfway through a download.
        HeadObjectResponse head;
        try {
            head = s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                throw new StorageException("That file is no longer in storage.");
            }
            throw new StorageException("Could not read that file.", ex);
        } catch (SdkException ex) {
            throw new StorageException("Could not read that file.", ex);
        }
        return new ObjectResource(key, head.contentLength());
    }

    @Override
    public void delete(String storagePath) {
        String key = requireCleanKey(storagePath);
        try {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (SdkException ex) {
            // Same contract as local disk: log it, do not throw. The only caller is tidying up
            // a refused duplicate, and that refusal is what the student needs to see, not a
            // storage error. An orphaned object costs a few kilobytes and nothing else.
            log.warn("Could not delete {} from R2: {}", key, ex.getMessage());
        }
    }

    /** Releases the HTTP connection pool when the application shuts down. */
    @PreDestroy
    void close() {
        s3.close();
    }

    /**
     * Object storage has no directories to escape, so this is not the traversal guard local
     * disk needs. It keeps keys plain, though: no leading slash, no backslash, no empty,
     * {@code .} or {@code ..} segment. A path that passes here would also resolve inside the
     * upload root on disk, which is what lets one database move between the two backends.
     */
    private static String requireCleanKey(String key) {
        if (key == null || key.isBlank() || key.startsWith("/") || key.endsWith("/") || key.contains("\\")) {
            throw new StorageException("Rejected a malformed storage path.");
        }
        for (String segment : key.split("/")) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new StorageException("Rejected a malformed storage path.");
            }
        }
        return key;
    }

    private record Fingerprint(String sha256, long sizeBytes) {
    }

    /** SHA-256 and byte count of the upload, in one read that keeps nothing in memory. */
    private static Fingerprint fingerprint(MultipartFile file, String name) {
        MessageDigest digest = UploadChecks.sha256Digest();
        try (InputStream in = new DigestInputStream(file.getInputStream(), digest)) {
            long sizeBytes = in.transferTo(OutputStream.nullOutputStream());
            return new Fingerprint(HexFormat.of().formatHex(digest.digest()), sizeBytes);
        } catch (IOException ex) {
            throw new StorageException("Could not read " + name + ".", ex);
        }
    }

    /** The SDK's stream provider cannot throw a checked exception, so this wraps it. */
    private static InputStream openStream(MultipartFile file) {
        try {
            return file.getInputStream();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static S3Client clientFor(String endpoint, String bucket, String accessKeyId, String secretAccessKey) {
        List<String> missing = new ArrayList<>();
        if (endpoint == null || endpoint.isBlank()) {
            missing.add("dms.storage.r2.endpoint (R2_ENDPOINT)");
        }
        if (bucket == null || bucket.isBlank()) {
            missing.add("dms.storage.r2.bucket (R2_BUCKET)");
        }
        if (accessKeyId == null || accessKeyId.isBlank()) {
            missing.add("dms.storage.r2.access-key-id (R2_ACCESS_KEY_ID)");
        }
        if (secretAccessKey == null || secretAccessKey.isBlank()) {
            missing.add("dms.storage.r2.secret-access-key (R2_SECRET_ACCESS_KEY)");
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException("dms.storage.type=r2 also needs " + String.join(", ", missing)
                    + ". The values are on the R2 API token page of the Cloudflare dashboard.");
        }

        return S3Client.builder()
                // https://<account id>.r2.cloudflarestorage.com
                .endpointOverride(URI.create(endpoint))
                // R2 has no AWS regions. The SDK insists on one, and "auto" is R2's answer.
                .region(Region.of("auto"))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
                // Put the bucket in the path (endpoint/bucket/key) rather than in the host
                // name, so the endpoint is used exactly as configured.
                .forcePathStyle(true)
                // Recent AWS SDKs attach a CRC checksum to every upload by default, and
                // S3-compatible stores have not always accepted it (R2 refused it when the
                // change first shipped). Sending checksums only where the S3 API requires
                // one keeps uploads working whichever way the store handles it.
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .build();
    }

    /**
     * A handle on one object that starts a fresh download each time it is read. Nothing is
     * held in memory, so a 25 MB report streams from R2 to the browser, and the PDF reader
     * behind the chapter summary can open it like any other resource.
     */
    private final class ObjectResource extends AbstractResource {

        private final String key;
        private final long contentLength;

        private ObjectResource(String key, long contentLength) {
            this.key = key;
            this.contentLength = contentLength;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            try {
                return s3.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build());
            } catch (S3Exception ex) {
                if (ex.statusCode() == 404) {
                    throw new FileNotFoundException(getDescription() + " is no longer in storage");
                }
                throw new IOException("Could not read " + getDescription(), ex);
            } catch (SdkException ex) {
                throw new IOException("Could not read " + getDescription(), ex);
            }
        }

        /** load() checked this a moment ago; asking R2 again would cost a request per call. */
        @Override
        public boolean exists() {
            return true;
        }

        /** From the HEAD request load() made. The default would download the whole file to count it. */
        @Override
        public long contentLength() {
            return contentLength;
        }

        @Override
        public String getFilename() {
            return key.substring(key.lastIndexOf('/') + 1);
        }

        @Override
        public String getDescription() {
            return "R2 object [" + bucket + "/" + key + "]";
        }
    }
}
