package com.dms.change;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

/** Section 4.11 wants a written request with a justification, so the reason is required. */
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ChangeRequestForm {

    @NotNull(message = "What do you want to change?")
    ChangeKind kind;

    @Size(min = 40, max = 2000, message = "Give the committee 40 to 2000 characters of justification")
    String reason;

    /** Optional: a supervisor change need not name a replacement. */
    Long preferredSupervisorId;

    @Size(max = 255, message = "Title must be 255 characters or fewer")
    String proposedTitle;

    @AssertTrue(message = "Say what title you intend to propose instead")
    public boolean isTitlePresentForATitleChange() {
        if (kind != ChangeKind.TITLE) {
            return true;
        }
        return proposedTitle != null && !proposedTitle.isBlank();
    }
}
