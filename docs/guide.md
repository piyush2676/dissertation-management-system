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
| 7 | Topic overlap check, guide matching (Gemini) | done — needs a key to run |
| 8 | End-to-end acceptance pass | done — `scripts/acceptance.sh`, 20 assertions |
| 9 | In-app notifications | done |
| 10 | Email confirmation, password reset | done — mail optional |
| 11 | Verifiable provenance: timeline, certificate, public verify | done |
| 12 | Guideline alignment: dissertation phases, review milestones, marks-based rubric with CO/PO map, Annexure-1 fields, thesis code, co-supervisor | done |
| 13 | Logbook (Annexure-4) with guide countersign sealed into the provenance chain | done |
| 14 | Outcomes registry, plagiarism fields, deliverable checklist, readiness ledger, 50% viva gate | done |
| 15 | Review panels (guide excluded), panel scoring, Annexure-6 recommendation | done |
| 16 | Supervisor/title change request, title bank, Format 4/5 exports, CO attainment | done |

`.\mvnw.cmd test` — 310 tests, green. Flyway at V18. `scripts/acceptance.sh` — 20 assertions.
Phases 12 to 16 are complete: every mandate in section 13's table is carried.

**Phases 12–16 follow one source document:** `docs/m.tech_m.tech int._dissertation_guidelines_v3.md`,
the institute's dissertation guidelines for M.Tech / M.Tech Int. from 2025-26. Section 13 below
maps each guideline mandate to the phase that carries it, and records what stays deliberately
different from the reference portal the guidelines are usually paired with.

**Phase 7 changed provider, for a reason worth recording.** Section 10 below planned Anthropic
plus an unnamed embedding model. Anthropic ships no embedding model, and three of the five
features are embedding-driven, so that design always needed a second provider. Google AI Studio
covers chat and embeddings on one free-tier key, so it is the provider now.

**The AI features are off unless a key is configured**, and the rest of the system does not care.
That is not a guard in application code: the Google auto-configuration builds its client eagerly
and throws at startup with no key, before anything of ours runs. Two model switches plus two
auto-configuration exclusions in `application.properties` are what keep a key-less checkout
booting. `application-local.properties.example` has the three lines that turn it on.

**pgvector is still not installed and cannot be built on that machine** — the Visual Studio
BuildTools install there is headers-only, with no compiler, and the PostgreSQL directory needs
elevation. Vectors are stored as JSONB and scanned exactly by `CosineSimilarityProvider`, behind
the `SimilarityProvider` interface. At department scale an exact scan beats an index and is
exactly right rather than nearly right; past a few thousand rows, that one class is what pgvector
replaces, plus a migration copying the arrays into a `vector(768)` column.

**The long-standing limitation is closed.** `AllocationStatus.COORDINATOR_ASSIGNED` used to be
terminal, so a coordinator who placed a student with the wrong guide could not undo it and the
partial unique index then blocked a second live allocation. Phase 16 reversed that invariant, and
the one alongside it on `Topic.APPROVED`, behind guideline §4.11's written change request: the
state machines allow the moves, `ChangeRequestService.approve` is the only caller that may make
them, and `AllocationService.withdraw` gained an explicit guard so a student still cannot pull a
placement they have. The two state tests were rewritten to state the new rule; the old ones were
right about why it mattered.


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
Milestone       (session, phase, name, dueDate, weightage, sequenceNo)
        -- Programmes differ ONLY here. Same code, different rows.
        -- BTECH_MTECH_INTEGRATED was added with no logic change at all:
        -- one enum value plus V2 widening the column. That is section 6 working.
        -- phase: PRE | FINAL. Unique (session, phase, sequenceNo). Seeded as the
        -- three reviews per semester the guidelines prescribe (§4.12, §5.6).

DissertationPhase  PRE (3rd / 9th semester)  |  FINAL (4th / 10th semester)
        -- Derived from StudentProfile.semester, never stored on the student and
        -- never a second session. One academic year holds both phases for two
        -- cohorts, so the session stays one row and the phase picks which
        -- milestone and rubric rows a student sees.

Topic      (student, title, abstractText, keywords, researchDomain, objectives,
            sdgAlignment, expectedOutcomes, proposedSupervisor, status, thesisCode)
        -- researchDomain, objectives, sdgAlignment, expectedOutcomes are Annexure-1/2.
        -- expectedOutcomes: Set<ExpectedOutcome> stored as one CSV column, not a
        -- join table -- a lazy collection would reach the template.
        -- thesisCode: "MT26-001" / "MI26-001", assigned once on APPROVED, unique.
Allocation (student, supervisor, coSupervisor?, session, status, allocatedOn, allocatedBy)
        -- unique (student, session). Supervisor capacity enforced in service.
        -- coSupervisor is optional (guidelines §3.3 want one; seats are counted
        -- against the primary guide only) and must differ from supervisor.

Submission        (allocation, milestone, currentVersionNo, status, lateFlag)
SubmissionVersion (submission, versionNo, storagePath, sha256, sizeBytes, submittedAt)
        -- immutable, append-only. Never overwrite.

ReviewComment   (submissionVersion, reviewer, pageNo, body, resolved, createdAt)
RubricCriterion (session, phase, name, maxMarks, weightage, coCode?, poMapping?)
        -- Since phase 12 a row's weightage IS its marks (Format 6 sums to 100 for
        -- PRE, Format 15 to 200 for FINAL), so a total reads as marks out of the
        -- phase maximum. coCode / poMapping carry the outcome mapping the
        -- guidelines print beside every row; GradeBand (S/A/B/C at 81/61/41) is
        -- a function of percentage, not a column.
Evaluation      (submission, examiner, scores JSONB, total, remarks, submittedAt)
VivaSchedule    (allocation, scheduledAt, venue, status)
PanelMember     (vivaSchedule, user, role)
Notification    (recipient, type, payload, readAt, createdAt)
LogbookEntry    (allocation, meetingNo, meetingAt, workAssigned, workCompleted, challenges?,
                 status, supervisorRemarks?, signedBy?, signedAt?, entryDigest?)
Outcome         (allocation, kind, title, venue?, indexing, status, reference?, outcomeDate?,
                 notes?, verifiedBy?, verifiedAt?, verificationNote?)
        -- §4.4 / §7.2 / Annexure-6(d,e): papers, patents, products. The student
        -- reports; the coordinator verifies against the evidence (DOI, letter,
        -- filing number). Any edit after verification clears it. status is a
        -- reported state, not a workflow -- there is no transition map.
PlagiarismCheck (submissionVersion 1:1, similarityPercent, aiPercent, tool?, note?,
                 checkedBy, checkedAt)
PanelMember     (allocation, member, addedBy, addedAt)
        -- §2.2.1's review panel, per student. unique (allocation, member). The
        -- student's own guide and co-supervisor cannot be members: they already
        -- score as the guide, and appointing them twice would double-weight one
        -- opinion in an average. Members hold REVIEWER or SUPERVISOR.
Recommendation  (allocation 1:1, verdict, organisation?, technicalContent?, strengths?,
                 queries?, vivaQuestions?, submittedBy, submittedAt, updatedAt)
ChangeRequest   (allocation, kind, reason, preferredSupervisor?, proposedTitle?, status,
                 decisionNote?, requestedBy, requestedAt, decidedBy?, decidedAt?)
        -- §4.11. kind: SUPERVISOR | TITLE. status: PENDING -> APPROVED | REJECTED.
        -- At most one PENDING per allocation (partial unique index). Approving is
        -- what makes the two reversals below legal, and only through here.
BankedTitle     (supervisor, title, abstractText, domain, expectedOutcome, complexity,
                 status, createdAt, updatedAt)
        -- §4.3: each guide proposes at least three titles; §4.6: a scholar may pick
        -- one instead of inventing their own. status: OPEN | WITHDRAWN.
        -- Annexure-6, the supervisor's summary sheet. verdict: ACCEPTABLE |
        -- MINOR_REVISIONS | MAJOR_REVISIONS | REJECTED. Marked confidential on
        -- the form, so the student never sees it; the coordinator does.
        -- §8.3: similarity under 10%, AI-generated 0%. Its own row so the
        -- version stays append-only. The guide records it against one version.
Milestone.deliverable?  SYNOPSIS | LITERATURE_SURVEY | SYSTEM_DESIGN | TECHNICAL_REPORT | FINAL_THESIS
        -- which of §2.2.3's documents a review slot collects. Papers 1 and 2
        -- come from Outcome, not from an upload.
        -- Annexure-4, one row per guide meeting. Student writes, guide countersigns.
        -- unique (allocation, meetingNo). On SIGNED the row is frozen and its digest
        -- (SHA-256 over the fields above) is stored; the certificate's facts list
        -- every signed digest, so editing a signed row breaks the certificate.
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
                       -> WITHDRAWN    (student pulls the request)
             ACCEPTED, COORDINATOR_ASSIGNED -> WITHDRAWN
                       -- phase 16 only, and only through an approved supervisor
                          change request. Nothing else may call it.
             COORDINATOR_ASSIGNED      (override, bypasses supervisor accept)

Topic:       APPROVED -> CHANGES_REQUESTED
                       -- phase 16 only, and only through an approved title change
                          request. The thesis code survives; the round number goes up.

ChangeRequest: PENDING -> APPROVED
                       -> REJECTED

Submission:  DRAFT -> SUBMITTED -> UNDER_REVIEW -> APPROVED
                                                -> REVISION_REQUESTED -> SUBMITTED (new version)
                                                -> REJECTED

Logbook:     PENDING -> SIGNED                  (terminal; the row is now sealed)
                     -> RETURNED -> PENDING     (student corrects and re-submits)
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
| `/student/topic/new` | GET | `student/topic/form` | `form`, `supervisors`, `outcomes`, `mode` | `TopicForm` (+ researchDomain, objectives, sdgAlignment, expectedOutcomes) |
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
| `/coordinator/allocate/assign` | POST | redirect | — | `AllocationAssignForm` (studentId, supervisorId, coSupervisorId?) |
| `/coordinator/viva` | GET | `coordinator/viva` | `schedules`, `placed` | `VivaScheduleForm` |
| `/coordinator/marksheet` | GET | `coordinator/marksheet` | `sheet` (rubric rows carry phase, coCode, poMapping; rows carry percent and band) | — |
| `/admin/dashboard` | GET | `admin/dashboard` | `board`, `recentTopics` | — |
| `/admin/users` | GET | `admin/users` | `users`, `students`, `supervisors` | — |
| `/admin/audit` | GET | `admin/audit` | `entries` | — |
| `/files/submissions/versions/{id}` | GET | file download | — | — |
| `/review/comments` | POST | redirect | — | `ReviewCommentForm` |
| `/student/logbook` | GET | `student/logbook` | `board`, `form`, `mode` | `LogbookEntryForm` |
| `/student/logbook` | POST | redirect `/student/logbook` | — | `LogbookEntryForm` |
| `/student/logbook/{id}/edit` | GET | `student/logbook` | `board`, `form`, `mode=edit` | `LogbookEntryForm` |
| `/student/logbook/{id}` | POST | redirect | — | `LogbookEntryForm` |
| `/supervisor/logbook` | GET | `supervisor/logbook` | `queue`, `students`, `form` | `LogbookSignForm` |
| `/supervisor/logbook/{id}/sign` | POST | redirect `/supervisor/logbook` | — | `LogbookSignForm` |
| `/supervisor/logbook/student/{allocationId}` | GET | `supervisor/logbook-student` | `board` | — |
| `/student/outcomes` | GET | `student/outcomes` | `board`, `form`, `mode`, `kinds`, `indexings`, `statuses` | `OutcomeForm` |
| `/student/outcomes` | POST | redirect | — | `OutcomeForm` |
| `/student/outcomes/{id}/edit` | GET | `student/outcomes` | as above, `mode=edit` | `OutcomeForm` |
| `/student/outcomes/{id}` | POST | redirect | — | `OutcomeForm` |
| `/coordinator/outcomes` | GET | `coordinator/outcomes` | `queue`, `form` | `OutcomeVerifyForm` |
| `/coordinator/outcomes/{id}/verify` | POST | redirect | — | `OutcomeVerifyForm` |
| `/supervisor/submissions/{id}/versions/{versionId}/plagiarism` | POST | redirect to the submission | — | `PlagiarismCheckForm` |
| `/student/readiness` | GET | `student/readiness` | `ledger` | — |
| `/coordinator/readiness` | GET | `coordinator/readiness` | `rows`, `programme` | — |
| `/coordinator/readiness/{allocationId}` | GET | `coordinator/readiness-detail` | `ledger` | — |
| `/coordinator/panels` | GET | `coordinator/panels` | `board`, `programme`, `form` | `PanelMemberForm` |
| `/coordinator/panels/{allocationId}/add` | POST | redirect | — | `PanelMemberForm` |
| `/coordinator/panels/{allocationId}/remove/{memberId}` | POST | redirect | — | — |
| `/review/panel` | GET | `review/panel` | `assignments` | — |
| `/review/panel/{allocationId}` | GET | `review/panel-score` | `allocation`, `rubric`, `form` | `EvaluationForm` |
| `/review/panel/{allocationId}` | POST | redirect `/review/panel` | — | `EvaluationForm` |
| `/supervisor/recommendation` | GET | `supervisor/recommendations` | `students` | — |
| `/supervisor/recommendation/{allocationId}` | GET | `supervisor/recommendation-form` | `allocation`, `form`, `verdicts` | `RecommendationForm` |
| `/supervisor/recommendation/{allocationId}` | POST | redirect | — | `RecommendationForm` |
| `/coordinator/recommendation/{allocationId}` | GET | `coordinator/recommendation` | `view` | — |
| `/student/change-request` | GET | `student/change-request` | `board`, `form`, `supervisors` | `ChangeRequestForm` |
| `/student/change-request` | POST | redirect | — | `ChangeRequestForm` |
| `/coordinator/change-requests` | GET | `coordinator/change-requests` | `queue`, `form` | `ChangeRequestDecisionForm` |
| `/coordinator/change-requests/{id}/decide` | POST | redirect | — | `ChangeRequestDecisionForm` |
| `/supervisor/titles` | GET | `supervisor/titles` | `titles`, `form`, `mode` | `BankedTitleForm` |
| `/supervisor/titles` | POST | redirect | — | `BankedTitleForm` |
| `/supervisor/titles/{id}/edit` | GET | `supervisor/titles` | as above, `mode=edit` | `BankedTitleForm` |
| `/supervisor/titles/{id}` | POST | redirect | — | `BankedTitleForm` |
| `/supervisor/titles/{id}/withdraw` | POST | redirect | — | — |
| `/student/titles` | GET | `student/titles` | `titles`, `q` | — |
| `/coordinator/exports` | GET | `coordinator/exports` | `programme`, `programmes` | — |
| `/coordinator/exports/format4.csv` | GET | CSV download | — | — |
| `/coordinator/exports/format5.csv` | GET | CSV download | — | — |
| `/coordinator/attainment` | GET | `coordinator/attainment` | `report`, `programme` | — |

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
├── student/     dashboard, topic/form, topic/view, guide, submissions, submission, result, logbook, outcomes, readiness,
│              change-request, titles
├── supervisor/  dashboard, topics, requests, submissions, submission, evaluate, evaluate-form, logbook, logbook-student,
│              recommendations, recommendation-form, titles
├── review/      panel, panel-score
├── coordinator/ dashboard, allocate, viva, marksheet, outcomes, readiness, readiness-detail, panels, recommendation,
│              change-requests, exports, attainment
├── admin/       dashboard, users, audit
└── error/       403, 404, 409, 500
```

---

## 10. Spring AI design (Phase 7)

Added last, on a working system. Two features shipped of the five planned.

| Feature | How | State |
|---|---|---|
| Topic overlap check | Embed the abstract, retrieve top-k approved topics, model writes the note | **shipped** |
| Supervisor matching | Cosine: topic embedding vs `researchInterests` | **shipped** |
| Archive semantic search | Standalone search page over the same vectors | cut — retrieval ships inside the overlap check |
| Regulations Q&A | RAG over the department handbook PDF | cut |
| Chapter summary for reviewer | 200-word summary + draft review checklist | cut |

**Provider: Google AI Studio (Gemini), not Anthropic.** The original plan named Anthropic for
the narrative half and left the embedding model unnamed. Anthropic ships no embedding model, and
three of the five features are embedding-driven, so the plan always needed a second provider.
Gemini covers both on one key with a free tier — `gemini-2.5-flash` for the note,
`text-embedding-004` for the vectors.

Key from `GOOGLE_API_KEY`, or `spring.ai.google.genai.api-key` in the gitignored
`application-local.properties` — **never committed**. Off by default; see section 0.

Two economies worth naming. Every embedding row stores the SHA-256 of the text it was built
from, so unchanged text is never re-embedded and never re-billed. And each feature runs only when
the user asks: embedding on page load would burn quota rendering a page nobody was reading.

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

---

## 13. Guideline alignment (phases 12–16)

The institute's dissertation guidelines (`docs/m.tech_m.tech int._dissertation_guidelines_v3.md`)
describe the process the system must carry. This section is the map from mandate to phase, and
the reasoning for the model choices that were not obvious.

### What the guidelines mandate, and where it lands

| Guideline | Section | Phase |
|---|---|---|
| Two phases: Pre-Dissertation (3rd / 9th sem) and Final (4th / 10th sem) | §2.1, ch. 5–6 | 12 |
| Three review presentations per semester with fixed goals | §4.12, §5.6 | 12 |
| Annexure-1/2 fields: research domain, objectives, SDG, expected outcomes | Annexures 1–2 | 12 |
| Supervisor plus co-supervisor | §3.3 | 12 |
| Rubric per phase with S/A/B/C bands and PO mapping (Format 6, Format 15) | ch. 5, §6.7 | 12 |
| Thesis ID on every record (`Mint_002` in the forms) | Format 4, Annexures 3–5 | 12 |
| 50% of internal marks to sit the external viva | §7.1 | 12 shows it, 14 enforces it |
| Progress report card: meeting, work assigned, work done, remarks, sign | Annexure 4, §2.2.3 | 13 |
| Seven deliverables and the submission checklist | §2.2.3, Annexure 5 | 14 |
| Paper 1 / Paper 2 / patent; 1 SCI-Scopus journal or 2 Scopus-IEEE conferences | §4.4, §7.2 | 14 |
| Plagiarism under 10%, AI-generated content 0% | §8.3 | 14 |
| DCEC review panel that excludes the student's own guide | §2.2.1, §3.2 | 15 |
| Supervisor recommendation A/B/C/D and viva questions | Annexure 6 | 15 |
| Supervisor / title change through a formal request | §4.11 | 16 |
| Title bank: guides propose three titles each | §4.3, §4.6 | 16 |
| Format 4 / Format 5 lists for the Director Academics | §4.13, §5.6 | 16 |
| CO–PO attainment | §1.2, Annexure 6(b) | 16 |

### Phase 12 decisions

**The phase is derived, not stored.** A dissertation phase is a property of where a student is in
the programme — 3rd or 9th semester is PRE, 4th or 10th is FINAL — so `DissertationPhase.forSemester`
computes it from `StudentProfile.semester` and `Programme`. Storing it would create a second
source of truth to drift; splitting the academic session in two would double every session-scoped
query for no gain, since one academic year genuinely holds both phases for two cohorts. Any other
semester means the student is not in a dissertation phase yet and sees no milestones or rubric.

**Rubric weightage now means marks.** The guidelines print their rubrics as marks out of 100 (PRE)
and 200 (FINAL). Keeping weightage as a percentage would have forced a conversion in every view.
Setting `maxMarks == weightage` makes the weighted total the plain sum of marks while leaving the
weighting code untouched, so an examiner who scores 28 on a 35-mark row sees 28 in the total.
The pass line moves from the old 40-absolute convention to 50% of the phase maximum, which is the
guidelines' minimum internal requirement.

**CO and PO codes are stored verbatim from the guidelines**, including Format 15's `CO4` and `CO5`
that §1.2 never defines. Copying the document exactly and letting the department correct the rows
beats silently fixing an inconsistency in their own regulation.

**Thesis code on approval, not on allocation.** Annexures 3–5 print the Thesis ID on the proposal
and progress forms, which exist before a guide is placed. It is assigned when the topic reaches
APPROVED: programme prefix (`MT` / `MI`), the two-digit year, a three-digit sequence per prefix.
A unique index is the last line against a collision; the department is far too small to need a
sequence table.

**Expected outcomes as one CSV column.** Annexure-2's tick list is a small closed set. A join table
would be a lazy collection on `Topic`, and rule 1 of `CLAUDE.md` says that must never reach a
template. `Set<ExpectedOutcome>` through an `AttributeConverter` keeps it a plain column.

**Co-supervisor does not take a seat.** Guidelines count workload against the primary guide, and
the partial unique index and capacity check stay exactly as they were. The only rule added is that
the two cannot be the same person.

### Phase 13 decisions

**One row per meeting, not one per week.** Annexure-4 is a meeting register — meeting number,
date, work assigned, work done, remarks, signature — and §2.2.2's weekly update is the same
information on a cadence. The entry is the meeting; the cadence is a number on the dashboard
(days since the last signed entry), not a second table. A department that wants strict weeks
tightens a threshold, not the model.

**The student writes, the guide signs.** The guide never edits the student's text; they sign it
or return it with a remark, and a returned entry goes back to the student to correct. That is
the paper form's division of labour and it keeps authorship unambiguous when the row is sealed.

**Sealing is a digest on the row, then a line in the certificate.** On SIGNED the entry's fields
are hashed into `entryDigest` and the row is frozen by the state machine. `ProvenanceService`
adds a `logbook` fact — `meetingNo:digest` for every signed entry — but **only when there is at
least one**, so certificates issued before this phase, over dissertations that never had a
logbook, still verify. A certificate issued before a later entry is signed will read CHANGED,
which is the correct answer: the record moved after it was sealed.

**Hashing moved to `common.Digests`.** `ProvenanceService.digestOf` delegates to it so the
logbook can seal rows without the logbook package depending on provenance.

### Phase 14 decisions

**One hard gate, everything else is evidence.** The guidelines state exactly one prerequisite as
a rule: 50% of internal marks to sit the external viva (§7.1). That one is enforced —
`VivaService.schedule` refuses below it. The rest of what the readiness ledger checks — seven
deliverables, the publication requirement, the plagiarism thresholds, a countersigned logbook —
is shown with the fact that satisfies it, who verified it and when, and left for the coordinator
to weigh. A locked button hides *why*; a ledger shows it. That is the deliberate difference from
the reference portal, and it is also what the department actually does with a paper form.

**Deliverables are a property of the review slot.** §2.2.3 lists seven documents; three of the
five uploadable ones fall in the Pre-Dissertation reviews and two in the Final. `Milestone` gains
a nullable `deliverable`, the seeder sets it, and V16 backfills existing rows by name. A checklist
item is satisfied when its slot's submission is APPROVED — evidence: the version number and
SHA-256 that was approved. Research Paper 1 and 2 are not uploads; they are verified `Outcome`
rows, so the checklist reads them from there.

**The plagiarism check is its own row.** `SubmissionVersion` is append-only and the design
depends on that; a similarity percentage written onto it later would break the rule. A
`PlagiarismCheck` is one row per version, recorded by the guide, and the ledger reads the check
on the *latest* version of the final thesis. Under 10% similarity and 0% AI, as §8.3 requires;
the numbers are the department's to change in one place.

**The coordinator verifies outcomes, not the guide.** The guide is close to the work; the
coordinator's office is what Format 4 and the Director Academics list actually run through, and a
verification by a party who does not also mark the student is the stronger fact for a ledger. An
edit by the student after verification clears it, so a verified row always describes what was
seen. Verified outcomes join the certificate as a conditional `outcomes` fact, exactly as the
logbook does.

### Phase 15 decisions

**The guide still scores; they just cannot sit on their own student's panel.** §7.1 puts the
supervisor's assessment inside the internal marks, so removing the guide from scoring would
contradict the guidelines. What the conflict rule actually protects is the average: the mark
sheet already means "the mean of every examiner's total", so appointing a guide to their own
student's panel would let one person's opinion count twice. The service refuses the supervisor
and the co-supervisor, and the database refuses a duplicate member. That is a narrower rule than
the reference portal's "double-blind" claim and it is one this system can actually keep.

**Panel scoring lives under `/review/**`, not `/supervisor/**`.** A panel member holds REVIEWER,
which the role prefixes do not admit to the supervisor area, and inventing a second prefix would
mean two routes to one form. `/review/**` already exists for exactly this case — it authorises by
ownership in the service rather than by URL — so panel scoring joins it. This is also the first
job the REVIEWER role has had since phase 1; it was seeded and unused.

**One `Evaluation` row per examiner, unchanged.** Phase 6 keyed evaluations by
(allocation, examiner) and phase 12 left that alone, so a panel of two plus the guide is three
rows and the existing average, band and pass line need no change at all. Widening
`EvaluationService.score` from "the supervising guide" to "the supervising guide or a panel
member" is the whole of the scoring change — which is what that key was for.

**Annexure-6 is confidential, and the model says so.** The form is headed "(To be filled by
Supervisor)" and marked confidential, so the recommendation is readable by the supervisor who
wrote it and by the coordinator, and never by the student — there is no student route to it at
all, not a hidden one. The viva questions it carries are the reason: the guidelines let the
supervisor decide whether they reach the candidate beforehand.

### Phase 16 decisions

**Two invariants are reversed, and only one door opens either.** §4.11 requires a formal process
for changing supervisor or title, so `ACCEPTED`/`COORDINATOR_ASSIGNED -> WITHDRAWN` and
`Topic.APPROVED -> CHANGES_REQUESTED` both become legal — but `AllocationService.withdraw` still
refuses anything that is not `REQUESTED`, and the new transitions are reachable *only* from
`ChangeRequestService.approve`. The state machines say what is possible; the services say who may
do it, and here only one caller may. `AllocationStatusTest` and `TopicStatusTest` were rewritten
to state the new rule rather than deleted, because the old ones were right about why it mattered.

**A change request is data, not a status on the thing it changes.** Putting a
`CHANGE_REQUESTED` state on `Allocation` would have meant a student's pending paperwork
suspending their supervision, and §4.11 is explicit that the scholar keeps working while the
committee considers it. So the request is its own row with its own tiny state machine, and the
allocation is untouched until a decision lands. A partial unique index allows one pending request
per allocation; a student cannot queue three.

**The title bank is the guide's, and adopting one is still a proposal.** §4.3 asks each guide for
three titles; §4.6 lets a scholar take one *or* bring their own. A banked title therefore
prefills the Annexure-1 form and nothing more — it does not pre-approve anything, does not bind
the student to that guide, and the normal approval still runs. Withdrawing a title never touches
a topic already proposed from it.

**Attainment is computed, never stored.** Every rubric row carries a CO code (phase 12) and every
evaluation stores marks keyed by criterion id (phase 6), so CO attainment is a query over what is
already there. Storing it would create a number that silently goes stale the moment an examiner
rescores. The threshold — the share of students at or above 60% of a CO's marks — is the one
knob, and it is a constant in `AttainmentReport`, not a column.

### What stays different from the reference portal

The guidelines are usually paired with a portal whose public pages advertise a ten-step
lifecycle, a "double-blind" panel, a locked final-submission button and OTP self-registration.
This system keeps its own footing on purpose:

- **Evidence over gates.** Where that portal disables a button, this one shows a readiness ledger:
  which fact satisfied which rule, who verified it, when, and its digest. Phase 14.
- **Provenance carries the new records.** Logbook countersigns and outcome verifications become
  chain events; tampering with them breaks the certificate. Nothing in the reference portal is
  tamper-evident.
- **Outcome-based reporting.** CO/PO codes on rubric rows make CO attainment a query, which is
  what the guidelines' Annexure 6(b) and the accreditation paperwork actually ask for.
- **Accounts come from institute records.** No self-registration; that was cut in phase 1 for a
  reason that has not changed.
- **The conflict-of-interest rule is a service constraint**, named plainly. It is not branded.
