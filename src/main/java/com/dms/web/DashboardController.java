package com.dms.web;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Set;

@Controller
public class DashboardController {
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
    public String student(){
        return "student/dashboard";
    }
    @GetMapping("/supervisor/dashboard")
    public String supervisor(){
        return "supervisor/dashboard";
    }
    @GetMapping("/coordinator/dashboard")
    public String coordinator(){
        return "coordinator/dashboard";
    }
    @GetMapping("/admin/dashboard")
    public String admin(){
        return "admin/dashboard";
    }
}
