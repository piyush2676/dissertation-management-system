package com.dms.export;

import com.dms.user.Programme;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DissertationReportWritersTest {

    @Test
    void everyRowHasOneCellPerColumn() {
        assertEquals(DissertationReport.COLUMNS.size(), row(1, "Aakrit Kumar Tiwari").cells().size());
    }

    @Test
    void aCsvCellThatWouldRunAsAFormulaIsMadePlainText() {
        assertEquals("'=HYPERLINK(\"x\")", ExportService.defuse("=HYPERLINK(\"x\")"));
        assertEquals("'+91 98", ExportService.defuse("+91 98"));
        assertEquals("'@SUM(A1)", ExportService.defuse("@SUM(A1)"));
        assertEquals("Federated learning", ExportService.defuse("Federated learning"));
        assertEquals("", ExportService.defuse(null));
    }

    @Test
    void theExcelFileIsAWorkbookWithEveryScholarAndEscapedText() throws IOException {
        List<List<String>> rows = List.of(row(1, "Aakrit <Tiwari> & co").cells(), row(2, "Abhay Baghel").cells());

        byte[] xlsx = XlsxWriter.write("Dissertations", DissertationReport.COLUMNS, rows, new int[0]);

        Set<String> parts = new HashSet<>();
        String sheet = null;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(xlsx))) {
            for (ZipEntry e; (e = zip.getNextEntry()) != null; ) {
                parts.add(e.getName());
                if (e.getName().equals("xl/worksheets/sheet1.xml")) {
                    sheet = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        assertTrue(parts.containsAll(Set.of("[Content_Types].xml", "_rels/.rels", "xl/workbook.xml",
                "xl/_rels/workbook.xml.rels", "xl/styles.xml", "xl/worksheets/sheet1.xml")));
        assertTrue(sheet.contains("Aakrit &lt;Tiwari&gt; &amp; co"));
        assertTrue(sheet.contains("<row r=\"3\">"), "header plus two scholars");
        assertTrue(sheet.contains("<autoFilter ref=\"A1:X3\"/>"));
        assertTrue(sheet.contains("state=\"frozen\""));
    }

    @Test
    void columnLettersRunPastZ() {
        assertEquals("A", XlsxWriter.column(1));
        assertEquals("X", XlsxWriter.column(24));
        assertEquals("AA", XlsxWriter.column(27));
    }

    @Test
    void controlCharactersThatBreakXmlAreDropped() {
        assertEquals("ab", XlsxWriter.xml("a\u0001b"));
    }

    @Test
    void thePdfListsEveryScholarAcrossNumberedPages() throws IOException {
        List<DissertationReport.Row> rows = new ArrayList<>();
        for (int i = 1; i <= 60; i++) {
            rows.add(row(i, "Scholar " + i));
        }
        byte[] pdf = ReportPdfWriter.write(new DissertationReport(Programme.BTECH_MTECH_INTEGRATED, "2026-27", Instant.now(), rows));

        try (PDDocument document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(document.getNumberOfPages() > 1, "sixty scholars need more than one page");
            assertTrue(text.contains("Scholar 1\n") || text.contains("Scholar 1 "), text.substring(0, 200));
            assertTrue(text.contains("Scholar 60"));
            assertTrue(text.contains("Page 1 of " + document.getNumberOfPages()));
            assertTrue(text.contains("Dissertation report -- Integrated M.Tech, session 2026-27"));
        }
    }

    @Test
    void aNameTheStandardFontCannotDrawDoesNotFailThePdf() throws IOException {
        byte[] pdf = ReportPdfWriter.write(new DissertationReport(Programme.MTECH, "2026-27", Instant.now(),
                List.of(row(1, "पीयूष Pandey"))));

        try (PDDocument document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("Pandey"));
            assertFalse(text.contains("पीयूष"));
        }
    }

    @Test
    void constantsReadAsWords() {
        assertEquals("Coordinator assigned", DissertationReportService.words("COORDINATOR_ASSIGNED"));
    }

    private static DissertationReport.Row row(int serial, String name) {
        return new DissertationReport.Row(serial, "MInt._0" + serial, name, "22013301005" + serial,
                "0221mcsd0" + serial + "@niet.co.in", "9", "Pre-Dissertation",
                "Federated learning for campus energy forecasting", "Machine learning", "Approved",
                "Dr. Hitesh Singh", "Ms. Roshni Prasad", "Coordinator assigned", "2 of 3",
                "Review 1 - Problem statement v2 -- Approved (24 Sep 2026)", "6.5% similar, 0% AI", "3",
                "1: Conference paper (IEEE, Accepted)", "80 / 100 (80.0%)", "A -- Good", "2", "7 of 9 met",
                "Scheduled, 11 Feb 2027, 10:30", "[B] Acceptable after minor technical modifications.");
    }
}
