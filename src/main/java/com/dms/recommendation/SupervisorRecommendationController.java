package com.dms.recommendation;

import com.dms.allocation.Allocation;
import com.dms.allocation.AllocationRepository;
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

import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/supervisor/recommendation")
@RequiredArgsConstructor
public class SupervisorRecommendationController {

    private final RecommendationService recommendationService;
    private final AllocationRepository allocationRepository;

    /** One supervised student per row, with whether their sheet is filed. */
    public record StudentRow(Long allocationId, String rollNo, String studentName,
                             String topicTitle, Verdict verdict) {
        public boolean filed() {
            return verdict != null;
        }
    }

    @ModelAttribute("verdicts")
    public Verdict[] verdicts() {
        return Verdict.values();
    }

    @GetMapping("")
    public String students(Authentication authentication, Model model) {
        List<StudentRow> rows = new ArrayList<>();
        for (Allocation allocation : allocationRepository
                .findBySupervisorUserEmailAndStatusInOrderByRequestedAtDesc(
                        authentication.getName(), AllocationStatus.OCCUPIES_A_SEAT)) {
            rows.add(new StudentRow(
                    allocation.getId(),
                    allocation.getStudent().getRollNo(),
                    allocation.getStudent().getUser().getFullName(),
                    allocation.getTopic() == null ? null : allocation.getTopic().getTitle(),
                    recommendationService.verdictFor(allocation).orElse(null)));
        }
        model.addAttribute("students", rows);
        return "supervisor/recommendations";
    }

    @GetMapping("/{allocationId}")
    public String form(@PathVariable Long allocationId, Authentication authentication, Model model) {
        String email = authentication.getName();
        model.addAttribute("allocation", recommendationService.supervisedAllocation(email, allocationId));
        model.addAttribute("existing", recommendationService.forSupervisor(email, allocationId).orElse(null));
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", recommendationService.forSupervisor(email, allocationId)
                    .map(v -> {
                        RecommendationForm form = new RecommendationForm();
                        form.setVerdict(v.verdict());
                        form.setOrganisation(v.organisation());
                        form.setTechnicalContent(v.technicalContent());
                        form.setStrengths(v.strengths());
                        form.setQueries(v.queries());
                        form.setVivaQuestions(v.vivaQuestions());
                        return form;
                    })
                    .orElseGet(RecommendationForm::new));
        }
        return "supervisor/recommendation-form";
    }

    @PostMapping("/{allocationId}")
    public String file(@PathVariable Long allocationId,
                       @Valid @ModelAttribute("form") RecommendationForm form,
                       BindingResult bindingResult,
                       Authentication authentication,
                       Model model,
                       RedirectAttributes redirectAttributes) {
        String email = authentication.getName();
        if (bindingResult.hasErrors()) {
            model.addAttribute("allocation", recommendationService.supervisedAllocation(email, allocationId));
            model.addAttribute("existing", recommendationService.forSupervisor(email, allocationId).orElse(null));
            return "supervisor/recommendation-form";
        }
        try {
            Recommendation saved = recommendationService.file(email, allocationId, form);
            redirectAttributes.addFlashAttribute("success",
                    "Summary sheet filed: " + saved.getVerdict().getCode() + " — " + saved.getVerdict().getLabel() + ".");
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/supervisor/recommendation";
    }
}
