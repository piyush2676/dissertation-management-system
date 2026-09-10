package com.dms.viva;

import com.dms.allocation.AllocationBoard;
import com.dms.allocation.AllocationService;
import com.dms.common.InvalidStateTransitionException;
import com.dms.evaluation.EvaluationService;
import com.dms.evaluation.MarkSheet;
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

import java.time.ZoneId;
import java.util.List;

@Controller
@RequestMapping("/coordinator")
@RequiredArgsConstructor
public class CoordinatorVivaController {

    private final VivaService vivaService;
    private final EvaluationService evaluationService;
    private final AllocationService allocationService;

    @ModelAttribute("programmes")
    public List<Programme> programmes() {
        return List.of(Programme.values());
    }

    // ---- viva ---------------------------------------------------------------

    @GetMapping("/viva")
    public String viva(@RequestParam(defaultValue = "MTECH") Programme programme, Model model) {
        model.addAttribute("programme", programme);

        try {
            model.addAttribute("schedules", vivaService.scheduleFor(programme));

            AllocationBoard board = allocationService.board(programme);
            model.addAttribute("placed", board.allocated());
            model.addAttribute("sessionLabel", board.sessionLabel());
        } catch (IllegalStateException ex) {
            model.addAttribute("schedules", List.of());
            model.addAttribute("placed", List.of());
            model.addAttribute("error", ex.getMessage());
        }

        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new VivaScheduleForm());
        }
        return "coordinator/viva";
    }

    @PostMapping("/viva/schedule")
    public String schedule(@RequestParam Programme programme,
                           @Valid @ModelAttribute("form") VivaScheduleForm form,
                           BindingResult bindingResult,
                           Authentication authentication,
                           RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("form", form);
            redirectAttributes.addFlashAttribute(BindingResult.MODEL_KEY_PREFIX + "form", bindingResult);
            return "redirect:/coordinator/viva?programme=" + programme.name();
        }

        try {
            vivaService.schedule(authentication.getName(), form.getAllocationId(),
                    form.getScheduledAt().atZone(ZoneId.systemDefault()).toInstant(),
                    form.getVenue(), form.getPanel());
            redirectAttributes.addFlashAttribute("success", "Viva scheduled.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        } catch (InvalidStateTransitionException ex) {
            redirectAttributes.addFlashAttribute("error", "That viva is already closed.");
        }
        return "redirect:/coordinator/viva?programme=" + programme.name();
    }

    @PostMapping("/viva/{vivaId}/mark")
    public String mark(@PathVariable Long vivaId,
                       @RequestParam Programme programme,
                       @RequestParam VivaStatus status,
                       Authentication authentication,
                       RedirectAttributes redirectAttributes) {
        try {
            vivaService.mark(authentication.getName(), vivaId, status);
            redirectAttributes.addFlashAttribute("success", "Viva marked as " + status + ".");
        } catch (InvalidStateTransitionException ex) {
            redirectAttributes.addFlashAttribute("error", "That viva can no longer change state.");
        }
        return "redirect:/coordinator/viva?programme=" + programme.name();
    }

    // ---- mark sheet ---------------------------------------------------------

    @GetMapping("/marksheet")
    public String markSheet(@RequestParam(defaultValue = "MTECH") Programme programme, Model model) {
        MarkSheet sheet = evaluationService.markSheet(programme);
        model.addAttribute("programme", programme);
        model.addAttribute("sheet", sheet);
        return "coordinator/marksheet";
    }
}
