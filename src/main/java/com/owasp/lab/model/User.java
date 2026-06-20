package com.owasp.lab.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * User entity.
 *
 * REMEDIATION (OWASP A02:2021 - Cryptographic Failures /
 *              OWASP A07:2021 - Identification and Authentication Failures):
 * The {@code password} column stores a hashed credential produced by the
 * configured {@link org.springframework.security.crypto.password.PasswordEncoder}
 * (BCrypt by default).  Plaintext passwords never reach the database.
 *
 * <p>REMEDIATION (OWASP A01:2021 / A02:2021): the {@code password},
 * {@code email} and {@code balance} getters are annotated
 * {@code @JsonIgnore} so Jackson never serialises them in API
 * responses.  Callers that need to expose users must use a dedicated
 * DTO.  The {@code setRole} setter has been removed so a request
 * body cannot elevate a user to ADMIN via mass assignment
 * (combined with {@code spring.jackson.deserialization.fail-on-unknown-properties=true}
 * the unknown {@code role} field is rejected outright).</p>
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    @NotBlank
    @Size(min = 3, max = 64)
    private String username;

    // The column continues to hold a hashed credential (BCrypt).
    // The getter is annotated @JsonIgnore so the hash is never
    // echoed back in JSON responses, but the field can still be
    // populated server-side (e.g. by DataSeeder / AuthController).
    @Column(nullable = false)
    private String password;

    @Email
    @Size(max = 254)
    private String email;

    // role cannot be final because Hibernate reflects on fields when
    // hydrating entities from the database.  Instead we expose the
    // value via a getter only - there is no public setRole, so a
    // request body cannot elevate a user to ADMIN via mass
    // assignment.
    private String role;        // e.g. "USER", "ADMIN"

    private Double balance;     // for /transfer demo

    public User() {
        this.role = "USER";
    }

    public User(String username, String password, String email, String role, Double balance) {
        this.username = username;
        this.password = password;
        this.email = email;
        this.role = role;
        this.balance = balance;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    @JsonIgnore
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    @JsonIgnore
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getRole() { return role; }

    @JsonIgnore
    public Double getBalance() { return balance; }
    public void setBalance(Double balance) { this.balance = balance; }
}
