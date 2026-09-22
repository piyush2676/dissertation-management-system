package com.dms.attainment;

import com.dms.session.DissertationPhase;
import com.dms.user.Programme;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class CoordinatorAttainmentController {

    private final AttainmentService attainmentService;

    @GetMapping("/coordinator/attainment")
    public String report(@RequestParam(defaultValue = "MTECH") Programme programme,
                         @RequestParam(defaultValue = "FINAL") DissertationPhase phase,
                         Model model) {
        model.addAttribute("report", attainmentService.reportFor(programme, phase));
        model.addAttribute("programme", programme);
        model.addAttribute("phase", phase);
        model.addAttribute("programmes", Programme.values());
        model.addAttribute("phases", DissertationPhase.values());
        return "coordinator/attainment";
    }
}
