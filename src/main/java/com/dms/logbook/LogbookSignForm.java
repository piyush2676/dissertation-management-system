package com.dms.logbook;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

/** The guide's side: sign it, or send it back with a reason. */
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class LogbookSignForm {

    @NotNull(message = "Sign or return the entry")
    LogbookEntryStatus decision;

    @Size(max = 1000, message = "Keep the remark under 1000 characters")
    String remarks;

    @AssertTrue(message = "Say what needs correcting when returning an entry")
    public boolean isRemarkPresentWhenReturning() {
        if (decision != LogbookEntryStatus.RETURNED) {
            return true;
        }
        return remarks != null && !remarks.isBlank();
    }
}
