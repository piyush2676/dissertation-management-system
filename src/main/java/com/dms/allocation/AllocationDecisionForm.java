package com.dms.allocation;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@NoArgsConstructor
public class AllocationDecisionForm {
    @NotNull(message = "Choose a decision")
    AllocationStatus decision;
    @Size(max = 200)
    String reason;
    @AssertTrue(message = "A reason is required when declining a request")
     public boolean isReasonPresentWhenDeclining(){
        if (decision == null) return true;
        else if (decision != AllocationStatus.DECLINED) return true;
        return reason != null && !reason.isBlank();

    }
}
