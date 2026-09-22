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

@Controller
@RequestMapping("/coordinator/exports")
@RequiredArgsConstructor
public class CoordinatorExportController {

    private final ExportService exportService;

    @GetMapping("")
    public String page(@RequestParam(defaultValue = "MTECH") Programme programme, Model model) {
        model.addAttribute("programme", programme);
        model.addAttribute("programmes", Programme.values());
        return "coordinator/exports";
    }

    @GetMapping("/format4.csv")
    public ResponseEntity<byte[]> format4(@RequestParam(defaultValue = "MTECH") Programme programme) {
        return csv(exportService.format4(programme), "format4-" + programme.name().toLowerCase() + ".csv");
    }

    @GetMapping("/format5.csv")
    public ResponseEntity<byte[]> format5(@RequestParam(defaultValue = "MTECH") Programme programme) {
        return csv(exportService.format5(programme), "format5-" + programme.name().toLowerCase() + ".csv");
    }

    /**
     * A BOM so Excel opens it as UTF-8 rather than the system codepage. Without it
     * a name with a diacritic arrives mangled in the office.
     */
    private static ResponseEntity<byte[]> csv(String body, String filename) {
        byte[] bytes = ("﻿" + body).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(bytes);
    }
}
