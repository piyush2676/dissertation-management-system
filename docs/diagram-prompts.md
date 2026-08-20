# Diagram prompts (for Napkin.ai)

Paste one block at a time into Napkin. Each block is self-contained — Napkin has no
memory of the previous one, so every prompt repeats the names it needs.

Tips that materially change the output:
- Napkin renders structure, not prose. Keep the arrows (`->`) and the bullet lists.
- Ask for the diagram type by name in the first line ("Draw an ER diagram…").
- If the first render is too busy, delete the last two or three bullets and re-run.
- Generate one diagram per slide. A combined "everything" diagram is unreadable at
  projector size.
- Export as SVG where offered, PNG otherwise. SVG stays sharp when the PPT is stretched.

Diagrams 1–5 cover what is built or fully designed. Diagram 6 onwards covers phases not
yet implemented — safe for a design PPT, but say "planned" on the slide if asked.

---

## 1. System flow diagram — end-to-end dissertation lifecycle

```
Draw a horizontal end-to-end process flow diagram for a Dissertation Management System
used by a college department. Show the actor above each step.

Student -> Registers and logs in
Student -> Proposes a dissertation topic with title, abstract and keywords
Supervisor -> Reviews the topic and approves, rejects, or requests changes
Coordinator -> Allocates an approved student to a supervisor, checking supervisor capacity
Coordinator -> Publishes milestones with due dates for the academic session
Student -> Uploads a report against a milestone, creating version 1
Supervisor -> Reviews the version and adds page-level comments
Supervisor -> Requests a revision
Student -> Uploads version 2, keeping version 1 intact in history
Supervisor -> Approves the submission
Coordinator -> Schedules the viva and assigns a panel of examiners
Examiners -> Score the work against a configurable rubric
System -> Computes the weighted total and generates the mark sheet
System -> Adds the approved thesis to the searchable department archive

Every step writes an audit log entry recording who acted and when.
```

---

## 2. ER diagram — database schema

```
Draw an entity relationship diagram for a Dissertation Management System.
Show entity names, key attributes, and the cardinality on each relationship.

USER (id, email unique, password_hash, full_name, enabled, created_at)
USER_ROLES (user_id, role) - a user holds many roles: STUDENT, SUPERVISOR, REVIEWER, COORDINATOR, ADMIN
STUDENT_PROFILE (id, user_id, roll_no unique, programme, department, batch, semester)
SUPERVISOR_PROFILE (id, user_id, designation, department, research_interests, max_students)
ACADEMIC_SESSION (id, label, programme, start_date, end_date, active)
MILESTONE (id, session_id, name, due_date, weightage, sequence_no)
TOPIC (id, student_id, title, abstract_text, keywords, proposed_supervisor_id, status, version)
ALLOCATION (id, student_id, supervisor_id, session_id, status, allocated_on, allocated_by)
SUBMISSION (id, allocation_id, milestone_id, current_version_no, status, late_flag)
SUBMISSION_VERSION (id, submission_id, version_no, storage_path, sha256, size_bytes, submitted_at)
REVIEW_COMMENT (id, submission_version_id, reviewer_id, page_no, body, resolved, created_at)
RUBRIC_CRITERION (id, session_id, name, max_marks, weightage)
EVALUATION (id, submission_id, examiner_id, scores_jsonb, total, remarks, submitted_at)
VIVA_SCHEDULE (id, allocation_id, scheduled_at, venue, status)
PANEL_MEMBER (id, viva_schedule_id, user_id, role)
NOTIFICATION (id, recipient_id, type, payload, read_at, created_at)
AUDIT_LOG (id, actor_id, action, entity_type, entity_id, old_value, new_value, at)

Relationships:
USER one-to-one STUDENT_PROFILE
USER one-to-one SUPERVISOR_PROFILE
USER one-to-many USER_ROLES
STUDENT_PROFILE one-to-many TOPIC
ACADEMIC_SESSION one-to-many MILESTONE
ACADEMIC_SESSION one-to-many RUBRIC_CRITERION
STUDENT_PROFILE one-to-many ALLOCATION
SUPERVISOR_PROFILE one-to-many ALLOCATION
ALLOCATION one-to-many SUBMISSION
MILESTONE one-to-many SUBMISSION
SUBMISSION one-to-many SUBMISSION_VERSION
SUBMISSION_VERSION one-to-many REVIEW_COMMENT
SUBMISSION one-to-many EVALUATION
ALLOCATION one-to-one VIVA_SCHEDULE
VIVA_SCHEDULE one-to-many PANEL_MEMBER
USER one-to-many NOTIFICATION
USER one-to-many AUDIT_LOG
```

If that renders too dense, split it in two and run each separately:

```
Draw an entity relationship diagram covering only the identity and setup half of a
Dissertation Management System: USER, USER_ROLES, STUDENT_PROFILE, SUPERVISOR_PROFILE,
ACADEMIC_SESSION, MILESTONE, RUBRIC_CRITERION. Show attributes and cardinality.
```

```
Draw an entity relationship diagram covering only the workflow half of a Dissertation
Management System: TOPIC, ALLOCATION, SUBMISSION, SUBMISSION_VERSION, REVIEW_COMMENT,
EVALUATION, VIVA_SCHEDULE, PANEL_MEMBER, AUDIT_LOG. Show attributes and cardinality.
```

---

## 3. Class diagram — UML

> **Napkin renders these two badly.** Napkin is an infographic generator, not a UML
> renderer — it has no notion of a compartment box, a multiplicity label, or a
> composition diamond, so a class diagram comes out as decorative boxes. Use
> **PlantUML** instead: `docs/diagrams/class-diagram.puml` and
> `docs/diagrams/object-diagram.puml` are ready to render. Paste either into
> <http://www.plantuml.com/plantuml>, or install the PlantUML Integration plugin in
> IntelliJ and open the file for a live preview. Export SVG.
>
> The Napkin prompt below is kept only as a fallback if you want a looser,
> more illustrative version for a non-technical slide.


```
Draw a UML class diagram for a Java Spring Boot Dissertation Management System.
Show class names, fields with types, key methods, and the associations between classes.

class User { Long id; String email; String passwordHash; String fullName; boolean enabled;
  Instant createdAt; Set<Role> roles }
enum Role { STUDENT, SUPERVISOR, REVIEWER, COORDINATOR, ADMIN; String authority() }
enum Programme { MTECH, BTECH_MTECH_INTEGRATED }
class StudentProfile { Long id; User user; String rollNo; Programme programme;
  String department; String batch; Integer semester }
class SupervisorProfile { Long id; User user; String designation; String department;
  String researchInterests; int maxStudents }
class Topic { Long id; StudentProfile student; String title; String abstractText;
  String keywords; SupervisorProfile proposedSupervisor; TopicStatus status; int version }
enum TopicStatus { DRAFT, PROPOSED, APPROVED, CHANGES_REQUESTED, REJECTED }
class Allocation { Long id; StudentProfile student; SupervisorProfile supervisor;
  AcademicSession session; AllocationStatus status; LocalDate allocatedOn; User allocatedBy }
class AcademicSession { Long id; String label; Programme programme; LocalDate startDate;
  LocalDate endDate; boolean active }
class Milestone { Long id; AcademicSession session; String name; LocalDate dueDate;
  BigDecimal weightage; int sequenceNo }
class Submission { Long id; Allocation allocation; Milestone milestone;
  int currentVersionNo; SubmissionStatus status; boolean lateFlag }
class SubmissionVersion { Long id; Submission submission; int versionNo; String storagePath;
  String sha256; long sizeBytes; Instant submittedAt }
class ReviewComment { Long id; SubmissionVersion version; User reviewer; int pageNo;
  String body; boolean resolved; Instant createdAt }
class Evaluation { Long id; Submission submission; User examiner; Map scores;
  BigDecimal total; String remarks; Instant submittedAt }

Service classes:
class TopicService { Topic propose(); Topic decide(); void assertTransitionAllowed() }
class AllocationService { Allocation allocate(); boolean hasCapacity() }
class SubmissionService { SubmissionVersion upload(); Submission changeStatus() }
class AuthzService { boolean isSelf(); boolean supervises(); boolean ownsSubmission() }
interface StorageService { String store(); Resource load() }
class LocalFileSystemStorage implements StorageService

Associations:
User 1..1 StudentProfile
User 1..1 SupervisorProfile
StudentProfile 1..* Topic
StudentProfile 1..* Allocation
SupervisorProfile 1..* Allocation
Allocation 1..* Submission
Submission 1..* SubmissionVersion
SubmissionVersion 1..* ReviewComment
Submission 1..* Evaluation
AcademicSession 1..* Milestone
```

---

## 4. Object diagram — a runtime snapshot

```
Draw a UML object diagram showing one concrete runtime snapshot of a Dissertation
Management System. Use the objectName : ClassName notation and show the actual
attribute values and the links between objects.

piyush : User
  email = "student4@college.edu"
  fullName = "Piyush Pandey"
  roles = { STUDENT }

piyushProfile : StudentProfile
  rollNo = "21INT015"
  programme = BTECH_MTECH_INTEGRATED
  department = "CSE"
  batch = "2021-2026"
  semester = 9

sharma : User
  email = "guide1@college.edu"
  fullName = "Dr A Sharma"
  roles = { SUPERVISOR, REVIEWER }

sharmaProfile : SupervisorProfile
  designation = "Associate Professor"
  researchInterests = "machine learning, federated systems"
  maxStudents = 5

session2526 : AcademicSession
  label = "2025-26"
  active = true

topic101 : Topic
  title = "Federated Learning for Privacy-Preserving Health Records"
  status = APPROVED
  version = 2

alloc55 : Allocation
  status = ACCEPTED
  allocatedOn = 2025-08-12

interimMilestone : Milestone
  name = "Interim Report"
  dueDate = 2025-11-30
  sequenceNo = 2

sub900 : Submission
  currentVersionNo = 2
  status = UNDER_REVIEW
  lateFlag = false

v1 : SubmissionVersion
  versionNo = 1
  sha256 = "a91f..."
  submittedAt = 2025-11-28

v2 : SubmissionVersion
  versionNo = 2
  sha256 = "7c30..."
  submittedAt = 2025-12-05

comment1 : ReviewComment
  pageNo = 14
  body = "Expand the evaluation section"
  resolved = false

Links:
piyush -- piyushProfile
sharma -- sharmaProfile
piyushProfile -- topic101
topic101 -- sharmaProfile (proposedSupervisor)
piyushProfile -- alloc55 -- sharmaProfile
alloc55 -- session2526
session2526 -- interimMilestone
alloc55 -- sub900 -- interimMilestone
sub900 -- v1
sub900 -- v2
v1 -- comment1
comment1 -- sharma (reviewer)

Highlight that v1 is never overwritten when v2 arrives — both versions coexist.
```

---

## 5. State machine diagrams

Run each of the three separately. They are small, so Napkin renders them cleanly.

### 5a. Topic

```
Draw a UML state machine diagram for the Topic entity in a Dissertation Management System.
Label every transition with the action and the actor who performs it.

DRAFT -> PROPOSED : student submits the proposal
PROPOSED -> APPROVED : supervisor approves
PROPOSED -> CHANGES_REQUESTED : supervisor requests changes with a reason
PROPOSED -> REJECTED : supervisor rejects with a reason
CHANGES_REQUESTED -> PROPOSED : student edits and resubmits, version increments

DRAFT is the initial state. APPROVED and REJECTED are terminal.
Any transition not shown here throws InvalidStateTransitionException at the service layer.
```

### 5b. Allocation

```
Draw a UML state machine diagram for the Allocation entity in a Dissertation Management
System. Label every transition with the action and the actor.

REQUESTED -> ACCEPTED : supervisor accepts, allowed only if current student count is
  below max_students
REQUESTED -> DECLINED : supervisor declines, the request falls back to the coordinator
DECLINED -> COORDINATOR_ASSIGNED : coordinator overrides and assigns a supervisor directly

REQUESTED is the initial state. ACCEPTED and COORDINATOR_ASSIGNED are terminal.
The capacity check is enforced in the service layer, not in the database.
```

### 5c. Submission

```
Draw a UML state machine diagram for the Submission entity in a Dissertation Management
System. Label every transition with the action and the actor.

DRAFT -> SUBMITTED : student uploads a file, creating an immutable SubmissionVersion
SUBMITTED -> UNDER_REVIEW : supervisor opens the submission
UNDER_REVIEW -> APPROVED : supervisor approves
UNDER_REVIEW -> REVISION_REQUESTED : supervisor requests a revision with comments
UNDER_REVIEW -> REJECTED : supervisor rejects
REVISION_REQUESTED -> SUBMITTED : student uploads a new version; the previous version
  is retained, never overwritten

DRAFT is the initial state. APPROVED and REJECTED are terminal.
Show that each pass through SUBMITTED creates a new SubmissionVersion row.
```

---

## 6. Architecture diagram — layered

```
Draw a layered software architecture diagram for a Spring Boot monolith called the
Dissertation Management System. Show the layers stacked vertically with arrows flowing
downward, and label what crosses each boundary.

Layer 1 - Browser : server-rendered HTML over HTTP, form POST, no single-page framework
  crosses down as: HTTP request
Layer 2 - Thymeleaf templates : layout fragments, role-aware navbar, page templates
  crosses down as: view name plus model attributes
Layer 3 - Spring MVC Controllers : DashboardController, TopicController,
  AllocationController, SubmissionController
  crosses down as: DTO / form objects
Layer 4 - Services : TopicService, AllocationService, SubmissionService, AuthzService.
  Business rules, state transitions, @Transactional boundaries
  crosses down as: JPA entities
Layer 5 - Spring Data JPA Repositories : UserRepository, TopicRepository,
  SubmissionRepository
  crosses down as: SQL
Layer 6 - PostgreSQL 18, schema versioned by Flyway migrations, pgvector extension for
  embeddings

Side components attached across all layers:
  Spring Security filter chain - form login, BCrypt, CSRF, role and ownership rules
  StorageService interface - local disk today, S3 later, no caller changes
  Domain events - audit logging, notifications and vector indexing run as event listeners
  GlobalExceptionHandler - maps domain exceptions to 403, 404, 409 and 500 pages
```

---

## 7. Security and authorisation flow

```
Draw a decision flow diagram showing how a request is authorised in a Spring Security
web application called the Dissertation Management System.

Incoming HTTP request
-> Is the user authenticated? No -> redirect to /login
-> Yes -> Does the URL pattern match the user's role? For example /supervisor/** requires
   ROLE_SUPERVISOR
-> No -> show the 403 Access Denied page
-> Yes -> Does the method carry a @PreAuthorize ownership check?
-> No -> execute the controller method
-> Yes -> evaluate the named bean, for example @authz.supervises(studentId, authentication)
   or @authz.ownsSubmission(submissionId, authentication)
-> Ownership check fails -> show the 403 Access Denied page
-> Ownership check passes -> execute the controller method
-> Render the Thymeleaf view, where sec:authorize hides links the user may not use

Add a callout: role alone is not enough. A supervisor holds ROLE_SUPERVISOR for every
URL under /supervisor/**, so a second layer decides whether this particular supervisor
supervises this particular student.
```

---

## 8. Role and permission matrix

```
Draw a role capability matrix for a Dissertation Management System. Roles across the top,
capabilities down the side, a tick where the role has the capability.

Roles: STUDENT, SUPERVISOR, REVIEWER, COORDINATOR, ADMIN

Propose a topic: STUDENT
Approve or reject a topic: SUPERVISOR
Upload a report version: STUDENT
Download a report: STUDENT (own only), SUPERVISOR (own students only), REVIEWER,
  COORDINATOR, ADMIN
Add review comments: SUPERVISOR, REVIEWER
Allocate a student to a supervisor: COORDINATOR
Override an allocation: COORDINATOR
Create academic sessions and milestones: COORDINATOR
Configure the rubric: COORDINATOR
Score an evaluation: SUPERVISOR, REVIEWER
Schedule a viva: COORDINATOR
Manage user accounts: ADMIN
View the audit log: ADMIN

Note that one account may hold several roles at once — a professor is often both
SUPERVISOR and REVIEWER.
```

---

## 9. Spring AI feature flow

```
Draw a flow diagram showing how an AI advisory layer is wired into a Dissertation
Management System built with Spring AI and a pgvector store.

Ingestion path:
Approved thesis documents -> text extraction -> chunking -> embedding model ->
pgvector store inside PostgreSQL

Query path 1, archive semantic search:
Student types a natural language query -> embedding model -> similarity search in
pgvector -> ranked list of past theses shown by meaning, not by keyword

Query path 2, topic novelty check:
Student submits a proposed topic -> embed the abstract -> retrieve the top k most
similar past theses -> send the topic plus the retrieved theses to Claude Opus ->
an advisory report describing overlap and research gaps

Query path 3, supervisor matching:
Embed the topic abstract -> cosine similarity against each supervisor's
research_interests embedding -> ranked supervisor suggestions for the coordinator

Query path 4, regulations question answering:
Student question -> retrieve passages from the department handbook in pgvector ->
Claude Sonnet -> grounded answer with the source passage cited

Hard constraint, shown as a boundary box around every output:
The model never assigns a final grade and never makes an approve or reject decision.
Every AI output is advisory, rendered in a distinct panel labelled
"AI-generated — verify before acting", and stored with an aiGenerated flag set to true.
```

---

## 10. Use case diagram

```
Draw a UML use case diagram for a Dissertation Management System. Put the actors outside
the system boundary and the use cases inside it.

Actors: Student, Supervisor, Reviewer, Coordinator, Administrator

Student: Log in, Propose topic, View milestones and deadlines, Upload report version,
  View feedback, Resubmit after revision, Search the thesis archive, View marks
Supervisor: Log in, Review topic proposals, Accept or decline allocation requests,
  View assigned students, Review submissions, Add comments, Request a revision,
  Approve a submission, Score an evaluation
Reviewer: Log in, Review submissions, Add comments, Score an evaluation
Coordinator: Log in, Create academic sessions, Define milestones, Allocate students to
  supervisors, Override an allocation, Configure the rubric, Schedule a viva,
  Assign a panel, Generate reports
Administrator: Log in, Manage user accounts, Assign roles, View the audit log

Log in is shared by every actor — show it as a common use case.
Supervisor and Reviewer overlap on reviewing and scoring, because one account can hold
both roles.
```

---

## 11. Before-and-after comparison (good opening slide)

```
Draw a side-by-side comparison diagram contrasting a manual process with an automated one.

Left side, titled "Manual process today":
Topics arrive over email
Guide allocation tracked in a spreadsheet
Deadlines announced on WhatsApp
Reports shared as report_final_FINAL_v2.docx on pen drives
Feedback handwritten on printed copies
Marks totalled in Excel
No record of who approved what, or when

Right side, titled "Dissertation Management System":
Topic proposals submitted and tracked in the application
Allocation enforced against supervisor capacity
Milestone deadlines published per programme, late submissions flagged automatically
Every upload stored as an immutable version with a SHA-256 checksum
Review comments anchored to a page and tracked until resolved
Marks computed from a configurable rubric
Every state change written to an audit log with actor and timestamp
```
