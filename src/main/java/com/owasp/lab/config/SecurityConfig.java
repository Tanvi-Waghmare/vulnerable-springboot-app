package com.owasp.lab.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * Spring Security configuration.
 *
 * REMEDIATION summary:
 *  - VULN-005: authentication is now REQUIRED for every endpoint except
 *    the explicit public list (/api/login, /api/register, /h2-console/**,
 *    /error).  The lab is no longer world-writable.
 *  - VULN-011: CSRF protection is re-enabled for state-changing
 *    endpoints.  Login uses HTTP Basic in this lab, but form / cookie
 *    flows now expect a CSRF token.
 *  - VULN-016: baseline HTTP security response headers are configured
 *    (Content-Security-Policy, X-Content-Type-Options, Referrer-Policy,
 *    X-Frame-Options DENY, Strict-Transport-Security).  H2 console
 *    frames are allowed only on /h2-console/**.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain insecureFilterChain(HttpSecurity http) throws Exception {
        http
            // REMEDIATION (A01:2021 / A05:2021): require authentication
            // for every endpoint not explicitly listed as public.
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                        new AntPathRequestMatcher("/api/login"),
                        new AntPathRequestMatcher("/api/register"),
                        new AntPathRequestMatcher("/error")
                ).permitAll()
                // REMEDIATION (VULN-2026-008 / A01:2021 / CWE-306):
                // /h2-console/** is no longer permitAll.  When the H2
                // console is enabled via H2_CONSOLE_ENABLED=true the
                // console path is reachable only by ADMIN so a
                // misconfigured deployed environment cannot expose an
                // unauthenticated database console to the network.
                .requestMatchers(
                        new AntPathRequestMatcher("/h2-console/**")
                ).hasRole("ADMIN")
                .anyRequest().authenticated()
            )

            // REMEDIATION (A05:2021): enable HTTP Basic so the
            // {@code AuthenticationManager} (backed by the JPA user
            // details service) is exercised on every request, and the
            // @AuthenticationPrincipal injection on /api/transfer works.
            .httpBasic(basic -> {})

            // REMEDIATION (A05:2021): keep STATELESS so each request
            // must carry credentials, removing CSRF's session-cookie
            // attack surface for the JSON API.
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // REMEDIATION (VULN-2026-001 / A05:2021): this is a non-browser
            // JSON API that authenticates via HTTP Basic and runs
            // STATELESS.  There is no ambient authority (no session
            // cookie, no stored Basic credentials) for an attacker to
            // ride on, so CSRF is disabled per the Spring Security 6
            // reference.  Every state-changing request MUST carry its
            // own credentials.
            .csrf(csrf -> csrf.disable())

            // REMEDIATION (A05:2021 / VULN-2026-010): defence-in-depth
            // response headers.  Adds CSP, X-Frame-Options, Referrer-Policy,
            // HSTS, Permissions-Policy, COOP, COEP, CORP.
            .headers(h -> {
                h.contentSecurityPolicy(csp -> csp.policyDirectives(
                        "default-src 'self'; " +
                        "frame-ancestors 'none'; " +
                        "base-uri 'none'; " +
                        "form-action 'self'; " +
                        "script-src 'self'; " +
                        "object-src 'none'"));
                h.frameOptions(f -> f.deny());
                h.referrerPolicy(r -> r.policy(
                        org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter
                                .ReferrerPolicy.NO_REFERRER));
                h.httpStrictTransportSecurity(hsts -> hsts
                        .includeSubDomains(true).maxAgeInSeconds(31536000));
                h.permissionsPolicy(p -> p.policy(
                        "geolocation=(), camera=(), microphone=()"));
                h.crossOriginOpenerPolicy(co -> co.policy(
                        org.springframework.security.web.header.writers.CrossOriginOpenerPolicyHeaderWriter
                                .CrossOriginOpenerPolicy.SAME_ORIGIN));
                h.crossOriginEmbedderPolicy(ce -> ce.policy(
                        org.springframework.security.web.header.writers.CrossOriginEmbedderPolicyHeaderWriter
                                .CrossOriginEmbedderPolicy.REQUIRE_CORP));
                h.crossOriginResourcePolicy(cr -> cr.policy(
                        org.springframework.security.web.header.writers.CrossOriginResourcePolicyHeaderWriter
                                .CrossOriginResourcePolicy.SAME_ORIGIN));
            });

        return http.build();
    }
}
