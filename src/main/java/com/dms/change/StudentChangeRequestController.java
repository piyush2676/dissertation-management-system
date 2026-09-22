package com.dms.change;

import com.dms.allocation.AllocationService;
import com.dms.user.SupervisorProfile;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/student/change-request")
@RequiredArgsConstructor
public class StudentChangeRequestController {

    private final ChangeRequestService changeRequestService;
    private final AllocationService allocationService;

    @ModelAttribute("kinds")
    public ChangeKind[] kinds() {
        return ChangeKind.values();
    }

    @ModelAttribute("supervisors")
    public List<SupervisorProfile> supervisors() {
        return allocationService.selectableSupervisors();
    }

    @GetMapping("")
    public String board(Authentication authentication, Model model) {
        model.addAttribute("board", changeRequestService.boardFor(authentication.getName()));
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new ChangeRequestForm());
        }
        return "student/change-request";
    }

    @PostMapping("")
    public String raise(@Valid @ModelAttribute("form") ChangeRequestForm form,
                        BindingResult bindingResult,
                        Authentication authentication,
                        Model model,
                        RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("board", changeRequestService.boardFor(authentication.getName()));
            return "student/change-request";
        }
        try {
            changeRequestService.raise(authentication.getName(), form);
            redirectAttributes.addFlashAttribute("success",
                    "Request submitted. Your supervision is unchanged while the committee considers it.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/student/change-request";
    }
}
