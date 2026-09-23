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
| Flyway | via `spring-boot-starter-flyway` | currently at **V19** |
| Thymeleaf | + `thymeleaf-extras-springsecurity6` | |
| Spring AI | 2.0.1 | Gemini via Google AI Studio; off unless a key is set |
| Build | Maven wrapper (`.\mvnw.cmd`) | no global Maven |

**Boot 4 renamed starters.** `spring-boot-starter-web` → `-webmvc`. `spring-boot-starter-test`
is split per module (`-webmvc-test`, `-data-jpa-test`, `-security-test`). Do not copy Boot 3.x
tutorials blindly.

---

## Current state (2026-09-23) — the guideline roadmap is complete

Phases 0–16 complete, 321 tests green, 176 commits, pushed to `origin/main`. Flyway at V19.
The end-to-end chain is scripted: `bash scripts/acceptance.sh 8081` — 20 assertions, all green.

**Phases 12–16 follow the institute guidelines** in
`docs/m.tech_m.tech int._dissertation_guidelines_v3.md`. `docs/guide.md` §13 maps each mandate to
a phase and records what stays deliberately different from the reference portal
(`niet-dms.vercel.app`): evidence ledgers over locked buttons, provenance-sealed records, CO/PO
reporting, no self-registration, no borrowed branding.

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
| 8 | End-to-end acceptance | done — scripted, 20/20 |
| 9 | In-app notifications | done |
| 10 | Email confirmation, password reset | done — mail optional |
| 11 | Verifiable provenance: timeline, certificate, public verify | done |
| 12 | Guideline alignment: dissertation phase, review milestones, marks-based rubric with bands + CO/PO, Annexure-1 fields, thesis code, co-supervisor | done |
| 13 | Logbook (Annexure-4): student records meetings, guide countersigns, signed rows digested and listed by the certificate | done |
| 14 | Outcomes registry, similarity checks, deliverable checklist, readiness ledger, 50% viva gate enforced | done |
| 15 | Review panels (guide off their own), panel scoring, Annexure-6 recommendation | done |
| 16 | Supervisor/title change request, title bank, Format 4/5 exports, CO attainment | done |

### Phase 12 — what changed underneath

- **`DissertationPhase` is derived, never stored.** `forSemester(programme, semester)`: M.Tech
  3→PRE, 4→FINAL; integrated 9→PRE, 10→FINAL; anything else → empty, and that student sees no
  milestones and no rubric. Milestones and rubric rows carry `phase`; the session does not.
- **Rubric `weightage` now equals `maxMarks`** (Format 6 sums to 100, Format 15 to 200). The
  weighted-total code is unchanged; the arithmetic just collapses to a sum. Pass is **50% of the
  phase maximum** (`MarkSheet.PASS_PERCENT`), not the old absolute 40. `GradeBand` S/A/B/C at
  81/61/41.
- **Thesis code** `MT26-001` / `MI26-001` on `topics.thesis_code`, assigned once at APPROVED,
  partial unique index. `TopicForm` now requires `researchDomain`, `objectives`, at least one
  `expectedOutcomes`; the acceptance script posts them.
- **Co-supervisor** on `allocations.co_supervisor_id`: optional, no seat, CHECK ≠ supervisor.
  Entity graphs that feed templates were widened with `coSupervisor`, `coSupervisor.user`.
- **V14 backfilled pre-existing milestone and rubric rows as FINAL**, and the seeder is guarded
  per session+phase, so an old DB gains the guideline PRE track on next start but keeps its
  legacy FINAL rows (5 generic criteria out of 10). A fresh DB gets Format 6 + Format 15. The
  local DB's two MTECH students at semester 8 (impossible; stale seed) were set to 4 by hand on
  2026-09-17.
- **V19 gave those legacy FINAL rows their CO codes** (CO1/CO2/CO3 — the ones §1.2 defines for
  the Final Dissertation, not Format 15's undefined CO4/CO5). Without it CO attainment grouped on
  a null column and read empty for the whole FINAL cohort. Guarded on `co_code IS NULL` and
  matched by name: a no-op after phase 12, and it cannot overwrite a departmental code. **If the
  seeder ever gains a column the rubric groups on, the guard means old rows need a migration —
  `seedRubrics` will not revisit them.**

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

### Open decision — taken 2026-09-17, shipped in phase 16

`AllocationStatus.COORDINATOR_ASSIGNED` and `ACCEPTED` are no longer terminal, and
`Topic.APPROVED -> CHANGES_REQUESTED` is now legal. **Both moves have exactly one caller:
`ChangeRequestService.approve`**, after the coordinator answers a written §4.11 request.

Two things keep that honest, and both must stay:
- `AllocationService.withdraw` has an **explicit `status != REQUESTED` guard**. Without it,
  widening the transition map silently let a student withdraw their own live placement —
  `AllocationServiceTest.withdrawAfterTheGuideAcceptedThrows` caught exactly that.
- `TopicService.decide` starts from `PROPOSED`, so it cannot reach the new topic transition.

If you widen a transition map again, go looking for the services that were relying on the old
invariant instead of checking explicitly.

### Cut from scope

OTP verification (there is no self-registration to verify — accounts come from institute
records), admin user CRUD (read-only roll instead), archive search UI, and three of the five
planned AI features (regulations Q&A, chapter summary, and the standalone archive search page —
retrieval itself ships inside the overlap check).

Two entries here have since changed and the note was wrong until 2026-09-23: **mail is wired**
(`account/SmtpMailer` on `JavaMailSender`, phase 10 — still optional, in-app notifications work
without it), and **the review panel did get its own table** in phase 15 (`panel_members`).

---

## What is left

Nothing on the guideline roadmap. These are the open ends, in the order they would bite:

1. **The imported cohort has no topics.** The department's sheet carries no thesis titles, so 58
   scholars have allocations and no proposals. If a titles sheet turns up, extend `CohortImporter`
   to create the topics with their real `MInt._` codes — `Topic.thesisCode` already takes them.
2. **pgvector.** Still not installable on this machine (headers-only BuildTools, elevation needed
   for `Program Files\PostgreSQL8\`). One class plus one migration when it is.
3. **The three cut AI features**, if they are ever wanted: regulations Q&A, chapter summary,
   standalone archive search.

Closed on 2026-09-23: **the viva's internal panel is now a join** on `panel_members`
(`VivaService.panelsFor` / `panelFor`); the free-text column survives only as
`externalExaminers`, still named `panel` in SQL so no migration was needed. **The landing page
has an AI card** ("Advice, not verdicts"), and the Admin card no longer claims account CRUD.
The acceptance script also scores 80% of each criterion's own `max_marks` now — the flat
`scores=8` it posted predated phase 12 and totalled 48 on a fresh DB, which the viva gate then
refused.

Also 2026-09-23, from a rehearsal of `docs/demo.md` on a freshly seeded database (macOS): the
walkthrough leaned on records only the Windows DB had — it searched for `Avika` as guide1's
student, but a fresh seed allocates nobody — and on pre-phase-12 milestones ("Synopsis", four vs
five). It now searches for `Piyush` (student4, whom the run itself places with guide1) and names
the three PRE reviews. The readiness page's *Summary sheet* button only renders once Annexure-6
is filed (it was a 404 before), and the footer's role links render only for that role or a
signed-out visitor. Both scripts now use `psql` from PATH before the Windows path.

2026-09-24, **the AI features ran live for the first time**, and four things broke that the
stubbed tests could not see: `gemini-2.5-flash` and `text-embedding-004` are closed to new keys
(now `gemini-3.6-flash` and `gemini-embedding-001` at 768 dims); the documented enable steps
crashed on start, because the embedding model needs the embedding *connection* bean that
`application.properties` excludes (the `.example` now narrows the exclusion, and the embedding
model's separate `embedding.api-key` is set too); the "switched off" panel always rendered,
because `th:replace` outranks `th:if` on one element (moved to a `th:block`); and Spring AI's
default of 10 retries at x5 backoff held the page open for many minutes (now 2 attempts). Also
added: `AiArchiveBackfill` indexes approved topics at start, `embedAndStore` re-embeds a row from
another model, and the prompt asks for plain text because the note renders verbatim.
**zsh trap for curl probes:** `"$M:generateContent"` is read as a history modifier on `$M` and
silently mangles the URL into an empty-bodied 404 — write `${M}`.

Deliberately not on this list: anything the guidelines mandate. Section 13 of `docs/guide.md`
maps every mandate to the phase that carries it, and all sixteen are done.

---

## Commands

```powershell
.\mvnw.cmd -o test              # full suite, needs the DB up (321 tests)
.\mvnw.cmd -o -q compile        # fast syntax check
.\mvnw.cmd -o spring-boot:run   # runs on 8080
```

`-o` (offline) is safe and much faster — dependencies are already in the local repo.

### Real cohort import

`CohortImporter` loads the department's own allocation list (M.Tech Int. 2022-27, 58 scholars,
42 faculty) when `dms.import.cohort-file` points at a CSV. Off unless that property is set, and
idempotent — a roll number already on record is skipped.

- **The data never enters git.** `/data/` and `*.xlsx` are gitignored, and the property lives in
  the gitignored `application-local.properties`. The importer is committed; the list is not.
- **No contact details are imported.** The source sheet carries institutional mail ids and mobile
  numbers; none are read. Sign-in addresses are generated — `<rollNo>@college.edu` for scholars,
  `firstname.lastname@college.edu` for faculty — so nothing in the database is a real address.
  Imported accounts share the password `niet123`, local demonstration only.
- **Faculty identity is by derived address**, and the derivation turns every non-letter into a
  gap first. The sheet writes one guide with a plain space and elsewhere with a non-breaking
  space; matching on the raw string split them into two accounts. `CohortImporterTest` pins it.
- The sheet has **no thesis titles** despite its filename, so no topics are created — scholars
  propose those in the app, which is the workflow anyway.
- Regenerate the CSV from a new workbook with the scratchpad scripts, or hand-write it: the
  header is `thesisId,studentName,rollNo,supervisor,coSupervisor,titleFormReceived`.

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
  A quoted `<<'EOF'` heredoc into `python -` still **collapses `\\` to `\`**, so a Python
  string ending in `\\` + newline becomes a line continuation; build backslashes with `chr(92)`.
- **Python's default write encoding here is cp1252.** A `§` or `—` written through
  `open(p,'w')` lands as a single non-UTF-8 byte and `javac` fails with "unmappable character".
  Pass `encoding='utf-8'` for any file that may hold one, or keep Java sources ASCII.
- **Thymeleaf fragment arguments are single-quoted.** An apostrophe inside a `hero('...')`
  literal ("student's") throws at render time, not compile time. Rephrase; do not escape.
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
├── notification/ Notification, NotificationService, NotificationListener, controller
├── allocation/   Allocation, AllocationStatus, AllocationService, AllocationBoard, controllers
├── common/       exceptions, GlobalExceptionHandler
├── evaluation/   RubricCriterion, Evaluation, EvaluationService, MarkSheet, controllers
├── review/       ReviewComment, ReviewService, ReviewCommentController
├── security/     SecurityConfig, CustomUserDetailsService, AuthzService
├── logbook/      LogbookEntry, LogbookEntryStatus, LogbookService, LogbookBoard, controllers
├── outcome/      Outcome, OutcomeKind/Indexing/Status, OutcomeService, OutcomeBoard, controllers
├── attainment/   AttainmentReport, AttainmentService, CoordinatorAttainmentController
├── change/       ChangeRequest, ChangeKind, ChangeRequestStatus, ChangeRequestService, controllers
├── export/       ExportService (Format 4/5 CSV), CoordinatorExportController
├── panel/        PanelMember, PanelService, PanelBoard, CoordinatorPanelController, PanelReviewController
├── titlebank/    BankedTitle, Complexity, TitleBankService, controllers
├── recommendation/ Recommendation, Verdict, RecommendationService, controllers
├── readiness/    ReadinessLedger, ReadinessService, controllers
├── session/      AcademicSession, Milestone, DissertationPhase, DeliverableType
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
   `SubmissionStatus`, `VivaStatus`, `LogbookEntryStatus` (+ the topic/allocation pairs above).
   `DissertationPhase` is an enum but not a state machine — nothing transitions; it is a pure
   function of semester.
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
- Logbook: **student** writes, **guide** signs or returns. The guide never edits the student's
  text. A SIGNED row is terminal and digested; `ProvenanceService.factsFor` adds the `logbook`
  fact **only when at least one row is signed**, so pre-phase-13 certificates still verify and a
  meeting signed after issue makes the certificate read CHANGED (the coordinator reissues).
- The canonical hash lives in `common.Digests`; `ProvenanceService.digestOf` delegates. Do not
  add a second SHA-256 helper.
- **Outcomes: student reports, COORDINATOR verifies** (not the guide — the party who confirms a
  paper exists should not be the one marking the student). Any student edit clears the
  verification. Only `verified && status.achieved()` counts toward a rule.
- **The readiness ledger enforces nothing.** `ReadinessService` is read-only; the single gate is
  `VivaService.schedule` asking `internalMarksMet` (§7.1, 50% of the phase maximum). Everything
  else is shown with its evidence for the coordinator to weigh. Do not turn a ledger line into a
  gate without saying so in `docs/guide.md` §13 first.
- `PlagiarismCheck` is its own table, one row per version. Never add a similarity column to
  `SubmissionVersion` — it is append-only and provenance depends on that.
- **A change request is data, not a status on the allocation.** §4.11 says the scholar keeps
  working while the committee considers it, so nothing moves until a decision lands. One pending
  request per allocation, held by a partial unique index.
- **A banked title is a prefill, never an approval.** Adopting one fills the Annexure-1 form and
  stops; the normal proposal and approval still run, and withdrawing a title never touches a
  topic already proposed from it.
- **Attainment is never stored.** `AttainmentService` reads rubric CO codes and examiners' marks
  each time. A stored attainment number goes stale the moment somebody rescores.
- **The guide still scores; they just cannot sit on their own student's panel.** §7.1 puts the
  supervisor's assessment inside the internal marks. The conflict rule exists because the mark
  sheet is a *mean* — one opinion must not count twice. `PanelService.add` refuses the supervisor
  and co-supervisor; removing a member never deletes their marks.
- **Panel scoring is under `/review/**`, not `/supervisor/**`** — a panel member may hold only
  REVIEWER. Same reason `/files/**` and `/review/**` already sit outside the role prefixes.
- **Annexure-6 is confidential and the model enforces it.** `RecommendationService` has no student
  route; `ReadinessService.Audience` decides what the ledger prints — the office sees the verdict,
  the student sees only that it is filed and whether the thesis is cleared. A ledger rule that
  reads a confidential source must take the audience.

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
| `README.md` | Repo front door: what it is, how to run it, known limitations |
| `docs/guide.md` | Living design doc — status, architecture, view contract, rationale |
| `docs/m.tech_m.tech int._dissertation_guidelines_v3.md` | Institute guidelines (OCR'd, 3166 lines) — source for phases 12–16; §-references in code point here. **Gitignored**: the repo is public and the document is the department's, carrying its letterhead and a sample scholar's details. Keep your own copy at that path; every §-reference in the code and in `docs/guide.md` cites it rather than quoting it, so nothing breaks without it. |
| `docs/demo.md` | Click-by-click presentation walkthrough (~10 min) |
| `scripts/demo-reset.sh` | Resets one student so the walkthrough is repeatable |
| `docs/phase1-contract.md` | Phase 1 auth contract (historical) |
| `docs/diagram-prompts.md`, `docs/diagrams/` | PPT diagram sources |
| `application-local.properties` | DB password, gitignored |
| `src/main/resources/db/migration/` | V1–V19 |
