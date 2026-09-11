package com.dms.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth ->
                auth.requestMatchers("/","/login","/css/**","/js/**","/images/**","/favicon.ico","/error",
                                // Someone who has forgotten their password cannot sign in
                                // to ask for a reset, so these are open by necessity.
                                "/forgot-password","/forgot-password-sent",
                                "/reset-password","/verify-email",
                                // An external examiner holding a printed certificate
                                // has no account, and a check that needs a login
                                // checks nothing for the person who most needs it.
                                "/verify","/verify/**")
                        .permitAll().requestMatchers("/student/**").hasRole("STUDENT").
                        requestMatchers("/supervisor/**").hasRole("SUPERVISOR")
                        .requestMatchers("/coordinator/**").hasRole("COORDINATOR").
                        requestMatchers("/admin/**").hasRole("ADMIN").anyRequest().
                        authenticated()).formLogin(form -> form.loginPage("/login").
                defaultSuccessUrl("/dashboard",true).failureUrl("/login?error").permitAll())
                .logout(logout -> logout.logoutSuccessUrl("/login?logout").permitAll());
        return http.build();
    }
}
