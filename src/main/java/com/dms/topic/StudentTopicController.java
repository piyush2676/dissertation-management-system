package com.dms.topic;

import com.dms.common.InvalidStateTransitionException;
import com.dms.common.NotFoundException;
import com.dms.user.SupervisorProfile;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Controller
@RequestMapping("/student/topic")
@RequiredArgsConstructor
public class StudentTopicController {
    private final TopicService topicService;

    @ModelAttribute("supervisors")
    public List<SupervisorProfile> supervisors() {
        return topicService.selectableSupervisors();
    }

    @ModelAttribute("editableStatuses")
    public Set<TopicStatus> editableStatuses() {
        return Set.of(TopicStatus.DRAFT, TopicStatus.CHANGES_REQUESTED);
    }

    @GetMapping("")
    public String view(Authentication authentication, Model model) {
        Optional<Topic> maybe = topicService.currentTopicFor(authentication.getName());
        model.addAttribute("hasTopic", maybe.isPresent());
        model.addAttribute("topic", maybe.orElse(null));
        maybe.ifPresent(topic -> {
            model.addAttribute("canEdit", editableStatuses().contains(topic.getStatus()));
            model.addAttribute("canSubmit", topic.getStatus().canTransitionTo(TopicStatus.PROPOSED));
            model.addAttribute("isTerminal", topic.getStatus().isTerminal());
        });
        return "student/topic/view";
    }

    @GetMapping("/new")
    public String newForm(Authentication authentication, Model model) {
        Optional<Topic> existing = topicService.currentTopicFor(authentication.getName());
        if (existing.isPresent() && !existing.get().getStatus().isTerminal()) {
            return "redirect:/student/topic";
        }
        model.addAttribute("form", new TopicForm());
        model.addAttribute("mode", "new");
        return "student/topic/form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Authentication authentication, Model model) {
        Topic topic = topicService.currentTopicFor(authentication.getName())
                .filter(t -> t.getId().equals(id))
                .orElseThrow(() -> new NotFoundException("Topic", id));
        if (!editableStatuses().contains(topic.getStatus())) {
            throw new InvalidStateTransitionException(topic.getStatus(), TopicStatus.DRAFT);
        }
        model.addAttribute("form", TopicForm.form(topic));
        model.addAttribute("mode", "edit");
        model.addAttribute("status", topic.getStatus());
        model.addAttribute("decisionReason", topic.getDecisionReason());
        return "student/topic/form";
    }

    @PostMapping("/save")
    public String saveDraft(@Valid @ModelAttribute("form") TopicForm form,
                            BindingResult bindingResult,
                            Authentication authentication,
                            Model model,
                            RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("mode", form.getId() == null ? "new" : "edit");
            return "student/topic/form";
        }
        topicService.saveDraft(authentication.getName(), form);
        redirectAttributes.addFlashAttribute("success", "Draft saved.");
        return "redirect:/student/topic";
    }

    @PostMapping("/submit")
    public String submit(@Valid @ModelAttribute("form") TopicForm form,
                         BindingResult bindingResult,
                         Authentication authentication,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("mode", form.getId() == null ? "new" : "edit");
            return "student/topic/form";
        }
        topicService.submitForApproval(authentication.getName(), form);
        redirectAttributes.addFlashAttribute("success", "Topic submitted. Your supervisor will review it.");
        return "redirect:/student/topic";
    }

    @PostMapping("/{id}/submit")
    public String submitExisting(@PathVariable Long id,
                                 Authentication authentication,
                                 RedirectAttributes redirectAttributes) {
        topicService.propose(authentication.getName(), id);
        redirectAttributes.addFlashAttribute("success", "Topic submitted for approval.");
        return "redirect:/student/topic";
    }
}
