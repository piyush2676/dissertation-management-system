package com.dms.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Landing page.
 *
 * <p>View-only controller — no service calls, so it lives on the frontend side of the
 * working agreement. Every other controller is yours; Claude supplies the signatures.
 */
@Controller
public class HomeController {

    @GetMapping("/")
    public String home() {
        return "home";
    }
}
