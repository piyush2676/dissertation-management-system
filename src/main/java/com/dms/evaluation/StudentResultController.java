package com.dms.evaluation;

import com.dms.viva.VivaService;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/student/result")
@RequiredArgsConstructor
public class StudentResultController {

    private final EvaluationService evaluationService;
    private final VivaService vivaService;

    @GetMapping("")
    public String result(Authentication authentication, Model model) {
        model.addAttribute("result", evaluationService.resultFor(authentication.getName()));
        model.addAttribute("viva", vivaService.forStudent(authentication.getName()).orElse(null));
        return "student/result";
    }
}
