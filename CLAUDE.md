# CLAUDE.md

Operating notes for this repo. Read this first, then `docs/guide.md` for design reasoning.

This file is the **how to work here**. `docs/guide.md` is the **why it is built this way** and
is the living design document — update it before writing code that changes a decision in it.

---

## What this is

Dissertation Management System for M.Tech and integrated M.Tech programmes. Server-rendered
Spring Boot + Thymeleaf, no SPA. Three goals in priority order: interview artefact, college
submission, adoptable by the department.

Owner: Piyush Pandey. Repo: `github.com/piyush2676/dissertation-management-system`, branch `main`.

## Stack

| Thing | Version | Note |
|---|---|---|
| Java | 21 | |
| Spring Boot | 4.1.0 | starters renamed vs 3.x — see below |
| PostgreSQL | 18 | database `dms`, service `postgresql-x64-18` |
| Flyway | via `spring-boot-starter-flyway` | currently at **V10** |
| Thymeleaf | + `thymeleaf-extras-springsecurity6` | |
| Spring AI | 2.0.1 | Gemini via Google AI Studio; off unless a key is set |
| Build | Maven wrapper (`.\mvnw.cmd`) | no global Maven |

**Boot 4 renamed starters.** `spring-boot-starter-web` → `-webmvc`. `spring-boot-starter-test`
is split per module (`-webmvc-test`, `-data-jpa-test`, `-security-test`). Do not copy Boot 3.x
tutorials blindly.

---

## Current state (2026-09-10)

Phases 0–8 complete, verified in a browser, 140 tests green, 107 commits. Flyway at V10.
The end-to-end chain is scripted: `bash scripts/acceptance.sh 8081` — 19 assertions, all green.

| Phase | Covers | State |
|---|---|---|
| 0 | Scaffold, landing, error pages, design system | done |
| 1 | Auth, roles, dashboards | done |
| 2 | Topic proposal and approval | done |
| 3 | Session, milestones, guide allocation, coordinator override | done |
| 4 | Submissions with immutable version history + audit trail | done |
| 5 | Review comments pinned to a version | done |
| 6 | Rubric, weighted evaluation, viva, mark sheet | done |
| 7 | Topic overlap check + guide matching (Gemini) | done — key optional |
| 8 | End-to-end acceptance | done — scripted, 19/19 |

### Phase 7 — Google AI Studio, not Anthropic

**Anthropic ships no embedding model.** Three of the five planned AI features are
embedding-driven, so `docs/guide.md` §10 always needed a second provider. Gemini via AI Studio
covers chat *and* embeddings on one free-tier key.

**The features are off by default and the app must keep starting without a key.** The Google
auto-configuration builds its client eagerly and throws at startup when none is set — no guard
in application code can catch that, because it happens first. Three properties hold the line:

```properties
spring.ai.model.chat=none
spring.ai.model.embedding.text=none
spring.autoconfigure.exclude=...GoogleGenAiEmbeddingConnectionAutoConfiguration,...GoogleGenAiImageConnectionAutoConfiguration
```

The two `*ConnectionAutoConfiguration` exclusions are separate from the switches and demand a
Vertex `project-id`; excluding them is the only way off. To enable, uncomment the three lines in
`application-local.properties.example` and supply a key from `aistudio.google.com/apikey`.

**pgvector is still NOT installed and cannot be built here** — the VS 2022 BuildTools install is
headers-only (no `cl.exe`, no `nmake`, no Windows SDK), and `Program Files\PostgreSQL\18\` needs
elevation. Vectors are stored as JSONB and scanned exactly in `CosineSimilarityProvider`, behind
the `SimilarityProvider` interface. At department scale that is instant. Swapping to pgvector
later = one class + a migration copying the arrays into a `vector(768)` column.

**Hard constraint, enforced in the service not the template:** the model never approves, rejects
or grades. The novelty prompt forbids it explicitly, output renders inside the AI-labelled panel,
and similarity is described as overlap with the department archive — it is not plagiarism
detection and there is no web-wide corpus behind it.

### Open decision, deliberately not taken

`AllocationStatus.COORDINATOR_ASSIGNED` is terminal, so a coordinator who places a student with
the wrong guide **cannot undo it** from the UI, and the partial unique index then blocks a second
live allocation. Fix is one line — allow `WITHDRAWN` from `ACCEPTED` and `COORDINATOR_ASSIGNED`,
plus a revoke action — but it reverses an invariant `AllocationStatusTest` pins on purpose
("only REQUESTED may be non-terminal"). Ask before changing it.

### Cut from scope

Email notifications, admin user CRUD (read-only roll instead), viva panel as its own table
(free-text names instead), archive search UI, and three of the five planned AI features
(regulations Q&A, chapter summary, and the standalone archive search page -- retrieval itself
ships inside the overlap check).

---

## Commands

```powershell
.\mvnw.cmd -o test              # full suite, needs the DB up (140 tests)
.\mvnw.cmd -o -q compile        # fast syntax check
.\mvnw.cmd -o spring-boot:run   # runs on 8080
```

`-o` (offline) is safe and much faster — dependencies are already in the local repo.

### Demo accounts

Seeded by `DataSeeder` only when the users table is empty. Demo credentials, never for deployment.

| Email | Password | Roles |
|---|---|---|
| `admin@college.edu` | `admin123` | ADMIN |
| `coordinator@college.edu` | `coord123` | COORDINATOR |
| `guide1@college.edu` | `guide123` | SUPERVISOR + REVIEWER (cap 5) |
| `guide2@college.edu` | `guide123` | SUPERVISOR (cap 3) |
| `student1..3@college.edu` | `student123` | STUDENT, M.Tech |
| `student4@gmail.com` | `student123` | STUDENT, integrated |

---

## Environment traps on this machine

These cost real time in past sessions. Read before running anything.

- **Port 8080 is often already taken** by the user's own instance. Start test runs elsewhere:
  `./mvnw.cmd -o spring-boot:run "-Dspring-boot.run.arguments=--server.port=8081"`.
- **Never `taskkill //F //IM java.exe`** — it kills whatever the user is running too. Find the
  PID: `netstat -ano | grep ":8081" | grep LISTENING` then kill that PID only.
- **psql is not on PATH.** It is at `/c/Program Files/PostgreSQL/18/bin/psql.exe`. Password is
  in `application-local.properties` (gitignored).
- **Git Bash mangles curl file arguments.** `curl -F "file=@/c/path;type=application/pdf"` fails
  with `curl: (26) Failed to open/read local data` because MSYS treats the `;` as a path-list
  separator. Fix: `export MSYS2_ARG_CONV_EXCL='*'` **and** switch every file path — cookie jars
  included — to Windows form (`C:/Users/...`).
- **Heredocs eat backticks and apostrophes** in this shell. Write file content with the Write
  tool, or write the script to a file first and run it; do not inline it in `python -c "..."`.
- **Line endings are mixed.** `src/main` is CRLF, `src/test` is LF. Any script that rewrites a
  file must detect: `NL = '\r\n' if '\r\n' in s else '\n'`.
- **Local DB predates the M.Tech migration** — some roll numbers read `21CSE001` where the
  current seeder writes `24MCS001`. Not a bug.
- **psql emits CRLF.** Splitting a multi-row `psql -t -A` result leaves `` glued to every
  value but the last, and the terminal hides it. Always pipe through `tr -d ''`.
- **`grep -oP` fails here** — "supports only unibyte and UTF-8 locales". Use `sed -n 's/x//p'`.
  Silent empty output from it once left `psql` waiting on stdin forever; pass `-w` so a missing
  password fails instead of hanging.

---

## Package layout — by feature, not by layer

```
com.dms
├── audit/        AuditLog, DomainEvent(s), AuditLogListener, AuditLogController
├── allocation/   Allocation, AllocationStatus, AllocationService, AllocationBoard, controllers
├── common/       exceptions, GlobalExceptionHandler
├── evaluation/   RubricCriterion, Evaluation, EvaluationService, MarkSheet, controllers
├── review/       ReviewComment, ReviewService, ReviewCommentController
├── security/     SecurityConfig, CustomUserDetailsService, AuthzService
├── session/      AcademicSession, Milestone
├── storage/      StorageService, LocalDiskStorageService, StoredFile
├── submission/   Submission, SubmissionVersion, SubmissionStatus, SubmissionService, controllers
├── ai/           Embedding, SimilarityProvider, CosineSimilarityProvider, EmbeddingService,
│                TopicNoveltyService, SupervisorMatchingService, AiIndexingListener
├── topic/        Topic, TopicStatus, TopicService, controllers
├── user/         User, Role, Programme, profiles, DataSeeder, AdminUserController
├── viva/         VivaSchedule, VivaStatus, VivaService, CoordinatorVivaController
└── web/          HomeController, DashboardController, DashboardService, Dashboards
```

Feature-specific controllers live with their feature. `web/` holds only what spans features.

---

## Rules that must not be broken

These are load-bearing. Violating one produces a runtime failure, not a compile error.

1. **`open-in-view=false`.** Templates must never receive a JPA entity with a lazy association.
   Services return **view records** built inside the transaction (`AllocationBoard`,
   `SubmissionDetail`, `Dashboards.*`, `MarkSheet`). Handing Thymeleaf a lazy `StudentProfile`
   throws `LazyInitializationException` once the transaction closes.
2. **Do not drop `@EntityGraph`s** on the cohort/queue reads. They are what keep the boards at a
   flat query count instead of N+1.
3. **`ddl-auto=validate`.** Entity and migration must agree or the app refuses to start. Note
   SQL `CHAR(64)` maps to `bpchar` and will **fail** against `@Column(length=64)` — use
   `VARCHAR`. This has already bitten once.
4. **Migrations are additive.** Never edit an applied migration — Flyway checksums them,
   comments included. Local-only escape hatch, documented in `guide.md` §6:
   `DELETE FROM flyway_schema_history WHERE version = 'N';` plus dropping the tables it created,
   then re-run. Only valid when the migration has never left this machine.
5. **State machines are declarative** — one `Map<State, Set<State>>` per aggregate, validated in
   the service. Never scatter `if` checks. Five exist: `TopicStatus`, `AllocationStatus`,
   `SubmissionStatus`, `VivaStatus` (+ the topic/allocation pairs above).
6. **Compare enums with `==`, not `.equals()`.** Several of these fields are legitimately null
   (a student with no topic yet). `.equals()` on a null receiver NPEs — this shipped once.
7. **CSRF stays on.** Thymeleaf injects the token into `th:action` forms. Do not switch a form
   to a plain `action=`.
8. **`uploads/` is never a static resource directory.** Every read goes through
   `SubmissionDownloadController`, which re-checks ownership.
9. **`/files/**` and `/review/**` sit outside the role prefixes on purpose.** A submission file
   and a comment thread are legitimately touched by student, guide, coordinator and admin, so
   they authorise by **ownership in the service**, not by URL prefix.
10. **Doc before code.** A flow change updates `docs/guide.md` §9 view contract first, then the
    controller and template.

### Smaller invariants worth knowing

- Lateness is judged on the **first** version only — a revision after the due date is the review
  cycle running, not a missed deadline.
- An identical re-upload (same SHA-256) is refused and the stored file deleted.
- `authz.supervises` uses `OCCUPIES_A_SEAT`, not `LIVE` — a merely `REQUESTED` allocation is not
  supervision and must not grant read access.
- The audit listener is a plain `@EventListener` (same transaction), not after-commit, so a
  rolled-back action leaves no trail entry.
- Evaluation totals are weighted: each criterion contributes its weight scaled by the fraction
  of its own maximum earned. Rescoring updates the examiner's row in place.
- Guide comments; **student** resolves. Not the other way round.

---

## Conventions

**Commits.** Single lowercase `type(scope): subject` line, body explaining *why* when it is not
obvious. Types in use: `feat`, `fix`, `chore`, `docs`, `test`. **No `Co-Authored-By` trailer** —
the user asked for this explicitly; the log is read as their own work record.

Commit in small logical units — the user values commit count. Order commits so each one compiles
(repository method before the service that calls it).

**Prose in code and docs.** Match the existing voice: plain, explains the reasoning behind a
decision, no marketing tone. Comments say *why*, not *what*.

**Templates.** Reuse the existing class vocabulary — `hero`, `eyebrow`, `lead`, `page-head`,
`stat-grid`/`stat`, `card-grid`/`card`, `table-wrap`, `badge badge-{draft,pending,approved,rejected}`,
`field`, `field-error`, `btn btn-{primary,ghost} btn-sm`, `status-note`, `no-print`, `reveal`.
Check a class exists in `app.css` before using it — `section-title` did not, and was silently
inert.

**Tests.** Mockito for services, one test per rule with a name that states the rule. When a
service gains a constructor dependency, every `@InjectMocks` test needs the matching `@Mock` or
it NPEs. Use `lenient()` for stubs that validation tests never reach.

---

## Verifying work

Unit tests are not enough — Thymeleaf fails at **runtime**, not compile time. After a UI change,
start the app and hit the pages.

The end-to-end chain that must keep working:

> student proposes topic → guide approves → student requests guide → guide accepts →
> student files v1 → guide starts review → guide requests revision → student files v2 →
> guide approves → guide scores against rubric → coordinator books viva → mark sheet shows result

All 22 routes were confirmed 200, and these denials confirmed 403: student → `/coordinator/**`,
student → `/admin/**`, guide → `/admin/**`, coordinator → `/supervisor/**`.

---

## Key files

| Path | What |
|---|---|
| `docs/guide.md` | Living design doc — status, architecture, view contract, rationale |
| `docs/phase1-contract.md` | Phase 1 auth contract (historical) |
| `docs/diagram-prompts.md`, `docs/diagrams/` | PPT diagram sources |
| `application-local.properties` | DB password, gitignored |
| `src/main/resources/db/migration/` | V1–V10 |
