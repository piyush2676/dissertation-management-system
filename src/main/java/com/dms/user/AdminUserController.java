package com.dms.user;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

/**
 * Read-only roll of accounts. Creating and editing users is deliberately out of
 * scope: accounts arrive from the institute records, and DataSeeder covers the demo.
 */
@Controller
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserRepository userRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final SupervisorProfileRepository supervisorProfileRepository;

    @GetMapping("")
    public String users(Model model) {
        List<User> users = userRepository.findAll();
        model.addAttribute("users", users);
        model.addAttribute("students", studentProfileRepository.findAll());
        model.addAttribute("supervisors", supervisorProfileRepository.findAllBy());
        return "admin/users";
    }
}
