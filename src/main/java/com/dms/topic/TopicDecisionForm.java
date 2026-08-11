package com.dms.topic;

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
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TopicDecisionForm {
    @NotNull(message = "Choose a decision")
    TopicStatus decision;
    @Size(max = 200)
    String reason;
    @AssertTrue(message = "A reason is required when rejecting or requesting change")
    public boolean isReasonPresentWhenNeeded(){
        if(decision == null) return true;
        if(decision == TopicStatus.APPROVED) return true;
        return reason!=null && !reason.isBlank();
    }
}
