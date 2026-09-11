package com.dms.account;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ResetPasswordForm {

    private String token;

    @NotBlank(message = "Choose a password")
    @Size(min = AccountService.MIN_PASSWORD_LENGTH,
          message = "Use at least " + AccountService.MIN_PASSWORD_LENGTH + " characters")
    private String password;

    @NotBlank(message = "Type the password again")
    private String confirmPassword;

    /** Catching the typo here beats locking someone out of their own account. */
    @AssertTrue(message = "The two passwords do not match")
    public boolean isPasswordsMatching() {
        if (password == null || confirmPassword == null) {
            return true;
        }
        return password.equals(confirmPassword);
    }
}
