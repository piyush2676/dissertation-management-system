package com.dms.recommendation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/** The coordinator's office reads Annexure-6. There is no student route to it at all. */
@Controller
@RequiredArgsConstructor
public class CoordinatorRecommendationController {

    private final RecommendationService recommendationService;

    @GetMapping("/coordinator/recommendation/{allocationId}")
    public String view(@PathVariable Long allocationId, Model model) {
        model.addAttribute("view", recommendationService.forCoordinator(allocationId));
        return "coordinator/recommendation";
    }
}
