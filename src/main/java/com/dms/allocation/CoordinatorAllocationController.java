package com.dms.allocation;

import com.dms.common.NotFoundException;
import com.dms.user.Programme;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Coordinator override. A guide request normally starts with the student and is
 * accepted by the supervisor; this is the path for when that stalls -- the
 * coordinator places the student directly, bypassing the supervisor's consent.
 *
 * <p>SecurityConfig already gates /coordinator/** on ROLE_COORDINATOR, so no
 * method-level check is repeated here.
 */
@Controller
@RequestMapping("/coordinator/allocate")
@RequiredArgsConstructor
public class CoordinatorAllocationController {

    private final AllocationService allocationService;

    @ModelAttribute("programmes")
    public List<Programme> programmes() {
        return List.of(Programme.values());
    }

    @GetMapping("")
    public String board(@RequestParam(defaultValue = "MTECH") Programme programme, Model model) {
        model.addAttribute("programme", programme);

        try {
            model.addAttribute("board", allocationService.board(programme));
        } catch (IllegalStateException ex) {
            // No active session for this programme. That is an empty page, not a
            // rule violation, so it must not reach the 409 handler.
            model.addAttribute("board", null);
            model.addAttribute("error", ex.getMessage());
        }

        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new AllocationAssignForm());
        }
        return "coordinator/allocate";
    }

    @PostMapping("/assign")
    public String assign(@RequestParam Programme programme,
                         @Valid @ModelAttribute("form") AllocationAssignForm form,
                         BindingResult bindingResult,
                         Authentication authentication,
                         RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("form", form);
            redirectAttributes.addFlashAttribute(BindingResult.MODEL_KEY_PREFIX + "form", bindingResult);
            return redirectTo(programme);
        }

        try {
            allocationService.assign(authentication.getName(), form.getStudentId(), form.getSupervisorId());
            redirectAttributes.addFlashAttribute("success", "Guide assigned.");
        } catch (CapacityExceededException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        } catch (NotFoundException ex) {
            redirectAttributes.addFlashAttribute("error", "That student or guide no longer exists.");
        }

        return redirectTo(programme);
    }

    /** Redirect after post, carrying the tab so an assign does not reset the programme. */
    private String redirectTo(Programme programme) {
        return "redirect:/coordinator/allocate?programme=" + programme.name();
    }
}
