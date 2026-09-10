package com.dms.ai;

import com.dms.topic.Topic;
import com.dms.topic.TopicService;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;

/**
 * The two AI features a student can reach. Both are advisory and both are
 * explicitly labelled as such in the templates.
 *
 * <p>Each runs only when asked. Embedding on every page load would burn a free-tier
 * quota to render a page nobody was reading.
 */
@Controller
@RequestMapping("/student/ai")
@RequiredArgsConstructor
public class StudentAiController {

    private final TopicService topicService;
    private final TopicNoveltyService noveltyService;
    private final SupervisorMatchingService matchingService;

    // ---- novelty ------------------------------------------------------------

    @GetMapping("/novelty")
    public String novelty(Authentication authentication, Model model) {
        Optional<Topic> topic = topicService.currentTopicFor(authentication.getName());

        model.addAttribute("hasTopic", topic.isPresent());
        model.addAttribute("topic", topic.orElse(null));
        model.addAttribute("embeddingsAvailable", noveltyService.embeddingsAvailable());
        model.addAttribute("narrativeAvailable", noveltyService.narrativeAvailable());

        topic.flatMap(t -> noveltyService.existingReport(t.getId()))
                .ifPresent(report -> model.addAttribute("storedReport", report));

        return "student/ai/novelty";
    }

    @PostMapping("/novelty")
    public String runNovelty(Authentication authentication, RedirectAttributes redirectAttributes) {
        Optional<Topic> topic = topicService.currentTopicFor(authentication.getName());

        if (topic.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Propose a topic first.");
            return "redirect:/student/topic";
        }

        try {
            NoveltyResult result = noveltyService.check(topic.get().getId());
            redirectAttributes.addFlashAttribute("result", result);
            redirectAttributes.addFlashAttribute("success", "Overlap check complete.");
        } catch (AiUnavailableException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/student/ai/novelty";
    }

    // ---- supervisor matching ------------------------------------------------

    @GetMapping("/match")
    public String match(Authentication authentication, Model model) {
        Optional<Topic> topic = topicService.currentTopicFor(authentication.getName());

        model.addAttribute("hasTopic", topic.isPresent());
        model.addAttribute("topic", topic.orElse(null));
        model.addAttribute("available", matchingService.isAvailable());
        return "student/ai/match";
    }

    @PostMapping("/match")
    public String runMatch(Authentication authentication, RedirectAttributes redirectAttributes) {
        Optional<Topic> topic = topicService.currentTopicFor(authentication.getName());

        if (topic.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Propose a topic first.");
            return "redirect:/student/topic";
        }

        try {
            redirectAttributes.addFlashAttribute("matches", matchingService.match(
                    topic.get().getTitle() + ". " + topic.get().getAbstractText(), 5));
            redirectAttributes.addFlashAttribute("success", "Ranked by closeness to stated interests.");
        } catch (AiUnavailableException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/student/ai/match";
    }
}
