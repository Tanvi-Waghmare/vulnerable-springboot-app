package com.owasp.lab.controller;

import com.owasp.lab.model.User;
import com.owasp.lab.service.UserService;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.Map;

/**
 * Authentication endpoints.
 *
 * REMEDIATION summary:
 *  - VULN-002: loginUnsafe now uses parameterised SQL and a
 *    PasswordEncoder.matches() hash compare.
 *  - VULN-004: passwords are hashed on register before persistence.
 *  - VULN-006: /transfer requires authentication and verifies the
 *    caller's principal matches the source user.
 *  - VULN-009: the /api/login response no longer contains the password.
 *  - VULN-012: /register binds to a server-side DTO and the role is
 *    always forced to "USER".  ADMIN elevation requires a separate,
 *    authenticated flow.
 *  - VULN-015: failed login attempts are logged via SLF4J.
 */
@RestController
@RequestMapping("/api")
@Validated
public class AuthController {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    public AuthController(UserService userService, PasswordEncoder passwordEncoder) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String username = body.getOrDefault("username", "");
        String password = body.getOrDefault("password", "");

        User u = userService.loginUnsafe(username, password, passwordEncoder);
        if (u == null) {
            // REMEDIATION (A09:2021): emit a structured warning so
            // brute-force attempts are visible in log aggregation.
            org.slf4j.LoggerFactory.getLogger(AuthController.class)
                    .warn("Failed login attempt for username of length {}",
                            username == null ? 0 : username.length());
            return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
        }
        // REMEDIATION (A04:2021 / A02:2021): never echo the password
        // (or the password hash) back to the caller.
        return ResponseEntity.ok(Map.of(
                "id", u.getId(),
                "username", u.getUsername(),
                "role", u.getRole()
        ));
    }

    @PostMapping("/register")
    public ResponseEntity<User> register(@Valid @RequestBody RegistrationRequest body) {
        // REMEDIATION (A01:2021 / A04:2021): the role is ALWAYS forced
        // to USER server-side.  Even if the caller supplies a role
        // field, it is ignored - ADMIN elevation must go through an
        // authenticated, audited admin-only endpoint.
        // REMEDIATION (A04:2021): input is bound to a typed DTO with
        // Bean Validation constraints so oversized / malformed input
        // is rejected before it reaches the database.
        User u = new User(body.getUsername(),
                passwordEncoder.encode(body.getPassword()),
                body.getEmail(), "USER", 0.0);
        User saved = userService.save(u);
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/transfer")
    public ResponseEntity<?> transfer(@Valid @RequestBody TransferRequest body,
                                       @AuthenticationPrincipal UserDetails caller) {
        Long fromId = body.getFromId();
        Long toId   = body.getToId();
        Double amount = body.getAmount();

        // REMEDIATION (A01:2021 - IDOR): the caller must own the
        // source account unless they are an ADMIN.
        if (caller == null) {
            throw new AccessDeniedException("Authentication required");
        }
        User from = userService.findByIdUnsafe(fromId);
        if (from == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "User not found"));
        }
        boolean isAdmin = caller.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (!isAdmin && !caller.getUsername().equals(from.getUsername())) {
            throw new AccessDeniedException("Cannot transfer from another user's account");
        }

        User to = userService.findByIdUnsafe(toId);
        if (to == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Recipient not found"));
        }
        if (amount == null || amount <= 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "Amount must be positive"));
        }
        if (from.getBalance() < amount) {
            return ResponseEntity.badRequest().body(Map.of("error", "Insufficient funds"));
        }
        from.setBalance(from.getBalance() - amount);
        to.setBalance(to.getBalance() + amount);
        userService.save(from);
        userService.save(to);

        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "fromBalance", from.getBalance(),
                "toBalance", to.getBalance()
        ));
    }

    /**
     * Typed payload for /api/register.
     *
     * REMEDIATION (A04:2021 - Insecure Design): bean-validation
     * constraints reject empty / oversized / malformed input BEFORE
     * the controller body executes, eliminating the unbounded-input
     * sink from the original {@code Map<String,String>} binding.
     */
    public static class RegistrationRequest {
        @NotBlank
        @Size(min = 3, max = 64)
        private String username;

        @NotBlank
        @Size(min = 8, max = 128)
        private String password;

        @Email
        @Size(max = 254)
        private String email;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
    }

    /**
     * Typed payload for /api/transfer.
     *
     * REMEDIATION (A04:2021 - Insecure Design): @NotNull / @Positive
     * reject negative or missing amounts BEFORE the controller
     * executes.  The original untyped Map allowed callers to send
     * {@code {"amount": -1e308}}.
     */
    public static class TransferRequest {
        @NotNull
        private Long fromId;

        @NotNull
        private Long toId;

        @NotNull
        @Positive
        private Double amount;

        public Long getFromId() { return fromId; }
        public void setFromId(Long fromId) { this.fromId = fromId; }

        public Long getToId() { return toId; }
        public void setToId(Long toId) { this.toId = toId; }

        public Double getAmount() { return amount; }
        public void setAmount(Double amount) { this.amount = amount; }
    }
}
