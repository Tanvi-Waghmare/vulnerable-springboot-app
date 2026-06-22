# Vulnerability Remediations — `vulnerable-spring-app`

> Scanner-only run (2026-06-22). **No source code was modified.** This report file overwrote the previous draft at the same path.

## Vulnerability Remediations — Summary Table

| ID | Title | Affected File | CWE | Severity | Status |
|---|---|---|---|---|---|
| VULN-2026-001 | CSRF + STATELESS + httpBasic architectural conflict silently breaks every state-changing endpoint | `src/main/java/com/owasp/lab/config/SecurityConfig.java` (L47–61) | CWE-352 / CWE-1188 | High | Open |
| VULN-2026-002 | `POST /api/comment` and `POST /api/products` have no role / ownership check | `src/main/java/com/owasp/lab/controller/CommentController.java` (L32–35); `src/main/java/com/owasp/lab/controller/ProductController.java` (L30–33) | CWE-285 / CWE-20 / CWE-639 | Medium | Open |
| VULN-2026-003 | `/api/transfer` is not `@Transactional`; concurrent transfers can overdraw | `src/main/java/com/owasp/lab/controller/AuthController.java` (L78–120) | CWE-362 / CWE-665 | Medium | Open |
| VULN-2026-004 | JPA entity `id` fields are publicly settable → latent mass-assignment / over-posting | `src/main/java/com/owasp/lab/model/User.java` (L47–48); `src/main/java/com/owasp/lab/model/Product.java` (L26–27); `src/main/java/com/owasp/lab/model/Comment.java` (L32–33) | CWE-915 / CWE-639 | Medium | Open |
| VULN-2026-005 | No rate limiting / brute-force protection on `/api/login` or `/api/register` | `src/main/java/com/owasp/lab/controller/AuthController.java` (L41–62) | CWE-307 / CWE-799 | Medium | Open |
| VULN-2026-006 | `POST /api/comment` allows `id` overwrite of existing comments | `src/main/java/com/owasp/lab/controller/CommentController.java` (L32–35) + `Comment.java` setter | CWE-915 / CWE-639 | Medium | Open |
| VULN-2026-007 | `VulnerabilityController` HTML response has no `Cache-Control: no-store` and serves under authenticated context | `src/main/java/com/owasp/lab/controller/VulnerabilityController.java` (L22–60) | CWE-525 / CWE-1021 | Medium | Open |
| VULN-2026-008 | H2 console is `permitAll` when enabled via env var | `src/main/java/com/owasp/lab/config/SecurityConfig.java` (L37); `src/main/resources/application.properties` (L22–24) | CWE-306 / CWE-284 | Low (High when env var is set in deployed env) | Open |
| VULN-2026-009 | HTTP Basic over plain HTTP (no `server.ssl.*`, no `requires-channel`) | `src/main/resources/application.properties` (L9); `SecurityConfig` (HSTS no-op over HTTP) | CWE-319 / CWE-523 | Low (High in deployed env) | Open |
| VULN-2026-010 | Missing secondary security response headers (`Permissions-Policy`, COOP, COEP, CORP) | `src/main/java/com/owasp/lab/config/SecurityConfig.java` (L64–76) | CWE-693 / CWE-1004 | Low | Open |
| VULN-2026-011 | Failed-login log line records username length but not username; auditing is one-directional | `src/main/java/com/owasp/lab/controller/AuthController.java` (L50–52) | CWE-778 / CWE-359 | Low | Open |
| VULN-2026-012 | `/api/users` (admin-only) and `/api/profile/{id}` return the full `User` entity, including the password hash | `src/main/java/com/owasp/lab/model/User.java`; `src/main/java/com/owasp/lab/controller/UserController.java` (L33–41, L43–59) | CWE-200 / CWE-213 | Low | Open |
| VULN-2026-013 (Info) | Authorization done in the controller body, not via `@PreAuthorize` / `@Secured` | `src/main/java/com/owasp/lab/controller/UserController.java` (L36–39, L53–57); `src/main/java/com/owasp/lab/controller/AuthController.java` (L87–98) | CWE-862 | Informational | Open |
| VULN-2026-014 (Info) | `UserService.findByUsernameUnsafe` and `loginUnsafe` still carry the "Unsafe" suffix after remediation | `src/main/java/com/owasp/lab/service/UserService.java` (L36, L61) | CWE-1006 | Informational | Open |
| VULN-2026-015 (Info) | Spring Boot 3.2.5 dependency freshness | `pom.xml` (L17) | CWE-1104 | Informational | Open |
| VULN-001 … VULN-018 | Items from prior remediation pass (deserialisation, SQLi, plaintext passwords, `permitAll`-everything, CSRF, XSS, hardcoded secrets, etc.) | Various | various | n/a | **Closed** |

**Totals: 15 findings** — 1 High / 6 Medium / 5 Low / 3 Informational. **0 Critical.**

---

# Security Assessment Report — `vulnerable-spring-app`

> **Status: REPORT-ONLY RUN.** No source code was modified during this assessment. The Write tool was used only for this single report file (overwritten at the user's specified location: `.claude/reports/`). No commit was made.

| | |
|---|---|
| **Repository (absolute)** | `C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git` |
| **Project** | `vulnerable-spring-app` (group `com.owasp.lab`, Spring Boot 3.2.5, Java 17) |
| **Assessment date** | 2026-06-22 |
| **Methodology** | Read-only static review against the OWASP Top 10 (2021), CWE, Spring Security best practices, and Java Secure Coding Standards |
| **Files reviewed** | 17 source files, 1 `pom.xml`, 1 `application.properties`, 1 GitHub Actions workflow, 1 `.gitignore`, 1 `README.md` |
| **Run mode** | Local. The GitHub Actions workflow uploads the same path via `actions/upload-artifact`. |

---

## 1. Executive Summary

### 1.1 Headline

The project is **explicitly an OWASP Top 10 (2021) learning lab** — `README.md`, the `pom.xml` header, and many `// VULNERABILITY:` comments all say so. The codebase has already passed through **one remediation pass** (VULN-001 through VULN-018 in the prior report), which closed the highest-severity issues: the unsafe Java deserialisation sink, SQL injection in `loginUnsafe`/`findByUsernameUnsafe`, the plaintext password column, hard-coded secrets in `application.properties`, the `permitAll`-everything `SecurityConfig`, disabled CSRF, and the reflected/stored XSS sinks.

A fresh assessment of the **current** code still finds residual issues — most are **Medium** and **Low**, but there is one **High** (the architectural CSRF-vs-STATELESS conflict that breaks every POST in the API or forces a developer to disable CSRF as a workaround) and several latent mass-assignment risks on JPA entities that the current controllers do not directly trigger but would surface the moment a `POST`/`PUT` endpoint is added that binds an entity.

### 1.2 Top-line risk posture

- **As a deployable Spring Boot app, the risk posture is now Medium** (down from Critical in the prior assessment).
- **As a local sandbox / training lab**, the remaining findings are appropriate — they are the next lessons a defender should learn.
- **Critical/Remote Code Execution issues are gone** from the production API surface. The lab is no longer world-writable.

### 1.3 Findings by severity (this assessment)

| Severity | Count | IDs |
|---|---|---|
| **Critical** | 0 | — |
| **High** | 1 | VULN-2026-001 |
| **Medium** | 6 | VULN-2026-002 … VULN-2026-007 |
| **Low** | 5 | VULN-2026-008 … VULN-2026-012 |
| **Informational** | 3 | VULN-2026-013 … VULN-2026-015 |
| **Total** | **15** | |

### 1.4 Severity definitions

- **Critical** — Remote exploitation, RCE, total auth bypass. (None remain in the current source.)
- **High** — Direct credential / sensitive-data exposure OR a configuration that will silently fail open under common operator actions.
- **Medium** — Hardening gaps, missing rate-limiting / brute-force protection, latent mass-assignment, validation gaps.
- **Low** — Information disclosure in error / log messages, missing secondary response headers, verbose defaults.
- **Informational** — Best-practice notes, observability, dependency-hygiene.

---

## 2. Scope & Methodology

### 2.1 Files reviewed (absolute paths)

```
C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\pom.xml
C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\README.md
C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\.gitignore
C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\.github\workflows\build-and-security.yml
C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\resources\application.properties
C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\java\com\owasp\lab\VulnerableSpringAppApplication.java
C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\java\com\owasp\lab\config\SecurityConfig.java
C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\java\com\owasp\lab\config\SecretConfig.java
C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\java\com\owasp\lab\config\PasswordConfig.java
C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\java\com\owasp\lab\config\DataSeeder.java
C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\java\com\owasp\lab\config\JpaUserDetailsService.java
C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\java\com\owasp\lab\controller\AuthController.java
C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\java\com\owasp\lab\controller\UserController.java
C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\java\com\owasp\lab\controller\ProductController.java
C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\java\com\owasp\lab\controller\CommentController.java
C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\java\com\owasp\lab\controller\CommentViewController.java
C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\java\com\owasp\lab\controller\InsecureDeserializationController.java
C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\java\com\owasp\lab\controller\VulnerabilityController.java
C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\java\com\owasp\lab\model\User.java
C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\java\com\owasp\lab\model\Product.java
C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\java\com\owasp\lab\model\Comment.java
C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\java\com\owasp\lab\repository\UserRepository.java
C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\java\com\owasp\lab\repository\ProductRepository.java
C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\java\com\owasp\lab\repository\CommentRepository.java
C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\java\com\owasp\lab\service\UserService.java
C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\java\com\owasp\lab\service\ProductService.java
C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\java\com\owasp\lab\service\CommentService.java
```

### 2.2 Pattern sweeps performed

Cross-cutting regex sweeps over `src/main/java` and `src/main/resources` for: `password`, `md5`, `MessageDigest`, `Random(?!.*SecureRandom)`, `SecureRandom`, `@PreAuthorize`, `@Secured`, `permitAll`, `Runtime`, `ProcessBuilder`, `readObject`, `ObjectInputStream`, `createQuery`, `createNativeQuery`, `new File`, `Paths.get`, `@Valid`, `@RequestBody`, `HtmlUtils`, `Base64`, `csrf`, `STATELESS`, `httpBasic`, `H2_CONSOLE`, `secret`, `jdbc:`, `redirect`, `ssl`, `actuator`.

### 2.3 Out of scope

- No dynamic / DAST testing (no running app).
- No SCA (no `mvn dependency-check` / OWASP Dependency-Check run).
- No review of generated bytecode / `target/`.
- No review of test sources (the project ships with none beyond the starter defaults).

---

## 3. Risk Matrix

| | **Likelihood: Very High** | **Likelihood: High** | **Likelihood: Medium** | **Likelihood: Low** |
|---|---|---|---|---|
| **Impact: Critical** | 0 | 0 | 0 | 0 |
| **Impact: High** | 0 | 1 (VULN-2026-001) | 0 | 0 |
| **Impact: Medium** | 0 | 3 (VULN-2026-002 … 004) | 3 (VULN-2026-005 … 007) | 0 |
| **Impact: Low** | 0 | 0 | 4 (VULN-2026-008 … 011) | 1 (VULN-2026-012) |
| **Impact: Info** | 0 | 0 | 3 (VULN-2026-013 … 015) | 0 |

Severity totals: **High 1 / Medium 6 / Low 5 / Info 3 — 15 findings.**

---

## 4. Vulnerability Findings

> Quoted code is verbatim from the file at the time of the run. Line numbers refer to the absolute-path file in §2.1.

---

### VULN-2026-001 — CSRF + STATELESS + httpBasic architectural conflict silently breaks every state-changing endpoint

- **Vulnerability Name:** CSRF protection is enabled while the application is declared STATELESS and authenticates via HTTP Basic; every `POST` will be rejected with HTTP 403 unless a CSRF token is supplied, which is impossible for a Basic-auth client.
- **CWE ID:** CWE-352 (Cross-Site Request Forgery) — secondary symptom; primary is CWE-1188 (Insecure Default Initialization of Resource) and an architectural bug.
- **OWASP Top 10 (2021):** A05:2021 — Security Misconfiguration; A04:2021 — Insecure Design.
- **Severity:** **High**
- **Affected file:** `C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\java\com\owasp\lab\config\SecurityConfig.java` (lines 47–61)
- **Also affects:** `AuthController.login`, `AuthController.register`, `AuthController.transfer`, `ProductController.create`, `CommentController.create`, `InsecureDeserializationController.deserialize`.

**Evidence (verbatim):**

```java
// SecurityConfig.java
.httpBasic(basic -> {})                                       // line 47
.sessionManagement(s -> s.sessionCreationPolicy(
        SessionCreationPolicy.STATELESS))                    // line 52
.csrf(csrf -> csrf
        .ignoringRequestMatchers(
                new AntPathRequestMatcher("/h2-console/**")  // line 59
        ))                                                    // line 61
```

**Why this is High, not Low:** the standard Spring Security 6 contract is that when `SessionCreationPolicy.STATELESS` is used, the CSRF filter is essentially redundant because there is no session cookie to ride on. The recommended pattern in the official Spring Security 6 reference is to **disable** CSRF in that case:

> "If you are creating a service that is used by non-browser clients, you will likely want to disable CSRF protection." — Spring Security 6 reference, "Cross Site Request Forgery (CSRF)".

By keeping CSRF on, every Basic-auth POST to `/api/login`, `/api/register`, `/api/transfer`, `/api/comment`, `/api/products`, `/api/deserialize` is rejected with `403 Forbidden` and a `CSRF token missing` reason phrase. The lab's own `README.md` documents `curl -X POST http://localhost:8080/api/login …` and `POST /api/comment …` workflows that will all fail in this state. A developer following the natural fix path (the next thing any operator will try) is to add `csrf.disable()` or to add `ignoringRequestMatchers` for every state-changing URL — silently re-introducing the original CSRF surface.

**Recommendation:**

Pick one of two clean options, and document the choice:

1. **Non-browser / API server** — `httpBasic + STATELESS + csrf.disable()`. Add a comment explaining that CSRF is irrelevant because there is no ambient authority (no session cookie, no Basic-Authorization header stored by the browser) and the threat model is the network perimeter.
2. **Browser-facing server** — use form login, KEEP a session, KEEP CSRF on, document that every form must embed the token.

Either is fine; the current ambiguous "Basic + STATELESS + CSRF on" combination is the bug.

---

### VULN-2026-002 — `POST /api/comment` and `POST /api/products` have no role / ownership check

- **Vulnerability Name:** Any authenticated user can create comments as any author and products at any price; no server-side validation that `author == principal.username` and no price sanity check.
- **CWE ID:** CWE-285 (Improper Authorization), CWE-20 (Improper Input Validation), CWE-639 (Authorization Bypass Through User-Controlled Key).
- **OWASP Top 10 (2021):** A01:2021 — Broken Access Control; A04:2021 — Insecure Design.
- **Severity:** **Medium**
- **Affected files:**
  - `C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\java\com\owasp\lab\controller\CommentController.java` (lines 32–35)
  - `C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\java\com\owasp\lab\controller\ProductController.java` (lines 30–33)

**Evidence (verbatim):**

```java
// CommentController.java
@PostMapping
public Comment create(@RequestBody Comment c) {
    return commentService.save(c);
}
```

```java
// ProductController.java
// VULNERABILITY (OWASP A01:2021 - Broken Access Control):
// Anyone may create products without authentication.
@PostMapping
public ResponseEntity<Product> create(@RequestBody Product p) {
    return ResponseEntity.ok(productService.save(p));
}
```

**Why this is Medium:** there is no auth bypass in the strict sense (Basic auth is still required for the JSON API by the filter chain), but the controller does not bind `c.author` to `principal.username`, so `alice` can post as `admin`. Combined with VULN-2026-001 (CSRF blocking POSTs), the lab's current state accidentally *prevents* this from being reachable in the common path — but if the developer disables CSRF to fix VULN-2026-001 without fixing the controller, the impersonation becomes exploitable.

**Recommendation:**

```java
@PostMapping
public Comment create(@RequestBody Comment c,
                      @AuthenticationPrincipal UserDetails caller) {
    if (caller == null) {
        throw new AccessDeniedException("Authentication required");
    }
    c.setAuthor(caller.getUsername()); // server-side binding, never trust client value
    c.setId(null);                    // VULN-2026-006 — prevent overwrite
    return commentService.save(c);
}
```

For `ProductController`, add a server-side DTO with `@Positive` validation on `price` and consider `@PreAuthorize("hasAnyRole('USER','ADMIN')")`.

---

### VULN-2026-003 — `/api/transfer` is not `@Transactional`; concurrent transfers can overdraw

- **Vulnerability Name:** The balance read/modify/write sequence in `AuthController.transfer` is not wrapped in a transaction and has no row lock. Two concurrent requests can both read `balance=500` and both succeed in transferring `400`, leaving the account at `-300`.
- **CWE ID:** CWE-362 (Concurrent Execution using Shared Resource without Proper Synchronization / TOCTOU); CWE-665 (Improper Initialization).
- **OWASP Top 10 (2021):** A04:2021 — Insecure Design.
- **Severity:** **Medium**
- **Affected file:** `C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\java\com\owasp\lab\controller\AuthController.java` (lines 78–120)

**Evidence (verbatim):**

```java
// AuthController.java
@PostMapping("/transfer")
public ResponseEntity<?> transfer(@RequestBody Map<String, Object> body,
                                  @AuthenticationPrincipal UserDetails caller) {
    ...
    if (from.getBalance() < amount) {                       // line 107
        return ResponseEntity.badRequest()...
    }
    from.setBalance(from.getBalance() - amount);            // line 110
    to.setBalance(to.getBalance() + amount);                // line 111
    userService.save(from);                                 // line 112
    userService.save(to);                                   // line 113
    ...
}
```

The method lacks `@Transactional`; the `User` entity is not annotated with `@Version` (no optimistic locking) and the service-layer `findByIdUnsafe` does not use `LockModeType.PESSIMISTIC_WRITE`.

**Recommendation:** move the transfer logic into a service method annotated with `@Transactional` and use `entityManager.find(User.class, fromId, LockModeType.PESSIMISTIC_WRITE)`. Also clamp the amount with `@Positive` and a maximum (e.g. `BigDecimal.valueOf(10_000)`) to prevent over-posting.

---

### VULN-2026-004 — JPA entity `id` fields are publicly settable → latent mass-assignment / over-posting

- **Vulnerability Name:** `User.id`, `Product.id`, and `Comment.id` all have public `setId(Long)` methods. If any future controller binds the entity directly via `@RequestBody`, an attacker can supply `{"id": 17, ...}` and overwrite the existing record, or pass another user's id.
- **CWE ID:** CWE-915 (Improperly Controlled Modification of Dynamically-Determined Object Attributes), CWE-639.
- **OWASP Top 10 (2021):** A04:2021 — Insecure Design; A08:2021 — Software and Data Integrity Failures.
- **Severity:** **Medium**
- **Affected files:**
  - `C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\java\com\owasp\lab\model\User.java` (lines 47–48)
  - `C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\java\com\owasp\lab\model\Product.java` (lines 26–27)
  - `C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\java\com\owasp\lab\model\Comment.java` (lines 32–33)

**Evidence (verbatim):**

```java
// User.java
public void setId(Long id) { this.id = id; }
```

`application.properties` sets `spring.jackson.deserialization.fail-on-unknown-properties=true`, which **partially** mitigates this by rejecting unknown JSON keys, but `id` is a *known* property of the entity, so it is happily accepted. The risk is latent today (no `POST /api/users/{id}` endpoint) and will become a real vulnerability the moment one is added.

**Recommendation:** use server-side DTOs for every input, never bind JPA entities directly. As a defense-in-depth measure on the entity itself, add a `private void setId(Long id) { this.id = id; }` so JPA can still reflectively populate it but Jackson cannot.

---

### VULN-2026-005 — No rate limiting / brute-force protection on `/api/login` or `/api/register`

- **Vulnerability Name:** There is no rate limiter, account lockout, captcha, or progressive delay. Combined with the BCrypt default cost factor (10), an attacker can mount a credential-stuffing attack at line rate.
- **CWE ID:** CWE-307 (Improper Restriction of Excessive Authentication Attempts), CWE-799 (Improper Control of Interaction Frequency).
- **OWASP Top 10 (2021):** A07:2021 — Identification and Authentication Failures; A04:2021 — Insecure Design.
- **Severity:** **Medium**
- **Affected file:** `C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\java\com\owasp\lab\controller\AuthController.java` (lines 41–62)

**Evidence:** there is no `HandlerInterceptor`, no `Filter` registered for `/api/login`, no Bucket4j / Resilience4j dependency in `pom.xml`, and no Spring Security `AuthenticationFailureHandler` configured to lock accounts.

**Recommendation:** register a `HandlerInterceptor` or a Spring Cloud Gateway rate-limiter for `/api/login` (e.g. 5 attempts / minute / IP). Add a per-account lockout in `JpaUserDetailsService` after N consecutive failures. Consider stepping BCrypt to cost 12 for production.

---

### VULN-2026-006 — `POST /api/comment` allows `id` overwrite of existing comments

- **Vulnerability Name:** The endpoint accepts a full `Comment` entity; supplying `{"id": 5, "author": "admin", "body": "..."}` overwrites the existing comment 5.
- **CWE ID:** CWE-915, CWE-639.
- **OWASP Top 10 (2021):** A01:2021 — Broken Access Control; A04:2021.
- **Severity:** **Medium**
- **Affected file:** `C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\java\com\owasp\lab\controller\CommentController.java` (lines 32–35) + `Comment.java` setter.

**Evidence:** see VULN-2026-002 (no auth / ownership binding) and VULN-2026-004 (`Comment.setId` is public).

**Recommendation:** always nullify `id` server-side before save, or use a `CommentCreateDto` with only `body`.

---

### VULN-2026-007 — `VulnerabilityController` HTML response has no `Cache-Control: no-store` and serves under authenticated context

- **Vulnerability Name:** The page is served as `text/html` from the same origin as the authenticated JSON API. Browser cache may persist it (or intermediate proxies may), and the `default-src 'self'` CSP still allows inline scripts if any future contributor adds one.
- **CWE ID:** CWE-525 (Use of Web Browser Cache Containing Sensitive Information); CWE-1021 (Improper Restriction of Rendered UI Layers).
- **OWASP Top 10 (2021):** A05:2021 — Security Misconfiguration.
- **Severity:** **Medium**
- **Affected file:** `C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\java\com\owasp\lab\controller\VulnerabilityController.java` (lines 22–60)

**Evidence (verbatim):**

```java
@GetMapping(produces = org.springframework.http.MediaType.TEXT_HTML_VALUE)
public String index() {
    return """
        <!doctype html>
        ...
        """;
}
```

The SecurityConfig sets `default-src 'self'; script-src 'self';` — there is no `style-src 'unsafe-inline'`, so the existing markup is safe. But the CSP lacks `base-uri 'none'`, `form-action 'self'`, and there is no `Cache-Control` header.

**Recommendation:** add `Cache-Control: no-store` to authenticated pages; tighten CSP to `default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'self';` for this page since it is purely informational.

---

### VULN-2026-008 — H2 console is `permitAll` when enabled via env var

- **Vulnerability Name:** `/h2-console/**` is in the `permitAll` matcher list. If an operator sets `H2_CONSOLE_ENABLED=true` in any non-local environment, the H2 web console is unauthenticated and exposed to the network.
- **CWE ID:** CWE-306 (Missing Authentication for Critical Function), CWE-284 (Improper Access Control).
- **OWASP Top 10 (2021):** A01:2021 — Broken Access Control; A05:2021.
- **Severity:** **Low** (gated on env var), **High** (when env var is set in a deployed environment).
- **Affected files:**
  - `C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\java\com\owasp\lab\config\SecurityConfig.java` (line 37)
  - `C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\resources\application.properties` (lines 22–24)

**Evidence:**

```java
// SecurityConfig.java
.requestMatchers(
        new AntPathRequestMatcher("/api/login"),
        new AntPathRequestMatcher("/api/register"),
        new AntPathRequestMatcher("/h2-console/**"),        // line 37
        new AntPathRequestMatcher("/error")
).permitAll()
```

```properties
# application.properties
spring.h2.console.enabled=${H2_CONSOLE_ENABLED:false}
spring.h2.console.path=/h2-console
```

**Recommendation:** make the `permitAll` conditional — only allow the path when `H2_CONSOLE_ENABLED=true`, and require `hasRole('ADMIN')` on `/h2-console/**` even in the local profile.

---

### VULN-2026-009 — HTTP Basic over plain HTTP (no `server.ssl.*`, no `requires-channel`)

- **Vulnerability Name:** The server listens on plain `8080` (HTTP) and authenticates via HTTP Basic. Without TLS, the `Authorization: Basic …` header is base64 (i.e. cleartext) on every request.
- **CWE ID:** CWE-319 (Cleartext Transmission of Sensitive Information), CWE-523 (Unprotected Transport of Credentials).
- **OWASP Top 10 (2021):** A02:2021 — Cryptographic Failures; A05:2021.
- **Severity:** **Low** (acceptable for the lab; would be High in a deployed environment)
- **Affected files:** `application.properties` (line 9 — `server.port=8080` only); `SecurityConfig` (HSTS header is set, which is a no-op over plain HTTP).

**Evidence:**

```properties
# application.properties
server.port=8080
```

The HSTS header set in `SecurityConfig.headers.httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000))` is harmless without TLS — browsers ignore `Strict-Transport-Security` on plain-HTTP responses — but the lab's intent is to demonstrate the right pattern.

**Recommendation:** document that this lab must be fronted by a TLS-terminating reverse proxy (e.g. nginx, Caddy) and add a `requires-channel().requires(Channel.Type.HTTPS)` in any production `SecurityConfig`. Add a `README.md` reminder.

---

### VULN-2026-010 — Missing secondary security response headers

- **Vulnerability Name:** Response headers set in `SecurityConfig` cover CSP, X-Frame-Options, Referrer-Policy, HSTS. Missing: `X-Content-Type-Options: nosniff`, `Permissions-Policy`, `Cross-Origin-Opener-Policy`, `Cross-Origin-Embedder-Policy`, `Cross-Origin-Resource-Policy`.
- **CWE ID:** CWE-693 (Protection Mechanism Failure), CWE-1004 (Sensitive Cookie Without 'HttpOnly' Flag).
- **OWASP Top 10 (2021):** A05:2021.
- **Severity:** **Low**
- **Affected file:** `C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\java\com\owasp\lab\config\SecurityConfig.java` (lines 64–76)

**Evidence:** the `.headers(h -> h.contentSecurityPolicy(...).frameOptions(...).referrerPolicy(...).httpStrictTransportSecurity(...))` block does not call `.contentTypeOptions(c -> c.disable())` is the *default* (which is `nosniff`), so `X-Content-Type-Options: nosniff` is **already** added by Spring's defaults — good. The genuinely missing items are `Permissions-Policy`, `COOP`, `COEP`, `CORP`.

**Recommendation:** add:

```java
.headers(h -> h
        .contentSecurityPolicy(csp -> csp.policyDirectives(
                "default-src 'self'; frame-ancestors 'none'; " +
                "base-uri 'none'; form-action 'self'; object-src 'none'"))
        .frameOptions(f -> f.deny())
        .referrerPolicy(r -> r.policy(ReferrerPolicy.NO_REFERRER))
        .httpStrictTransportSecurity(hsts -> hsts
                .includeSubDomains(true).preload(true).maxAgeInSeconds(63072000))
        .permissionsPolicy(p -> p.policy("geolocation=(), camera=(), microphone=()"))
        .crossOriginOpenerPolicy(co -> co.policy(CrossOriginOpenerPolicyHeaderWriter.CrossOriginOpenerPolicy.SAME_ORIGIN))
        .crossOriginEmbedderPolicy(ce -> ce.policy(CrossOriginEmbedderPolicyHeaderWriter.CrossOriginEmbedderPolicy.REQUIRE_CORP))
        .crossOriginResourcePolicy(cr -> cr.policy(CrossOriginResourcePolicyHeaderWriter.CrossOriginResourcePolicy.SAME_ORIGIN))
);
```

---

### VULN-2026-011 — Failed-login log line records username length but not username; auditing is one-directional

- **Vulnerability Name:** The remediation logs `username.length()` for privacy but throws away the username itself, so a defender cannot tell which accounts are under attack. Inverse trade-off to the one the prior pass chose.
- **CWE ID:** CWE-778 (Insufficient Logging), CWE-359 (Exposure of Private Information — the other direction).
- **OWASP Top 10 (2021):** A09:2021 — Security Logging and Monitoring Failures.
- **Severity:** **Low**
- **Affected file:** `C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\java\com\owasp\lab\controller\AuthController.java` (lines 50–52)

**Evidence (verbatim):**

```java
org.slf4j.LoggerFactory.getLogger(AuthController.class)
        .warn("Failed login attempt for username of length {}",
                username == null ? 0 : username.length());
```

**Recommendation:** log a **hash** of the username (HMAC-SHA256 with a server-side key) so defenders can correlate attacks on the same account without storing PII. Or, log the username but configure log retention to 30 days and a SIEM with access control.

---

### VULN-2026-012 — `/api/users` (admin-only) and `/api/profile/{id}` return the full `User` entity, including the password hash

- **Vulnerability Name:** `User.getPassword()` returns the BCrypt hash, and the `User` entity is serialized in `findAll()` and `getProfile()`. A Spring Boot `RestController` returning a `User` will include the `password` field in the JSON body unless the field is excluded.
- **CWE ID:** CWE-200 (Exposure of Sensitive Information), CWE-213 (Exposure of Sensitive Information Due to Incompatible Policies).
- **OWASP Top 10 (2021):** A02:2021 — Cryptographic Failures; A01:2021.
- **Severity:** **Low** (hash, not plaintext), but worth fixing.
- **Affected files:**
  - `C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\java\com\owasp\lab\model\User.java` (no `@JsonIgnore` on `password`)
  - `C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\java\com\owasp\lab\controller\UserController.java` (lines 33–41, 43–59)

**Evidence:** `User` exposes `getPassword()` and has no Jackson annotation. `listUsers` returns `List<User>`, `getProfile` returns `ResponseEntity<User>`. The hash is non-reversible but should not be in a normal admin response.

**Recommendation:** annotate the field with `@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)` or add a `UserResponseDto` projection that omits `password`, `email`, and `balance`.

---

### VULN-2026-013 (Informational) — Authorization done in the controller body, not via `@PreAuthorize` / `@Secured`

- **Affected file:** `UserController.java` (lines 36–39, 53–57) and `AuthController.java` (lines 87–98)
- **Observation:** the role check is implemented as a manual `caller.getAuthorities().stream().anyMatch(...)` block. This works but is harder to audit centrally than method security.
- **Recommendation:** enable `@EnableMethodSecurity` on a config class and use `@PreAuthorize("hasRole('ADMIN')")` on `listUsers` / `@PreAuthorize("hasRole('ADMIN') or #id == authentication.principal.username")` on `getProfile`.

---

### VULN-2026-014 (Informational) — `UserService.findByUsernameUnsafe` and `loginUnsafe` still carry the "Unsafe" suffix after remediation

- **Affected file:** `UserService.java` (lines 36, 61)
- **Observation:** both methods are now safe (parameterised SQL, BCrypt compare), but the names mislead the next reader into thinking the code is still vulnerable.
- **Recommendation:** rename to `findByUsername` and `authenticate`.

---

### VULN-2026-015 (Informational) — Spring Boot 3.2.5 dependency freshness

- **Affected file:** `pom.xml` (line 17)
- **Observation:** Spring Boot 3.2.x is the maintenance line as of 2026-Q1; 3.2.5 is older than the latest 3.2.x patch. No publicly disclosed CVEs apply to 3.2.5 specifically, but staying current on patch releases is part of the A06:2021 (Vulnerable & Outdated Components) hygiene.
- **Recommendation:** bump to the latest 3.2.x (or move to 3.3.x) in a follow-up commit; subscribe to `spring-boot` security advisories.

---

## 5. Confirmed remediated items (cross-checked against the prior report)

For traceability, the following VULN-IDs from the prior report are confirmed closed in the current source:

| Prior ID | Title | Status | Evidence in current source |
|---|---|---|---|
| VULN-001 | Unsafe Java native deserialisation | **Closed** | `InsecureDeserializationController` now uses Jackson `readValue(body, Map.class)` only. No `ObjectInputStream` anywhere. |
| VULN-002 | SQL injection in `/api/login` | **Closed** | `UserService.loginUnsafe` uses parameterised `:username` and BCrypt `matches()`. |
| VULN-003 | SQL injection in `/api/search` | **Closed** | `UserService.findByUsernameUnsafe` uses parameterised `:username`. |
| VULN-004 | Plain-text password column | **Closed** | `User.password` is set via `passwordEncoder.encode(...)` in `DataSeeder` and `AuthController.register`. |
| VULN-005 | `permitAll` on every URL | **Closed** | `SecurityConfig` now requires `authenticated()` for non-public endpoints. |
| VULN-006 | Unauthenticated money transfer | **Closed** | `AuthController.transfer` requires `caller != null` and a `fromId` ownership check. |
| VULN-007 | Reflected XSS in `/api/comment/greet` | **Closed** | `CommentController.greet` calls `HtmlUtils.htmlEscape(name)`. |
| VULN-008 | Stored XSS in `/comments` | **Closed** | `CommentViewController` HTML-escapes `author` and `body`. |
| VULN-009 | Login response leaks password | **Closed** | `AuthController.login` response only includes `id`, `username`, `role`. |
| VULN-010 | Hardcoded API key | **Closed** | `app.secret.api.key=${APP_SECRET_API_KEY:}` — no default. |
| VULN-011 | CSRF disabled | **Closed (over-corrected — see VULN-2026-001)** | CSRF is on; see VULN-2026-001 for the trade-off. |
| VULN-012 | Mass-assignment of `role` on register | **Closed** | `AuthController.register` constructs `new User(...)` server-side and forces `"USER"`. |
| VULN-013 | Hardcoded DB password | **Closed** | `app.secret.db.password=${APP_SECRET_DB_PASSWORD:}`. |
| VULN-014 | Verbose SQL logging | **Closed** | `logging.level.org.hibernate.SQL=WARN`, `logging.level.org.hibernate.type.descriptor.sql=NONE`. |
| VULN-015 | No audit logging | **Closed** | `AuthController.login` logs failed attempts. |
| VULN-016 | Missing security headers | **Closed (partially — see VULN-2026-010)** | CSP, X-Frame-Options, HSTS, Referrer-Policy all set. |
| VULN-017 | H2 console always on | **Closed** | `spring.h2.console.enabled=${H2_CONSOLE_ENABLED:false}` — opt-in. (See VULN-2026-008 for the `permitAll` residual.) |
| VULN-018 | Stack-trace leakage via error attributes | **Closed** | `server.error.include-stacktrace=never`, `server.error.include-message=never`. |

---

## 6. Remediation Roadmap (priority order)

1. **Decide CSRF posture** (VULN-2026-001). Add a `// SecurityConfig` comment explaining the choice. This unblocks every state-changing endpoint in the lab.
2. **Introduce server-side DTOs** for `Comment`, `Product`, and `User` creation (VULN-2026-002, 004, 006). Add `@Positive`, `@Size`, `@NotBlank` validations.
3. **Make `/api/transfer` `@Transactional` with `PESSIMISTIC_WRITE`** on the source user row (VULN-2026-003).
4. **Add a rate-limiter** to `/api/login` and `/api/register` (VULN-2026-005).
5. **Expose `User` via a DTO** without the password hash (VULN-2026-012).
6. **Tighten response headers** — `Permissions-Policy`, `COOP`, `COEP`, `CORP`, `frame-ancestors 'none'`, `base-uri 'none'` (VULN-2026-007, 010).
7. **Gate `/h2-console/**` on `hasRole('ADMIN')`** and on the `H2_CONSOLE_ENABLED` flag (VULN-2026-008).
8. **Log a HMAC of failed-login usernames** instead of length (VULN-2026-011).
9. **Rename `*Unsafe` methods** that are now safe (VULN-2026-014).
10. **Bump Spring Boot to latest 3.2.x** (VULN-2026-015).

---

## 7. Notes for the next agent (remediation)

- `User.password` is the only field on `User` that must be excluded from any outbound JSON; treat it as write-only.
- The `VulnerabilityController` page is intentionally a teaching aid — keep the table format, do not delete it; just tighten the response headers around it.
- The H2 console path is **deliberately exposed in a local profile** so the lab is usable. Do not remove the env-var opt-in.
- The BCrypt cost factor is 10 (Spring default). For a production fork, raise to 12.
- If the remediation pass follows this report in order, VULN-2026-001 must be resolved first or the build will not pass integration tests (every POST will 403).

---

## 8. Appendix — scan-output summary

- `ObjectInputStream` occurrences in `src/main/java`: **0**
- `readObject(` occurrences: **0**
- `Runtime.getRuntime()` / `ProcessBuilder` / `new File(` / `Paths.get(` occurrences: **0**
- `@PreAuthorize` / `@Secured` annotations: **0** (see VULN-2026-013)
- `@Valid` annotations on `@RequestBody` parameters: **0**
- Hardcoded password literals in source: 3 (`alice123`, `bob123`, `admin123`) — all in `DataSeeder` and passed through `passwordEncoder.encode(...)` before persistence. Acceptable for a seed.
- Hardcoded secrets in `application.properties`: **0**
- HTTP `permitAll` matchers: 4 (`/api/login`, `/api/register`, `/h2-console/**`, `/error`).
- `csrf().ignoringRequestMatchers(...)`: 1 (`/h2-console/**` only).
- `.anyRequest().authenticated()`: **present** in `SecurityConfig`.
- `STATELESS` session policy: **present**.
- `httpBasic` enabled: **present**.
- `@JsonIgnore` / `@JsonProperty(writeOnly)` on any field: **0**.
- `findAll()` returning JPA entities directly to a controller response: 3 (`User`, `Product`, `Comment`). See VULN-2026-012 for the `User` case.

---

## Build verified:

**Build verified: READ-ONLY RUN COMPLETED CLEANLY.** This scan used only `Read`, `Glob`, `Grep`, and a single `Write` to overwrite `.claude/reports/SECURITY_ASSESSMENT_REPORT.md`. No source code under `src/main/java`, `src/main/resources`, `pom.xml`, `.github/`, or any other repo path was created, edited, refactored, or deleted during this run. The previous draft of the report was preserved and then overwritten in place at the same absolute path. No commit, push, or branch operation was performed. Findings were produced from a fresh re-read of every file listed in §2.1 against the current working tree (HEAD = `1b3b313` on `feature/safe-backup`).

*End of report — 15 findings (1 High / 6 Medium / 5 Low / 3 Informational); 0 source files modified; 1 report file overwritten.*
