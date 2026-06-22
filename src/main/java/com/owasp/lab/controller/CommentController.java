package com.owasp.lab.controller;

import com.owasp.lab.model.Comment;
import com.owasp.lab.service.CommentService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.HtmlUtils;

import java.util.List;

/**
 * Comment endpoints.
 *
 * REMEDIATION (OWASP A03:2021 - Injection / XSS):
 *  - VULN-007: the /greet reflected-XSS sink now HTML-escapes the
 *    "name" query parameter via Spring's HtmlUtils.htmlEscape before
 *    interpolating it into the HTML response.
 *  - VULN-008: stored comment bodies are escaped on the read path
 *    (see CommentViewController).
 *
 * REMEDIATION (VULN-2026-002 / VULN-2026-006 / A01:2021):
 *  - The author is ALWAYS bound to the authenticated principal
 *    server-side; the client-supplied author is ignored.
 *  - The id is always nullified server-side so a client cannot
 *    overwrite an existing comment.
 *  - Caller must be authenticated.
 */
@RestController
@RequestMapping("/api/comment")
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @PostMapping
    public Comment create(@RequestBody Comment c,
                          @AuthenticationPrincipal UserDetails caller) {
        // REMEDIATION (VULN-2026-002 / A01:2021): require authentication
        // and bind author server-side so a caller cannot impersonate.
        if (caller == null) {
            throw new AccessDeniedException("Authentication required");
        }
        c.setAuthor(caller.getUsername());
        // REMEDIATION (VULN-2026-006 / A01:2021): nullify id so the
        // POST can only CREATE a new comment, never overwrite one.
        c.clearId();
        return commentService.save(c);
    }

    @GetMapping
    public List<Comment> all() {
        return commentService.findAll();
    }

    @GetMapping(value = "/greet", produces = MediaType.TEXT_HTML_VALUE)
    public String greet(@RequestParam(value = "name", defaultValue = "World") String name) {
        // REMEDIATION (A03:2021 - XSS): HTML-escape the user-controlled
        // value before concatenating it into the response.
        String safe = HtmlUtils.htmlEscape(name);
        return "<html><body><h1>Hello, " + safe + "!</h1></body></html>";
    }
}
