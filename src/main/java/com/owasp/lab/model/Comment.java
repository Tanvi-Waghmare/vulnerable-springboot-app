package com.owasp.lab.model;

import jakarta.persistence.*;

/**
 * Comment entity used by the XSS demo endpoint.
 */
@Entity
@Table(name = "comments")
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String author;

    // VULNERABILITY (A03:2021 - Injection / XSS):
    // Body is stored raw; the controller will echo it back into HTML
    // WITHOUT escaping. This is the XSS sink.
    @Column(length = 2000)
    private String body;

    public Comment() {}

    public Comment(String author, String body) {
        this.author = author;
        this.body = body;
    }

    public Long getId() { return id; }

    /**
     * REMEDIATION (VULN-2026-004 / A04:2021 / A08:2021): package-private
     * setter so Jackson cannot mass-assign the id via {@code @RequestBody}.
     * JPA still has field access to the {@code @Id} field directly.
     */
    void setId(Long id) { this.id = id; }

    /**
     * REMEDIATION (VULN-2026-006 / A01:2021): the controller can call
     * this to defensively nullify the id on a freshly-bound entity
     * before persistence, so a POST can only create a new row and
     * never overwrite an existing comment.  Public so the controller
     * package can use it; the package-private {@link #setId} still
     * prevents Jackson from binding it.
     */
    public void clearId() { this.id = null; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
}
