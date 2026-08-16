package com.dms.allocation;

import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PACKAGE)
public class AllocationRequestForm {
    @NotNull(message = "Choose a guide to request")
    Long supervisorId;

}
