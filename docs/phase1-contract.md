# Phase 1 Contract — Auth and Roles

Frontend is **done and verified**. This is what the backend must provide for those templates
to work on the real URLs.

Delete `com.dms.web.PreviewController` when this phase is finished.

---

## 1. What the templates already expect

| Template | Real route | Needs from you |
|---|---|---|
| `auth/login.html` | `GET /login` | a controller returning `"auth/login"` |
| — | `POST /login` | **nothing** — Spring Security handles it |
| — | `POST /logout` | **nothing** — Spring Security handles it |
| `student/dashboard.html` | `GET /student/dashboard` | controller + `ROLE_STUDENT` |
| `supervisor/dashboard.html` | `GET /supervisor/dashboard` | controller + `ROLE_SUPERVISOR` |
| `coordinator/dashboard.html` | `GET /coordinator/dashboard` | controller + `ROLE_COORDINATOR` |
| `admin/dashboard.html` | `GET /admin/dashboard` | controller + `ROLE_ADMIN` |
| `error/403,404,500.html` | — | resolved by Boot automatically |

**No model attributes needed in Phase 1.** The dashboards read only
`sec:authentication="name"`, which comes from the security context. Data lands in Phase 2.

Login form field names are `username` and `password`. Don't rename them without also setting
`.usernameParameter()` / `.passwordParameter()`.

---

## 2. Enums — `com.dms.user`

```java
public enum Role {
    STUDENT, SUPERVISOR, REVIEWER, COORDINATOR, ADMIN;

    /** Spring Security expects the ROLE_ prefix; hasRole('STUDENT') matches ROLE_STUDENT. */
    public String authority() {
        return "ROLE_" + name();
    }
}

public enum Programme { BTECH, MTECH }
```

---

## 3. Entities — `com.dms.user`

`User` holds a `Set<Role>`, not a single role. A faculty member is often both `SUPERVISOR`
and `REVIEWER`, and the navbar already renders both link groups when that is true.

```java
@Entity
@Table(name = "users")
@Getter @Setter @NoArgsConstructor
public class User {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role", nullable = false)
    @Enumerated(EnumType.STRING)
    private Set<Role> roles = new HashSet<>();

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
```

`StudentProfile` — `@OneToOne User`, `rollNo` (unique), `programme`, `department`, `batch`, `semester`.

`SupervisorProfile` — `@OneToOne User`, `designation`, `department`, `researchInterests` (TEXT —
Phase 7 embeds this for supervisor matching), `maxStudents` (int, default 5).

> `roles` is `EAGER` deliberately. `UserDetailsService` needs authorities outside a transaction,
> and `spring.jpa.open-in-view=false` is set, so a lazy set would throw
> `LazyInitializationException` at login.

---

## 4. `V1__init.sql`

Goes in `src/main/resources/db/migration/`. Flyway runs it on next boot.
**Once applied, never edit it** — changes go in `V2__…sql`.

```sql
CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(255) NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE user_roles (
    user_id BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role    VARCHAR(32) NOT NULL,
    PRIMARY KEY (user_id, role)
);

CREATE TABLE student_profiles (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    roll_no    VARCHAR(32) NOT NULL UNIQUE,
    programme  VARCHAR(16) NOT NULL,
    department VARCHAR(128),
    batch      VARCHAR(16),
    semester   INT
);

CREATE TABLE supervisor_profiles (
    id                 BIGSERIAL PRIMARY KEY,
    user_id            BIGINT      NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    designation        VARCHAR(128),
    department         VARCHAR(128),
    research_interests TEXT,
    max_students       INT         NOT NULL DEFAULT 5
);
```

`spring.jpa.hibernate.ddl-auto=validate` is set, so if your entities and this SQL disagree the
app refuses to start and names the mismatch. That is the point.

---

## 5. Repositories — `com.dms.user`

```java
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}
```

Plus `StudentProfileRepository`, `SupervisorProfileRepository`.

---

## 6. `CustomUserDetailsService` — `com.dms.security`

```java
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("No user with email " + email));

        List<GrantedAuthority> authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.authority()))
                .collect(Collectors.toList());

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(user.getPasswordHash())
                .authorities(authorities)
                .disabled(!user.isEnabled())
                .build();
    }
}
```

---

## 7. `SecurityConfig` — replaces the Phase 0 shim entirely

```java
@Configuration
@EnableMethodSecurity          // required for @PreAuthorize("@authz...")
@RequiredArgsConstructor
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/login", "/css/**", "/js/**", "/error").permitAll()
                .requestMatchers("/student/**").hasRole("STUDENT")
                .requestMatchers("/supervisor/**").hasRole("SUPERVISOR")
                .requestMatchers("/coordinator/**").hasRole("COORDINATOR")
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/dashboard", true)
                .failureUrl("/login?error")
                .permitAll())
            .logout(logout -> logout
                .logoutSuccessUrl("/login?logout")
                .permitAll())
            .exceptionHandling(ex -> ex
                .accessDeniedPage("/error/403"));
        return http.build();
    }
}
```

Leave CSRF **on** (the Phase 0 shim disabled it). Thymeleaf injects the token into every
`th:action` form automatically, including the logout form already in the navbar.

---

## 8. `AuthzService` — `com.dms.security`

Bean name `authz` is what `@PreAuthorize` expressions reference. Stub it now; fill it in as the
entities arrive. This is the piece that makes role checks sufficient.

```java
@Service("authz")
@RequiredArgsConstructor
public class AuthzService {

    private final UserRepository userRepository;

    /** True when the signed-in supervisor actually supervises this student. */
    public boolean supervises(Long studentId, Authentication authentication) {
        return false; // Phase 3: check Allocation(student, supervisor) exists and is ACCEPTED
    }

    /** True when the signed-in user owns, or supervises the owner of, this submission. */
    public boolean ownsSubmission(Long submissionId, Authentication authentication) {
        return false; // Phase 4
    }
}
```

Returning `false` by default is intentional — deny first, open up as each phase lands.

---

## 9. `DashboardController` — `com.dms.web`

One dispatcher plus four leaf pages. The dispatcher is why `defaultSuccessUrl` points at
`/dashboard`: everyone lands on the same URL and gets routed by role.

```java
@Controller
@RequiredArgsConstructor
public class DashboardController {

    @GetMapping("/login")
    public String login() {
        return "auth/login";
    }

    @GetMapping("/dashboard")
    public String dispatch(Authentication authentication) {
        Set<String> authorities = AuthorityUtils.authorityListToSet(authentication.getAuthorities());
        if (authorities.contains("ROLE_ADMIN"))       return "redirect:/admin/dashboard";
        if (authorities.contains("ROLE_COORDINATOR")) return "redirect:/coordinator/dashboard";
        if (authorities.contains("ROLE_SUPERVISOR"))  return "redirect:/supervisor/dashboard";
        if (authorities.contains("ROLE_STUDENT"))     return "redirect:/student/dashboard";
        return "redirect:/";
    }

    @GetMapping("/student/dashboard")
    public String student() { return "student/dashboard"; }

    @GetMapping("/supervisor/dashboard")
    public String supervisor() { return "supervisor/dashboard"; }

    @GetMapping("/coordinator/dashboard")
    public String coordinator() { return "coordinator/dashboard"; }

    @GetMapping("/admin/dashboard")
    public String admin() { return "admin/dashboard"; }
}
```

Order matters — check the most privileged role first, since a user can hold several.

---

## 10. `DataSeeder` — `com.dms.config`

`CommandLineRunner` that inserts demo users **only when the table is empty**, so restarting
never duplicates rows. Hash passwords with the `PasswordEncoder` bean — never store plaintext.

| Email | Password | Roles |
|---|---|---|
| `admin@college.edu` | `admin123` | ADMIN |
| `coordinator@college.edu` | `coord123` | COORDINATOR |
| `guide1@college.edu` | `guide123` | SUPERVISOR, REVIEWER |
| `guide2@college.edu` | `guide123` | SUPERVISOR |
| `student1@college.edu` | `student123` | STUDENT (BTECH) |
| `student2@college.edu` | `student123` | STUDENT (MTECH) |
| `student3@college.edu` | `student123` | STUDENT (BTECH) |

Demo credentials only — fine for a local demo, never for a deployment.

---

## 11. Done when

- [ ] `/login` renders the styled page (not Spring's default form)
- [ ] Wrong password → `/login?error` shows the red alert
- [ ] `guide1@college.edu` sees **both** supervisor and reviewer links in the navbar
- [ ] Each role lands on its own dashboard via `/dashboard`
- [ ] A student requesting `/coordinator/dashboard` gets the 403 page
- [ ] Sign out returns to `/login?logout` with the green alert
- [ ] `PreviewController` deleted
- [ ] `mvnw test` green
