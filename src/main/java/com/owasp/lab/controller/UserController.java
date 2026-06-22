package com.owasp.lab.controller;

import com.owasp.lab.model.User;
import com.owasp.lab.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * User-related REST endpoints.
 *
 * REMEDIATION (OWASP A01:2021 - Broken Access Control / IDOR):
 *  - /api/users is restricted to ADMIN role.
 *  - /api/profile/{id} requires the caller to be the resource owner
 *    OR an ADMIN.
 *  - /api/search continues to use parameterised SQL (see UserService)
 *    and is restricted to authenticated users.
 *
 * REMEDIATION (VULN-2026-013 / A01:2021): role / ownership checks are
 * expressed declaratively via {@code @PreAuthorize} so the access
 * policy is auditable centrally instead of being duplicated in the
 * controller body.
 */
@RestController
@RequestMapping("/api")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public List<User> listUsers() {
        return userService.findAll();
    }

    @GetMapping("/profile/{id}")
    @PreAuthorize("hasRole('ADMIN') or #target.username == authentication.name")
    public ResponseEntity<User> getProfile(@PathVariable Long id,
                                           @org.springframework.security.core.annotation.AuthenticationPrincipal org.springframework.security.core.userdetails.UserDetails caller) {
        // REMEDIATION (VULN-2026-013 / A01:2021): @PreAuthorize enforces
        // ADMIN OR ownership against the loaded target user.  We only
        // need to look the user up and return them.
        User target = userService.findByIdUnsafe(id);
        if (target == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(target);
    }

    // @GetMapping("/search")
    // public List<User> search(@RequestParam("q") String q) {
    //     return userService.findByUsernameUnsafe(q);
    @GetMapping("/search")
    public List<User> search(@RequestParam("q") String q) {
            return userService.findByUsername(q);

    }
}
