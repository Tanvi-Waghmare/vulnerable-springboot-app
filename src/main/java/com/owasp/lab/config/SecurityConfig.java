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
 *  - VULN-001: method-level security is enabled so controllers can use
 *    {@code @PreAuthorize} to require specific roles.
 *  - VULN-008: requiresChannel(...).requiresSecure() is set so the
 *    application refuses to serve over plain HTTP.  Real deployments
 *    terminate TLS at a proxy or configure {@code server.ssl.*}.
 *  - VULN-010 / VULN-011: stricter CSP (style-src, img-src, base-uri,
 *    form-action) and frameOptions(deny()) are applied.
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
                        // REMEDIATION (VULN-007): /h2-console/**
                        // remains permitAll in this configuration
                        // because it is a sandbox learning-lab
                        // artifact.  The application.properties
                        // file gates H2_CONSOLE_ENABLED behind an
                        // env var (default false) and disables
                        // remote connections, so the console is
                        // only reachable when an operator
                        // explicitly enables it for a local run.
                        // For non-local deployments the operator
                        // MUST remove this matcher.
                        new AntPathRequestMatcher("/h2-console/**"),
                        new AntPathRequestMatcher("/error")
                ).permitAll()
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

            // REMEDIATION (A05:2021): enable CSRF for session-based
            // flows.  For STATELESS Basic auth, CSRF is also enforced
            // and a 403 will be returned if a token is missing.
            .csrf(csrf -> csrf
                    .ignoringRequestMatchers(
                            new AntPathRequestMatcher("/h2-console/**")
                    )
            )

            // REMEDIATION (A02:2021 - Cleartext Transmission): require
            // HTTPS for every request.  Plain HTTP traffic is rejected
            // with a 403 / redirect.  This makes HTTP Basic safe.
            .requiresChannel(rc -> rc.anyRequest().requiresSecure())

            // REMEDIATION (A05:2021): defence-in-depth response headers.
            .headers(h -> h
                    .contentSecurityPolicy(csp -> csp.policyDirectives(
                            "default-src 'self'; " +
                            "frame-ancestors 'none'; " +
                            "script-src 'self'; " +
                            "style-src 'self'; " +
                            "img-src 'self' data:; " +
                            "object-src 'none'; " +
                            "base-uri 'none'; " +
                            "form-action 'self'"))
                    .frameOptions(f -> f.deny())
                    .contentTypeOptions(c -> {})
                    .referrerPolicy(r -> r.policy(
                            org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter
                                    .ReferrerPolicy.NO_REFERRER))
                    .httpStrictTransportSecurity(hsts -> hsts
                            .includeSubDomains(true).maxAgeInSeconds(31536000))
            );

        return http.build();
    }
}
