package com.dms.account;

import com.dms.user.User;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The properties that matter for a bearer credential: the plaintext is never
 * stored, a token works once, an expired one does not work at all, and issuing a
 * new one kills the old.
 */
@ExtendWith(MockitoExtension.class)
class AuthTokenServiceTest {

    @Mock private AuthTokenRepository tokenRepository;

    @InjectMocks private AuthTokenService service;

    @Test
    void theStoredValueIsADigestNotTheToken() {
        when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AuthTokenService.Issued issued = service.issue(user(), TokenPurpose.PASSWORD_RESET);

        assertNotNull(issued.plaintext());
        assertNotEquals(issued.plaintext(), issued.record().getTokenHash(),
                "storing the token itself would make a database leak an account takeover");
        assertEquals(64, issued.record().getTokenHash().length(), "SHA-256 hex is 64 characters");
        assertEquals(AuthTokenService.hash(issued.plaintext()), issued.record().getTokenHash());
    }

    @Test
    void twoTokensAreNeverTheSame() {
        when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String first = service.issue(user(), TokenPurpose.PASSWORD_RESET).plaintext();
        String second = service.issue(user(), TokenPurpose.PASSWORD_RESET).plaintext();

        assertNotEquals(first, second);
    }

    @Test
    void issuingBurnsAnyOutstandingTokenOfTheSamePurpose() {
        User user = user();
        when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.issue(user, TokenPurpose.PASSWORD_RESET);

        verify(tokenRepository).invalidateOutstanding(eq(user), eq(TokenPurpose.PASSWORD_RESET), any());
    }

    @Test
    void expiryFollowsThePurpose() {
        when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AuthToken reset = service.issue(user(), TokenPurpose.PASSWORD_RESET).record();
        AuthToken verify = service.issue(user(), TokenPurpose.EMAIL_VERIFICATION).record();

        assertTrue(reset.getExpiresAt().isBefore(verify.getExpiresAt()),
                "a reset link is a credential, so it must die sooner than a confirmation link");
    }

    @Test
    void aGoodTokenIsRedeemedAndStampedUsed() {
        AuthToken token = token(Instant.now().plusSeconds(600), null);
        when(tokenRepository.findByTokenHashAndPurpose(AuthTokenService.hash("secret"),
                TokenPurpose.PASSWORD_RESET)).thenReturn(Optional.of(token));
        when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertTrue(service.consume("secret", TokenPurpose.PASSWORD_RESET).isPresent());

        ArgumentCaptor<AuthToken> saved = ArgumentCaptor.forClass(AuthToken.class);
        verify(tokenRepository).save(saved.capture());
        assertNotNull(saved.getValue().getUsedAt(), "redeeming must mark the row spent");
    }

    @Test
    void aTokenCannotBeSpentTwice() {
        AuthToken alreadyUsed = token(Instant.now().plusSeconds(600), Instant.now().minusSeconds(60));
        when(tokenRepository.findByTokenHashAndPurpose(any(), any())).thenReturn(Optional.of(alreadyUsed));

        assertTrue(service.consume("secret", TokenPurpose.PASSWORD_RESET).isEmpty());
        verify(tokenRepository, never()).save(any());
    }

    @Test
    void anExpiredTokenIsRefused() {
        AuthToken expired = token(Instant.now().minusSeconds(1), null);
        when(tokenRepository.findByTokenHashAndPurpose(any(), any())).thenReturn(Optional.of(expired));

        assertTrue(service.consume("secret", TokenPurpose.PASSWORD_RESET).isEmpty());
    }

    @Test
    void anUnknownTokenIsRefused() {
        when(tokenRepository.findByTokenHashAndPurpose(any(), any())).thenReturn(Optional.empty());

        assertTrue(service.consume("nonsense", TokenPurpose.PASSWORD_RESET).isEmpty());
    }

    @Test
    void aTokenIsBoundToItsPurpose() {
        // A confirmation link must not be redeemable as a password reset, which is
        // why purpose is part of the lookup rather than checked afterwards.
        when(tokenRepository.findByTokenHashAndPurpose(AuthTokenService.hash("secret"),
                TokenPurpose.PASSWORD_RESET)).thenReturn(Optional.empty());

        assertTrue(service.consume("secret", TokenPurpose.PASSWORD_RESET).isEmpty());
        verify(tokenRepository).findByTokenHashAndPurpose(AuthTokenService.hash("secret"),
                TokenPurpose.PASSWORD_RESET);
    }

    @Test
    void aBlankTokenTouchesNothing() {
        assertTrue(service.consume(null, TokenPurpose.PASSWORD_RESET).isEmpty());
        assertTrue(service.consume("  ", TokenPurpose.PASSWORD_RESET).isEmpty());
        assertFalse(service.isUsable(null, TokenPurpose.PASSWORD_RESET));

        verify(tokenRepository, never()).findByTokenHashAndPurpose(any(), any());
    }

    @Test
    void peekingDoesNotSpendTheToken() {
        when(tokenRepository.findByTokenHashAndPurpose(any(), any()))
                .thenReturn(Optional.of(token(Instant.now().plusSeconds(600), null)));

        assertTrue(service.isUsable("secret", TokenPurpose.PASSWORD_RESET));
        verify(tokenRepository, never()).save(any());
    }

    // ---- fixtures -----------------------------------------------------------

    private User user() {
        User user = new User();
        user.setId(1L);
        user.setEmail("student@college.edu");
        user.setFullName("Test Student");
        return user;
    }

    private AuthToken token(Instant expiresAt, Instant usedAt) {
        AuthToken token = new AuthToken();
        token.setId(9L);
        token.setUser(user());
        token.setTokenHash(AuthTokenService.hash("secret"));
        token.setPurpose(TokenPurpose.PASSWORD_RESET);
        token.setExpiresAt(expiresAt);
        token.setUsedAt(usedAt);
        return token;
    }
}
