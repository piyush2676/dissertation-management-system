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
@RequestMapping("/coordinator/outcomes")
@RequiredArgsConstructor
public class CoordinatorOutcomeController {

    private final OutcomeService outcomeService;

    @GetMapping("")
    public String queue(Model model) {
        model.addAttribute("queue", outcomeService.queue());
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new OutcomeVerifyForm());
        }
        return "coordinator/outcomes";
    }

    @PostMapping("/{id}/verify")
    public String verify(@PathVariable Long id,
                         @Valid @ModelAttribute("form") OutcomeVerifyForm form,
                         BindingResult bindingResult,
                         Authentication authentication,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("form", form);
            redirectAttributes.addFlashAttribute(BindingResult.MODEL_KEY_PREFIX + "form", bindingResult);
            redirectAttributes.addFlashAttribute("openOutcomeId", id);
            return "redirect:/coordinator/outcomes";
        }
        Outcome decided = outcomeService.verify(authentication.getName(), id, form);
        redirectAttributes.addFlashAttribute("success", decided.isVerified()
                ? "Outcome verified. It now counts toward the requirements."
                : "Outcome returned to the student with your note.");
        return "redirect:/coordinator/outcomes";
    }
}
