package com.dms.outcome;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

/** The coordinator's side: seen and confirmed, or sent back with what is missing. */
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OutcomeVerifyForm {

    @NotNull(message = "Verify or return the outcome")
    Boolean verified;

    @Size(max = 1000, message = "Keep the note under 1000 characters")
    String note;

    @AssertTrue(message = "Say what is missing when returning an outcome")
    public boolean isNotePresentWhenReturning() {
        if (verified == null || verified) {
            return true;
        }
        return note != null && !note.isBlank();
    }
}
