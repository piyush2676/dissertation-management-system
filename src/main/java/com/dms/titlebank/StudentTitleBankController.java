package com.dms.titlebank;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The scholar browses what the faculty have offered. Adopting one hands the
 * proposal form a prefill and nothing else: section 4.6 keeps the choice, and
 * the approval, exactly where they were.
 */
@Controller
@RequiredArgsConstructor
public class StudentTitleBankController {

    private final TitleBankService titleBankService;

    @GetMapping("/student/titles")
    public String titles(@RequestParam(required = false) String q, Model model) {
        model.addAttribute("titles", titleBankService.open(q));
        model.addAttribute("q", q);
        return "student/titles";
    }
}
