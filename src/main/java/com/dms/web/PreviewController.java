package com.dms.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * TEMPORARY — delete this class at the end of Phase 1.
 *
 * <p>Lets the Phase 1 templates be viewed before the real controllers exist. Routes live
 * under {@code /preview/**} on purpose: the real pages are {@code /login},
 * {@code /student/dashboard} and so on, so mapping them here would collide with
 * {@code DashboardController} and fail startup with an ambiguous-mapping error.
 *
 * <p>Note the role dashboards render blank-ish here — {@code sec:authentication="name"}
 * has no principal to read while the Phase 0 security shim permits everyone through.
 * That is expected; they fill in once real authentication lands.
 */
@Controller
public class PreviewController {

    @GetMapping("/preview/login")
    public String login() {
        return "auth/login";
    }

    @GetMapping("/preview/student")
    public String student() {
        return "student/dashboard";
    }

    @GetMapping("/preview/supervisor")
    public String supervisor() {
        return "supervisor/dashboard";
    }

    @GetMapping("/preview/coordinator")
    public String coordinator() {
        return "coordinator/dashboard";
    }

    @GetMapping("/preview/admin")
    public String admin() {
        return "admin/dashboard";
    }

    @GetMapping("/preview/403")
    public String forbidden() {
        return "error/403";
    }
}