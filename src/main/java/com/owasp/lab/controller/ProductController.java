package com.owasp.lab.controller;

import com.owasp.lab.model.Product;
import com.owasp.lab.service.ProductService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Simple product endpoints used as additional demo targets.
 *
 * REMEDIATION (VULN-2026-002 / A01:2021 / A04:2021):
 *  - POST requires authentication and USER or ADMIN role.
 *  - Input is bound via a server-side DTO that nullifies the id
 *    and validates name/description/price fields.
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public List<Product> list() {
        return productService.findAll();
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<Product> create(@RequestBody ProductCreateDto dto) {
        // REMEDIATION (VULN-2026-002 / VULN-2026-004 / A01:2021):
        // server-side validation - reject blank name/description
        // and non-positive price, then build the entity so the
        // client cannot set the id or any other field.
        if (dto == null) {
            return ResponseEntity.badRequest().build();
        }
        if (dto.getName() == null || dto.getName().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        if (dto.getDescription() == null || dto.getDescription().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        if (dto.getPrice() == null || dto.getPrice() <= 0) {
            return ResponseEntity.badRequest().build();
        }
        Product p = new Product(dto.getName(), dto.getDescription(), dto.getPrice());
        // REMEDIATION (VULN-2026-004 / VULN-2026-006): id is null,
        // so this can only CREATE a new product, never overwrite.
        return ResponseEntity.ok(productService.save(p));
    }

    /**
     * Server-side DTO for product creation.  Limits mass-assignment
     * surface; the controller validates each field before constructing
     * the entity.
     */
    public static class ProductCreateDto {
        private String name;
        private String description;
        private Double price;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public Double getPrice() { return price; }
        public void setPrice(Double price) { this.price = price; }
    }
}
