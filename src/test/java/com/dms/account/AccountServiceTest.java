package com.dms.account;

import com.dms.user.User;
import com.dms.user.UserRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    private static final String EMAIL = "student@college.edu";

    @Mock private UserRepository userRepository;
    @Mock private AuthTokenService tokenService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private Mailer mailer;

    @InjectMocks private AccountService service;

    // ---- no user enumeration ------------------------------------------------

    @Test
    void anUnknownAddressIsSilentlyIgnoredOnReset() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        service.sendPasswordReset("nobody@example.com");

        // No mail, no token, and -- crucially -- no exception the caller could use
        // to tell a registered address from an unregistered one.
        verify(mailer, never()).send(anyString(), anyString(), anyString());
        verify(tokenService, never()).issue(any(), any());
    }

    @Test
    void anUnknownAddressIsSilentlyIgnoredOnVerification() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        service.sendVerification("nobody@example.com");

        verify(mailer, never()).send(anyString(), anyString(), anyString());
    }

    // ---- address confirmation ----------------------------------------------

    @Test
    void aConfirmationMailCarriesTheTokenInALink() {
        User user = user(null);
        ReflectionTestUtils.setField(service, "baseUrl", "https://dms.niet.co.in");
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(tokenService.issue(user, TokenPurpose.EMAIL_VERIFICATION))
                .thenReturn(new AuthTokenService.Issued("tok123", new AuthToken()));

        service.sendVerification(EMAIL);

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(mailer).send(eqEmail(), anyString(), body.capture());
        assertTrue(body.getValue().contains("https://dms.niet.co.in/verify-email?token=tok123"));
    }

    @Test
    void anAlreadyConfirmedAddressIsNotMailedAgain() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user(Instant.now())));

        service.sendVerification(EMAIL);

        verify(mailer, never()).send(anyString(), anyString(), anyString());
        verify(tokenService, never()).issue(any(), any());
    }

    @Test
    void confirmingStampsTheUser() {
        User user = user(null);
        when(tokenService.consume("tok", TokenPurpose.EMAIL_VERIFICATION)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertTrue(service.confirmEmail("tok").isPresent());
        assertNotNull(user.getEmailVerifiedAt());
    }

    @Test
    void aBadConfirmationTokenChangesNothing() {
        when(tokenService.consume(anyString(), any())).thenReturn(Optional.empty());

        assertTrue(service.confirmEmail("rubbish").isEmpty());
        verify(userRepository, never()).save(any());
    }

    // ---- password reset -----------------------------------------------------

    @Test
    void resettingHashesTheNewPasswordAndNeverStoresIt() {
        User user = user(Instant.now());
        when(tokenService.consume("tok", TokenPurpose.PASSWORD_RESET)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("a-good-password")).thenReturn("$2a$10$hashed");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertTrue(service.resetPassword("tok", "a-good-password"));
        assertEquals("$2a$10$hashed", user.getPasswordHash());
    }

    @Test
    void aShortPasswordIsRefusedBeforeTheTokenIsSpent() {
        assertFalse(service.resetPassword("tok", "short"));

        // The token must survive, or a typo would cost the user their only link.
        verify(tokenService, never()).consume(anyString(), any());
    }

    @Test
    void aBadResetTokenChangesNoPassword() {
        when(tokenService.consume(anyString(), any())).thenReturn(Optional.empty());

        assertFalse(service.resetPassword("rubbish", "a-good-password"));
        verify(userRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void aSuccessfulResetAlsoConfirmsTheAddress() {
        User user = user(null);
        when(tokenService.consume("tok", TokenPurpose.PASSWORD_RESET)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hashed");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.resetPassword("tok", "a-good-password");

        assertNotNull(user.getEmailVerifiedAt(),
                "receiving the mail is exactly the proof the address works");
    }

    // ---- fixtures -----------------------------------------------------------

    private static String eqEmail() {
        return org.mockito.ArgumentMatchers.eq(EMAIL);
    }

    private User user(Instant verifiedAt) {
        User user = new User();
        user.setId(1L);
        user.setEmail(EMAIL);
        user.setFullName("Test Student");
        user.setPasswordHash("$2a$10$old");
        user.setEmailVerifiedAt(verifiedAt);
        return user;
    }
}
