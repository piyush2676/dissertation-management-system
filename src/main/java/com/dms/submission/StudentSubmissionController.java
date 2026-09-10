package com.dms.submission;

import com.dms.common.InvalidStateTransitionException;
import com.dms.storage.StorageException;

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
@RequestMapping("/student/submissions")
@RequiredArgsConstructor
public class StudentSubmissionController {

    private final SubmissionService submissionService;

    @GetMapping("")
    public String board(Authentication authentication, Model model) {
        model.addAttribute("board", submissionService.boardFor(authentication.getName()));
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new SubmissionUploadForm());
        }
        return "student/submissions";
    }

    @GetMapping("/{submissionId}")
    public String detail(@PathVariable Long submissionId, Authentication authentication, Model model) {
        model.addAttribute("detail",
                submissionService.detailFor(submissionId, authentication.getName(), false));
        return "student/submission";
    }

    @PostMapping("/{milestoneId}/upload")
    public String upload(@PathVariable Long milestoneId,
                         @Valid @ModelAttribute("form") SubmissionUploadForm form,
                         BindingResult bindingResult,
                         Authentication authentication,
                         RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("error",
                    bindingResult.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/student/submissions";
        }

        try {
            Submission submission = submissionService.upload(
                    authentication.getName(), milestoneId, form.getFile(), form.getNote());
            redirectAttributes.addFlashAttribute("success",
                    "Version " + submission.getCurrentVersionNo() + " submitted.");
        } catch (StorageException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        } catch (InvalidStateTransitionException ex) {
            redirectAttributes.addFlashAttribute("error",
                    "That milestone is not open for a new version right now.");
        }

        return "redirect:/student/submissions";
    }
}
