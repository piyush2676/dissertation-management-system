package com.dms.ai;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Regulations Q&amp;A. Outside the role prefixes on purpose: a student, a guide and
 * the coordinator all read the same rules, so any signed-in user may ask.
 */
@Controller
@RequestMapping("/help/regulations")
@RequiredArgsConstructor
public class RegulationsController {

    private final RegulationsService regulationsService;

    @GetMapping("")
    public String page(Model model) {
        model.addAttribute("ready", regulationsService.documentLoaded());
        model.addAttribute("aiAvailable", regulationsService.aiAvailable());
        model.addAttribute("indexed", regulationsService.indexed());
        model.addAttribute("passageCount", regulationsService.passageCount());
        model.addAttribute("documentName", regulationsService.documentName());
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new RegulationQuestionForm());
        }
        return "help/regulations";
    }

    @PostMapping("")
    public String ask(@Valid @ModelAttribute("form") RegulationQuestionForm form,
                      BindingResult bindingResult,
                      RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("form", form);
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute(BindingResult.MODEL_KEY_PREFIX + "form", bindingResult);
            return "redirect:/help/regulations";
        }
        try {
            redirectAttributes.addFlashAttribute("answer", regulationsService.ask(form.getQuestion()));
        } catch (AiUnavailableException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/help/regulations";
    }
}
