package com.dms.titlebank;

import com.dms.topic.ExpectedOutcome;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/supervisor/titles")
@RequiredArgsConstructor
public class SupervisorTitleBankController {

    private final TitleBankService titleBankService;

    @ModelAttribute("outcomes")
    public ExpectedOutcome[] outcomes() {
        return ExpectedOutcome.values();
    }

    @ModelAttribute("complexities")
    public Complexity[] complexities() {
        return Complexity.values();
    }

    @ModelAttribute("expectedPerGuide")
    public int expectedPerGuide() {
        return BankedTitle.EXPECTED_PER_GUIDE;
    }

    @GetMapping("")
    public String titles(Authentication authentication, Model model) {
        model.addAttribute("titles", titleBankService.mine(authentication.getName()));
        model.addAttribute("openCount", titleBankService.openCountFor(authentication.getName()));
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new BankedTitleForm());
        }
        model.addAttribute("mode", "new");
        return "supervisor/titles";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long id, Authentication authentication, Model model) {
        BankedTitle banked = titleBankService.loadForEdit(authentication.getName(), id);
        model.addAttribute("titles", titleBankService.mine(authentication.getName()));
        model.addAttribute("openCount", titleBankService.openCountFor(authentication.getName()));
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", BankedTitleForm.form(banked));
        }
        model.addAttribute("mode", "edit");
        return "supervisor/titles";
    }

    @PostMapping("")
    public String offer(@Valid @ModelAttribute("form") BankedTitleForm form,
                        BindingResult bindingResult,
                        Authentication authentication,
                        Model model,
                        RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("titles", titleBankService.mine(authentication.getName()));
            model.addAttribute("openCount", titleBankService.openCountFor(authentication.getName()));
            model.addAttribute("mode", "new");
            return "supervisor/titles";
        }
        titleBankService.offer(authentication.getName(), form);
        redirectAttributes.addFlashAttribute("success", "Title added to the bank. Scholars can see it now.");
        return "redirect:/supervisor/titles";
    }

    @PostMapping("/{id}")
    public String revise(@PathVariable Long id,
                         @Valid @ModelAttribute("form") BankedTitleForm form,
                         BindingResult bindingResult,
                         Authentication authentication,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("titles", titleBankService.mine(authentication.getName()));
            model.addAttribute("openCount", titleBankService.openCountFor(authentication.getName()));
            model.addAttribute("mode", "edit");
            return "supervisor/titles";
        }
        titleBankService.revise(authentication.getName(), id, form);
        redirectAttributes.addFlashAttribute("success", "Title updated.");
        return "redirect:/supervisor/titles";
    }

    @PostMapping("/{id}/withdraw")
    public String withdraw(@PathVariable Long id,
                           Authentication authentication,
                           RedirectAttributes redirectAttributes) {
        titleBankService.withdraw(authentication.getName(), id);
        redirectAttributes.addFlashAttribute("success",
                "Title withdrawn. A proposal already made from it is untouched.");
        return "redirect:/supervisor/titles";
    }
}
