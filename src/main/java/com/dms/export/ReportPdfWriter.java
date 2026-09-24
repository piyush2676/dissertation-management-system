package com.dms.export;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * The dissertation report as a printable landscape table.
 *
 * <p>Twenty-four columns do not fit a page, so the PDF groups them into ten --
 * scholar, topic, guides, progress, marks and so on -- with several lines per cell.
 * Every value is the same string the CSV and Excel files carry; only the grouping
 * differs. The header row repeats on every page, and each page is numbered and
 * marked confidential, because the recommendation column is office-only.
 */
final class ReportPdfWriter {

    private static final PDRectangle PAGE = new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());
    private static final float MARGIN = 24;
    private static final float SIZE = 7f;
    private static final float LEADING = 8.6f;
    private static final float PAD = 3f;
    private static final Color RED = new Color(0xCF, 0x14, 0x27);
    private static final Color RULE = new Color(0xDD, 0xDD, 0xDD);

    private static final String[] HEADINGS = {"#", "Scholar", "Topic", "Guides", "Submissions",
            "Logbook & outcomes", "Internal marks", "Readiness", "Viva", "Annexure-6 verdict"};
    private static final float[] WIDTHS = {18, 108, 138, 96, 120, 84, 70, 50, 50, 60};

    private final PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private final PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    private ReportPdfWriter() {
    }

    static byte[] write(DissertationReport report) {
        return new ReportPdfWriter().render(report);
    }

    private byte[] render(DissertationReport report) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            String title = "Dissertation report -- " + report.programmeLabel()
                    + (report.sessionLabel().isBlank() ? "" : ", session " + report.sessionLabel());
            String subtitle = report.rows().size() + " scholars. Generated "
                    + DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm").withZone(ZoneId.systemDefault())
                    .format(report.generatedAt()) + " by the Dissertation Management System, NIET.";

            Page page = newPage(document, title, subtitle);
            for (DissertationReport.Row row : report.rows()) {
                List<List<String>> cells = new ArrayList<>();
                String[] texts = group(row);
                float height = 0;
                for (int i = 0; i < texts.length; i++) {
                    List<String> lines = wrap(texts[i], WIDTHS[i] - 2 * PAD, regular);
                    cells.add(lines);
                    height = Math.max(height, lines.size() * LEADING + 2 * PAD);
                }
                if (page.y - height < MARGIN + 16) {
                    page.stream.close();
                    page = newPage(document, title, subtitle);
                }
                drawRow(page, cells, height, regular, null);
            }
            page.stream.close();
            number(document);
            document.save(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /** Folds the twenty-four report columns into the ten the page has room for. */
    private static String[] group(DissertationReport.Row r) {
        return new String[]{
                String.valueOf(r.serial()),
                join(r.scholar(), r.rollNo(), r.signIn(), line("Sem ", r.semester()) + line(", ", r.phase())),
                join(line("", r.thesisId()), r.thesisTitle(), line("Domain: ", r.researchDomain()), r.topicStatus()),
                join(line("Guide: ", r.supervisor()), line("Co-guide: ", r.coSupervisor()), r.placement()),
                join(line("Filed: ", r.milestonesFiled()), r.latestSubmission(), line("Similarity: ", r.similarity())),
                join(line("Meetings signed: ", r.logbookSigned()), line("Outcomes: ", r.outcomes())),
                join(r.internalMarks(), r.gradeBand(), r.examiners().isBlank() || r.examiners().equals("0")
                        ? "" : r.examiners() + " examiner(s)"),
                r.readiness(),
                r.viva(),
                r.recommendation()
        };
    }

    private Page newPage(PDDocument document, String title, String subtitle) throws IOException {
        PDPage pdPage = new PDPage(PAGE);
        document.addPage(pdPage);
        PDPageContentStream stream = new PDPageContentStream(document, pdPage);
        float y = PAGE.getHeight() - MARGIN;

        stream.setNonStrokingColor(RED);
        text(stream, bold, 13, MARGIN, y - 11, safe(title, bold));
        stream.setNonStrokingColor(Color.DARK_GRAY);
        text(stream, regular, 8, MARGIN, y - 24, safe(subtitle, regular));
        y -= 34;

        Page page = new Page(stream, y);
        List<List<String>> headings = new ArrayList<>();
        float height = 0;
        for (int i = 0; i < HEADINGS.length; i++) {
            List<String> lines = wrap(HEADINGS[i], WIDTHS[i] - 2 * PAD, bold);
            headings.add(lines);
            height = Math.max(height, lines.size() * LEADING + 2 * PAD + 2);
        }
        drawRow(page, headings, height, bold, RED);
        return page;
    }

    private void drawRow(Page page, List<List<String>> cells, float height, PDType1Font font, Color fill)
            throws IOException {
        float x = MARGIN;
        float top = page.y;
        float tableWidth = 0;
        for (float w : WIDTHS) {
            tableWidth += w;
        }
        if (fill != null) {
            page.stream.setNonStrokingColor(fill);
            page.stream.addRect(MARGIN, top - height, tableWidth, height);
            page.stream.fill();
        }
        page.stream.setNonStrokingColor(fill != null ? Color.WHITE : Color.BLACK);
        for (int i = 0; i < cells.size(); i++) {
            float y = top - PAD - SIZE;
            for (String line : cells.get(i)) {
                text(page.stream, font, SIZE, x + PAD, y, line);
                y -= LEADING;
            }
            x += WIDTHS[i];
        }
        page.stream.setStrokingColor(RULE);
        page.stream.setLineWidth(0.5f);
        page.stream.moveTo(MARGIN, top - height);
        page.stream.lineTo(MARGIN + tableWidth, top - height);
        page.stream.stroke();
        page.y = top - height;
    }

    private void number(PDDocument document) throws IOException {
        int total = document.getNumberOfPages();
        for (int i = 0; i < total; i++) {
            PDPage pdPage = document.getPage(i);
            try (PDPageContentStream stream = new PDPageContentStream(
                    document, pdPage, PDPageContentStream.AppendMode.APPEND, true)) {
                stream.setNonStrokingColor(Color.GRAY);
                text(stream, regular, 7, MARGIN, 12,
                        "Confidential: carries supervisors' Annexure-6 recommendations. For the department office only.");
                String label = "Page " + (i + 1) + " of " + total;
                text(stream, regular, 7, PAGE.getWidth() - MARGIN - regular.getStringWidth(label) / 1000 * 7, 12, label);
            }
        }
    }

    /** Greedy word wrap inside one cell; explicit newlines start a new line. */
    private List<String> wrap(String text, float width, PDType1Font font) throws IOException {
        List<String> lines = new ArrayList<>();
        for (String paragraph : safe(text, font).split("\n")) {
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (font.getStringWidth(candidate) / 1000 * SIZE <= width) {
                    line.setLength(0);
                    line.append(candidate);
                    continue;
                }
                if (!line.isEmpty()) {
                    lines.add(line.toString());
                }
                // A single word wider than the cell (an email address) is broken by characters.
                line.setLength(0);
                for (char ch : word.toCharArray()) {
                    if (font.getStringWidth(line.toString() + ch) / 1000 * SIZE > width && !line.isEmpty()) {
                        lines.add(line.toString());
                        line.setLength(0);
                    }
                    line.append(ch);
                }
            }
            if (!line.isEmpty()) {
                lines.add(line.toString());
            }
        }
        return lines.isEmpty() ? List.of("") : lines;
    }

    /**
     * The standard PDF fonts cover Latin text only. A character they cannot draw --
     * a Devanagari name, an emoji in a title -- becomes "?" rather than failing the
     * whole report.
     */
    private static String safe(String text, PDType1Font font) {
        if (text == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            String ch = new String(Character.toChars(cp));
            if (ch.equals("\n")) {
                out.append(ch);
            } else {
                try {
                    font.encode(ch);
                    out.append(ch);
                } catch (IOException | IllegalArgumentException ex) {
                    out.append('?');
                }
            }
            i += Character.charCount(cp);
        }
        return out.toString();
    }

    private static void text(PDPageContentStream stream, PDType1Font font, float size, float x, float y, String text)
            throws IOException {
        stream.beginText();
        stream.setFont(font, size);
        stream.newLineAtOffset(x, y);
        stream.showText(text);
        stream.endText();
    }

    private static String join(String... parts) {
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                if (!out.isEmpty()) {
                    out.append('\n');
                }
                out.append(part);
            }
        }
        return out.toString();
    }

    private static String line(String label, String value) {
        return value == null || value.isBlank() ? "" : label + value;
    }

    private static final class Page {
        final PDPageContentStream stream;
        float y;

        Page(PDPageContentStream stream, float y) {
            this.stream = stream;
            this.y = y;
        }
    }
}
