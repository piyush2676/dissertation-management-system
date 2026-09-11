# Demo walkthrough

A click-by-click run for a presentation or viva. Roughly **10 minutes** at a steady pace, or
15 if you take the optional detours.

The run uses **`student4@gmail.com`** — the integrated M.Tech student, who starts with no topic,
no guide and no submissions. Students 1 to 3 already hold completed records, so keep them for
"here is what a finished file looks like" rather than driving them live.

---

## Before you start

**1. Reset the demo student.** Do this even if you think it is clean — a half-finished run from
rehearsal is the most common way a demo derails.

```bash
bash scripts/demo-reset.sh
```

**2. Start the app.**

```powershell
.\mvnw.cmd spring-boot:run
```

Wait for `Started DissertationManagementSystemApplication` before opening a browser.

**3. Prepare the browser.**

- **Hard-refresh once** (`Ctrl+Shift+R`) so you are not showing a cached stylesheet.
- Open **two windows**, not two tabs — one for the student, one for the guide. Swapping windows
  is faster than logging in and out, and it makes the two-sided workflow obvious.
- Use a **private window for the second account**, or the two sessions will fight over one cookie.
- Zoom to about **110%** for a projector.

**4. Have a PDF ready.** Any small PDF. You will upload it twice, so have a *second*, slightly
different one too — the system refuses a byte-identical re-upload, and that refusal is worth
showing on purpose.

---

## The run

### 1 · Landing and roles — 1 min

Open <http://localhost:8080> signed out.

> "One system for the whole dissertation cycle. Four roles, and the workflow is the same code for
> both the M.Tech and the five-year integrated programme — they differ only in data."

### 2 · Student proposes a topic — 2 min

Sign in as `student4@gmail.com` / `student123`.

- **Dashboard** — the pipeline strip shows step 1 highlighted, because the system knows this
  student has not proposed anything yet.
- **My topic → Propose a topic.** Fill in a title, an abstract (a paragraph is enough), pick
  `Dr A Sharma`, submit.

> "The abstract has a minimum length, and the form binds to a DTO rather than the entity — a
> rename in the domain can't ripple into the HTML."

- Back on **My topic**, the status reads `PROPOSED`.

### 3 · Guide approves it — 1 min

Switch to the guide window. Sign in as `guide1@college.edu` / `guide123`.

- **Dashboard** leads with what is waiting, so nobody hunts through three pages.
- Point at **Alerts** in the navigation — the unread count went up the moment the student
  submitted.

> "Notifications are a listener on the same domain events the audit trail consumes. Adding them
> changed no existing service."

- **Topic approvals** → open the topic → **Approve**.

*(Optional, 30s: choose "Changes requested" first to show the send-back loop, then approve.)*

### 4 · Guide allocation with capacity — 2 min

Back in the student window:

- **My guide** → request `Dr A Sharma` → the request appears as `REQUESTED`.

Guide window:

- **Guide requests** → **Accept**.

> "Capacity is enforced in the service, not the UI. The dropdown greys out a full guide as a
> courtesy, but the real check is in `assertHasCapacity`, and a partial unique index stops a
> student holding two live allocations even if two requests race."

*(Optional: sign in as the coordinator and show **Allocate guides** — the override path, with the
warning badge on a student whose topic isn't approved.)*

### 5 · Submission and the version history — 3 min

**This is the part to spend time on.** It is the strongest thing in the model.

Student window:

- **Submissions** — every milestone in the session is listed, whether or not anything has been
  filed. Note the integrated programme has four milestones where M.Tech has five.
- Upload your PDF against **Synopsis**, with a note.
- **Try uploading the same file again.** It is refused.

> "Byte-identical re-uploads are rejected — they add nothing to the history and hide the real
> change from the guide."

Guide window:

- **Submissions** → open it → **Mark as under review**.

> "Picking work up is a separate step from deciding on it, so the student can see their work was
> actually read."

- Add a **review comment**, with a page number.
- Record **Revision requested** with a note.

Student window:

- The comment is there; **mark it as done**.
- Upload the *second* PDF. It becomes **v2**.
- **View history** — both versions are listed, each with its own SHA-256 and size.

> "Submissions and versions are separate tables. The guide always reads the latest; nothing is
> ever overwritten. Every file has a digest, so you can prove the bytes on disk are the bytes that
> were submitted. Comments are pinned to the version they were written against, so a new upload
> starts a clean sheet and the old thread stays with the old file."

Guide window: review v2 and **Approve**.

### 6 · Evaluation, viva and the mark sheet — 2 min

Guide window:

- **Evaluate** → open the student → score each criterion → submit.

> "The rubric is rows scoped to the session, not an enum, and the scores are JSONB keyed by
> criterion id — so the department can reweight the marking scheme between years without a code
> change or a migration. The total is weighted by each criterion's share."

Coordinator window (sign in as `coordinator@college.edu` / `coord123`):

- **Viva** → switch to the **BTECH MTECH INTEGRATED** tab → schedule the defence.
- **Mark sheet** → the student now shows an average and an outcome.

Student window: **Result** shows the marks and the viva.

### 7 · The audit trail — 1 min

Sign in as `admin@college.edu` / `admin123` → **Audit trail**.

> "Every state change you just made is here — who did it, to which record, when, and what changed.
> The rows are written by an event listener inside the same transaction as the change, so an
> action that rolls back leaves no entry, and no entry can describe something that never happened.
> Nothing on this page is editable."

Scroll to show your run from the last ten minutes.

---

## Optional detours

Take these only if you have time or are asked.

| Ask | Show |
|---|---|
| "Is it secure?" | Sign in as `guide2` and open the other guide's submission URL directly — **404, not 403**. A stranger learns nothing about whether the record exists. |
| "What about AI?" | **Overlap check** under the student menu. Without a key it says so plainly; with one it embeds the abstract, retrieves the nearest approved topics and writes an advisory note — inside a panel labelled *AI-generated*. |
| "Can one person have two roles?" | `guide1` is both SUPERVISOR and REVIEWER — one account, two link groups, driven by `sec:authorize`. |
| "How do you know it works?" | `bash scripts/acceptance.sh 8081` — 19 assertions over the whole chain. |

---

## If something goes wrong

| Symptom | Fix |
|---|---|
| Old cream colour scheme | Hard-refresh, `Ctrl+Shift+R` |
| `Port 8080 was already in use` | Another instance is running. `netstat -ano \| findstr :8080`, then `taskkill /F /PID <pid>` |
| Login always fails | `application-local.properties` is missing or has the wrong database password |
| A page shows 409 | That is the system refusing an illegal state transition. It is **correct behaviour** — say so, it is a good moment |
| The demo student already has data | You skipped the reset. Run `scripts/demo-reset.sh` and start again |
| AI pages say "switched off" | Expected without a Gemini key. Say it is optional and move on |

---

## The three sentences to land

If you only get to say three things, make them these.

1. **"Submissions and versions are separate tables, so the history cannot be rewritten."**
2. **"State machines are declared once as data and validated in the service, so an illegal
   transition is impossible rather than merely discouraged."**
3. **"Role checks get you to the page; ownership checks get you to the record — and every change
   leaves an audit row written in the same transaction."**
