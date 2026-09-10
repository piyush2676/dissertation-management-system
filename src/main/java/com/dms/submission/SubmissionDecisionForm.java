package com.dms.submission;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SubmissionDecisionForm {

    @NotNull(message = "Choose a decision")
    private SubmissionStatus decision;

    @Size(max = 2000, message = "Keep the note under 2000 characters")
    private String note;

    /** Approval can stand on its own; anything else has to say why. */
    @AssertTrue(message = "Tell the student what to change")
    public boolean isNotePresentWhenNotApproving() {
        if (decision == null || decision == SubmissionStatus.APPROVED) {
            return true;
        }
        return note != null && !note.isBlank();
    }
}
