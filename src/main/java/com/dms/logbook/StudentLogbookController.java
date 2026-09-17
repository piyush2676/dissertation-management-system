package com.dms.logbook;

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
@RequestMapping("/student/logbook")
@RequiredArgsConstructor
public class StudentLogbookController {

    private final LogbookService logbookService;

    @GetMapping("")
    public String board(Authentication authentication, Model model) {
        model.addAttribute("board", logbookService.boardFor(authentication.getName()));
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new LogbookEntryForm());
        }
        model.addAttribute("mode", "new");
        return "student/logbook";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long id, Authentication authentication, Model model) {
        LogbookEntry entry = logbookService.loadForEdit(authentication.getName(), id);
        model.addAttribute("board", logbookService.boardFor(authentication.getName()));
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", LogbookEntryForm.form(entry));
        }
        model.addAttribute("mode", "edit");
        model.addAttribute("editingMeetingNo", entry.getMeetingNo());
        model.addAttribute("editingStatus", entry.getStatus());
        model.addAttribute("editingRemarks", entry.getSupervisorRemarks());
        return "student/logbook";
    }

    @PostMapping("")
    public String record(@Valid @ModelAttribute("form") LogbookEntryForm form,
                         BindingResult bindingResult,
                         Authentication authentication,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("board", logbookService.boardFor(authentication.getName()));
            model.addAttribute("mode", "new");
            return "student/logbook";
        }
        try {
            LogbookEntry saved = logbookService.record(authentication.getName(), form);
            redirectAttributes.addFlashAttribute("success",
                    "Meeting " + saved.getMeetingNo() + " recorded. Your guide has it to sign.");
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/student/logbook";
    }

    @PostMapping("/{id}")
    public String revise(@PathVariable Long id,
                         @Valid @ModelAttribute("form") LogbookEntryForm form,
                         BindingResult bindingResult,
                         Authentication authentication,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            LogbookEntry entry = logbookService.loadForEdit(authentication.getName(), id);
            model.addAttribute("board", logbookService.boardFor(authentication.getName()));
            model.addAttribute("mode", "edit");
            model.addAttribute("editingMeetingNo", entry.getMeetingNo());
            model.addAttribute("editingStatus", entry.getStatus());
            model.addAttribute("editingRemarks", entry.getSupervisorRemarks());
            return "student/logbook";
        }
        LogbookEntry saved = logbookService.revise(authentication.getName(), id, form);
        redirectAttributes.addFlashAttribute("success", "Meeting " + saved.getMeetingNo() + " updated.");
        return "redirect:/student/logbook";
    }
}
