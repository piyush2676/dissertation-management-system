# Demo walkthrough

A click-by-click run for a presentation or viva. Roughly **14 minutes** at a steady pace, or
20 with the optional detours.

The run uses **`student4@gmail.com`** — the integrated M.Tech student (semester 9, so the
pre-dissertation phase), who starts with no topic, no guide and no submissions, and whose address
is deliberately unconfirmed. Everything the run shows, `student4` creates on the way, so it works
on a freshly seeded database. Students 1 to 3 start blank too on a fresh database; they only hold
records if earlier runs (or `scripts/acceptance.sh`, which drives `student3`) left them there.

---

## Before you start

**1. Reset the demo student.** Do this even if you think it is clean — a half-finished run from
rehearsal is the most common way a demo derails.

```bash
bash scripts/demo-reset.sh
```

**2. Start the app.**

```powershell
.\mvnw.cmd spring-boot:run     # Windows
./mvnw spring-boot:run         # macOS / Linux
```

Wait for `Started DissertationManagementSystemApplication` before opening a browser.

**3. Prepare the browser.**

- **Hard-refresh once** (`Ctrl+Shift+R`, or `Cmd+Shift+R` on a Mac) so you are not showing a
  cached stylesheet.
- Open **two windows**, not two tabs — one for the student, one for the guide. Swapping windows
  is faster than logging in and out, and it makes the two-sided workflow obvious.
- Use a **private window for the second account**, or the two sessions fight over one cookie.
- **Turn off password autofill** in the demo profile. Chrome overwriting a typed address mid-demo
  looks like a bug in your system.
- Zoom to about **110%** for a projector.

**4. Have two PDFs ready.** Any small ones, but they must **differ** — the system refuses a
byte-identical re-upload, and that refusal is worth showing on purpose.

**5. Keep the application log visible** in a second terminal if you plan to demonstrate the
password reset. With no mail server configured the link is written there.

---

## The run

### 1 · Landing and identity — 1 min

Open <http://localhost:8080> signed out.

> "One system for the whole dissertation cycle, in the institute's own identity. Four roles, and
> the same code serves both the M.Tech and the five-year integrated programme — they differ only
> in data."

Point out the header: institute mark, the dissertation-cell contact, and the search box.

### 2 · Student proposes a topic — 2 min

Sign in as `student4@gmail.com` / `student123`.

**Stop on the red banner at the top** — *your email address is not confirmed*.

> "Notifications inside the system are one thing, but if the institute imported a typo'd address
> nobody finds out until it matters. Until an address is confirmed reachable, the system says so."

- **Dashboard** — the pipeline shows step 1 highlighted, because the system knows this student
  has not proposed anything.
- **My topic → Propose a topic.** Title, an abstract of a paragraph, research domain, objectives,
  at least one expected outcome (these are the Annexure-1 fields), pick `Dr A Sharma`, submit.
- Status reads `PROPOSED`.

### 3 · Guide approves it — 1 min

Switch to the guide window. Sign in as `guide1@college.edu` / `guide123`.

- **Dashboard** leads with what is waiting, so nobody hunts through three pages.
- Point at **Alerts** — the unread count went up the moment the student submitted.

> "Notifications are a listener on the same domain events the audit trail consumes. Adding them
> changed no existing service — that was the claim the architecture made, and this is it paying off."

- **Topic approvals** → open → **Approve**.

*(Optional, 30s: choose "Changes requested" first to show the send-back loop, then approve.)*

### 4 · Guide allocation with capacity — 2 min

Student window: **My guide** → request `Dr A Sharma` → appears as `REQUESTED`.

Guide window: **Guide requests** → **Accept**.

> "Capacity is enforced in the service, not the UI. The dropdown greys out a full guide as a
> courtesy, but the real check is `assertHasCapacity`, and a partial unique index stops a student
> holding two live allocations even if two requests race."

*(Optional: sign in as the coordinator and show **Allocate guides** — the override path, with the
warning badge on a student whose topic is not approved.)*

### 5 · Submission and the version history — 3 min

**This is the part to spend time on.** It is the strongest thing in the model.

Student window:

- **Submissions** — every milestone for the student's phase is listed, filed or not: the three
  reviews of the pre-dissertation phase, because `student4` is in semester 9. A semester-10
  student would see the final phase's three instead — the phase is derived from the semester,
  never stored.
- Upload your PDF against **Review 1 - Problem statement**, with a note.
- **Try uploading the same file again.** Refused.

> "Byte-identical re-uploads are rejected — they add nothing to the history and hide the real
> change from the guide."

Guide window: **Submissions** → open → **Mark as under review**.

> "Picking work up is a separate step from deciding on it, so the student can see it was read."

- Add a **review comment** with a page number.
- Record **Revision requested** with a note.

Student window: the comment is there → **mark it done** → upload the *second* PDF → **v2**.

**View history** — both versions, each with its own SHA-256 and size.

> "Submissions and versions are separate tables. The guide always reads the latest; nothing is
> ever overwritten. Every file carries a digest, so the bytes on disk can be proved to be the
> bytes submitted. Comments are pinned to the version they were written against, so a new upload
> starts a clean sheet and the old thread stays with the old file."

Guide window: review v2 and **Approve**.

### 6 · Search — 1 min

**This one rewards a little theatre.** In the guide window, search the header for **`Piyush`** —
`student4`, whom `guide1` accepted a few minutes ago. Results appear.

Now sign in as `guide2@college.edu` / `guide123` and search **`Piyush`** again.

> "Nothing. Search is scoped inside the query, not filtered afterwards — a guide literally cannot
> retrieve a row for another guide's student. Search is a classic way to leak records sideways,
> so it gets the same ownership rules the pages do."

### 6b · The logbook — 1 min

Student window: **Title bank** → the titles the faculty are offering → **Use this as my starting
point** → the proposal form opens prefilled, and every field is still editable.

Student window: **Logbook** → record a meeting (date, what the guide assigned, what got done).

Guide window: **Logbook** → the row is in the queue → **Return** it with a remark. Student window:
the row is back, marked RETURNED with the remark → **Correct and resubmit**. Guide window:
**Countersign**.

> "This is Annexure-4 of the guidelines — the progress report card — as data instead of a
> photocopied register. The student writes, the guide signs; the guide never edits the
> student's words. The moment it is signed the row is frozen and its SHA-256 is stored. Watch
> what that does to the certificate in a minute."

Student window: **Print progress report card** — the sealed digest sits in the signature column.

### 6c · Outcomes and the readiness ledger — 2 min

Student window: **Outcomes** → report a paper (kind, venue, indexing, status, DOI) → save.
Guide window: **Submissions** → open the latest version → record the similarity report
(e.g. 6.5% and 0%).

Coordinator window: **Outcomes** → the paper is in the queue → follow the reference →
**Verified**. Then **Readiness** → open the student's ledger.

> "This is the part I would defend hardest. Every requirement the guidelines put between a
> student and the viva is on one page — marks, each of the seven documents, the publication
> rule, the similarity thresholds, the logbook — and each line carries the fact that meets it,
> who verified that fact, when, and the digest it is pinned by. A portal that just greys out a
> button tells the student nothing. Only one of these is a gate: below half the internal marks
> the coordinator cannot book the viva at all, because that is the one prerequisite the
> guidelines state as a rule."

Coordinator window: **Viva** → try to book a student who is short on marks; the refusal names
the reason.

### 6d · Panels and the supervisor's sheet — 2 min

Coordinator window: **Panels** → try to appoint the student's own guide; the row is greyed out and
a direct post is refused with the reason. Appoint the *other* guide instead.

Sign in as that other guide in a third window: **Panel** → the student is there → **Score**.

Coordinator window: **Mark sheet** → the student now shows **2 examiners** and the average has
moved.

> "The guidelines keep the supervisor's own assessment inside the internal marks, so the guide
> still scores. What the system refuses is a guide on their own student's panel — the mark sheet
> is a mean, and one opinion must not count twice. The panel member holds REVIEWER, which is why
> this page lives under /review rather than /supervisor: it authorises by who you are to this
> student, not by a URL prefix."

Guide window: **Summary sheets** → file Annexure-6, pick [C], leave the queries box empty; it
refuses. Fill it in, pick [B], file.

Student window: **Readiness** → the recommendation line says it is filed and the thesis is
cleared, and nothing else.

> "That sheet is confidential. The coordinator reads the verdict, the wording and the viva
> questions; the student's own ledger will not print them, and there is no student route to the
> page at all."

### 6e · The change nobody could make before — 2 min

Student window: **Change request** → ask to change supervisor, give a real reason, submit. Point
out that the guide, the topic and every submission are untouched.

Coordinator window: **Change requests** → approve it. Then **Allocate guides** — the scholar is
back on the board and can be placed with somebody else.

> "Until this phase that was a dead end. A coordinator who placed a student with the wrong guide
> could not undo it, because the allocation states were terminal and a partial unique index
> blocked a second placement. The guidelines require a formal change process, so the invariant
> was reversed — but only one method in the whole system may make that move, and a student still
> cannot withdraw a placement they have. The test suite caught me the moment I widened the state
> machine without adding that guard."

Coordinator window: **Exports** → download Format 4 and open it. Then **CO attainment**.

> "Those are the two lists the department sends the Director Academics, generated from the
> register rather than retyped. And that is course outcome attainment — not stored anywhere,
> computed from the CO codes on the rubric and the marks the examiners entered, so it cannot
> disagree with the mark sheet."

### 7 · Evaluation, viva and the mark sheet — 2 min

Guide window: **Evaluate** → open the student → score each criterion → submit.

> "The rubric is rows scoped to the session, not an enum, and scores are JSONB keyed by criterion
> id — so the department can reweight the marking scheme between years without a code change or a
> migration. The total is weighted by each criterion's share."

Coordinator window (`coordinator@college.edu` / `coord123`):

- **Viva** → the **BTECH MTECH INTEGRATED** tab → schedule the defence.
- **Mark sheet** → the student shows an average and an outcome.

Student window: **Result** shows marks and viva.

### 8 · Provenance and the verifiable certificate — 2 min

**Finish on this.** It is the thing no other project in the room will have.

Coordinator window: **Mark sheet** → **Provenance** on any row.

> "This dissertation's entire life, rebuilt from the audit trail. Proposed, approved by whom
> and when, guide accepted, every version filed with the digest of its bytes, revision
> requested, every countersigned meeting with its digest, every verified outcome with the
> coordinator who verified it, marks, viva. None of it was typed by hand and none of it can be
> edited."

- **Issue certificate** → **Download PDF**.
- Open the PDF. Point at the digest and the QR code.
- **Scan the QR with your phone**, on the projector.

> "No account needed — an external examiner holding a printed certificate has none. The page
> recomputes the digest from the live register and compares it with the one sealed at issue."

**If you are feeling confident**, have a second terminal ready and run:

```sql
UPDATE evaluations SET total = 95.00 WHERE allocation_id = <id>;
```

Reload the verify page. It now reports **the register has changed since this was issued**.
Put the mark back and reload again: it passes.

The same happens if a countersigned logbook row is edited in the database, or if a new meeting
is countersigned after the certificate was issued — the record moved, so the seal says so, and
the coordinator reissues.

> "Tamper-evident, not tamper-proof. It cannot stop someone editing the database. It makes
> the edit impossible to hide."

### 9 · The audit trail — 1 min

Sign in as `admin@college.edu` / `admin123` → **Audit trail**.

> "Every state change you just watched is here — who did it, to which record, when, and what
> changed. Written by an event listener inside the same transaction as the change, so an action
> that rolls back leaves no entry, and no entry can describe something that never happened.
> Nothing on this page is editable."

---

## Optional detours

| Ask | Show |
|---|---|
| "What if someone forgets their password?" | **Forgotten your password?** on the sign-in page. Enter `student4@gmail.com`, then read the link out of the application log. Set a new password, then **paste the same link again** — refused. Single-use, one-hour expiry. |
| "Does it leak who has an account?" | Request a reset for `nobody@example.com`. Identical response, no token issued. "Telling a stranger which addresses exist would turn this page into a roll of the department." |
| "Is it secure?" | Sign in as `guide2` and open another guide's submission URL directly — **404, not 403**. A stranger learns nothing about whether the record exists. |
| "What about AI?" | **Overlap check** under the student menu. Without a key it says so plainly; with one it embeds the abstract, retrieves the nearest approved topics and writes an advisory note — inside a panel labelled *AI-generated*, which never approves or rejects anything. |
| "Can one person hold two roles?" | `guide1` is SUPERVISOR **and** REVIEWER — one account, two link groups, driven by `sec:authorize`. |
| "Accessibility?" | The **A+ / A / A−** controls on the right edge scale the whole interface and remember the choice. |
| "How do you know it works?" | `bash scripts/acceptance.sh 8081` — 20 assertions across the whole chain, live. |

---

## If something goes wrong

| Symptom | Fix |
|---|---|
| Old colour scheme | Hard-refresh, `Ctrl+Shift+R` |
| `Port 8080 was already in use` | Windows: `netstat -ano \| findstr :8080`, then `taskkill /F /PID <pid>`. macOS: `lsof -ti :8080`, then `kill <pid>` |
| Login always fails on a Mac after a reboot | PostgreSQL is not running: `pg_ctl -D ~/.local/pgdata -l ~/.local/pgdata/server.log start` |
| Login always fails | `application-local.properties` missing or wrong database password |
| Browser overwrites the address you typed | Autofill. Turn it off in the demo profile |
| A page shows 409 | The system refusing an illegal state transition. **Correct behaviour** — say so, it is a good moment |
| The demo student already has data | You skipped the reset. Run `scripts/demo-reset.sh` |
| AI pages say "switched off" | Expected without a Gemini key. Say it is optional and move on |
| Reset email never arrives | Expected — no mail server configured. The link is in the application log |

---

## The three sentences to land

If you only get to say three things, make them these.

1. **"Submissions and versions are separate tables, so the history cannot be rewritten."**
2. **"State machines are declared once as data and validated in the service, so an illegal
   transition is impossible rather than merely discouraged."**
3. **"Role checks get you to the page; ownership checks get you to the record — and every change
   leaves an audit row written in the same transaction."**

And if you get a fourth: **"Every dissertation can be reduced to one digest, printed on a
certificate, and checked by a stranger with a phone."**
