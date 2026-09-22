package com.dms.change;

import com.dms.common.InvalidStateTransitionException;

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
@RequestMapping("/coordinator/change-requests")
@RequiredArgsConstructor
public class CoordinatorChangeRequestController {

    private final ChangeRequestService changeRequestService;

    @GetMapping("")
    public String queue(Model model) {
        model.addAttribute("queue", changeRequestService.queue());
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new ChangeRequestDecisionForm());
        }
        return "coordinator/change-requests";
    }

    @PostMapping("/{id}/decide")
    public String decide(@PathVariable Long id,
                         @Valid @ModelAttribute("form") ChangeRequestDecisionForm form,
                         BindingResult bindingResult,
                         Authentication authentication,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("form", form);
            redirectAttributes.addFlashAttribute(BindingResult.MODEL_KEY_PREFIX + "form", bindingResult);
            redirectAttributes.addFlashAttribute("openRequestId", id);
            return "redirect:/coordinator/change-requests";
        }
        try {
            ChangeRequest decided = changeRequestService.decide(authentication.getName(), id, form);
            if (decided.getStatus() != ChangeRequestStatus.APPROVED) {
                redirectAttributes.addFlashAttribute("success", "Request refused; the scholar sees your reason.");
            } else if (decided.getKind() == ChangeKind.SUPERVISOR) {
                redirectAttributes.addFlashAttribute("success",
                        "Approved. The placement is withdrawn and the scholar is waiting on the allocation board.");
            } else {
                redirectAttributes.addFlashAttribute("success",
                        "Approved. The title is back with the scholar to propose again.");
            }
        } catch (InvalidStateTransitionException ex) {
            redirectAttributes.addFlashAttribute("error", "That request has already been answered.");
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/coordinator/change-requests";
    }
}
