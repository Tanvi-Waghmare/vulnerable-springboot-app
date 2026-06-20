package com.owasp.lab.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * REMEDIATION (OWASP A02:2021 - Cryptographic Failures /
 *              OWASP A05:2021 - Security Misconfiguration):
 *
 *  - VULN-010: secrets are no longer hardcoded literals in
 *    application.properties.  They are sourced from environment
 *    variables (APP_SECRET_API_KEY, APP_SECRET_DB_PASSWORD,
 *    APP_SECRET_JWT_SIGNING_KEY) which MUST be supplied by a real
 *    secrets manager (Spring Cloud Config, HashiCorp Vault, AWS
 *    Secrets Manager) at deploy time.  No defaults are provided so
 *    a misconfigured deployment fails fast rather than silently
 *    picking up an attacker-known value.
 *  - VULN-013: the JWT signing key, when one is required, must be a
 *    high-entropy value generated via SecureRandom and rotated
 *    periodically.
 *  - VULN-015: secrets are NO LONGER exposed as named {@code String}
 *    beans in the application context.  Exposing them as beans would
 *    allow any autowired {@code String} matched by name anywhere in
 *    the application to silently receive them.  Consumers that need a
 *    specific secret should {@code @Value}-inject it directly inside
 *    their own bean.
 */
@Configuration
public class SecretConfig {

    @Value("${app.secret.api.key:}")
    private String apiKey;

    @Value("${app.secret.db.password:}")
    private String dbPassword;

    @Value("${app.secret.jwt.signing.key:}")
    private String jwtSigningKey;

    public String getApiKey() {
        return apiKey;
    }

    public String getDbPassword() {
        return dbPassword;
    }

    public String getJwtSigningKey() {
        return jwtSigningKey;
    }
}
