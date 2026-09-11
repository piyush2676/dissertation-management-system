package com.dms.provenance;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.Map;

/**
 * Renders a certificate as a one-page PDF carrying a QR code.
 *
 * <p>The QR encodes the verify URL, so a reader with a phone reaches the page
 * that recomputes the digest rather than having to trust the paper. The digest
 * is also printed in full, because a QR is unreadable to a person and a document
 * that can only be checked by machine is a worse document.
 */
@Component
@RequiredArgsConstructor
public class CertificatePdfRenderer {

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm").withZone(ZoneId.systemDefault());

    private static final float MARGIN = 56f;
    private static final float RED_R = 0.81f, RED_G = 0.08f, RED_B = 0.15f;

    private final CertificateService certificateService;

    public byte[] render(Certificate certificate) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            float width = page.getMediaBox().getWidth();
            float top = page.getMediaBox().getHeight() - MARGIN;

            try (PDPageContentStream out = new PDPageContentStream(document, page)) {

                // Institute rule across the head of the page.
                out.setNonStrokingColor(RED_R, RED_G, RED_B);
                out.addRect(0, page.getMediaBox().getHeight() - 14f, width, 14f);
                out.fill();

                float y = top - 24f;
                y = text(out, bold(), 20f, MARGIN, y, "Noida Institute of Engineering and Technology");
                y = text(out, regular(), 11f, MARGIN, y - 4f, "An Autonomous Institute  |  Dissertation Cell");

                y -= 26f;
                out.setNonStrokingColor(RED_R, RED_G, RED_B);
                y = text(out, bold(), 16f, MARGIN, y, "Dissertation Record");
                out.setNonStrokingColor(0f, 0f, 0f);

                y = text(out, regular(), 10f, MARGIN, y - 2f,
                        "This document states what the dissertation register held at the moment it was issued.");

                y -= 22f;
                Map<String, String> facts = certificate.getPayload();
                y = row(out, MARGIN, y, "Roll number", facts.getOrDefault("rollNo", ""));
                y = row(out, MARGIN, y, "Student", facts.getOrDefault("student", ""));
                y = row(out, MARGIN, y, "Programme", facts.getOrDefault("programme", "").replace('_', ' '));
                y = row(out, MARGIN, y, "Session", facts.getOrDefault("session", ""));
                y = row(out, MARGIN, y, "Guide", facts.getOrDefault("guide", ""));
                y = row(out, MARGIN, y, "Topic", facts.getOrDefault("topic", ""));
                y = row(out, MARGIN, y, "Marks", facts.getOrDefault("marks", "not recorded"));
                y = row(out, MARGIN, y, "Viva", facts.getOrDefault("viva", "not scheduled"));

                y -= 14f;
                out.setNonStrokingColor(RED_R, RED_G, RED_B);
                y = text(out, bold(), 12f, MARGIN, y, "Submitted work");
                out.setNonStrokingColor(0f, 0f, 0f);

                String versions = facts.getOrDefault("versions", "");
                if (versions.isBlank()) {
                    y = text(out, regular(), 10f, MARGIN, y - 2f, "No versions were filed.");
                } else {
                    for (String entry : versions.split("\\|")) {
                        y = text(out, mono(), 8.5f, MARGIN, y - 1f, entry.strip());
                    }
                }

                // The seal, and how to check it.
                y -= 24f;
                out.setNonStrokingColor(RED_R, RED_G, RED_B);
                y = text(out, bold(), 12f, MARGIN, y, "Verification");
                out.setNonStrokingColor(0f, 0f, 0f);

                y = row(out, MARGIN, y, "Certificate", certificate.getCode());
                y = row(out, MARGIN, y, "Issued", STAMP.format(certificate.getIssuedAt()));
                y = text(out, regular(), 9f, MARGIN, y - 2f, "Digest (SHA-256 of the sealed record)");
                y = text(out, mono(), 8f, MARGIN, y, certificate.getDigest());

                String url = certificateService.verifyUrl(certificate.getCode());
                y = text(out, regular(), 9f, MARGIN, y - 10f,
                        "Scan the code or open the address below. The page recomputes this digest from");
                y = text(out, regular(), 9f, MARGIN, y, "the live record and reports whether anything has changed since issue.");
                y = text(out, mono(), 8.5f, MARGIN, y - 2f, url);

                PDImageXObject qr = LosslessFactory.createFromImage(document, qrImage(url, 260));
                out.drawImage(qr, width - MARGIN - 130f, y - 148f, 130f, 130f);

                out.setNonStrokingColor(0.45f, 0.45f, 0.45f);
                text(out, regular(), 8f, MARGIN, MARGIN,
                        "A mismatch does not imply wrongdoing. It means the register has changed since this was issued.");
            }

            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            document.save(bytes);
            return bytes.toByteArray();
        }
    }

    // ---- drawing helpers ----------------------------------------------------

    private static PDType1Font bold() {
        return new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    }

    private static PDType1Font regular() {
        return new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    }

    private static PDType1Font mono() {
        return new PDType1Font(Standard14Fonts.FontName.COURIER);
    }

    private static float text(PDPageContentStream out, PDType1Font font, float size,
                              float x, float y, String value) throws IOException {
        out.beginText();
        out.setFont(font, size);
        out.newLineAtOffset(x, y);
        out.showText(sanitise(value));
        out.endText();
        return y - (size + 5f);
    }

    private static float row(PDPageContentStream out, float x, float y,
                             String label, String value) throws IOException {
        out.beginText();
        out.setFont(bold(), 9.5f);
        out.newLineAtOffset(x, y);
        out.showText(sanitise(label));
        out.endText();

        out.beginText();
        out.setFont(regular(), 10.5f);
        out.newLineAtOffset(x + 110f, y);
        out.showText(sanitise(trim(value, 78)));
        out.endText();
        return y - 18f;
    }

    /**
     * The Standard 14 fonts are WinAnsi, so a character outside it throws when the
     * page is written. A name with an unusual glyph must not break a certificate.
     */
    private static String sanitise(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder safe = new StringBuilder(value.length());
        for (char c : value.toCharArray()) {
            safe.append(c >= 32 && c <= 255 ? c : '?');
        }
        return safe.toString();
    }

    private static String trim(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }

    private static BufferedImage qrImage(String content, int size) throws IOException {
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 1);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");

            BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints);
            return MatrixToImageWriter.toBufferedImage(matrix);
        } catch (Exception ex) {
            throw new IOException("Could not build the QR code", ex);
        }
    }
}
