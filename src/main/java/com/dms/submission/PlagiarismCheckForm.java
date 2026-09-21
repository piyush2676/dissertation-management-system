package com.dms.submission;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

/** What the guide reads off the similarity report for one version. */
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PlagiarismCheckForm {

    @NotNull(message = "Enter the similarity percentage")
    @DecimalMin(value = "0.00", message = "Percentages start at 0")
    @DecimalMax(value = "100.00", message = "Percentages end at 100")
    @Digits(integer = 3, fraction = 2, message = "Up to two decimal places")
    BigDecimal similarityPercent;

    @NotNull(message = "Enter the AI-generated percentage")
    @DecimalMin(value = "0.00", message = "Percentages start at 0")
    @DecimalMax(value = "100.00", message = "Percentages end at 100")
    @Digits(integer = 3, fraction = 2, message = "Up to two decimal places")
    BigDecimal aiPercent;

    @Size(max = 64, message = "Tool name must be 64 characters or fewer")
    String tool;

    @Size(max = 255, message = "Note must be 255 characters or fewer")
    String note;
}
