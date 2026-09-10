package com.dms.web;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Set;

/**
 * One dispatcher plus four leaf pages. The dispatcher is why defaultSuccessUrl
 * points at /dashboard: everyone lands on the same URL and is routed by role,
 * most privileged first, since a user can hold several.
 */
@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/login")
    public String login() {
        return "auth/login";
    }

    @GetMapping("/dashboard")
    public String dispatch(Authentication authentication) {
        Set<String> authorities = AuthorityUtils.authorityListToSet(authentication.getAuthorities());
        if (authorities.contains("ROLE_ADMIN")) return "redirect:/admin/dashboard";
        if (authorities.contains("ROLE_COORDINATOR")) return "redirect:/coordinator/dashboard";
        if (authorities.contains("ROLE_SUPERVISOR")) return "redirect:/supervisor/dashboard";
        if (authorities.contains("ROLE_STUDENT")) return "redirect:/student/dashboard";
        return "redirect:/";
    }

    @GetMapping("/student/dashboard")
    public String student(Authentication authentication, Model model) {
        model.addAttribute("board", dashboardService.student(authentication.getName()));
        return "student/dashboard";
    }

    @GetMapping("/supervisor/dashboard")
    public String supervisor(Authentication authentication, Model model) {
        model.addAttribute("board", dashboardService.supervisor(authentication.getName()));
        return "supervisor/dashboard";
    }

    @GetMapping("/coordinator/dashboard")
    public String coordinator(Model model) {
        model.addAttribute("board", dashboardService.coordinator());
        return "coordinator/dashboard";
    }

    @GetMapping("/admin/dashboard")
    public String admin(Model model) {
        model.addAttribute("board", dashboardService.admin());
        model.addAttribute("recentTopics", dashboardService.recentApprovedTopics());
        return "admin/dashboard";
    }
}
