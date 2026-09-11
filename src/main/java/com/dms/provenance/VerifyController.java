package com.dms.provenance;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The public end of the story. Deliberately open: an external examiner holding a
 * printed certificate has no account here, and a verification that needs a login
 * verifies nothing for the person who most needs it.
 *
 * <p>What it exposes is only what the certificate already prints, so opening it
 * to the world discloses nothing the document did not. Codes are random rather
 * than sequential, so a stranger cannot walk the range.
 */
@Controller
@RequiredArgsConstructor
public class VerifyController {

    private final CertificateService certificateService;

    @GetMapping("/verify")
    public String form(@RequestParam(required = false) String code, Model model) {
        if (code != null && !code.isBlank()) {
            return "redirect:/verify/" + code.strip();
        }
        model.addAttribute("result", null);
        return "provenance/verify";
    }

    @GetMapping("/verify/{code}")
    public String verify(@PathVariable String code, Model model) {
        model.addAttribute("result", certificateService.verify(code));
        return "provenance/verify";
    }
}
