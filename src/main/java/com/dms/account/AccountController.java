package com.dms.account;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Address confirmation and password reset.
 *
 * <p>Most of this is reachable signed out, by necessity -- somebody who has
 * forgotten their password cannot sign in to ask for a reset.
 */
@Controller
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    // ---- address confirmation ----------------------------------------------

    @GetMapping("/verify-email")
    public String verifyEmail(@RequestParam(required = false) String token, Model model) {
        model.addAttribute("confirmed", accountService.confirmEmail(token).isPresent());
        return "auth/verify-email";
    }

    /** Signed-in user asking for another confirmation link. */
    @PostMapping("/account/resend-verification")
    public String resend(Authentication authentication, RedirectAttributes redirectAttributes) {
        accountService.sendVerification(authentication.getName());

        redirectAttributes.addFlashAttribute("success", accountService.mailIsReal()
                ? "Confirmation link sent. Check your inbox."
                : "No mail server is configured, so the link was written to the application log instead.");
        return "redirect:/dashboard";
    }

    // ---- password reset -----------------------------------------------------

    @GetMapping("/forgot-password")
    public String forgotPassword(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new ForgotPasswordForm());
        }
        model.addAttribute("mailIsReal", accountService.mailIsReal());
        return "auth/forgot-password";
    }

    @PostMapping("/forgot-password")
    public String requestReset(@Valid @ModelAttribute("form") ForgotPasswordForm form,
                               BindingResult bindingResult,
                               RedirectAttributes redirectAttributes,
                               Model model) {

        if (bindingResult.hasErrors()) {
            model.addAttribute("mailIsReal", accountService.mailIsReal());
            return "auth/forgot-password";
        }

        accountService.sendPasswordReset(form.getEmail());

        // Always the same answer, whether or not the address is known. Confirming
        // which addresses exist would turn this page into a roll of the department.
        redirectAttributes.addFlashAttribute("sent", true);
        redirectAttributes.addFlashAttribute("mailIsReal", accountService.mailIsReal());
        return "redirect:/forgot-password-sent";
    }

    @GetMapping("/forgot-password-sent")
    public String resetRequested(Model model) {
        if (!model.containsAttribute("sent")) {
            return "redirect:/forgot-password";
        }
        return "auth/forgot-password-sent";
    }

    @GetMapping("/reset-password")
    public String resetForm(@RequestParam(required = false) String token, Model model) {
        model.addAttribute("tokenUsable", accountService.resetTokenLooksUsable(token));

        if (!model.containsAttribute("form")) {
            ResetPasswordForm form = new ResetPasswordForm();
            form.setToken(token);
            model.addAttribute("form", form);
        }
        return "auth/reset-password";
    }

    @PostMapping("/reset-password")
    public String reset(@Valid @ModelAttribute("form") ResetPasswordForm form,
                        BindingResult bindingResult,
                        Model model,
                        RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            model.addAttribute("tokenUsable", accountService.resetTokenLooksUsable(form.getToken()));
            return "auth/reset-password";
        }

        if (!accountService.resetPassword(form.getToken(), form.getPassword())) {
            model.addAttribute("tokenUsable", false);
            model.addAttribute("error", "That link has expired or has already been used.");
            return "auth/reset-password";
        }

        redirectAttributes.addFlashAttribute("success", "Password changed. Sign in with it now.");
        return "redirect:/login";
    }
}
