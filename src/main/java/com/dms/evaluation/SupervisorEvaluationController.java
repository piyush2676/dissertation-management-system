package com.dms.evaluation;

import com.dms.allocation.Allocation;
import com.dms.allocation.AllocationRepository;
import com.dms.allocation.AllocationStatus;
import com.dms.common.NotFoundException;

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
@RequestMapping("/supervisor/evaluate")
@RequiredArgsConstructor
public class SupervisorEvaluationController {

    private final EvaluationService evaluationService;
    private final EvaluationRepository evaluationRepository;
    private final AllocationRepository allocationRepository;

    @GetMapping("")
    public String students(Authentication authentication, Model model) {
        List<Allocation> supervised = allocationRepository
                .findBySupervisorUserEmailAndStatusInOrderByRequestedAtDesc(
                        authentication.getName(), AllocationStatus.OCCUPIES_A_SEAT);
        model.addAttribute("students", supervised);
        return "supervisor/evaluate";
    }

    @GetMapping("/{allocationId}")
    public String form(@PathVariable Long allocationId, Authentication authentication, Model model) {
        Allocation allocation = load(allocationId, authentication.getName());

        model.addAttribute("allocation", allocation);
        model.addAttribute("rubric", evaluationService.rubricFor(allocation));

        if (!model.containsAttribute("form")) {
            EvaluationForm form = new EvaluationForm();
            form.setAllocationId(allocationId);

            // Prefill from an existing evaluation so a correction is an edit,
            // not a blank sheet the examiner has to fill in twice.
            evaluationRepository.findByAllocation(allocation).stream()
                    .filter(e -> e.getExaminer().getEmail().equals(authentication.getName()))
                    .findFirst()
                    .ifPresent(existing -> {
                        existing.getScores().forEach((key, value) ->
                                form.getScores().put(Long.valueOf(key), value));
                        form.setRemarks(existing.getRemarks());
                    });

            model.addAttribute("form", form);
        }
        return "supervisor/evaluate-form";
    }

    @PostMapping("/{allocationId}")
    public String score(@PathVariable Long allocationId,
                        @Valid @ModelAttribute("form") EvaluationForm form,
                        BindingResult bindingResult,
                        Authentication authentication,
                        RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("form", form);
            redirectAttributes.addFlashAttribute(BindingResult.MODEL_KEY_PREFIX + "form", bindingResult);
            return "redirect:/supervisor/evaluate/" + allocationId;
        }

        try {
            Evaluation evaluation = evaluationService.score(
                    authentication.getName(), allocationId, form.getScores(), form.getRemarks());
            redirectAttributes.addFlashAttribute("success",
                    "Marks recorded. Weighted total " + evaluation.getTotal() + ".");
            return "redirect:/supervisor/evaluate";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/supervisor/evaluate/" + allocationId;
        }
    }

    private Allocation load(Long allocationId, String email) {
        Allocation allocation = allocationRepository.findWithGraphById(allocationId)
                .orElseThrow(() -> new NotFoundException("Allocation", allocationId));
        if (!allocationRepository.existsByIdAndSupervisorUserEmail(allocationId, email)) {
            throw new NotFoundException("Allocation", allocationId);
        }
        return allocation;
    }
}
