package com.dms.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The upload gate: the file's own first bytes have to back up the name and type the browser sent. */
class LocalDiskStorageServiceTest {

    private static final String PDF = "application/pdf";
    private static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String DOC = "application/msword";

    @TempDir Path root;
    private LocalDiskStorageService storage;

    @BeforeEach
    void setUp() {
        storage = new LocalDiskStorageService(root.toString());
    }

    private static MockMultipartFile upload(String name, String type, byte[] bytes) {
        return new MockMultipartFile("file", name, type, bytes);
    }

    private static byte[] ascii(String s) {
        return s.getBytes(StandardCharsets.ISO_8859_1);
    }

    @Test
    void aRealPdfIsStored() {
        StoredFile stored = storage.store(upload("r.pdf", PDF, ascii("%PDF-1.4\nbody\n%%EOF")), "a/b");
        assertThat(stored.contentType()).isEqualTo(PDF);
        assertThat(stored.sizeBytes()).isPositive();
    }

    @Test
    void htmlRenamedToPdfIsRefused() {
        assertThatThrownBy(() -> storage.store(upload("x.pdf", PDF, ascii("<html><script>alert(1)</script></html>")), "a"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("not a real PDF");
    }

    @Test
    void anExecutableRenamedToPdfIsRefused() {
        assertThatThrownBy(() -> storage.store(upload("m.pdf", PDF, new byte[]{'M', 'Z', (byte) 0x90, 0, 3, 0, 0, 0}), "a"))
                .isInstanceOf(StorageException.class);
    }

    @Test
    void aFileShorterThanItsHeaderIsRefused() {
        assertThatThrownBy(() -> storage.store(upload("t.pdf", PDF, ascii("%PD")), "a"))
                .isInstanceOf(StorageException.class);
    }

    @Test
    void aDocxMustBeAZipArchive() {
        StoredFile ok = storage.store(upload("t.docx", DOCX, new byte[]{'P', 'K', 3, 4, 20, 0, 0, 0}), "a");
        assertThat(ok.contentType()).isEqualTo(DOCX);
        assertThatThrownBy(() -> storage.store(upload("t.docx", DOCX, ascii("plain text, not a zip")), "a"))
                .isInstanceOf(StorageException.class);
    }

    @Test
    void aLegacyDocMustBeAnOleFile() {
        byte[] ole = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1, 0};
        assertThat(storage.store(upload("t.doc", DOC, ole), "a").contentType()).isEqualTo(DOC);
        assertThatThrownBy(() -> storage.store(upload("t.doc", DOC, ascii("%PDF-1.4 pretending")), "a"))
                .isInstanceOf(StorageException.class);
    }

    @Test
    void aPdfBehindAWordNameIsRefused() {
        // Real PDF bytes under a .docx name: the claim and the content disagree.
        assertThatThrownBy(() -> storage.store(upload("t.docx", DOCX, ascii("%PDF-1.4\n")), "a"))
                .isInstanceOf(StorageException.class);
    }

    @Test
    void theStoredTypeComesFromTheFileNotTheBrowser() {
        // Right bytes and extension but a wrong, still-allowed declared type: stored as what it is.
        StoredFile stored = storage.store(upload("r.pdf", DOC, ascii("%PDF-1.7\n")), "a");
        assertThat(stored.contentType()).isEqualTo(PDF);
    }

    @Test
    void disallowedExtensionsAndEmptyFilesStillFail() {
        assertThatThrownBy(() -> storage.store(upload("n.txt", "text/plain", ascii("hello")), "a"))
                .isInstanceOf(StorageException.class);
        assertThatThrownBy(() -> storage.store(upload("e.pdf", PDF, new byte[0]), "a"))
                .isInstanceOf(StorageException.class);
    }
}
