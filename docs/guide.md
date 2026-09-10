# Dissertation Management System — Guide

Living design document. **Update this file before writing code that changes a decision here.**

---

## 0. Where this stands

Built, verified in a browser, and covered by tests:

| Phase | What it covers | State |
|---|---|---|
| 0 | Scaffold, landing page, error pages, design system | done |
| 1 | Auth, roles, dashboards | done |
| 2 | Topic proposal and approval | done |
| 3 | Academic session, milestones, guide allocation, coordinator override | done |
| 4 | Milestone submissions with immutable version history, audit trail | done |
| 5 | Review comments pinned to a version | done |
| 6 | Rubric, weighted evaluation, viva scheduling, mark sheet | done |
| 7 | Spring AI features | **not started** -- see the note below |
| 8 | End-to-end acceptance pass | partly: the chain below runs, notifications do not exist |

`.\mvnw.cmd test` — 115 tests, green. Flyway at V9.

**Phase 7 is blocked on the environment, not on the code.** The vector features need the
pgvector extension, and `SELECT * FROM pg_available_extensions WHERE name = 'vector'` returns
nothing on the PostgreSQL 18 instance this was built against, so the extension is not
installed. They also need an `ANTHROPIC_API_KEY` and spend real money per call. Both are
decisions for whoever runs the demo rather than things to decide silently in code. The seam is
already in place: `StorageService` demonstrates the interface-per-external-dependency pattern
the AI services would follow, so adding them is additive.

**Known limitation.** `AllocationStatus.COORDINATOR_ASSIGNED` is terminal, so a coordinator who
places a student with the wrong guide cannot undo it from the UI, and the partial unique index
then blocks a second live allocation. The fix is one line — allow `WITHDRAWN` from `ACCEPTED`
and `COORDINATOR_ASSIGNED`, then add a revoke action — but it reverses a decision that
`AllocationStatusTest` deliberately pins ("only REQUESTED may be non-terminal"), so it is left
as a decision to take rather than one made quietly.


---

## 1. Why this exists

M.Tech dissertation work is run manually: topics by email, guide allocation in a
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

Built so far (phases 1 to 6):

```
com.dms
├── DissertationManagementSystemApplication.java
├── audit/           AuditLog, DomainEvent, DomainEvents, AuditLogListener,
│                    AuditLogRepository, AuditLogController
├── allocation/      Allocation, AllocationStatus, AllocationService, AllocationBoard,
│                    capacity rules, student / supervisor / coordinator controllers
├── common/          exceptions, GlobalExceptionHandler
├── evaluation/      RubricCriterion, Evaluation, EvaluationService, MarkSheet,
│                    SupervisorEvaluationController, StudentResultController
├── review/          ReviewComment, ReviewService, ReviewCommentController
├── security/        SecurityConfig, CustomUserDetailsService, AuthzService
├── session/         AcademicSession, Milestone
├── storage/         StorageService, LocalDiskStorageService, StoredFile
├── submission/      Submission, SubmissionVersion, SubmissionStatus, SubmissionService,
│                    student / supervisor / download controllers
├── topic/           Topic, TopicStatus, TopicService, TopicForm, controllers
├── user/            User, Role, Programme, StudentProfile, SupervisorProfile,
│                    repositories, DataSeeder, AdminUserController
├── viva/            VivaSchedule, VivaStatus, VivaService, CoordinatorVivaController
└── web/             HomeController, DashboardController, DashboardService, Dashboards
```

Still planned:

```
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
   cost one enum value and one `ALTER TABLE`, and dropping standalone B.Tech later cost one
   enum value and one data migration, with no change to any logic.
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
| `student1@college.edu` | `student123` | STUDENT — M.Tech |
| `student2@college.edu` | `student123` | STUDENT — M.Tech |
| `student3@college.edu` | `student123` | STUDENT — M.Tech |
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

Every route below is built and reachable. `board` is used as the model attribute name
throughout: each page gets exactly one assembled view record rather than a scatter of
loose attributes, which is what keeps a lazy entity from ever reaching a template.

| Route | Method | View | Model attributes | Form object |
|---|---|---|---|---|
| `/` | GET | `home` | — | — |
| `/login` | GET | `auth/login` | — | — |
| `/dashboard` | GET | redirect by role | — | — |
| `/student/dashboard` | GET | `student/dashboard` | `board` | — |
| `/student/topic` | GET | `student/topic/view` | `topic`, `hasTopic`, `canEdit` | — |
| `/student/topic/new` | GET | `student/topic/form` | `form`, `supervisors`, `mode` | `TopicForm` |
| `/student/topic/submit` | POST | redirect `/student/topic` | — | `TopicForm` |
| `/student/guide` | GET | `student/guide` | `allocation`, `history`, `seatsTaken` | `AllocationRequestForm` |
| `/student/guide/request` | POST | redirect | — | `AllocationRequestForm` |
| `/student/submissions` | GET | `student/submissions` | `board` | `SubmissionUploadForm` |
| `/student/submissions/{milestoneId}/upload` | POST | redirect | — | `SubmissionUploadForm` |
| `/student/submissions/{id}` | GET | `student/submission` | `detail`, `comments` | — |
| `/student/result` | GET | `student/result` | `result`, `viva` | — |
| `/supervisor/dashboard` | GET | `supervisor/dashboard` | `board` | — |
| `/supervisor/topics` | GET | `supervisor/topics` | `pending`, `decided` | `TopicDecisionForm` |
| `/supervisor/topics/{id}/decide` | POST | redirect | — | `TopicDecisionForm` |
| `/supervisor/requests` | GET | `supervisor/requests` | `pending`, `decided` | `AllocationDecisionForm` |
| `/supervisor/submissions` | GET | `supervisor/submissions` | `queue` | — |
| `/supervisor/submissions/{id}` | GET | `supervisor/submission` | `detail`, `comments` | `SubmissionDecisionForm` |
| `/supervisor/submissions/{id}/start` | POST | redirect | — | — |
| `/supervisor/submissions/{id}/decide` | POST | redirect | — | `SubmissionDecisionForm` |
| `/supervisor/evaluate` | GET | `supervisor/evaluate` | `students` | — |
| `/supervisor/evaluate/{id}` | GET | `supervisor/evaluate-form` | `allocation`, `rubric` | `EvaluationForm` |
| `/coordinator/dashboard` | GET | `coordinator/dashboard` | `board` | — |
| `/coordinator/allocate` | GET | `coordinator/allocate` | `board`, `programme` | `AllocationAssignForm` |
| `/coordinator/allocate/assign` | POST | redirect | — | `AllocationAssignForm` |
| `/coordinator/viva` | GET | `coordinator/viva` | `schedules`, `placed` | `VivaScheduleForm` |
| `/coordinator/marksheet` | GET | `coordinator/marksheet` | `sheet` | — |
| `/admin/dashboard` | GET | `admin/dashboard` | `board`, `recentTopics` | — |
| `/admin/users` | GET | `admin/users` | `users`, `students`, `supervisors` | — |
| `/admin/audit` | GET | `admin/audit` | `entries` | — |
| `/files/submissions/versions/{id}` | GET | file download | — | — |
| `/review/comments` | POST | redirect | — | `ReviewCommentForm` |

Two routes sit outside the role prefixes on purpose. A submission file and a comment
thread are both legitimately touched by the student, their guide, the coordinator and the
admin, so `/files/**` and `/review/**` authorise by ownership in the service rather than by
URL, and the uploads directory is never served as a static resource.

Template tree:

```
templates/
├── layout/      base, _navbar, _flash, _footer, _pagehero, _versions, _comments
├── home.html
├── auth/        login
├── student/     dashboard, topic/form, topic/view, guide, submissions, submission, result
├── supervisor/  dashboard, topics, requests, submissions, submission, evaluate, evaluate-form
├── coordinator/ dashboard, allocate, viva, marksheet
├── admin/       dashboard, users, audit
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
