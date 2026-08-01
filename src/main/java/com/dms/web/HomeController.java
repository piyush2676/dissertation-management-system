package com.dms.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Landing page.
 *
 * <p>View-only controller: no service calls, no model attributes. Deliberately kept trivial
 * so the public entry point has nothing that can fail.
 */
@Controller
public class HomeController {

    @GetMapping("/")
    public String home() {
        return "home";
    }
}
