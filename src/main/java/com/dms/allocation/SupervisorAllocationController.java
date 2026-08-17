package com.dms.allocation;

import com.dms.common.InvalidStateTransitionException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/supervisor/requests")
@RequiredArgsConstructor
public class SupervisorAllocationController {
    private final AllocationService allocationService;
    @ModelAttribute("decisions")
    public List<AllocationStatus> decisions() {
        return List.of(AllocationStatus.ACCEPTED,AllocationStatus.DECLINED);
    }
    @GetMapping("")
    public String inbox(Authentication auth, Model model) {
        String email = auth.getName();
        model.addAttribute("pending",allocationService.inboxFor(email));
        model.addAttribute("decided",allocationService.decidedBy(email));
        if(!model.containsAttribute("form")){
            model.addAttribute("form",new AllocationDecisionForm());
        }
        return "supervisor/requests";
    }
    @PostMapping("{id}/decide")
    public String decide(@PathVariable Long  id, @Valid @ModelAttribute("form") AllocationDecisionForm form, BindingResult bindingResult, Authentication auth, RedirectAttributes redirectAttributes){
        if(bindingResult.hasErrors()){
            redirectAttributes.addFlashAttribute("form",form);
            redirectAttributes.addFlashAttribute(BindingResult.MODEL_KEY_PREFIX+"form",bindingResult);
            redirectAttributes.addFlashAttribute("openRequestId" , id);
            return "redirect:/supervisor/requests";
        }
        try {
            switch (form.getDecision()){
                case ACCEPTED:
                    allocationService.accept(auth.getName(),id);
                    redirectAttributes.addFlashAttribute("success","Request accepted.");
                    break;
                    case DECLINED:
                        allocationService.decline(auth.getName(),id,form.getReason());
                        redirectAttributes.addFlashAttribute("success","Request declined.");
                        break;
                        default:
                            redirectAttributes.addFlashAttribute("error","That is not a decision you can record.");
            }
        }catch (CapacityExceededException e){
            redirectAttributes.addFlashAttribute("error",e.getMessage());
        }catch (InvalidStateTransitionException ex){
            redirectAttributes.addFlashAttribute("error","That request is no longer open - it may have been withdrawn");
        }
        return "redirect:/supervisor/requests";

    }
}
