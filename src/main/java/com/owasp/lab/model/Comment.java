package com.owasp.lab.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Comment entity used by the XSS demo endpoint.
 *
 * REMEDIATION (OWASP A03:2021 - Injection / XSS):
 * Input length is capped via Bean Validation.  Defence-in-depth: stored
 * comments are still HTML-escaped on the read path by
 * {@link com.owasp.lab.controller.CommentViewController}.
 */
@Entity
@Table(name = "comments")
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 100)
    private String author;

    @Column(length = 2000)
    @NotBlank
    @Size(max = 2000)
    private String body;

    public Comment() {}

    public Comment(String author, String body) {
        this.author = author;
        this.body = body;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
}
