package com.dms.viva;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class VivaScheduleForm {

    @NotNull(message = "Choose a student")
    private Long allocationId;

    @NotNull(message = "Pick a date and time")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime scheduledAt;

    @NotBlank(message = "A venue is required")
    @Size(max = 255)
    private String venue;

    @Size(max = 1000, message = "Keep the panel list under 1000 characters")
    private String panel;
}
