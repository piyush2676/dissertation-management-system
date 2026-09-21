package com.dms.outcome;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/student/outcomes")
@RequiredArgsConstructor
public class StudentOutcomeController {

    private final OutcomeService outcomeService;

    @ModelAttribute("kinds")
    public OutcomeKind[] kinds() {
        return OutcomeKind.values();
    }

    @ModelAttribute("indexings")
    public OutcomeIndexing[] indexings() {
        return OutcomeIndexing.values();
    }

    @ModelAttribute("statuses")
    public OutcomeStatus[] statuses() {
        return OutcomeStatus.values();
    }

    @GetMapping("")
    public String board(Authentication authentication, Model model) {
        model.addAttribute("board", outcomeService.boardFor(authentication.getName()));
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new OutcomeForm());
        }
        model.addAttribute("mode", "new");
        return "student/outcomes";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long id, Authentication authentication, Model model) {
        Outcome outcome = outcomeService.loadForEdit(authentication.getName(), id);
        model.addAttribute("board", outcomeService.boardFor(authentication.getName()));
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", OutcomeForm.form(outcome));
        }
        model.addAttribute("mode", "edit");
        model.addAttribute("editingVerified", outcome.isVerified());
        model.addAttribute("editingNote", outcome.getVerificationNote());
        return "student/outcomes";
    }

    @PostMapping("")
    public String report(@Valid @ModelAttribute("form") OutcomeForm form,
                         BindingResult bindingResult,
                         Authentication authentication,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("board", outcomeService.boardFor(authentication.getName()));
            model.addAttribute("mode", "new");
            return "student/outcomes";
        }
        try {
            outcomeService.report(authentication.getName(), form);
            redirectAttributes.addFlashAttribute("success", "Outcome reported. The coordinator will verify it.");
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/student/outcomes";
    }

    @PostMapping("/{id}")
    public String revise(@PathVariable Long id,
                         @Valid @ModelAttribute("form") OutcomeForm form,
                         BindingResult bindingResult,
                         Authentication authentication,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            Outcome outcome = outcomeService.loadForEdit(authentication.getName(), id);
            model.addAttribute("board", outcomeService.boardFor(authentication.getName()));
            model.addAttribute("mode", "edit");
            model.addAttribute("editingVerified", outcome.isVerified());
            model.addAttribute("editingNote", outcome.getVerificationNote());
            return "student/outcomes";
        }
        outcomeService.revise(authentication.getName(), id, form);
        redirectAttributes.addFlashAttribute("success",
                "Outcome updated. Any earlier verification is cleared until the coordinator looks again.");
        return "redirect:/student/outcomes";
    }
}
