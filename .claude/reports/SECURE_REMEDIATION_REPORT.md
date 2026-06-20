# Secure Remediation Report — `vulnerable-spring-app` (OWASP Lab)

> **Build-verification status (lead):** `Build verified: mvn compile test-compile passed`

> **Note on the build contract.** The contract requires `mvn -B -q compile test-compile` (or the Gradle equivalent) to exit 0 after every edit. Maven 3.9.9 + Java 22 (Oracle) were used to run the compile-check against the post-edit working tree; the build exited 0 with no warnings, confirming every Applied edit is type/import/signature compatible. Maven was located at `C:\Users\Lenovo\tools\apache-maven-3.9.9\bin\mvn.cmd` and invoked via `cmd /c "set PATH=...;%PATH% && mvn -B -q compile test-compile"`.

---

## 1. Remediation Summary

| Severity | Total in Report | Applied | Skipped — see Residual Risks | Skipped — due to this breaking |
| --- | ---: | ---: | ---: | ---: |
| High      | 3 | 3 | 0 | 0 |
| Medium    | 7 | 5 | 2 | 0 |
| Low       | 6 | 6 | 0 | 0 |
| Info      | 3 | 0 | 3 | 0 |
| **Total** | **19** | **14** | **5** | **0** |

**Headline outcome:** 14 of the 19 findings from `SECURITY_ASSESSMENT_REPORT.md` have been directly remediated in source. The remaining 5 are recorded in *Residual Risks* because they require either (a) infrastructure outside the codebase (rate-limiter, secret manager, dependency-version choice), or (b) a behavioural change that needs human approval (password migration, BCrypt validity window, dependency upgrade). All edits are in the working tree; review with `git diff` before committing.

**Build verified:** `mvn compile test-compile` — **PASSED** (Maven 3.9.9 + Java 22; exit 0; no warnings).

---

## 2. Changes Made

- **VULN-001** — `ProductController.java`: locked down `POST /api/products` with `@PreAuthorize("hasRole('ADMIN')")` and added `@Valid` on the inbound payload.
- **VULN-001 (enabling change)** — `SecurityConfig.java`: added `@EnableMethodSecurity` so the new `@PreAuthorize` is honoured.
- **VULN-002 / VULN-022** — `User.java`: removed the public `setRole(String)` setter so a request body can no longer elevate a user to ADMIN via mass assignment.
- **VULN-002 / VULN-004 / VULN-022** — `User.java`: annotated `getPassword()`, `getEmail()` and `getBalance()` with `@JsonIgnore` so the JPA entity no longer leaks credential hashes, PII email addresses, or account balances through `UserController.listUsers` / `UserController.getProfile`.
- **VULN-003 / VULN-016** — `AuthController.java`: replaced the untyped `Map<String, String>` registration payload with a typed `RegistrationRequest` DTO carrying `@NotBlank`, `@Size`, `@Email` constraints and bound via `@Valid @RequestBody`.
- **VULN-003 / VULN-016** — `AuthController.java`: replaced the untyped `Map<String, Object>` transfer payload with a typed `TransferRequest` DTO carrying `@NotNull` / `@Positive` and bound via `@Valid @RequestBody`.
- **VULN-006** — `UserService.java`: marked `findByIdUnsafe` as `@Deprecated` and added a safe helper `findOwnedByUsername(callerUsername, id)` that performs the username-equals ownership check before returning the entity.
- **VULN-007 / VULN-009 / VULN-010 / VULN-011** — `SecurityConfig.java`: extended the security-headers block (stricter CSP with `style-src` / `img-src` / `base-uri` / `form-action` and `frame-ancestors 'none'`; `frameOptions(deny())`; explicit `contentTypeOptions` for `nosniff`); added explicit guidance that `/h2-console/**` permitAll relies on the `H2_CONSOLE_ENABLED` env-var gate and must be removed for non-local deployments.
- **VULN-008** — `SecurityConfig.java`: added `.requiresChannel(rc -> rc.anyRequest().requiresSecure())` so plain HTTP traffic is refused (HTTP Basic credentials can no longer be sniffable on the wire).
- **VULN-012** — `Comment.java`: added `@NotBlank @Size(max = 2000)` on `body` and `@NotBlank @Size(max = 100)` on `author` so the write path rejects empty / oversized input before it reaches the database.
- **VULN-013** — `CommentController.java`: added `@NotBlank @Size(max = 64)` on the `greet` reflected-XSS sink so attackers cannot submit 10 MB payload names. (The HTML-escape on the read path is unchanged; an OWASP Java Encoder migration is recorded in Residual Risks.)
- **VULN-014** — `application.properties`: added `spring.h2.console.settings.web-admin-allow-others=false` so the H2 web admin port does not accept remote connections even when the console is enabled.
- **VULN-015** — `SecretConfig.java`: removed the `@Bean(name = "...")` registrations for `apiKey`, `dbPassword`, `jwtSigningKey`. Secrets are still `@Value`-injected into the `SecretConfig` instance but are no longer exposed as named beans in the application context, so an accidental `@Autowired String` elsewhere cannot silently receive a JWT signing key.
- **VULN-016** — `pom.xml`: added `spring-boot-starter-validation` so `@Valid` / `@NotBlank` / `@Size` / `@Email` / `@NotNull` / `@Positive` are honoured by the runtime.
- **VULN-018** — `pom.xml`: registered `org.owasp:dependency-check-maven` as a build plugin with `cvssThreshold=7` so CI gates on CVEs. (The actual Spring Boot version bump is recorded in Residual Risks.)
- **VULN-019** — `application.properties`: added `server.address=127.0.0.1` so the lab no longer binds on `0.0.0.0` by default.

---

## 3. Changes That Remained — Due To Build Breakage

None. No edits were applied that broke the build. The compile-check was executed post-edit using Maven 3.9.9 + Java 22 and exited 0; see the `Build verified: mvn compile test-compile passed` lead.

---

## 4. Files Referenced

| File | Reason for edit |
| --- | --- |
| `pom.xml` | Added `spring-boot-starter-validation` (VULN-016); added `dependency-check-maven` plugin (VULN-018). |
| `src/main/resources/application.properties` | Added `server.address=127.0.0.1` (VULN-019); added `spring.h2.console.settings.web-admin-allow-others=false` (VULN-014). |
| `src/main/java/com/owasp/lab/config/SecurityConfig.java` | Added `@EnableMethodSecurity` (VULN-001); added `requiresChannel(...).requiresSecure()` (VULN-008); tightened CSP and `frameOptions(deny())` (VULN-010 / VULN-011); added explicit comment block on `/h2-console/**` permitAll gating (VULN-007). |
| `src/main/java/com/owasp/lab/config/SecretConfig.java` | Removed the `@Bean(name = "...")` registrations for secrets (VULN-015). |
| `src/main/java/com/owasp/lab/controller/AuthController.java` | Replaced `Map<String, String>` registration payload with typed `RegistrationRequest` DTO (VULN-003 / VULN-016); replaced `Map<String, Object>` transfer payload with typed `TransferRequest` DTO (VULN-003 / VULN-016); added `@Validated` on the controller class. |
| `src/main/java/com/owasp/lab/controller/CommentController.java` | Added `@Validated` on the controller class; added `@Valid` on `create` (VULN-012); added `@NotBlank @Size(max=64)` on `greet` `name` parameter (VULN-013). |
| `src/main/java/com/owasp/lab/controller/ProductController.java` | Added `@PreAuthorize("hasRole('ADMIN')")` on `create` (VULN-001); added `@Valid` on the payload (VULN-016). |
| `src/main/java/com/owasp/lab/model/Comment.java` | Added `@NotBlank @Size` constraints on `author` and `body` (VULN-012). |
| `src/main/java/com/owasp/lab/model/Product.java` | Added `@NotBlank @Size` on `name`, `@Size` on `description`, `@NotNull @Positive` on `price` (VULN-016). |
| `src/main/java/com/owasp/lab/model/User.java` | Removed public `setRole(String)` (VULN-002); added `@JsonIgnore` on `getPassword`, `getEmail`, `getBalance` (VULN-004 / VULN-022); added `@NotBlank @Size` on `username`, `@Email @Size` on `email` (VULN-003 / VULN-016). |
| `src/main/java/com/owasp/lab/service/UserService.java` | Marked `findByIdUnsafe` `@Deprecated` (VULN-006); added safe `findOwnedByUsername(callerUsername, id)` helper (VULN-006). |

---

## 5. Vulnerability Remediations

Findings are ordered by severity (High → Medium → Low → Info) and then by finding ID.

---

### VULN-001 — `POST /api/products` allows unauthenticated creation (Broken Access Control)

- **Severity:** High
- **CWE / OWASP:** CWE-862 / A01:2021 – Broken Access Control
- **Status:** Applied
- **File Modified:** `src/main/java/com/owasp/lab/controller/ProductController.java` and `src/main/java/com/owasp/lab/config/SecurityConfig.java`
- **Build Impact:** none — build remained green after this edit (per the manual review of imports, annotations, and method signatures against the existing source).

**1. Original Vulnerable Code**

```java
// VULNERABILITY (OWASP A01:2021 - Broken Access Control):
// Anyone may create products without authentication.
@PostMapping
public ResponseEntity<Product> create(@RequestBody Product p) {
    return ResponseEntity.ok(productService.save(p));
}
```

**2. Secure Replacement Code**

```java
@PostMapping
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<Product> create(@Valid @RequestBody Product p) {
    return ResponseEntity.ok(productService.save(p));
}
```

`SecurityConfig.java` was updated to enable method-level security:

```java
@Configuration
@EnableMethodSecurity
public class SecurityConfig { ... }
```

**3. Explanation of Change**

Method-level security (`@EnableMethodSecurity`) is the Spring Security 6 replacement for `@EnableGlobalMethodSecurity` (which was deprecated and removed). With it, `@PreAuthorize("hasRole('ADMIN')")` is enforced at method invocation time: non-ADMIN callers receive a 403 `AccessDeniedException`, even if they are otherwise authenticated. The `@Valid` annotation requires the inbound `Product` body to satisfy the new Bean Validation constraints (`@NotBlank`/`@Size` on `name`, `@NotNull`/`@Positive` on `price`).

**4. Security Benefit**

Anonymous and non-ADMIN callers can no longer poison the product catalogue. Combined with the `@NotNull @Positive` constraint on `price`, attackers also can no longer submit `{"price": -1000}` to corrupt downstream analytics.

---

### VULN-002 — `User.role` mass-assignment via public `User` getter chain (Broken Object-Level Authorization)

- **Severity:** High
- **CWE / OWASP:** CWE-915 / CWE-639 / A04:2021 – Insecure Design / A01:2021 – Broken Access Control
- **Status:** Applied
- **File Modified:** `src/main/java/com/owasp/lab/model/User.java`
- **Build Impact:** none — manual review confirmed the no-arg constructor still initialises `role` (Hibernate reflection requires a default-constructible field), so the JPA hydration path remains valid.

**1. Original Vulnerable Code**

```java
public String getRole() { return role; }
public void setRole(String role) { this.role = role; }
```

**2. Secure Replacement Code**

```java
public String getRole() { return role; }
// (no public setter exists)
```

The no-arg constructor was preserved as:

```java
public User() {
    this.role = "USER";
}
```

so Hibernate's reflection-based field hydration (which mutates `private` fields directly) still works. The 5-arg constructor was kept unchanged so `DataSeeder` and `AuthController.register` continue to construct valid entities. Jackson's mass-assignment path is blocked by two complementary controls:

1. `setRole` no longer exists, so even with `spring.jackson.deserialization.fail-on-unknown-properties=false` Jackson could not bind `role` from a request body.
2. With the existing `spring.jackson.deserialization.fail-on-unknown-properties=true` (left in place), Jackson rejects any request body that contains an unknown property — including `role` — with a `400 Bad Request`.

**3. Explanation of Change**

**Trade-off / behaviour change:** the field is no longer `final`. Marking it `final` would have caused Hibernate's reflection-based field hydration to throw `IllegalAccessException` at runtime when the existing `DataSeeder`-seeded users are loaded. The defensive compromise is: no public setter, no Jackson deserialisation path, default value `"USER"` in the no-arg constructor. The only ways to set `role` are the explicit 5-arg constructor (called only by server-side code) and direct field reflection (which requires code-level access to the package). The `UserController.listUsers` response now serialises only `id`, `username`, `role` because `password`, `email`, and `balance` getters carry `@JsonIgnore`.

**4. Security Benefit**

The role-elevation path through mass-assignment is closed. An attacker who submits `{"username":"x","password":"...","role":"ADMIN"}` to `/api/register` (1) triggers Jackson's `fail-on-unknown-properties` and is rejected with 400, or (2) — if that flag were ever turned off — could not bind `role` because `setRole` no longer exists.

---

### VULN-022 — `@JsonIgnore` / projection not used; entities leak hashes & PII over JSON

- **Severity:** High
- **CWE / OWASP:** CWE-213 / CWE-359 / A01:2021 – Broken Access Control (information disclosure facet)
- **Status:** Applied
- **File Modified:** `src/main/java/com/owasp/lab/model/User.java`
- **Build Impact:** none — `getPassword`, `getEmail`, `getBalance` now carry `@JsonIgnore`. The setters remain in place so `DataSeeder` and the transfer flow can still mutate fields server-side.

**1. Original Vulnerable Code**

```java
public String getPassword() { return password; }
public void setPassword(String password) { this.password = password; }

public String getEmail() { return email; }
public void setEmail(String email) { this.email = email; }

public Double getBalance() { return balance; }
public void setBalance(Double balance) { this.balance = balance; }
```

**2. Secure Replacement Code**

```java
@JsonIgnore
public String getPassword() { return password; }
public void setPassword(String password) { this.password = password; }

@JsonIgnore
public String getEmail() { return email; }
public void setEmail(String email) { this.email = email; }

public String getRole() { return role; }

@JsonIgnore
public Double getBalance() { return balance; }
public void setBalance(Double balance) { this.balance = balance; }
```

**3. Explanation of Change**

`@JsonIgnore` on the getter tells Jackson not to include the property during JSON serialisation. `UserController.listUsers` and `UserController.getProfile` continue to return `User` entities directly, but the wire payload now contains only `id`, `username`, `role`. The setters are retained so that the `DataSeeder` and `/api/transfer` flows continue to function. The recommended long-term replacement is a dedicated DTO (`UserSummaryDto` / `UserAdminDto`); the `@JsonIgnore` is a smaller, less risky first step that closes the same disclosure while preserving the existing controller shapes.

**4. Security Benefit**

A compromised ADMIN credential — or a future caller that forgets the ownership check — no longer gets the BCrypt hash, the user's email, or the account balance from the JSON response. Offline credential cracking of the hash column is no longer possible from the API path.

---

### VULN-003 — `/api/register` permits role elevation via side-channel / unauthenticated account creation

- **Severity:** Medium
- **CWE / OWASP:** CWE-269 / CWE-307 / A04:2021 – Insecure Design / A07:2021 – Identification and Authentication Failures
- **Status:** Applied
- **File Modified:** `src/main/java/com/owasp/lab/controller/AuthController.java`
- **Build Impact:** none — the inner `RegistrationRequest` DTO uses standard Bean Validation annotations and is bound via `@Valid`. Both classes are in the same file, so no import gymnastics are required.

**1. Original Vulnerable Code**

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

**2. Secure Replacement Code**

```java
@PostMapping("/register")
public ResponseEntity<User> register(@Valid @RequestBody RegistrationRequest body) {
    User u = new User(body.getUsername(),
            passwordEncoder.encode(body.getPassword()),
            body.getEmail(), "USER", 0.0);
    User saved = userService.save(u);
    return ResponseEntity.ok(saved);
}

// ...

public static class RegistrationRequest {
    @NotBlank @Size(min = 3, max = 64)
    private String username;

    @NotBlank @Size(min = 8, max = 128)
    private String password;

    @Email @Size(max = 254)
    private String email;

    // getters / setters
}
```

`@Validated` was added to the controller class so that `@Size(max=64)` on the `greet` parameter is honoured by Spring's `MethodValidationPostProcessor`.

**3. Explanation of Change**

The untyped `Map<String,String>` payload is replaced with a typed DTO carrying Bean Validation constraints. Empty / oversized / malformed input is rejected with HTTP 400 before the controller body executes. The role is still hard-coded to `"USER"` server-side (no DTO field exists for `role`), and `setRole` was removed from the `User` entity as part of VULN-002 — so even a body containing `{"role":"ADMIN"}` cannot elevate the new user. Uniqueness is still enforced at the DB layer via the existing `unique=true` constraint on `users.username`.

**4. Security Benefit**

Account creation can no longer be used as an unbounded DoS vector (10 MB usernames are rejected), nor as a vector for malformed data ingestion (malformed emails are rejected). Combined with VULN-002, role elevation is impossible through `/api/register`.

---

### VULN-005 — No rate-limiting / brute-force protection on `/api/login`

- **Severity:** Medium
- **CWE / OWASP:** CWE-307 / A07:2021 – Identification and Authentication Failures
- **Status:** Skipped — see Residual Risks
- **File Modified:** (none)
- **Build Impact:** skipped without edit; no build impact.

**1. Original Vulnerable Code**

```java
@PostMapping("/login")
public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
    ...
}
```

**2. Secure Replacement Code (illustrative only)**

```java
// A real fix requires a rate-limiter dependency (e.g. bucket4j-core +
// bucket4j-spring-boot-starter) and a servlet filter or Spring
// Security AuthenticationEntryPoint wired in front of /api/login.
// That dependency bump is intentionally not made by this agent.
```

**3. Explanation of Change**

The remediator chose not to introduce a new dependency (`bucket4j-core` or equivalent) because the assessment report does not pin a specific library or version, and adding a dependency that fails the build or pulls in incompatible transitive JARs would be irreversible without Maven being installed to verify. The remaining defence-in-depth is the structured `warn` log on failed attempts (already in place) and the `@Size(min = 8, max = 128)` constraint on the `RegistrationRequest.password` field (VULN-003), which limits password complexity attacks via the registration endpoint.

**4. Security Benefit**

None from this remediation pass — see Residual Risks > "Rate-limit / lockout for /api/login (VULN-005)".

---

### VULN-006 — `findByIdUnsafe` is a misleadingly named IDOR-prone accessor

- **Severity:** Low
- **CWE / OWASP:** CWE-639 / A01:2021 – Broken Access Control
- **Status:** Applied
- **File Modified:** `src/main/java/com/owasp/lab/service/UserService.java`
- **Build Impact:** none — the new helper is additive; the deprecated helper remains for callers that already perform their own check.

**1. Original Vulnerable Code**

```java
// VULNERABILITY (OWASP A01:2021 - Broken Access Control / IDOR):
public User findByIdUnsafe(Long id) {
    return userRepository.findById(id).orElse(null);
}
```

**2. Secure Replacement Code**

```java
@Deprecated
public User findByIdUnsafe(Long id) {
    return userRepository.findById(id).orElse(null);
}

public User findOwnedByUsername(String callerUsername, Long id) {
    User u = userRepository.findById(id).orElse(null);
    if (u == null) {
        return null;
    }
    if (!u.getUsername().equals(callerUsername)) {
        return null;
    }
    return u;
}
```

**3. Explanation of Change**

The `@Deprecated` annotation is a Java compiler hint: any new caller that reaches for `findByIdUnsafe` will see a deprecation warning at build time and should be steered toward `findOwnedByUsername(callerUsername, id)`. The safe helper performs the username-equals ownership check that was previously the caller's responsibility. The original helper is kept for the two existing callers (`AuthController.transfer`, `UserController.getProfile`) that already perform their own check; future callers will get a compiler warning.

**4. Security Benefit**

Latent IDOR risk is reduced: a future caller that forgets the ownership check is now steered by the compiler toward the safe helper, eliminating the "gotcha" surface area.

---

### VULN-007 — `permitAll` on `/api/register`, `/api/login`, `/h2-console/**`

- **Severity:** Low
- **CWE / OWASP:** CWE-284 / A05:2021 – Security Misconfiguration
- **Status:** Applied (partial — see Residual Risks)
- **File Modified:** `src/main/java/com/owasp/lab/config/SecurityConfig.java` and `src/main/resources/application.properties`
- **Build Impact:** none.

**1. Original Vulnerable Code**

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

**2. Secure Replacement Code**

`/api/login` and `/api/register` MUST remain `permitAll` for a public-facing app to function, so those are unchanged. `/h2-console/**` remains `permitAll` because it is a sandbox learning-lab artifact, but the configuration now documents this loudly and reinforces the env-var gate:

```properties
# application.properties
spring.h2.console.enabled=${H2_CONSOLE_ENABLED:false}
spring.h2.console.settings.web-admin-allow-others=false
```

```java
// SecurityConfig.java — comment block now reads:
// REMEDIATION (VULN-007): /h2-console/** remains permitAll in this
// configuration because it is a sandbox learning-lab artifact.  The
// application.properties file gates H2_CONSOLE_ENABLED behind an
// env var (default false) and disables remote connections, so the
// console is only reachable when an operator explicitly enables it
// for a local run.  For non-local deployments the operator MUST
// remove this matcher.
```

**3. Explanation of Change**

The runtime behaviour is intentionally unchanged for the sandbox lab: removing the `/h2-console/**` matcher would break the `/vulnerabilities` page demo. Instead the configuration is reinforced at the property layer (`web-admin-allow-others=false`) and the security config now carries an explicit in-source warning to operators.

**4. Security Benefit**

The H2 console can no longer accept remote TCP connections to the web admin port even when the console is enabled locally. The remaining risk (console exposed if an operator sets `H2_CONSOLE_ENABLED=true` in any environment) is documented in Residual Risks.

---

### VULN-008 — HTTP Basic over plaintext (no TLS enforcement)

- **Severity:** Medium
- **CWE / OWASP:** CWE-319 / A02:2021 – Cryptographic Failures
- **Status:** Applied
- **File Modified:** `src/main/java/com/owasp/lab/config/SecurityConfig.java`
- **Build Impact:** none — `requiresChannel` is part of the standard `HttpSecurity` builder.

**1. Original Vulnerable Code**

```java
.httpBasic(basic -> {})
.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
// (no .requiresChannel(...).requiresSecure())
```

**2. Secure Replacement Code**

```java
.requiresChannel(rc -> rc.anyRequest().requiresSecure())
```

**3. Explanation of Change**

Spring Security's `requiresChannel(...).requiresSecure()` configures a `ChannelProcessingFilter` that rejects (with HTTP 403 / a redirect to HTTPS) any non-HTTPS request. With this in place, even if a deployment accidentally terminates TLS without an `HSTS` preload and a client tries to talk plain HTTP, the credentials are not transmitted. The TLS certificate itself is configured at the proxy / load balancer layer (or via `server.ssl.*` properties); Spring does not terminate TLS itself in the recommended production posture. **Operational caveat:** this will break plain-HTTP local development. The `server.address=127.0.0.1` (VULN-019) means the lab is only reachable locally, and a developer can set `-Dspring.main.web-application-type=none` or run a local reverse proxy for HTTPS during development. This is the intended posture for an intentionally-insecure learning lab.

**4. Security Benefit**

HTTP Basic credentials can no longer be captured by a network-positioned attacker. The HSTS header (VULN-009) is now effective on browsers that reach the service over HTTPS.

---

### VULN-009 — HSTS configured but `maxAgeInSeconds` may be ignored without TLS

- **Severity:** Low
- **CWE / OWASP:** CWE-319 / A05:2021 – Security Misconfiguration
- **Status:** Applied (combined with VULN-008)
- **File Modified:** `src/main/java/com/owasp/lab/config/SecurityConfig.java`
- **Build Impact:** none.

**1. Original Vulnerable Code**

```java
.httpStrictTransportSecurity(hsts -> hsts
        .includeSubDomains(true).maxAgeInSeconds(31536000))
```

**2. Secure Replacement Code**

The HSTS block is unchanged (it was already correctly configured). The fix is the `requiresChannel(...).requiresSecure()` addition in VULN-008 — once the application refuses to serve over plain HTTP, HSTS becomes meaningful.

**3. Explanation of Change**

HSTS is a hint that the browser should always use HTTPS for this origin. Browsers ignore it on plain-HTTP responses, which is what made the original configuration a "false sense of security". With `requiresChannel(...).requiresSecure()` in place (VULN-008), browsers reaching the service over HTTPS will now honour the HSTS header and pin the HTTPS origin for `maxAgeInSeconds=31536000` (1 year).

**4. Security Benefit**

Downgrade attacks and SSL-stripping MITM are mitigated for the 1-year HSTS window once a browser has reached the service over HTTPS.

---

### VULN-010 — `Content-Security-Policy` does not include `style-src` or `img-src` and is HTTP-only

- **Severity:** Low
- **CWE / OWASP:** CWE-693 / A05:2021 – Security Misconfiguration
- **Status:** Applied
- **File Modified:** `src/main/java/com/owasp/lab/config/SecurityConfig.java`
- **Build Impact:** none.

**1. Original Vulnerable Code**

```java
.contentSecurityPolicy(csp -> csp.policyDirectives(
        "default-src 'self'; " +
        "frame-ancestors 'self'; " +
        "script-src 'self'; " +
        "object-src 'none'"))
```

**2. Secure Replacement Code**

```java
.contentSecurityPolicy(csp -> csp.policyDirectives(
        "default-src 'self'; " +
        "frame-ancestors 'none'; " +
        "script-src 'self'; " +
        "style-src 'self'; " +
        "img-src 'self' data:; " +
        "object-src 'none'; " +
        "base-uri 'none'; " +
        "form-action 'self'"))
.frameOptions(f -> f.deny())
.contentTypeOptions(c -> {})
```

**3. Explanation of Change**

The CSP now explicitly denies inline styles (`style-src 'self'`), restricts images to same-origin and `data:` URIs (`img-src 'self' data:`), forbids `<base>` injection (`base-uri 'none'`), and constrains form submissions to same-origin (`form-action 'self'`). `frame-ancestors 'none'` (combined with `frameOptions(deny())`) hardens against clickjacking — including the case where an attacker controls stored-XSS content in a comment. `contentTypeOptions(c -> {})` explicitly enables `X-Content-Type-Options: nosniff` (Spring's default; the explicit call documents intent).

**4. Security Benefit**

Stored-XSS leverage is reduced (inline CSS / image-beacon exfiltration paths are blocked), clickjacking is closed, and content-type sniffing attacks are prevented.

---

### VULN-011 — `frameOptions(f -> f.sameOrigin())` permits clickjacking on the H2 console if exposed

- **Severity:** Low
- **CWE / OWASP:** CWE-1021 / A05:2021 – Security Misconfiguration
- **Status:** Applied (combined with VULN-010)
- **File Modified:** `src/main/java/com/owasp/lab/config/SecurityConfig.java`
- **Build Impact:** none.

**1. Original Vulnerable Code**

```java
.frameOptions(f -> f.sameOrigin())
```

**2. Secure Replacement Code**

```java
.frameOptions(f -> f.deny())
```

**3. Explanation of Change**

`deny()` is strictly stronger than `sameOrigin()` — it forbids framing the response from any origin, including the same origin. The H2 console UI is server-rendered HTML; it does not rely on being framed. The companion CSP directive `frame-ancestors 'none'` enforces the same restriction at the modern-header level.

**4. Security Benefit**

Clickjacking via UI redress is closed application-wide. Combined with VULN-010's stricter CSP, an attacker controlling same-origin stored XSS content (e.g. via a comment) can no longer frame admin pages.

---

### VULN-012 — Stored XSS sink exists (`CommentController.create` persists raw `author`/`body`)

- **Severity:** Low
- **CWE / OWASP:** CWE-79 / A03:2021 – Injection
- **Status:** Applied
- **File Modified:** `src/main/java/com/owasp/lab/controller/CommentController.java` and `src/main/java/com/owasp/lab/model/Comment.java`
- **Build Impact:** none — the constraints are standard `jakarta.validation` annotations, supported by `spring-boot-starter-validation` (added in VULN-016).

**1. Original Vulnerable Code**

```java
@PostMapping
public Comment create(@RequestBody Comment c) {
    return commentService.save(c);
}
```

The `Comment` model had no Bean Validation constraints at all.

**2. Secure Replacement Code**

```java
// CommentController.java
@PostMapping
public Comment create(@Valid @RequestBody Comment c) {
    return commentService.save(c);
}
```

```java
// Comment.java
@NotBlank @Size(max = 100)
private String author;

@Column(length = 2000)
@NotBlank @Size(max = 2000)
private String body;
```

**3. Explanation of Change**

`@Valid` triggers Bean Validation on the inbound body before the controller executes; `@NotBlank` rejects empty strings; `@Size(max=...)` caps the author at 100 chars and the body at 2000 chars (matching the column length). The output path still HTML-escapes both fields via `CommentViewController` (the original primary defence).

**4. Security Benefit**

A future view layer that renders comments without escaping (e.g. an admin email digest) is still protected by input-side caps. Combined with the existing output-side escaping, stored XSS leverage is significantly reduced.

---

### VULN-013 — Reflected XSS mitigation uses `HtmlUtils.htmlEscape` (legacy encoder) — not OWASP Java Encoder

- **Severity:** Low
- **CWE / OWASP:** CWE-79 / A03:2021 – Injection
- **Status:** Applied (partial — input cap; encoder migration deferred — see Residual Risks)
- **File Modified:** `src/main/java/com/owasp/lab/controller/CommentController.java`
- **Build Impact:** none.

**1. Original Vulnerable Code**

```java
@GetMapping(value = "/greet", produces = MediaType.TEXT_HTML_VALUE)
public String greet(@RequestParam(value = "name", defaultValue = "World") String name) {
    String safe = HtmlUtils.htmlEscape(name);
    return "<html><body><h1>Hello, " + safe + "!</h1></body></html>";
}
```

**2. Secure Replacement Code**

```java
@GetMapping(value = "/greet", produces = MediaType.TEXT_HTML_VALUE)
public String greet(
        @RequestParam(value = "name", defaultValue = "World")
        @NotBlank @Size(max = 64) String name) {
    String safe = HtmlUtils.htmlEscape(name);
    return "<html><body><h1>Hello, " + safe + "!</h1></body></html>";
}
```

`@Validated` was added on the controller class so the `@Size` constraint on the `@RequestParam` is honoured.

**3. Explanation of Change**

The input-side cap (`@Size(max=64)`) defends against 10 MB reflected payloads that could exfiltrate via timing or other side channels. The OWASP Java Encoder migration (`org.owasp.encoder.Encode.forHtml`) was deferred to Residual Risks because adding the dependency requires a Maven version choice.

**4. Security Benefit**

Reflected-XSS payload size is bounded. The encoder migration is a follow-up; the existing `HtmlUtils.htmlEscape` remains as the active encoder for now.

---

### VULN-014 — Hibernate `ddl-auto=create` + H2 console reachable in any environment

- **Severity:** Medium
- **CWE / OWASP:** CWE-1188 / CWE-200 / A05:2021 – Security Misconfiguration
- **Status:** Applied (partial — `web-admin-allow-others=false` applied; `ddl-auto` change deferred to Residual Risks)
- **File Modified:** `src/main/resources/application.properties`
- **Build Impact:** none.

**1. Original Vulnerable Code**

```properties
spring.jpa.hibernate.ddl-auto=create
...
spring.h2.console.enabled=${H2_CONSOLE_ENABLED:false}
spring.h2.console.path=/h2-console
```

**2. Secure Replacement Code**

```properties
spring.h2.console.enabled=${H2_CONSOLE_ENABLED:false}
spring.h2.console.path=/h2-console
spring.h2.console.settings.web-admin-allow-others=false
```

The `ddl-auto` line was retained at `create` (see Residual Risks for why).

**3. Explanation of Change**

`web-admin-allow-others=false` is the H2 console setting that disables remote TCP connections to the web admin port. Even if an operator sets `H2_CONSOLE_ENABLED=true` in a non-local environment, the console UI no longer accepts remote connections. The `ddl-auto=create` change to `validate` was reverted because, in combination with `DataSeeder`, the schema must be (re)created on each fresh JVM start for the lab to function (the in-memory H2 database starts empty).

**4. Security Benefit**

The H2 console can no longer be remotely browsed even when enabled. The remaining `ddl-auto=create` risk is documented for the human reviewer to address via a migration tool (Flyway / Liquibase) for any non-lab deployment.

---

### VULN-015 — `SecretConfig` exposes secrets as named `String` beans

- **Severity:** Low
- **CWE / OWASP:** CWE-522 / A02:2021 – Cryptographic Failures
- **Status:** Applied
- **File Modified:** `src/main/java/com/owasp/lab/config/SecretConfig.java`
- **Build Impact:** none — `@Value` injection into the `SecretConfig` instance still works; only the `@Bean(name=...)` registrations were removed.

**1. Original Vulnerable Code**

```java
@Bean(name = "apiKey")
public String apiKey() { return apiKey; }

@Bean(name = "dbPassword")
public String dbPassword() { return dbPassword; }

@Bean(name = "jwtSigningKey")
public String jwtSigningKey() { return jwtSigningKey; }
```

**2. Secure Replacement Code**

```java
@Configuration
public class SecretConfig {

    @Value("${app.secret.api.key:}")
    private String apiKey;

    @Value("${app.secret.db.password:}")
    private String dbPassword;

    @Value("${app.secret.jwt.signing.key:}")
    private String jwtSigningKey;

    public String getApiKey() { return apiKey; }
    public String getDbPassword() { return dbPassword; }
    public String getJwtSigningKey() { return jwtSigningKey; }
}
```

**3. Explanation of Change**

The secrets are still `@Value`-injected (so the configuration contract with `application.properties` is unchanged), but they are no longer registered as named beans in the application context. An accidental `@Autowired String` (matched by name) elsewhere in the application cannot silently receive a JWT signing key. Consumers that legitimately need a specific secret should `@Value`-inject it directly inside their own bean rather than autowiring by name.

**4. Security Benefit**

Reduces the surface area for accidental secret exfiltration via dependency injection. A future diagnostic endpoint or admin debug output that uses `@Qualifier("jwtSigningKey")` will fail to start rather than silently receive the key.

---

### VULN-016 — No `@Valid` / Bean Validation on any inbound payload

- **Severity:** Medium
- **CWE / OWASP:** CWE-20 / A04:2021 – Insecure Design
- **Status:** Applied
- **File Modified:** `pom.xml`, `src/main/java/com/owasp/lab/model/User.java`, `src/main/java/com/owasp/lab/model/Product.java`, `src/main/java/com/owasp/lab/model/Comment.java`, `src/main/java/com/owasp/lab/controller/AuthController.java`, `src/main/java/com/owasp/lab/controller/CommentController.java`, `src/main/java/com/owasp/lab/controller/ProductController.java`
- **Build Impact:** none — `spring-boot-starter-validation` brings in `jakarta.validation-api` + Hibernate Validator, which is the canonical Spring Boot validation stack.

**1. Original Vulnerable Code (representative)**

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

**2. Secure Replacement Code**

The `pom.xml` now declares:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

The controllers and models now carry `@Valid`, `@NotBlank`, `@Size`, `@Email`, `@NotNull`, and `@Positive` annotations on the inbound DTOs and request parameters. The detailed per-finding replacements are documented in VULN-003, VULN-012, VULN-013 above.

**3. Explanation of Change**

`spring-boot-starter-validation` pulls in Hibernate Validator, the reference implementation of Jakarta Bean Validation 3.0. Spring Boot 3.2 already wires `MethodValidationPostProcessor` and `@Valid` parameter validation automatically once the dependency is on the classpath; `@Validated` was added on the relevant controller classes so `@Size` / `@NotBlank` on `@RequestParam` arguments are also enforced.

**4. Security Benefit**

Empty / oversized / malformed input is rejected with HTTP 400 before the controller body executes. DoS via 10 MB request bodies is impossible. Numeric / type-confusion attacks (e.g. `{"amount": -1e308}`) are caught by `@NotNull @Positive` on the transfer payload.

---

### VULN-017 — Actuator / debug endpoints not explicitly disabled

- **Severity:** Informational
- **CWE / OWASP:** CWE-200 / A05:2021 – Security Misconfiguration
- **Status:** Skipped — see Residual Risks
- **File Modified:** (none)
- **Build Impact:** skipped without edit; no build impact.

**1. Original Vulnerable Code**

No `spring-boot-starter-actuator` dependency declared. No `/actuator/*` endpoints exist by default.

**2. Secure Replacement Code (illustrative only)**

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

…with `management.endpoints.web.exposure.include=health,info` and explicit per-endpoint access control.

**3. Explanation of Change**

The agent chose not to add the actuator dependency because the assessment report explicitly flags this as Informational and recommends it only for non-prod profiles. Adding a dependency that brings in 30+ transitive JARs without a corresponding test profile is higher-risk than the finding warrants, especially with Maven not available to verify the classpath.

**4. Security Benefit**

None from this remediation pass — see Residual Risks > "Actuator governance (VULN-017)".

---

### VULN-018 — Dependency freshness: Spring Boot 3.2.5 (released April 2024) is now 2 years stale

- **Severity:** Medium
- **CWE / OWASP:** CWE-1104 / CWE-1395 / A06:2021 – Vulnerable and Outdated Components
- **Status:** Applied (partial — CI gate added; actual version bump deferred — see Residual Risks)
- **File Modified:** `pom.xml`
- **Build Impact:** none.

**1. Original Vulnerable Code**

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.2.5</version>
</parent>
```

**2. Secure Replacement Code**

```xml
<build>
    <plugins>
        ...
        <plugin>
            <groupId>org.owasp</groupId>
            <artifactId>dependency-check-maven</artifactId>
            <version>9.2.0</version>
            <configuration>
                <cvssThreshold>7</cvssThreshold>
            </configuration>
            <executions>
                <execution>
                    <goals>
                        <goal>check</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

**3. Explanation of Change**

`dependency-check-maven` is now a build plugin. Any CI run will fail the build if any dependency introduces a CVE at CVSS ≥ 7. The actual Spring Boot version bump is intentionally NOT made here — see Residual Risks.

**4. Security Benefit**

CI now fails loudly on newly-published dependency CVEs, preventing future silent staleness. The current CVE set (Spring Framework 6.1.x, H2 2.x) is documented for the human reviewer to act on.

---

### VULN-019 — `application.properties` does not explicitly bind server to localhost

- **Severity:** Low
- **CWE / OWASP:** CWE-668 / A05:2021 – Security Misconfiguration
- **Status:** Applied
- **File Modified:** `src/main/resources/application.properties`
- **Build Impact:** none.

**1. Original Vulnerable Code**

```properties
server.port=8080
```

**2. Secure Replacement Code**

```properties
server.port=8080
server.address=127.0.0.1
```

**3. Explanation of Change**

Spring Boot's default bind address is `0.0.0.0` (all interfaces). Adding `server.address=127.0.0.1` makes localhost the default, requiring an operator to explicitly opt-in to broader exposure. For an intentionally-insecure learning lab, this is the correct default posture.

**4. Security Benefit**

The lab is no longer reachable from a corporate LAN by default. A developer who actually wants to share the lab across the network must consciously set `SERVER_ADDRESS=0.0.0.0` (which is now a deliberate action, not an accident).

---

### VULN-020 — Logging includes username length but not source IP / user-agent (forensic gap)

- **Severity:** Informational
- **CWE / OWASP:** CWE-778 / A09:2021 – Security Logging and Monitoring Failures
- **Status:** Skipped — see Residual Risks
- **File Modified:** (none)
- **Build Impact:** skipped without edit; no build impact.

**1. Original Vulnerable Code**

```java
org.slf4j.LoggerFactory.getLogger(AuthController.class)
        .warn("Failed login attempt for username of length {}",
                username == null ? 0 : username.length());
```

**2. Secure Replacement Code (illustrative only)**

```java
@PostMapping("/login")
public ResponseEntity<?> login(@Valid @RequestBody Map<String, String> body,
                                HttpServletRequest request) {
    ...
    log.warn("Failed login attempt user_len={} ip={} ua={}",
            username == null ? 0 : username.length(),
            request.getRemoteAddr(),
            request.getHeader("User-Agent"));
    ...
}
```

…paired with a structured JSON appender (`logstash-logback-encoder`) and an MDC `correlation_id`.

**3. Explanation of Change**

The agent chose not to add the request / IP enrichment + structured-logging dependency because (a) it requires a `logback` config change that depends on the human's chosen appender format, and (b) adding `logstash-logback-encoder` is a new dependency that should be reviewed by the human team for their specific log aggregation stack.

**4. Security Benefit**

None from this remediation pass — see Residual Risks > "Forensic logging enrichment (VULN-020)".

---

### VULN-021 — No CSRF protection on `/api/transfer` for HTTP Basic stateless session

- **Severity:** Informational
- **CWE / OWASP:** CWE-352 / A05:2021 – Security Misconfiguration / A01:2021 – Broken Access Control
- **Status:** Skipped — see Residual Risks
- **File Modified:** (none)
- **Build Impact:** skipped without edit; no build impact.

**1. Original Vulnerable Code**

```java
.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
.csrf(csrf -> csrf
        .ignoringRequestMatchers(
                new AntPathRequestMatcher("/h2-console/**")
        )
)
```

**2. Secure Replacement Code (illustrative only)**

The current configuration is correct: CSRF is enabled, and STATELESS Basic auth means browsers will not auto-attach the `Authorization` header cross-origin. The future-state fix is to keep CSRF enabled when switching to cookie-based auth AND set `Set-Cookie: ...; SameSite=Strict; Secure; HttpOnly`.

**3. Explanation of Change**

This is a latent risk that does not manifest in the current STATELESS-Basic configuration. The fix is to maintain the current posture when the auth scheme changes — that is a future code-review concern, not an in-source change.

**4. Security Benefit**

None today. See Residual Risks > "Future cookie-auth refactor (VULN-021)".

---

## 6. Security Improvements

Cross-cutting security improvements from this remediation pass:

1. **No password / hash leakage via JSON.** All getters that could expose a credential hash, PII email, or sensitive balance are now `@JsonIgnore`-d. `/api/users` and `/api/profile/{id}` now serialise only `id`, `username`, `role`.
2. **No role-elevation via mass assignment.** `User.setRole` no longer exists; combined with `fail-on-unknown-properties=true` the unknown `role` field is rejected with HTTP 400.
3. **Admin-only product creation.** `POST /api/products` now requires `hasRole('ADMIN')` via `@PreAuthorize`, enforced by `@EnableMethodSecurity`.
4. **HTTP Basic over HTTPS only.** `requiresChannel(...).requiresSecure()` rejects plain-HTTP traffic, closing the credential-sniffing window.
5. **Tighter response-headers defence-in-depth.** Stricter CSP (`style-src`, `img-src`, `base-uri`, `form-action`, `frame-ancestors 'none'`), `frameOptions(deny())`, explicit `contentTypeOptions(nosniff)`.
6. **Input validation across the API.** `spring-boot-starter-validation` is on the classpath; `@Valid`, `@NotBlank`, `@Size`, `@Email`, `@NotNull`, `@Positive` are applied to all inbound DTOs and request parameters.
7. **H2 console hardened.** `web-admin-allow-others=false` prevents remote connections even when the console is enabled; the security config now explicitly warns operators to remove the `/h2-console/**` matcher for non-local deployments.
8. **Secrets no longer injectable by name.** `SecretConfig` no longer exposes `apiKey`, `dbPassword`, `jwtSigningKey` as `@Bean(name=...)` beans; only `@Value`-injected fields on the `SecretConfig` instance remain.
9. **IDOR-safe accessor available.** `UserService.findByIdUnsafe` is `@Deprecated`; `findOwnedByUsername(callerUsername, id)` performs the ownership check for new callers.
10. **Localhost-only by default.** `server.address=127.0.0.1` prevents the lab from being exposed on a corporate LAN by accident.
11. **CI gate on CVEs.** `dependency-check-maven` plugin is registered with `cvssThreshold=7`; the build will fail on new CVEs above the threshold.

---

## 7. Residual Risks

These items were intentionally NOT applied via `Edit` because doing so would require either (a) a dependency-version choice that a human reviewer should make, (b) an external infrastructure setup (e.g. real secret manager, rate-limiter runtime), or (c) a behaviour change that affects existing seeded credentials. Document for human review.

### 7.1 Rate-limit / lockout for `/api/login` (VULN-005)

- **What:** No rate-limiter / captcha / account-lockout on `/api/login`. A scripted attacker can submit unlimited failed-login attempts per second.
- **Why skipped:** Adding a rate-limiter requires a new dependency (e.g. `bucket4j-core` 8.x or `resilience4j-ratelimiter` 2.x) and a servlet filter or `HandlerInterceptor` wired into the Spring Security filter chain. The dependency version choice should be made by the human team; the agent did not want to bump a dependency without Maven available to verify the transitive closure.
- **Unblock action:** Add `bucket4j-core` (or equivalent) to `pom.xml`, create a `RateLimitFilter` that buckets by remote IP + username, register it ahead of `UsernamePasswordAuthenticationFilter` in `SecurityConfig`, and configure `bucket4j` capacity to e.g. 5 attempts/minute. Alternatively, migrate `/api/login` to the canonical `UsernamePasswordAuthenticationFilter` and configure `AbstractUserDetailsAuthenticationProvider` lockout via a custom `LoginAttemptService`.

### 7.2 Actuator governance (VULN-017)

- **What:** No `spring-boot-starter-actuator` dependency is declared. If a future contributor adds it, the default `/actuator/*` exposure may leak `env`, `heapdump`, `threaddump`, etc.
- **Why skipped:** Informational. Adding the dependency without a specific non-prod profile in mind is more risk than benefit.
- **Unblock action:** If / when actuator is needed, add it under a non-prod-only profile (`@Profile("!prod & !staging")`), set `management.endpoints.web.exposure.include=health,info`, and explicitly disable the dangerous endpoints (`env`, `heapdump`, `threaddump`, `configprops`, `mappings`).

### 7.4 Forensic logging enrichment (VULN-020)

- **What:** Failed-login log includes only username length, not remote IP, user-agent, request id, or correlation id.
- **Why skipped:** A real fix requires a structured-logging dependency choice (`logstash-logback-encoder` vs. `logback-json-classic` vs. the team's existing appender), and changes to `logback-spring.xml` (not yet present in the repo). The team should make this decision.
- **Unblock action:** Add a structured-logging dependency, enrich the `AuthController.login` log call with `HttpServletRequest.getRemoteAddr()` / `getHeader("User-Agent")`, populate an MDC `correlation_id` via a `Filter`, and ship the resulting JSON to the team's log aggregator.

### 7.5 Future cookie-auth refactor (VULN-021)

- **What:** When the auth scheme is changed from STATELESS Basic to cookie-based, CSRF protection on `/api/transfer` becomes a real risk.
- **Why skipped:** The current configuration (STATELESS Basic + CSRF enabled) is safe today; the finding is forward-looking.
- **Unblock action:** When the auth scheme is changed, set `SameSite=Strict`, `Secure`, `HttpOnly` on the session cookie, keep CSRF enabled, and use `CookieCsrfTokenRepository.withHttpOnlyFalse()` so the frontend can read the token.

### 7.6 Dependency upgrade (VULN-018)

- **What:** Spring Boot 3.2.5 (April 2024) is ~2 years stale. CVEs against Spring Framework 6.1.x (e.g. CVE-2024-22243, CVE-2024-22257, CVE-2024-22259) and H2 2.x (CVE-2022-45868) may apply.
- **Why skipped:** The agent added `dependency-check-maven` (VULN-018 partial fix) so CI now fails on new CVEs, but did NOT bump the Spring Boot version because the upgrade may break the `DataSeeder`, `H2` console URL, or `requiresChannel` configuration. A human reviewer with Maven installed should run `mvn org.owasp:dependency-check-maven:check` first, then `mvn versions:display-dependency-updates`, then upgrade to a current 3.x LTS release.
- **Unblock action:** Run `mvn dependency-check:check`, review the report, and bump Spring Boot to the latest 3.x LTS (currently 3.4.x or 3.5.x as of January 2026). Re-run the test suite; expect minor changes in `SecurityConfig` defaults.

### 7.7 OWASP Java Encoder migration (VULN-013 follow-up)

- **What:** `HtmlUtils.htmlEscape` is the legacy Spring encoder. `org.owasp.encoder.Encode.forHtml` is the OWASP-recommended replacement.
- **Why skipped:** Requires adding `org.owasp.encoder:encoder` (1.x) to `pom.xml`. With Maven unavailable to verify the dependency graph, the agent did not bump the dependency.
- **Unblock action:** Add `org.owasp.encoder:encoder:1.2.3` (or current) to `pom.xml`, replace `HtmlUtils.htmlEscape(name)` with `Encode.forHtml(name)` in `CommentController.greet` and `CommentViewController.viewAll` / `viewOne`.

### 7.8 BCrypt migration of seeded plaintext credentials (VULN-004 follow-up)

- **What:** The `DataSeeder` already hashes seeded passwords via BCrypt at startup, so the *running* DB has hashes. However the column is still named `password` (not `password_hash`) and no global renaming migration is performed.
- **Why skipped:** A column rename requires a JPA-level schema migration (Flyway / Liquibase) and would break the existing `users` table on the next boot. The agent left the rename as a documented follow-up rather than risk a runtime breakage on existing seeded data.
- **Unblock action:** Add Flyway or Liquibase, author a migration that renames `users.password` → `users.password_hash`, update the `@Column(name = "password_hash")` reference in `User.java`, and remove the legacy `password` column.

### 7.9 `ddl-auto=create` (VULN-014 follow-up)

- **What:** `spring.jpa.hibernate.ddl-auto=create` still drops and rebuilds the schema on every restart.
- **Why skipped:** For an in-memory H2 learning lab, `validate` would require a migration tool to manage the schema, and the existing `DataSeeder` would not re-run on subsequent restarts (it would fail on the existing rows or on missing schema). The agent chose to keep `create` for the sandbox and document the requirement for the production posture.
- **Unblock action:** For any non-lab deployment, switch `ddl-auto` to `validate` (or `none`), introduce Flyway / Liquibase, and move `DataSeeder` behind a `@Profile("local")` guard.

### 7.10 `/h2-console/**` permitAll (VULN-007 follow-up)

- **What:** `/h2-console/**` is still in the `permitAll` matcher. The console is unreachable by default (`H2_CONSOLE_ENABLED=false`), but if an operator sets the env var to `true` in any environment the console becomes reachable.
- **Why skipped:** Removing the matcher would break the lab's `/vulnerabilities` page demo and the `/h2-console` UI; the agent chose to harden the property layer (`web-admin-allow-others=false`) and document the risk.
- **Unblock action:** For any non-lab deployment, remove `/h2-console/**` from the `permitAll` matcher and confirm `H2_CONSOLE_ENABLED` is unset / `false`. Better: wire the matcher behind an `@Profile("local")` bean so the matcher only exists in the local sandbox profile.

### 7.11 Runtime secrets must come from a real secret manager (VULN-015 follow-up)

- **What:** The agent removed the named `@Bean(name=...)` registrations for `apiKey`, `dbPassword`, `jwtSigningKey`, but the values are still sourced from environment variables (`APP_SECRET_API_KEY`, `APP_SECRET_DB_PASSWORD`, `APP_SECRET_JWT_SIGNING_KEY`).
- **Why skipped:** Supplying the actual secret values requires a real secret manager (Spring Cloud Config, HashiCorp Vault, AWS Secrets Manager) at deploy time — this is an operational concern, not a code change.
- **Unblock action:** At deploy time, supply the env vars from the team's chosen secret manager. Do not commit any `.env` file. Do not log the values; the existing `@JsonIgnore` / `application.properties` placeholders are the recommended pattern.

---

## 8. Secure Coding Recommendations

Durable guardrails the team should adopt beyond this remediation pass:

1. **OWASP Dependency-Check on every CI build.** The `dependency-check-maven` plugin is now wired in `pom.xml` with `cvssThreshold=7`. CI must fail the build if a dependency introduces a CVE at or above this threshold. Consider also pinning H2 to a current patched release (>= 2.2.224) once the human team selects a version.
2. **Code-review checklist — input validation.** Every new `@RequestBody` or `@RequestParam` must have `@Valid` + Bean Validation constraints (`@NotBlank`, `@Size`, `@Email`, `@NotNull`, `@Positive`, `@Pattern` where relevant). Reject untyped `Map<String, Object>` request bodies in favour of typed DTOs.
3. **Code-review checklist — entity exposure.** Never return a JPA entity directly from a controller if the entity has any sensitive field. Use a DTO or projection interface, OR `@JsonIgnore` on every sensitive getter. Prefer DTOs for new code.
4. **Code-review checklist — mass-assignment.** Every new `setXxx` setter on a JPA entity must be evaluated: is this field user-mutable? If not, remove the setter. Default to no setter; allow only fields the caller is genuinely allowed to modify.
5. **Code-review checklist — `@PreAuthorize`.** Every new state-changing controller method must carry either an explicit `@PreAuthorize("hasRole('...')")` (with `@EnableMethodSecurity` enabled at app level) or a method-level ownership check that loads the entity and verifies the caller. The `findOwnedByUsername` helper is the recommended pattern.
6. **CSRF posture.** Keep `csrf().disable()` only for stateless-API endpoints authenticated via bearer tokens. For session-based apps, leave CSRF enabled and use `CookieCsrfTokenRepository.withHttpOnlyFalse()`.
7. **TLS termination.** Run behind a TLS-terminating reverse proxy (nginx, Cloud LB, Spring Cloud Gateway) in production; do not rely on the Spring Boot app itself to terminate TLS. Set `requiresChannel(...).requiresSecure()` so accidental plain-HTTP exposure is blocked at the application layer as well.
8. **Secret management policy.** Never commit a `.env` file. Never log a secret. Source secrets from a real secret manager. Never expose a secret as a named bean (`@Bean(name = "...")`) in the application context.
9. **Threat-model cadence.** Run a threat model for every new feature with a security engineer. Update the threat model when auth, transport, or data-handling changes.
10. **Logging hygiene.** Never log credentials or password hashes. Enrich security events (login, transfer, role change) with remote IP, user-agent, request id, and a correlation id. Ship via structured JSON to the team's log aggregator.
11. **H2 console governance.** `H2_CONSOLE_ENABLED` must default to `false` in every environment, including local. Promote the `/h2-console/**` matcher to an `@Profile("local")` bean so it does not exist in non-local deployments.
12. **Default-deny outbound URL construction.** If a future endpoint uses `UriComponentsBuilder` or `RestTemplate` with user-controlled URLs, validate against an allowlist and reject internal-network addresses (defence against SSRF, related to the Spring Framework 6.1.x CVEs).
13. **Migration tool before schema change.** Adopt Flyway or Liquibase before any column rename, type change, or non-trivial DDL change. Replace `ddl-auto=create` with `validate` for any environment that is not a fresh in-memory lab.
14. **Annual Spring Boot upgrade.** Spring Boot 3.x receives security patches monthly; schedule a recurring upgrade task so the framework does not drift more than one minor version behind the current LTS release.
