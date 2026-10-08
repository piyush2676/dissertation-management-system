package com.dms.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.CacheControlHeadersWriter;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * What a page may load. The only third party is Google Fonts (the institute typeface), named
     * explicitly. No template carries an inline script, so scripts are same-origin only; inline
     * style attributes are used in places, hence 'unsafe-inline' for styles alone. Framing is
     * refused, forms may only post back to this site, and plugins are off.
     */
    static final String CONTENT_SECURITY_POLICY = String.join("; ",
            "default-src 'self'",
            "script-src 'self'",
            "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com",
            "font-src 'self' https://fonts.gstatic.com",
            "img-src 'self' data:",
            "connect-src 'self'",
            "object-src 'none'",
            "base-uri 'self'",
            "form-action 'self'",
            "frame-ancestors 'none'");

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // Spring Security marks every response no-store, including the stylesheet and script,
        // so each page view fetched them again. Static files are fingerprinted by name
        // (spring.web.resources.chain), which makes long caching safe; pages stay uncached.
        RequestMatcher staticFiles = new OrRequestMatcher(
                PathPatternRequestMatcher.withDefaults().matcher("/css/**"),
                PathPatternRequestMatcher.withDefaults().matcher("/js/**"),
                PathPatternRequestMatcher.withDefaults().matcher("/images/**"),
                PathPatternRequestMatcher.withDefaults().matcher("/favicon.ico"));

        http.headers(headers -> headers
                .cacheControl(HeadersConfigurer.CacheControlConfig::disable)
                .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(
                        new NegatedRequestMatcher(staticFiles), new CacheControlHeadersWriter()))
                .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .addHeaderWriter(new StaticHeadersWriter("Permissions-Policy",
                        "camera=(), microphone=(), geolocation=(), payment=()")));

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
                        requestMatchers("/admin/**").hasRole("ADMIN")
                        // The dissertation report: head of the dissertation cell and head of department.
                        .requestMatchers("/reports/**").hasAnyRole("COORDINATOR", "ADMIN").anyRequest().
                        authenticated()).formLogin(form -> form.loginPage("/login").
                defaultSuccessUrl("/dashboard",true)
                .failureHandler((request, response, exception) ->
                        response.sendRedirect(request.getContextPath()
                                + (exception instanceof LockedException ? "/login?locked" : "/login?error")))
                .permitAll())
                .logout(logout -> logout.logoutSuccessUrl("/login?logout").permitAll());
        return http.build();
    }
}
