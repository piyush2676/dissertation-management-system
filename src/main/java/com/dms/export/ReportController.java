package com.dms.export;

import com.dms.user.Programme;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * The dissertation report for the head of the dissertation cell (COORDINATOR) and
 * the head of department (ADMIN). Outside both role prefixes because both roles
 * read it; SecurityConfig admits exactly those two.
 */
@Controller
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportController {

    /** Excel column widths, in characters, in DissertationReport.COLUMNS order. */
    private static final int[] WIDTHS = {6, 12, 24, 16, 28, 9, 16, 40, 20, 14, 22, 22, 20,
            12, 34, 24, 10, 34, 18, 16, 10, 14, 24, 30};

    private final DissertationReportService reportService;

    @GetMapping("")
    public String page(@RequestParam(defaultValue = "MTECH") Programme programme, Model model) {
        model.addAttribute("programme", programme);
        model.addAttribute("programmes", Programme.values());
        model.addAttribute("scholars", reportService.build(programme).rows().size());
        return "reports/index";
    }

    @GetMapping("/dissertations.csv")
    public ResponseEntity<byte[]> csv(@RequestParam(defaultValue = "MTECH") Programme programme) {
        DissertationReport report = reportService.build(programme);
        List<String[]> rows = new ArrayList<>();
        rows.add(DissertationReport.COLUMNS.toArray(String[]::new));
        for (DissertationReport.Row row : report.rows()) {
            rows.add(row.cells().toArray(String[]::new));
        }
        // A BOM so Excel reads UTF-8, as the Format 4/5 exports do.
        byte[] body = ("﻿" + ExportService.csv(rows)).getBytes(StandardCharsets.UTF_8);
        return download(body, filename(programme, "csv"), new MediaType("text", "csv", StandardCharsets.UTF_8));
    }

    @GetMapping("/dissertations.xlsx")
    public ResponseEntity<byte[]> xlsx(@RequestParam(defaultValue = "MTECH") Programme programme) {
        DissertationReport report = reportService.build(programme);
        byte[] body = XlsxWriter.write("Dissertations", DissertationReport.COLUMNS,
                report.rows().stream().map(DissertationReport.Row::cells).toList(), WIDTHS);
        return download(body, filename(programme, "xlsx"),
                MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }

    @GetMapping("/dissertations.pdf")
    public ResponseEntity<byte[]> pdf(@RequestParam(defaultValue = "MTECH") Programme programme) {
        byte[] body = ReportPdfWriter.write(reportService.build(programme));
        return download(body, filename(programme, "pdf"), MediaType.APPLICATION_PDF);
    }

    private static String filename(Programme programme, String extension) {
        return "dissertation-report-" + programme.name().toLowerCase().replace('_', '-')
                + "-" + LocalDate.now() + "." + extension;
    }

    private static ResponseEntity<byte[]> download(byte[] body, String filename, MediaType type) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(type)
                .body(body);
    }
}
