package com.dms.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * PHASE 0 SHIM — replace this entire class in Phase 1.
 *
 * <p>Adding {@code spring-boot-starter-security} to the classpath locks every endpoint behind
 * a generated password, which would make the Phase 0 landing page undemoable. This opens it
 * up so the scaffold can be verified, and nothing else.
 *
 * <p>In Phase 1 this becomes the real configuration: form login at {@code /login}, a
 * {@code BCryptPasswordEncoder} bean, {@code CustomUserDetailsService}, and URL rules —
 * {@code /student/**} requiring {@code ROLE_STUDENT}, and so on. See {@code docs/guide.md} §7.
 * Ownership checks ({@code @authz.supervises(...)}) go in {@code AuthzService}, because role
 * alone is not enough: a supervisor must not read another supervisor's student.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll())
                .csrf(csrf -> csrf.disable());
        return http.build();
    }
}
