package com.dms.account;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ForgotPasswordForm {

    @NotBlank(message = "Enter your institute email address")
    @Email(message = "That does not look like an email address")
    private String email;
}
