package com.dms.panel;

import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

/** Thin on purpose: who is appointable, and whether they conflict, is the service's job. */
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PanelMemberForm {

    @NotNull(message = "Choose a faculty member")
    Long memberId;
}
