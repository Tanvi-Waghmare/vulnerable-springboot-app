package com.owasp.lab.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;

/**
 * User entity.
 *
 * REMEDIATION (OWASP A02:2021 - Cryptographic Failures /
 *              OWASP A07:2021 - Identification and Authentication Failures):
 * The {@code password} column stores a hashed credential produced by the
 * configured {@link org.springframework.security.crypto.password.PasswordEncoder}
 * (BCrypt by default).  Plaintext passwords never reach the database.
 *
 * <p>Never return this field via any public API response.  The
 * {@code AuthController.login} response explicitly omits it.
 * The {@code @JsonProperty(WRITE_ONLY)} annotation ensures it is also
 * never serialised by Jackson in any other controller response
 * (VULN-2026-012 / A02:2021 / A01:2021).</p>
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    // VULNERABILITY: storing plaintext password (A02 / A07)
    @Column(nullable = false)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    private String email;
    private String role;        // e.g. "USER", "ADMIN"
    private Double balance;     // for /transfer demo

    public User() {}

    public User(String username, String password, String email, String role, Double balance) {
        this.username = username;
        this.password = password;
        this.email = email;
        this.role = role;
        this.balance = balance;
    }

    public Long getId() { return id; }

    /**
     * REMEDIATION (VULN-2026-004 / A04:2021 / A08:2021): keep the
     * setter package-private (Jackson cannot bind a field via a
     * non-public setter) so an attacker cannot mass-assign {@code id}
     * via {@code @RequestBody}.  JPA still has field access to the
     * {@code @Id} field directly and does not need a public setter.
     */
    void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public Double getBalance() { return balance; }
    public void setBalance(Double balance) { this.balance = balance; }
}
