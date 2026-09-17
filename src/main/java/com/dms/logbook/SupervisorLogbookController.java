package com.dms.logbook;

import com.dms.allocation.AllocationService;
import com.dms.allocation.AllocationStatus;

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

import java.util.List;

@Controller
@RequestMapping("/supervisor/logbook")
@RequiredArgsConstructor
public class SupervisorLogbookController {

    private final LogbookService logbookService;
    private final AllocationService allocationService;

    /** A supervised student, for the list of logbooks the guide can open. */
    public record StudentLink(Long allocationId, String rollNo, String studentName) {
    }

    @GetMapping("")
    public String queue(Authentication authentication, Model model) {
        String email = authentication.getName();
        model.addAttribute("queue", logbookService.queueFor(email));
        model.addAttribute("students", supervisedBy(email));
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new LogbookSignForm());
        }
        return "supervisor/logbook";
    }

    @GetMapping("/student/{allocationId}")
    public String student(@PathVariable Long allocationId, Authentication authentication, Model model) {
        model.addAttribute("board", logbookService.boardForSupervisor(authentication.getName(), allocationId));
        return "supervisor/logbook-student";
    }

    @PostMapping("/{id}/sign")
    public String sign(@PathVariable Long id,
                       @Valid @ModelAttribute("form") LogbookSignForm form,
                       BindingResult bindingResult,
                       Authentication authentication,
                       RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("form", form);
            redirectAttributes.addFlashAttribute(BindingResult.MODEL_KEY_PREFIX + "form", bindingResult);
            redirectAttributes.addFlashAttribute("openEntryId", id);
            return "redirect:/supervisor/logbook";
        }
        LogbookEntry decided = logbookService.decide(authentication.getName(), id, form);
        redirectAttributes.addFlashAttribute("success", decided.getStatus() == LogbookEntryStatus.SIGNED
                ? "Meeting " + decided.getMeetingNo() + " countersigned and sealed."
                : "Meeting " + decided.getMeetingNo() + " returned to the student.");
        return "redirect:/supervisor/logbook";
    }

    private List<StudentLink> supervisedBy(String email) {
        return allocationService.decidedBy(email).stream()
                .filter(a -> AllocationStatus.OCCUPIES_A_SEAT.contains(a.getStatus()))
                .map(a -> new StudentLink(a.getId(), a.getStudent().getRollNo(),
                        a.getStudent().getUser().getFullName()))
                .toList();
    }
}
