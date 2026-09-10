package com.dms.audit;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Read-only. The trail is written by listeners and is never edited from the UI. */
@Controller
@RequestMapping("/admin/audit")
@RequiredArgsConstructor
public class AuditLogController {

    private static final int PAGE_SIZE = 50;

    private final AuditLogRepository auditLogRepository;

    @GetMapping("")
    public String trail(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("entries",
                auditLogRepository.findAllByOrderByAtDesc(PageRequest.of(Math.max(0, page), PAGE_SIZE)));
        return "admin/audit";
    }
}
