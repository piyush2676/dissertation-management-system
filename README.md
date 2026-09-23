# Dissertation Management System

A workflow system for M.Tech and integrated M.Tech dissertations — topic approval, guide
allocation, milestone submissions with version history, review, evaluation and viva scheduling,
with an audit trail behind every state change.

Built for the dissertation cell at **Noida Institute of Engineering and Technology**.

**Spring Boot 4.1 · Java 21 · PostgreSQL 18 · Thymeleaf · Flyway · Spring AI**

---

## The problem

Dissertation work is usually run by hand: topics by email, guide allocation in a spreadsheet,
deadlines over WhatsApp, reports as `report_final_FINAL_v2.docx` on a pen drive, feedback on
printed copies, marks totalled in Excel. Nobody can answer *"who approved this, and when"*.

This system answers that question for every step.

---

## What it does

| Role | Can |
|---|---|
| **Student** | Browse the faculty title bank, propose a topic, request a guide, file each milestone, keep the logbook, report research outcomes, read and resolve review comments, follow their readiness ledger, see the viva and the result |
| **Guide** | Offer titles, approve or send back a topic, accept or decline students within capacity, read submissions, comment on a specific page, countersign logbook entries, record the similarity report, score against the rubric, file the Annexure-6 summary sheet |
| **Coordinator** | Place a student with a guide when the request route stalls, appoint review panels, verify research outcomes, answer change requests, schedule vivas, read the cohort mark sheet, export the department lists and CO attainment |
| **Admin** | The account roll and the full audit trail |

A user may hold several roles. A professor who supervises *and* reviews is one account, and the
navigation renders both link groups from their authorities.

### The parts worth looking at

- **Immutable version history.** `Submission` is the logical slot; `SubmissionVersion` is each
  physical upload. A revision is a new row — nothing is ever overwritten, so the guide always
  reads the latest while the whole history stays on record with a SHA-256 per file.
- **State machines as data.** Five of them, each declared once as a `Map<State, Set<State>>` and
  validated in the service. An illegal move throws and lands on a 409 page; it never half-writes.
- **Ownership authorisation.** `hasRole('SUPERVISOR')` gets you to the page. It does not get you
  another guide's student — that check is `@authz.supervises(...)` against the allocation.
- **Audit and notifications as listeners.** Services publish domain events. The audit trail and
  the notification feed are two listeners on those events; neither service knows they exist.
- **Bounded AI.** Advisory only, always labelled, never deciding anything. See below.
- **Evidence over locked buttons.** The readiness ledger shows every requirement between a
  scholar and the viva with the fact that satisfies it, who verified that fact and when. One
  rule is a gate — half the internal marks, which the guidelines state as a rule — and the rest
  are reported for the coordinator to weigh.
- **Tamper-evident provenance.** A dissertation's whole history is rebuilt from the audit trail
  and sealed into a certificate; a public page recomputes the digest and says whether the
  register has moved since it was issued.

---

## Running it

### Prerequisites

- JDK 21
- PostgreSQL 18 with a database named `dms`
- No Maven needed — the wrapper is in the repo

```sql
CREATE DATABASE dms;
```

### Configure

```bash
cp application-local.properties.example application-local.properties
```

Put your PostgreSQL password in it. The file is gitignored — no credential enters git history.

### Run

```powershell
.\mvnw.cmd spring-boot:run
```

Flyway applies `V1`–`V19` on first boot and `DataSeeder` creates the demo accounts, an academic
session with milestones, and a default marking rubric. Then open <http://localhost:8080>.

### Demo accounts

Seeded only when the users table is empty. **Demo credentials — never for a real deployment.**

| Email | Password | Roles |
|---|---|---|
| `admin@college.edu` | `admin123` | ADMIN |
| `coordinator@college.edu` | `coord123` | COORDINATOR |
| `guide1@college.edu` | `guide123` | SUPERVISOR + REVIEWER (capacity 5) |
| `guide2@college.edu` | `guide123` | SUPERVISOR (capacity 3) |
| `student1@college.edu` | `student123` | STUDENT — M.Tech |
| `student4@gmail.com` | `student123` | STUDENT — integrated M.Tech |

`docs/demo.md` is a click-by-click walkthrough for a presentation.

---

## Testing

```powershell
.\mvnw.cmd test                      # 314 tests
bash scripts/acceptance.sh 8081      # 20 end-to-end assertions against a running app
```

Unit tests cover every state machine (illegal transitions **must** throw), the service rules,
and the URL role rules via `@WebMvcTest` with `spring-security-test`.

The acceptance script drives the whole chain over HTTP and asserts what lands in the database:

> topic proposed → guide approves → student requests a guide → guide accepts → version 1 filed →
> identical re-upload refused → review started → comment raised and resolved → revision requested
> → version 2 filed with version 1 intact → approved → scored against the rubric → viva scheduled
> → owner downloads the file, another guide gets 404 → mark sheet and result render → every step
> left an audit row

It resets only the student it runs as, so it is repeatable.

---

## AI features (optional)

Two features, both advisory, both off unless a key is configured:

- **Topic overlap check** — embeds the abstract, retrieves the nearest approved topics from the
  department archive, and asks a model to describe the overlap and what is genuinely new.
- **Guide matching** — ranks faculty by how close their stated research interests sit to the topic.

**Provider: Google AI Studio (Gemini), free tier.** Anthropic ships no embedding model and three
of the five originally planned features are embedding-driven, so a second provider was always
required; Gemini covers chat and embeddings on one key.

To enable, get a key at <https://aistudio.google.com/apikey> and uncomment three lines in
`application-local.properties`. Without it the pages say so plainly and everything else works.

**Hard constraint, enforced in the service and not the template:** the model never approves,
rejects or grades. Every output renders inside a panel labelled *AI-generated — verify before
acting*, and similarity is reported honestly as overlap with this department's archive. It is not
plagiarism detection and there is no web-wide corpus behind it.

> Vectors are stored as JSONB and scanned exactly, behind a `SimilarityProvider` interface,
> because pgvector is not installed on the target server. At department scale an exact scan is
> instant; swapping to pgvector later is one class and one migration.

---

## Architecture

```
Browser
   |  HTML over HTTP (form POST, no SPA)
Thymeleaf templates
   |  view name + model attributes
@Controller
   |  DTO in, view record out
@Service            <-- business rules, state transitions, @Transactional
   |  entities
@Repository (Spring Data JPA)
   |
PostgreSQL 18
   |
StorageService -> local disk (uploads/, never served statically)
```

Packages are organised **by feature, not by layer** — `com.dms.topic` holds its own controller,
service and repository, so a change to one flow has a small blast radius.

`spring.jpa.open-in-view` is **false**. Services return view records built inside the
transaction; a JPA entity with a lazy association never reaches a template.

---

## Documentation

| File | What |
|---|---|
| `docs/guide.md` | The living design document — decisions and the reasoning behind them |
| `docs/demo.md` | Click-by-click walkthrough for a presentation |
| `CLAUDE.md` | Operating notes: commands, conventions, and the traps that cost time |
| `docs/diagrams/` | PlantUML class, object and cross-cutting diagrams |
| `scripts/acceptance.sh` | The end-to-end acceptance run |

---

## Status

Phases 0–16 complete. Beyond the core workflow, the system now follows the institute's
dissertation guidelines: the two dissertation phases with their own review milestones and
marking schemes, the Annexure-1 proposal fields and thesis codes, the Annexure-4 logbook with
countersigning sealed into the provenance chain, research outcomes verified by the coordinator,
similarity reporting, the readiness ledger, review panels, the Annexure-6 summary sheet, the
section 4.11 change request, the faculty title bank, Format 4 and 5 exports, and CO attainment.

`docs/guide.md` section 13 maps each guideline mandate to the phase that carries it.

Known limitations, recorded rather than hidden:

- **The AI features need a key.** They are off by default and have not been exercised against a
  live Gemini key; the code path is covered by unit tests with a stubbed provider.
- **pgvector is not installed**, so embeddings are stored as JSONB and scanned exactly. Correct
  and instant at department scale; past a few thousand rows it is one class and one migration.
- **Three of the five planned AI features were cut** — regulations Q&A, chapter summary and a
  standalone archive search page. Retrieval itself ships inside the overlap check.
- **The viva panel is still free text** on the schedule, even though review panels are now their
  own table. The two are not linked yet.
- **No self-registration**, so no OTP. Accounts come from institute records, and there is an
  importer for the department's own allocation list.
- **Admin user management is read-only.** The roll and the audit trail, not CRUD.

---

## Licence and attribution

Coursework project. The institute name and colours are used for a departmental deployment;
the logo asset is not bundled — a wordmark stands in until the institute supplies one.
