package com.dms.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class RegulationQuestionForm {

    @NotBlank(message = "Type a question first")
    @Size(max = 500, message = "Keep the question under 500 characters")
    private String question;
}
