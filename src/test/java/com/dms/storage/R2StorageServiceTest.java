package com.dms.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * R2 behind a stubbed S3 client: the same gate as local disk, the same StoredFile out,
 * and failures that read the way local disk's do. Nothing here touches the network.
 */
@ExtendWith(MockitoExtension.class)
class R2StorageServiceTest {

    private static final String PDF = "application/pdf";
    private static final String BUCKET = "dms-test";
    private static final byte[] REPORT = "%PDF-1.4\nchapter one\n%%EOF".getBytes(StandardCharsets.ISO_8859_1);

    @Mock private S3Client s3;
    private R2StorageService storage;

    @BeforeEach
    void setUp() {
        storage = new R2StorageService(s3, BUCKET);
    }

    private static MockMultipartFile report() {
        return new MockMultipartFile("file", "report.pdf", PDF, REPORT);
    }

    @Test
    void aRealPdfIsUploadedUnderAGeneratedKeyInItsFolder() {
        StoredFile stored = storage.store(report(), "12/3");

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3).putObject(request.capture(), any(RequestBody.class));
        assertThat(request.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(request.getValue().key())
                .isEqualTo(stored.storagePath())
                .matches("12/3/[0-9a-f-]{36}\\.pdf");
        assertThat(request.getValue().contentType()).isEqualTo(PDF);
        assertThat(request.getValue().contentLength()).isEqualTo(REPORT.length);
    }

    @Test
    void bothBackendsDescribeTheSameFileTheSameWay(@TempDir Path root) {
        // The digest is what provenance verifies later, so a backend switch must not change it.
        StoredFile onDisk = new LocalDiskStorageService(root.toString()).store(report(), "12/3");
        StoredFile inR2 = storage.store(report(), "12/3");

        assertThat(inR2.sha256()).isEqualTo(onDisk.sha256());
        assertThat(inR2.sizeBytes()).isEqualTo(onDisk.sizeBytes());
        assertThat(inR2.contentType()).isEqualTo(onDisk.contentType());
        assertThat(inR2.originalFilename()).isEqualTo(onDisk.originalFilename());
    }

    @Test
    void theUploadBodyCanBeReadAgainWhenTheSdkRetries() throws Exception {
        storage.store(report(), "12/3");

        ArgumentCaptor<RequestBody> body = ArgumentCaptor.forClass(RequestBody.class);
        verify(s3).putObject(any(PutObjectRequest.class), body.capture());
        // A retry asks the provider for a new stream; each one must start from the first byte.
        try (InputStream first = body.getValue().contentStreamProvider().newStream();
             InputStream second = body.getValue().contentStreamProvider().newStream()) {
            assertThat(first.readAllBytes()).isEqualTo(REPORT);
            assertThat(second.readAllBytes()).isEqualTo(REPORT);
        }
    }

    @Test
    void aFileTheChecksRefuseNeverReachesTheBucket() {
        MockMultipartFile disguised = new MockMultipartFile("file", "x.pdf", PDF,
                "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.ISO_8859_1));

        assertThatThrownBy(() -> storage.store(disguised, "12/3"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("not a real PDF");
        verifyNoInteractions(s3);
    }

    @Test
    void aFailedUploadIsReportedAsAStorageError() {
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(SdkClientException.create("connection reset"));

        assertThatThrownBy(() -> storage.store(report(), "12/3"))
                .isInstanceOf(StorageException.class)
                .hasMessage("Could not save report.pdf.");
    }

    @Test
    void loadKnowsTheLengthWithoutDownloadingAndStreamsOnRead() throws Exception {
        when(s3.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().contentLength((long) REPORT.length).build());
        when(s3.getObject(any(GetObjectRequest.class))).thenReturn(new ResponseInputStream<>(
                GetObjectResponse.builder().build(),
                AbortableInputStream.create(new ByteArrayInputStream(REPORT))));

        Resource resource = storage.load("12/3/v1.pdf");

        // The download controller and the converter ask for the length; that must not be a GET.
        assertThat(resource.contentLength()).isEqualTo(REPORT.length);
        assertThat(resource.getFilename()).isEqualTo("v1.pdf");
        verify(s3, never()).getObject(any(GetObjectRequest.class));

        try (InputStream in = resource.getInputStream()) {
            assertThat(in.readAllBytes()).isEqualTo(REPORT);
        }
    }

    @Test
    void aMissingObjectSaysSoInPlainWords() {
        when(s3.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404).message("Not Found").build());

        assertThatThrownBy(() -> storage.load("12/3/gone.pdf"))
                .isInstanceOf(StorageException.class)
                .hasMessage("That file is no longer in storage.");
    }

    @Test
    void anyOtherReadFailureIsAStorageErrorWithTheCauseKept() {
        // A 403 is what a revoked or mistyped API token looks like.
        S3Exception forbidden = (S3Exception) S3Exception.builder().statusCode(403).message("Access Denied").build();
        when(s3.headObject(any(HeadObjectRequest.class))).thenThrow(forbidden);

        assertThatThrownBy(() -> storage.load("12/3/v1.pdf"))
                .isInstanceOf(StorageException.class)
                .hasMessage("Could not read that file.")
                .hasCause(forbidden);
    }

    @Test
    void malformedPathsAreRefusedBeforeAnyRequest() {
        assertThatThrownBy(() -> storage.load("../12/3/v1.pdf")).isInstanceOf(StorageException.class);
        assertThatThrownBy(() -> storage.load("/12/3/v1.pdf")).isInstanceOf(StorageException.class);
        assertThatThrownBy(() -> storage.load("12//v1.pdf")).isInstanceOf(StorageException.class);
        assertThatThrownBy(() -> storage.delete("12\\3\\v1.pdf")).isInstanceOf(StorageException.class);
        assertThatThrownBy(() -> storage.store(report(), "12/../3")).isInstanceOf(StorageException.class);
        verifyNoInteractions(s3);
    }

    @Test
    void deleteRemovesTheObjectItWasGiven() {
        storage.delete("12/3/v1.pdf");

        ArgumentCaptor<DeleteObjectRequest> request = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3).deleteObject(request.capture());
        assertThat(request.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(request.getValue().key()).isEqualTo("12/3/v1.pdf");
    }

    @Test
    void aFailedDeleteIsLoggedNotThrown() {
        // The caller is refusing a duplicate; that refusal must reach the student, not this.
        when(s3.deleteObject(any(DeleteObjectRequest.class))).thenThrow(SdkClientException.create("timeout"));

        assertThatCode(() -> storage.delete("12/3/v1.pdf")).doesNotThrowAnyException();
    }

    @Test
    void missingSettingsStopStartupAndNameEachOneWithItsVariable() {
        assertThatThrownBy(() -> new R2StorageService("", BUCKET, "", " "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("R2_ENDPOINT")
                .hasMessageContaining("R2_ACCESS_KEY_ID")
                .hasMessageContaining("R2_SECRET_ACCESS_KEY")
                .hasMessageNotContaining("R2_BUCKET");
    }

    @Test
    void localDiskIsTheDefaultAndR2RunsOnlyWhenAsked(@TempDir Path root) {
        ApplicationContextRunner context = new ApplicationContextRunner()
                .withUserConfiguration(LocalDiskStorageService.class, R2StorageService.class)
                .withPropertyValues("dms.storage.root=" + root);

        context.run(ctx -> assertThat(ctx)
                .hasSingleBean(StorageService.class)
                .hasSingleBean(LocalDiskStorageService.class));

        // Builds a real client, which makes no request until it is used.
        context.withPropertyValues(
                        "dms.storage.type=r2",
                        "dms.storage.r2.endpoint=https://account.r2.cloudflarestorage.com",
                        "dms.storage.r2.bucket=" + BUCKET,
                        "dms.storage.r2.access-key-id=key",
                        "dms.storage.r2.secret-access-key=secret")
                .run(ctx -> assertThat(ctx)
                        .hasSingleBean(StorageService.class)
                        .hasSingleBean(R2StorageService.class));
    }
}
