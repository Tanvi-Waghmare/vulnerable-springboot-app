package com.owasp.lab.model;

import jakarta.persistence.*;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String description;
    private Double price;

    public Product() {}

    public Product(String name, String description, Double price) {
        this.name = name;
        this.description = description;
        this.price = price;
    }

    public Long getId() { return id; }

    /**
     * REMEDIATION (VULN-2026-004 / A04:2021 / A08:2021): package-private
     * setter so Jackson cannot mass-assign the id via {@code @RequestBody}.
     * JPA still has field access to the {@code @Id} field directly.
     */
    void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }
}
