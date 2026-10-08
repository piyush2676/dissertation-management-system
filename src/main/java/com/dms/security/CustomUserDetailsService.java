package com.dms.security;

import com.dms.user.User;
import com.dms.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {
    private final UserRepository userRepository;
    private final LoginAttemptService loginAttempts;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        // Checked before the account is looked up, so a locked address answers the same way
        // whether or not it exists and whether or not the password offered is right. Returned as
        // a locked account rather than thrown: the provider turns that into LockedException before
        // it compares any password, and wraps anything thrown from here as an internal error.
        if (loginAttempts.isLocked(email)) {
            return org.springframework.security.core.userdetails.User
                    .withUsername(email == null ? "" : email.strip().toLowerCase(java.util.Locale.ROOT))
                    .password("{noop}locked")
                    .authorities(List.of())
                    .accountLocked(true)
                    .build();
        }
        // Addresses are stored lower-case, and an ERP ID is usually typed in capitals
        // (0221MCSD006@niet.co.in), so sign-in ignores case.
        User user = userRepository.findByEmail(email == null ? "" : email.strip().toLowerCase(java.util.Locale.ROOT))
                .orElseThrow(() -> new UsernameNotFoundException("No user with email " + email));

        List<SimpleGrantedAuthority> authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.authority()))
                .toList();

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(user.getPasswordHash())
                .authorities(authorities)
                .disabled(!user.isEnabled())
                .build();
    }
}
