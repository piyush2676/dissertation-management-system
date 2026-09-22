package com.dms.change;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

/** The committee's answer. A refusal has to say why; section 4.11 promises feedback. */
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ChangeRequestDecisionForm {

    @NotNull(message = "Approve or reject the request")
    ChangeRequestStatus decision;

    @Size(max = 1000, message = "Keep the note under 1000 characters")
    String note;

    @AssertTrue(message = "Tell the scholar why the request was refused")
    public boolean isNotePresentWhenRejecting() {
        if (decision != ChangeRequestStatus.REJECTED) {
            return true;
        }
        return note != null && !note.isBlank();
    }
}
