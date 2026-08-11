package com.dms.topic;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/supervisor/topics")
@RequiredArgsConstructor
public class SupervisorTopicController {
    private final TopicService topicService;

    @ModelAttribute("decisions")
    public List<TopicStatus> decisions() {
        return List.of(TopicStatus.APPROVED, TopicStatus.CHANGES_REQUESTED, TopicStatus.REJECTED);
    }

    @GetMapping("")
    public String inbox(Authentication authentication, Model model) {
        String email = authentication.getName();
        model.addAttribute("pending", topicService.pendingFor(email));
        model.addAttribute("decided", topicService.decidedBy(email));
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new TopicDecisionForm());
        }
        return "supervisor/topics";
    }

    @PostMapping("/{id}/decide")
    public String decide(@PathVariable Long id,
                         @Valid @ModelAttribute("form") TopicDecisionForm form,
                         BindingResult bindingResult,
                         Authentication authentication,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("form", form);
            redirectAttributes.addFlashAttribute(BindingResult.MODEL_KEY_PREFIX + "form", bindingResult);
            redirectAttributes.addFlashAttribute("openTopicId", id);
            return "redirect:/supervisor/topics";
        }
        Topic decided = topicService.decide(authentication.getName(), id, form);
        redirectAttributes.addFlashAttribute("success", switch (decided.getStatus()) {
            case APPROVED -> "Topic approved.";
            case CHANGES_REQUESTED -> "Changes requested.";
            case REJECTED -> "Topic rejected.";
            default -> "Decision recorded.";
        });
        return "redirect:/supervisor/topics";
    }
}
