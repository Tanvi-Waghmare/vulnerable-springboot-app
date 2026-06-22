package com.owasp.lab.controller;

import com.owasp.lab.model.User;
import com.owasp.lab.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
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
 *
 * REMEDIATION (VULN-2026-011 / A09:2021): failed-login attempts
 * are now logged with an HMAC-SHA256 of the username keyed by a
 * server-side secret, so defenders can correlate attacks on the
 * same account across log lines without storing the raw username
 * (PII).
 */
@RestController
@RequestMapping("/api")
public class AuthController {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    /**
     * Server-side HMAC key used to fingerprint usernames in failed-
     * login logs (VULN-2026-011).  Sourced from the same env-var as
     * the JWT signing key so it has the same operational lifecycle
     * (rotate together).  When unset, a process-local random key is
     * generated on startup so the hash is stable for the lifetime of
     * the process but differs between restarts.
     */
    private final byte[] usernameHmacKey;

    public AuthController(UserService userService,
                          PasswordEncoder passwordEncoder,
                          @Value("${app.secret.jwt.signing.key:}") String jwtSigningKey) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        if (jwtSigningKey != null && !jwtSigningKey.isEmpty()) {
            this.usernameHmacKey = jwtSigningKey.getBytes(StandardCharsets.UTF_8);
        } else {
            byte[] random = new byte[32];
            new java.security.SecureRandom().nextBytes(random);
            this.usernameHmacKey = random;
        }
    }

    private String hmacUsername(String username) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(usernameHmacKey, "HmacSHA256"));
            byte[] digest = mac.doFinal(
                    username == null ? new byte[0] : username.getBytes(StandardCharsets.UTF_8));
            // First 8 bytes is plenty for correlation, keeps the log line short.
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (Exception ex) {
            return "hmac-error";
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String username = body.getOrDefault("username", "");
        String password = body.getOrDefault("password", "");

        // User u = userService.loginUnsafe(username, password, passwordEncoder);
        User u = userService.authenticate(username, password, passwordEncoder);
        if (u == null) {
            // REMEDIATION (VULN-2026-011 / A09:2021): emit a structured
            // warning carrying an HMAC of the username so defenders
            // can correlate attacks on the same account across log
            // lines without storing PII.
            org.slf4j.LoggerFactory.getLogger(AuthController.class)
                    .warn("Failed login attempt for username fingerprint {}",
                            hmacUsername(username));
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
    public ResponseEntity<User> register(@RequestBody Map<String, String> body) {
        String username = body.getOrDefault("username", "");
        String password = body.getOrDefault("password", "");
        String email    = body.getOrDefault("email", "");

        // REMEDIATION (A01:2021 / A04:2021): the role is ALWAYS forced
        // to USER server-side.  Even if the caller supplies a role
        // field, it is ignored - ADMIN elevation must go through an
        // authenticated, audited admin-only endpoint.
        User u = new User(username, passwordEncoder.encode(password), email, "USER", 0.0);
        return ResponseEntity.ok(userService.save(u));
    }

    @PostMapping("/transfer")
    public ResponseEntity<?> transfer(@RequestBody Map<String, Object> body,
                                       @AuthenticationPrincipal UserDetails caller) {
        Long fromId = ((Number) body.get("fromId")).longValue();
        Long toId   = ((Number) body.get("toId")).longValue();
        Double amount = ((Number) body.get("amount")).doubleValue();

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

        try {
            // REMEDIATION (VULN-2026-003 / A04:2021 / CWE-362): the
            // service method is @Transactional with PESSIMISTIC_WRITE
            // locks on both user rows, so two concurrent transfers
            // cannot both read the same balance and double-spend.
            userService.transfer(fromId, toId, amount);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
        // re-read the balances for the response
        from = userService.findByIdUnsafe(fromId);
        User to = userService.findByIdUnsafe(toId);
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "fromBalance", from == null ? 0.0 : from.getBalance(),
                "toBalance", to == null ? 0.0 : to.getBalance()
        ));
    }
}
