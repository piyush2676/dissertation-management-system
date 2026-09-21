package com.dms.readiness;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class StudentReadinessController {

    private final ReadinessService readinessService;

    @GetMapping("/student/readiness")
    public String ledger(Authentication authentication, Model model) {
        model.addAttribute("ledger", readinessService.ledgerForStudent(authentication.getName()).orElse(null));
        return "student/readiness";
    }
}
