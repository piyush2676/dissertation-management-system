package com.dms.evaluation;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

/**
 * Keyed by rubric criterion id, so the form shape follows the rubric rows rather
 * than being fixed at compile time.
 */
@Getter
@Setter
@NoArgsConstructor
public class EvaluationForm {

    private Long allocationId;

    private Map<Long, Integer> scores = new HashMap<>();

    @Size(max = 2000, message = "Keep remarks under 2000 characters")
    private String remarks;
}
