# Execution Checklist

`(you)` = backend · `(me)` = Claude, frontend + scaffold · `(both)` = joint verification

Tick as things land. Every phase ends with something openable in a browser.

---

## Phase 0 — Scaffold

- [x] Fresh project generated from Spring Initializr *(me)*
- [x] `pom.xml` — Boot 4.1.0, Java 21, webmvc/thymeleaf/jpa/security/validation/lombok/flyway/postgres/mail *(me)*
- [x] Parent version fixed `4.1.0.RELEASE` → `4.1.0` *(me)*
- [x] `mvnw clean compile` → BUILD SUCCESS *(me)*
- [x] `docs/guide.md` *(me)*
- [x] `docs/CHECKLIST.md` *(me)*
- [x] `application.properties` — datasource, JPA, Flyway, upload limits *(me)*
- [x] `layout/base.html`, `_navbar.html`, `_flash.html`, `home.html` *(me)*
- [x] `static/css/app.css` *(me)*
- [x] `HomeController` — landing page renders *(me)*
- [x] **Set `spring.datasource.password` in `application.properties`** *(you)*
- [x] **Create database `dms` in PostgreSQL 18** *(me — `CREATE DATABASE dms`)*
- [x] **Demo:** `http://localhost:8080` → HTTP 200, layout fragments compose, CSS served *(both)*
- [ ] `git init` + first commit *(you)*

---

## Phase 1 — Auth and roles

- [ ] `Role`, `Programme` enums *(you)*
- [ ] `User`, `StudentProfile`, `SupervisorProfile` entities *(you)*
- [ ] `V1__init.sql` — users, roles, profiles *(you)*
- [ ] `UserRepository`, `CustomUserDetailsService` *(you)*
- [ ] `SecurityConfig` — form login, BCrypt, URL rules *(you)*
- [ ] `AuthzService` bean for ownership checks *(you)*
- [ ] `DataSeeder` — admin, coordinator, 2 supervisors, 3 students *(you)*
- [ ] `DashboardController` role dispatcher *(you)*
- [x] `auth/login.html` *(me)*
- [x] Navbar with `sec:authorize` role links *(me)*
- [x] 4 role dashboards — student, supervisor, coordinator, admin *(me)*
- [x] `error/403.html`, `404.html`, `500.html` *(me)*
- [ ] **Demo:** log in as 3 roles, see 3 different navbars

---

## Phase 2 — Topic proposal

- [ ] `Topic` entity + `TopicStatus` + transition map *(you)*
- [ ] `TopicService.propose()` / `.decide()` + `InvalidStateTransitionException` *(you)*
- [ ] `TopicController` per view contract *(you)*
- [ ] Unit tests: every illegal transition throws *(you)*
- [ ] `TopicForm`, `TopicDecisionForm` DTOs *(me)*
- [ ] `student/topic-form.html`, `supervisor/topic-approvals.html` *(me)*
- [ ] **Demo:** student proposes, guide approves or rejects with a reason

---

## Phase 3 — Guide allocation

- [ ] `AcademicSession`, `Allocation` entities *(you)*
- [ ] Capacity rule — reject when supervisor at `maxStudents` *(you)*
- [ ] Student preference list + coordinator override *(you)*
- [ ] `coordinator/allocate.html` with live load counts *(me)*
- [ ] **Demo:** allocation succeeds; over-capacity blocked with a clear message

---

## Phase 4 — Milestones and submissions

- [ ] `Milestone` rows seeded per programme *(you)*
- [ ] `Submission` + `SubmissionVersion` + status machine *(you)*
- [ ] `StorageService` interface + `LocalFileSystemStorage` impl *(you)*
- [ ] SHA-256 checksum, size cap, MIME whitelist, late flag on deadline *(you)*
- [ ] Secure download controller with ownership re-check *(you)*
- [ ] `student/milestones.html` timeline, `submit.html`, version history *(me)*
- [ ] **Demo:** upload v1 then v2, both retrievable, late submission flagged

---

## Phase 5 — Review loop

- [ ] `ReviewComment` + `ReviewService` *(you)*
- [ ] Revision-requested → resubmit path *(you)*
- [ ] `supervisor/review.html`, threaded feedback on student side *(me)*
- [ ] **Demo:** guide comments, student resubmits, guide approves

---

## Phase 6 — Evaluation and viva

- [ ] `RubricCriterion`, `Evaluation` with JSONB scores *(you)*
- [ ] `ScoreCalculator` — weighted total *(you)*
- [ ] `VivaSchedule`, `PanelMember`, slot clash detection *(you)*
- [ ] Rubric form, viva calendar, mark sheet *(me)*
- [ ] **Demo:** two examiners score, total computed, no Excel

---

## Phase 7 — Spring AI

- [ ] Install pgvector into PostgreSQL 18 *(you)*
- [ ] Spring AI 2.0.0 BOM + `spring-ai-starter-model-anthropic` + pgvector store *(you)*
- [ ] `ANTHROPIC_API_KEY` via env var, never committed *(you)*
- [ ] Ingest approved theses into the vector store *(you)*
- [ ] Archive semantic search *(you)*
- [ ] Topic novelty check — RAG, `claude-opus-5` *(you)*
- [ ] Supervisor matching by embedding similarity *(you)*
- [ ] Handbook RAG Q&A — `claude-sonnet-5` *(you)*
- [ ] `archive/search.html`, AI advisory panel, chat widget *(me)*
- [ ] Every AI output labelled *AI-generated — verify before acting*; `aiGenerated` flag persisted *(both)*
- [ ] **Demo:** semantic search returns by meaning; novelty report on a live proposal

---

## Phase 8 — Polish

- [ ] Email notifications on state change *(you)*
- [ ] `AuditLog` via `@EventListener` *(you)*
- [ ] PDF export of mark sheet *(you)*
- [ ] Integration tests: `@WebMvcTest` + `spring-security-test` authz *(you)*
- [ ] Rebuild schema from zero via Flyway *(you)*
- [ ] Reports UI + print stylesheet *(me)*
- [ ] `README.md` with screenshots, setup, architecture diagram *(me)*
- [ ] **Demo:** full end-to-end chain (guide.md §11)
