package com.dms.panel;

import com.dms.common.NotFoundException;
import com.dms.user.Programme;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

@Controller
@RequestMapping("/coordinator/panels")
@RequiredArgsConstructor
public class CoordinatorPanelController {

    private final PanelService panelService;

    @GetMapping("")
    public String board(@RequestParam(defaultValue = "MTECH") Programme programme, Model model) {
        model.addAttribute("board", panelService.board(programme));
        model.addAttribute("programme", programme);
        model.addAttribute("programmes", Programme.values());
        model.addAttribute("expectedSize", PanelService.EXPECTED_SIZE);
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new PanelMemberForm());
        }
        return "coordinator/panels";
    }

    @PostMapping("/{allocationId}/add")
    public String add(@PathVariable Long allocationId,
                      @RequestParam Programme programme,
                      @Valid @ModelAttribute("form") PanelMemberForm form,
                      BindingResult bindingResult,
                      Authentication authentication,
                      RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", "Choose a faculty member.");
            return redirectTo(programme);
        }
        try {
            panelService.add(authentication.getName(), allocationId, form.getMemberId());
            redirectAttributes.addFlashAttribute("success", "Panel member appointed.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        } catch (NotFoundException ex) {
            redirectAttributes.addFlashAttribute("error", "That student or faculty member no longer exists.");
        }
        return redirectTo(programme);
    }

    @PostMapping("/{allocationId}/remove/{memberId}")
    public String remove(@PathVariable Long allocationId,
                         @PathVariable Long memberId,
                         @RequestParam Programme programme,
                         Authentication authentication,
                         RedirectAttributes redirectAttributes) {
        try {
            panelService.remove(authentication.getName(), allocationId, memberId);
            redirectAttributes.addFlashAttribute("success",
                    "Panel member removed. Any marks they recorded stay on the mark sheet.");
        } catch (NotFoundException ex) {
            redirectAttributes.addFlashAttribute("error", "They are not on this panel.");
        }
        return redirectTo(programme);
    }

    /** Redirect after post, carrying the tab so an appointment does not reset the programme. */
    private String redirectTo(Programme programme) {
        return "redirect:" + UriComponentsBuilder.fromPath("/coordinator/panels")
                .queryParam("programme", programme)
                .toUriString();
    }
}
