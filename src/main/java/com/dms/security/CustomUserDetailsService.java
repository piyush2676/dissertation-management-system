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

/**
 * Bridges the application's User table to Spring Security.
 *
 * <p>Spring calls this on every login attempt with whatever was typed in the username
 * field, and expects back a UserDetails carrying the stored hash and the granted
 * authorities. Passwords are not compared here -- the PasswordEncoder bean does that.
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    /** Must be final: @RequiredArgsConstructor only generates arguments for final fields. */
    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("No user with email " + email));

        // role.authority() applies the ROLE_ prefix. Passing role.name() instead would
        // make every hasRole(...) check silently fail to match.
        List<SimpleGrantedAuthority> authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.authority()))
                .toList();

        // Fully qualified: this is Spring's User, not the com.dms.user.User imported above.
        return org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(user.getPasswordHash())
                .authorities(authorities)
                .disabled(!user.isEnabled())
                .build();
    }
}
