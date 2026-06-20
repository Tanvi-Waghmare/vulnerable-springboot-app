package com.owasp.lab.controller;

import com.owasp.lab.model.Product;
import com.owasp.lab.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Product endpoints.
 *
 * REMEDIATION (OWASP A01:2021 - Broken Access Control):
 *  - POST /api/products is now restricted to ADMIN via @PreAuthorize.
 *    Non-admins (including anonymous callers) receive a 403 / AccessDenied.
 *  - The inbound payload is validated via @Valid + jakarta.validation
 *    constraints declared on the Product model.
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
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Product> create(@Valid @RequestBody Product p) {
        return ResponseEntity.ok(productService.save(p));
    }
}
