package com.dms.readiness;

import com.dms.user.Programme;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/coordinator/readiness")
@RequiredArgsConstructor
public class CoordinatorReadinessController {

    private final ReadinessService readinessService;

    @GetMapping("")
    public String overview(@RequestParam(defaultValue = "MTECH") Programme programme, Model model) {
        model.addAttribute("rows", readinessService.ledgersFor(programme));
        model.addAttribute("programme", programme);
        model.addAttribute("programmes", Programme.values());
        return "coordinator/readiness";
    }

    @GetMapping("/{allocationId}")
    public String detail(@PathVariable Long allocationId, Model model) {
        model.addAttribute("ledger", readinessService.ledgerFor(allocationId));
        return "coordinator/readiness-detail";
    }
}
