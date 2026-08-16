package com.dms.allocation;

import com.dms.common.InvalidStateTransitionException;
import com.dms.user.SupervisorProfile;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/student/guide")
@RequiredArgsConstructor
public class StudentAllocationController {
    private final AllocationService allocationService;
    @ModelAttribute("supervisors")
    public List<SupervisorProfile> supervisors(){
        return allocationService.selectableSupervisors();
    }
    @GetMapping("")
    public String view(Authentication auth, Model model){
        String email = auth.getName();
        Optional<Allocation> current = allocationService.currentAllocationFor(email);
        model.addAttribute("hasAllocation",current.isPresent());
        model.addAttribute("allocation",current.orElse(null));
        model.addAttribute("history",allocationService.historyFor(email));
        model.addAttribute("canRequest",current.isEmpty());
        model.addAttribute("hasTopic",allocationService.hasTopic(email));
        model.addAttribute("seatsTaken",allocationService.seatsTakenFor(email));
        if(!model.containsAttribute("form")){
            model.addAttribute("form",new AllocationRequestForm());
        }
        return "student/guide";
    }
    @PostMapping("/request")
    public String request(@Valid @ModelAttribute("form") AllocationRequestForm form, BindingResult bindingResult, Authentication auth, RedirectAttributes redirectAttributes){
        if(bindingResult.hasErrors()){
            redirectAttributes.addFlashAttribute("form", form);
            redirectAttributes.addFlashAttribute(BindingResult.MODEL_KEY_PREFIX + "form", bindingResult);
            return "redirect:/student/guide";
        }
        try {
            allocationService.request(auth.getName(), form.getSupervisorId());
            redirectAttributes.addFlashAttribute("success", "Request sent. Your guide will respond.");
        } catch (CapacityExceededException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/student/guide";
    }
     @PostMapping("/{id}/withdraw")
    public String withdraw(@PathVariable Long id,Authentication auth,RedirectAttributes redirectAttributes){
        try {
            allocationService.withdraw(auth.getName(), id);
            redirectAttributes.addFlashAttribute("success", "Request withdrawn.");
        } catch (InvalidStateTransitionException ex) {
            redirectAttributes.addFlashAttribute("error", "That request can no longer be withdrawn.");
        }
        return "redirect:/student/guide";
     }
}
