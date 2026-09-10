package com.dms.allocation;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Coordinator override: place a named student with a named guide. Thinner than
 * AllocationDecisionForm on purpose -- who and to whom are independently required,
 * and whether either still exists, or has a seat left, is the service's job.
 */
@Getter
@Setter
@NoArgsConstructor
public class AllocationAssignForm {

    @NotNull(message = "Choose a student")
    private Long studentId;

    @NotNull(message = "Choose a guide")
    private Long supervisorId;
}
