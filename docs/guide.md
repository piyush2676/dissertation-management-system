# Dissertation Management System — Guide

Living design document. **Update this file before writing code that changes a decision here.**

---

## 1. Why this exists

B.Tech and M.Tech dissertation work is run manually: topics by email, guide allocation in a
spreadsheet, deadlines over WhatsApp, reports as `report_final_FINAL_v2.docx` on pen drives,
feedback on printed copies, marks totalled in Excel. Nobody can answer *"who approved this,
and when"*.

Goals, in priority order:

1. **Interview artefact** — real domain modelling, state machines, ownership authorisation,
   an AI feature that is not a bolted-on chatbot.
2. **College submission** — demoable end to end on one laptop with seeded data.
3. **Adoptable** by the department next session — production seams stay in place even though
   we build demo-grade.

The workflow **will change** as we build. Section 6 is how the design absorbs that.

---

## 2. How controllers and templates stay in sync

Controllers and templates are coupled by name, not by the compiler. A controller that fails to
put `topicForm` in the model breaks `topic-form.html` at runtime, and nothing catches it until
the page is opened.

So the contract in **section 9** — route, view name, model attributes, form object — is fixed
*before* the controller or the template for a page is written. It is the single source of truth
for both sides, and it is why the DTO layer exists: templates bind to form objects, never to
JPA entities, so an entity rename cannot ripple into the HTML.

---

## 3. Stack — as verified on this machine

| Thing | Version | Note |
|---|---|---|
| Java | 21 (`jdk-21.0.10`) | LTS, installed |
| Spring Boot | **4.1.0** | Verified: `mvnw clean compile` → BUILD SUCCESS |
| Spring AI | **2.0.0** (Phase 7) | `spring-ai-starter-model-anthropic:2.0.0` depends on `spring-boot-starter:4.1.0` |
| PostgreSQL | 18 | Service `postgresql-x64-18`, already running |
| Flyway | 12.4.0 | via `spring-boot-starter-flyway` |
| Thymeleaf security extras | `thymeleaf-extras-springsecurity6` 3.1.5 | what Initializr pairs with Boot 4 |
| Build | Maven wrapper | `.\mvnw.cmd` — no global Maven needed |

### Boot 4 renamed the starters — do not copy 3.x tutorials blindly

| Boot 3.x | Boot 4.x (what we use) |
|---|---|
| `spring-boot-starter-web` | `spring-boot-starter-webmvc` |
| `spring-boot-starter-test` | per-module: `spring-boot-starter-webmvc-test`, `-data-jpa-test`, `-security-test`, … |
| — | `spring-boot-starter-flyway` (Flyway now has its own starter) |

> **Version history, so we don't relitigate it.** The plan originally locked Boot **3.5.4**, to
> protect the Spring AI phase — correct for Spring AI 1.x, which targets Boot 3.4/3.5. But
> Spring Initializr no longer serves 3.5.x, and Spring AI **2.0.0 GA** is built against Boot
> **4.1.0**. The constraint reversed. Boot 4.1.0 is now the version that *protects* Phase 7.

---

## 4. Architecture

```
Browser
   |  HTML over HTTP (form POST, no SPA)
Thymeleaf templates
   |  view name + model attributes
@Controller
   |  DTO in, DTO out
@Service             <-- you own: business rules, state transitions, @Transactional
   |  entities
@Repository (Spring Data JPA)
   |
PostgreSQL 18  +  pgvector (Phase 7)
   |
StorageService -> local disk (uploads/)
```

Cross-cutting: Spring Security filter chain, `@ControllerAdvice` for global errors, `AuditLog`
written by `@EventListener` on domain events, `JavaMailSender` for notifications.

### Package layout — by feature, not by layer

Built so far (Phase 1):

```
com.dms
├── DissertationManagementSystemApplication.java
├── security/        SecurityConfig, CustomUserDetailsService, AuthzService
├── user/            User, Role, Programme, StudentProfile, SupervisorProfile,
│                    UserRepository, StudentProfileRepository,
│                    SupervisorProfileRepository, DataSeeder
└── web/             HomeController, DashboardController
```

Planned as later phases land:

```
├── common/          BaseEntity, exceptions, GlobalExceptionHandler, AuditLog, DomainEvent
├── session/         AcademicSession, Milestone
├── topic/           Topic, TopicService, TopicController, TopicForm
├── allocation/      Allocation, AllocationService, capacity rules
├── submission/      Submission, SubmissionVersion, SubmissionService, StorageService
├── review/          ReviewComment, ReviewService
├── evaluation/      RubricCriterion, Evaluation, ScoreCalculator
├── viva/            VivaSchedule, PanelMember, VivaService
├── notification/    Notification, NotificationService
└── ai/              VectorIngestService, SimilarityService, TopicNoveltyService, RagChatService
```

Rationale: `com.dms.topic` holding its own controller/service/repo localises the blast radius
when a flow changes. Four giant `controller/`/`service/` folders do not.

Two deviations worth noting rather than pretending otherwise. `DataSeeder` sits in `user/`
rather than a `config/` package — it only touches user data, so it stays with what it seeds.
And `web/` holds the view controllers that span features (the landing page, the role
dispatcher); feature-specific controllers still belong in their own package.

---

## 5. Domain model

```
User (id, email UQ, passwordHash, fullName, enabled, createdAt)
  └─ roles: Set<Role>   [STUDENT, SUPERVISOR, REVIEWER, COORDINATOR, ADMIN]

StudentProfile    (user 1:1, rollNo UQ, programme, department, batch, semester)
        -- programme: BTECH | MTECH | BTECH_MTECH_INTEGRATED (5-year dual degree)
SupervisorProfile (user 1:1, designation, department, researchInterests, maxStudents)

AcademicSession (id, label "2025-26", programme, startDate, endDate, active)
Milestone       (session, name, dueDate, weightage, sequenceNo)
        -- Programmes differ ONLY here. Same code, different rows.
        -- BTECH_MTECH_INTEGRATED was added with no logic change at all:
        -- one enum value plus V2 widening the column. That is section 6 working.

Topic      (student, title, abstractText, keywords, proposedSupervisor, status)
Allocation (student, supervisor, session, status, allocatedOn, allocatedBy)
        -- unique (student, session). Supervisor capacity enforced in service.

Submission        (allocation, milestone, currentVersionNo, status, lateFlag)
SubmissionVersion (submission, versionNo, storagePath, sha256, sizeBytes, submittedAt)
        -- immutable, append-only. Never overwrite.

ReviewComment   (submissionVersion, reviewer, pageNo, body, resolved, createdAt)
RubricCriterion (session, name, maxMarks, weightage)
Evaluation      (submission, examiner, scores JSONB, total, remarks, submittedAt)
VivaSchedule    (allocation, scheduledAt, venue, status)
PanelMember     (vivaSchedule, user, role)
Notification    (recipient, type, payload, readAt, createdAt)
AuditLog        (actor, action, entityType, entityId, oldValue, newValue, at)
```

**Why `Submission` and `SubmissionVersion` are separate.** `Submission` is the logical slot
("Interim Report for Ravi"). `SubmissionVersion` is each physical upload. That split is what
makes "guide always sees the latest, history stays intact" work. Best single thing in this
model to defend in an interview.

### State machines

Transitions declared once as `Map<State, Set<State>>`, validated in the service. Illegal moves
throw `InvalidStateTransitionException` → 409 page. Not scattered `if` statements.

```
Topic:       DRAFT -> PROPOSED -> APPROVED
                               -> CHANGES_REQUESTED -> PROPOSED
                               -> REJECTED

Allocation:  REQUESTED -> ACCEPTED     (capacity permitting)
                       -> DECLINED     (falls back to coordinator)
             COORDINATOR_ASSIGNED      (override, bypasses supervisor accept)

Submission:  DRAFT -> SUBMITTED -> UNDER_REVIEW -> APPROVED
                                                -> REVISION_REQUESTED -> SUBMITTED (new version)
                                                -> REJECTED
```

---

## 6. Designed for change

The flow will keep moving. Seven mechanisms so that costs an edit, not a rewrite.

1. **Workflow is data.** Milestones are rows keyed to `(AcademicSession, Programme)`, not an
   enum. Inserting "Pre-submission Seminar" is an INSERT plus a `sequenceNo` renumber. Also how
   every programme shares one codebase — adding the five-year integrated B.Tech+M.Tech degree
   cost one enum value and one `ALTER TABLE`, with no change to any logic.
2. **State machines are declarative.** One transition map per aggregate. A new legal path is
   one line. A flow change can never silently corrupt data.
3. **Rubric is configurable.** `RubricCriterion` rows with weights, scoped to a session.
   `Evaluation.scores` is JSONB keyed by criterion id — adding a criterion needs no migration.
4. **Additive migrations only.** Flyway `V1`, `V2`… An applied migration is *never* edited;
   changes go in a new file. Schema evolves forward and rebuilds from zero on demand.

   > This one has teeth. Flyway stores a checksum of each applied file — **comments
   > included** — in `flyway_schema_history`. Editing an applied migration makes the next
   > startup fail with `Migration checksum mismatch`, before any application code runs.
   > Locally the fix is `DELETE FROM flyway_schema_history WHERE version = 'N';` and let it
   > re-apply. Once a migration has run somewhere you cannot reset, there is no clean fix —
   > which is the whole reason corrections go forward instead of backward.
5. **Interface seams at every external dependency.** `StorageService`, `NotificationSender`,
   `SimilarityProvider`, `AiAdvisor`. One impl today, another later, callers untouched.
6. **Domain events for side effects.** `TopicApprovedEvent`, `SubmissionUploadedEvent`. Audit,
   notification, and vector indexing are `@EventListener`s. New side effect = new listener,
   never an edit to `TopicService`.
7. **DTOs isolate UI from entities.** Templates bind to form/view objects, never JPA entities.
   Entity rename does not ripple into HTML; UI change does not force an entity change. This is
   what lets the two of us work in parallel.

**Change protocol.** Flow changes mid-build → update this file → adjust the view contract →
then write code. Doc before code, or the controller/template contract
drifts and we both lose a day.

---

## 7. Security model

Role alone is insufficient — a `SUPERVISOR` must not read a submission from a student they do
not supervise. Two layers:

1. **URL rules** in `SecurityConfig` — `/student/**` requires `ROLE_STUDENT`, etc.
2. **Ownership checks** via a named bean in `@PreAuthorize`:

```java
@PreAuthorize("@authz.supervises(#studentId, authentication)")
@PreAuthorize("@authz.ownsSubmission(#submissionId, authentication)")
```

Passwords BCrypt. CSRF on — Thymeleaf injects the token into `th:action` forms automatically.
File downloads go through a controller that re-checks ownership; `uploads/` is **never** exposed
as a static resource directory.

---

## 8. Local setup

```sql
-- 1. Create the database (once, as the postgres superuser).
--    psql is not on PATH by default -- use pgAdmin, or add
--    C:\Program Files\PostgreSQL\18\bin to PATH.
CREATE DATABASE dms;
```

```properties
# 2. Supply the password. Copy the example file, then fill it in.
#    application-local.properties is gitignored: no credential enters git history.
#      cp application-local.properties.example application-local.properties
spring.datasource.password=<your postgres password>
```

```powershell
# 3. Run. Flyway applies V1 and V2, then DataSeeder creates the demo accounts.
.\mvnw.cmd spring-boot:run

# 4. Open
http://localhost:8080
```

### Demo accounts

Seeded on first startup only — `DataSeeder` no-ops when the users table is non-empty.
**Demo credentials, never for a deployment.**

| Email | Password | Roles |
|---|---|---|
| `admin@college.edu` | `admin123` | ADMIN |
| `coordinator@college.edu` | `coord123` | COORDINATOR |
| `guide1@college.edu` | `guide123` | SUPERVISOR + REVIEWER (capacity 5) |
| `guide2@college.edu` | `guide123` | SUPERVISOR (capacity 3) |
| `student1@college.edu` | `student123` | STUDENT — B.Tech |
| `student2@college.edu` | `student123` | STUDENT — M.Tech |
| `student3@college.edu` | `student123` | STUDENT — B.Tech |
| `student4@gmail.com` | `student123` | STUDENT — integrated B.Tech+M.Tech |

`guide1` holding two roles is the case worth demonstrating: one account, two link groups in
the navbar, driven entirely by `sec:authorize`.

### Troubleshooting

| Symptom | Cause |
|---|---|
| `Non-resolvable parent POM ... 4.1.0.RELEASE` | Initializr writes its internal id. Parent version must be `4.1.0`. |
| `Migration checksum mismatch` | An applied migration was edited. See section 6, item 4. |
| `Cannot load driver class: org.postgresql.Driver` | DB `dms` not created, or credentials wrong |
| `FATAL: password authentication failed` | `application-local.properties` missing or wrong password |
| `Schema-validation: missing column ...` | Entity and migration disagree. `ddl-auto=validate` catching drift — read the named column. |
| `LazyInitializationException` at login | `User.roles` must be `EAGER`; `open-in-view` is false |
| Login always fails, no useful log | Password stored unhashed — `password_hash` must start `$2a$` |
| `hasRole('X')` never matches | Authority must be `ROLE_X`. That is what `Role.authority()` is for. |
| Lombok getters "not found" in IntelliJ | Settings → Build → Compiler → Annotation Processors → **Enable** |
| `illegal character: '\ufeff'` or nulls between chars | File saved as UTF-16 or UTF-8-BOM. Settings → Editor → File Encodings → UTF-8. |
| `spring-boot-starter-web` not found | Boot 4 renamed it to `spring-boot-starter-webmvc` |

---

## 9. View contract

This table is extended one phase ahead of the controllers that serve it, so attribute names are
settled before either the controller or the template is written.

| Route | Method | View | Model attributes | Form object |
|---|---|---|---|---|
| `/` | GET | `home` | — | — |
| `/login` | GET | `auth/login` | — | — |
| `/dashboard` | GET | redirect by role | — | — |
| `/student/dashboard` | GET | `student/dashboard` | `topic`, `allocation`, `milestones`, `pendingCount` | — |
| `/student/topic` | GET | `student/topic-form` | `topicForm`, `supervisors` | `TopicForm` |
| `/student/topic` | POST | redirect `/student/dashboard` | — | `TopicForm` |
| `/supervisor/topics` | GET | `supervisor/topic-approvals` | `pendingTopics` | — |
| `/supervisor/topics/{id}/decide` | POST | redirect | — | `TopicDecisionForm` |
| `/coordinator/allocate` | GET | `coordinator/allocate` | `unallocated`, `supervisorsWithLoad` | — |

Template tree:

```
templates/
├── layout/base.html, _navbar.html, _flash.html
├── home.html
├── auth/login.html
├── student/     dashboard, topic-form, milestones, submit, submission-detail, feedback
├── supervisor/  dashboard, my-students, topic-approvals, review, evaluate
├── coordinator/ dashboard, allocate, sessions, milestones, viva-schedule, reports
├── admin/       users, audit-log
├── archive/     search, thesis-detail
└── error/       403, 404, 409, 500
```

---

## 10. Spring AI design (Phase 7)

Added last, on a working system. One vector store, five features.

| Feature | How | Model |
|---|---|---|
| Archive semantic search | Embed approved theses into pgvector; search by meaning | embedding model |
| Topic novelty check | RAG: retrieve top-k similar theses, LLM reports overlap and gaps | `claude-opus-5` |
| Supervisor matching | Cosine similarity: topic embedding vs `researchInterests` | embedding model |
| Regulations Q&A | RAG over the department handbook PDF | `claude-sonnet-5` |
| Chapter summary for reviewer | 200-word summary + draft review checklist | `claude-sonnet-5` |

Cost per million tokens (in/out): `claude-opus-5` $5/$25 · `claude-sonnet-5` $3/$15 ·
`claude-haiku-4-5` $1/$5. Key from `ANTHROPIC_API_KEY` env var — **never committed**.

**Hard constraint, designed in from the start:** the LLM never assigns a final grade or an
approve/reject decision. Every AI output is advisory, rendered in a visually distinct panel
labelled *AI-generated — verify before acting*, and persisted with `aiGenerated = true`.
Similarity is reported honestly as *overlap with department archive*, never as web-wide
plagiarism detection.

---

## 11. Verification

Per phase: `.\mvnw.cmd spring-boot:run`, then walk the workflow that phase added end to end
in a browser before moving on.

`.\mvnw.cmd test` — service unit tests for every state machine (illegal transitions **must**
throw), `@DataJpaTest` for repository queries, `@WebMvcTest` + `spring-security-test` for authz
(assert a supervisor gets 403 on another supervisor's student).

Flyway: drop the schema and re-migrate from scratch to prove migrations are replayable.

**End-to-end acceptance** (Phase 8): student proposes topic → guide approves → coordinator
allocates → student uploads v1 → guide requests revision → student uploads v2 → guide approves
→ viva scheduled → examiners score → mark sheet generated → thesis appears in searchable archive.

**Definition of done:** that chain completes without touching the database by hand, and every
step leaves an `AuditLog` row.

---

## 12. Interview talking points

1. **Immutable version history** — why `Submission` and `SubmissionVersion` are separate tables.
2. **State machines as data** — transitions declared once, illegal moves rejected at the service layer.
3. **Ownership authorisation** — why `hasRole('SUPERVISOR')` is not enough, and how `@authz` fixes it.
4. **Designed for change** — section 6: workflow in rows not enums, additive migrations, event listeners.
5. **Bounded AI** — a model that advises and is labelled as such, never one that grades.
