package com.dms.user;

import com.dms.evaluation.RubricCriterion;
import com.dms.evaluation.RubricCriterionRepository;
import com.dms.session.AcademicSession;
import com.dms.session.AcademicSessionRepository;
import com.dms.session.DissertationPhase;
import com.dms.session.Milestone;
import com.dms.session.MilestoneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import static com.dms.user.Programme.*;
import static com.dms.user.Role.*;

@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SupervisorProfileRepository supervisorProfileRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final AcademicSessionRepository academicSessionRepository;
    private final MilestoneRepository milestoneRepository;
    private final RubricCriterionRepository rubricRepository;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        seedUsers();
        seedSessions();
        seedMilestones();
        seedRubrics();
    }

    private void seedUsers() {
        if (userRepository.count() > 0) {
            return;
        }

        createUser("admin@college.edu", "admin123", "Dept Admin", Set.of(ADMIN));
        createUser("coordinator@college.edu", "coord123", "PG COORDINATOR", Set.of(COORDINATOR));

        User guide1 = createUser("guide1@college.edu", "guide123", "Dr A Sharma", Set.of(SUPERVISOR, REVIEWER));
        User guide2 = createUser("guide2@college.edu", "guide123", "Dr B Pandey", Set.of(SUPERVISOR));
        User student1 = createUser("student1@college.edu", "student123", "Avika Singh", Set.of(STUDENT));
        User student2 = createUser("student2@college.edu", "student123", "Neha Kumari", Set.of(STUDENT));
        User student3 = createUser("student3@college.edu", "student123", "Anjana Nair", Set.of(STUDENT));
        User student4 = createUser("student4@gmail.com", "student123", "Piyush Pandey", Set.of(STUDENT));
        createSupervisorProfile(guide1, "Associate Professor", "CSE", "machine learning,federated systems,privacy-preserving computation", 5);
        createSupervisorProfile(guide2, "Assistant Professor", "CSE", "distributed databases,query optimisation", 3);
        createStudentProfile(student1, "24MCS001", MTECH, "CSE", "2024-2026", 4);
        createStudentProfile(student2, "24MCS007", MTECH, "CSE", "2024-2026", 4);
        createStudentProfile(student3, "24MCS042", MTECH, "CSE", "2024-2026", 3);
        createStudentProfile(student4, "21INT015", BTECH_MTECH_INTEGRATED, "CSE", "2021-2026", 9);

    }

    /**
     * Dates are anchored to the day the database is first seeded rather than
     * hard-coded, so a demo run months from now still has a session in progress
     * and deadlines ahead of it. Fixed dates would make every submission read as
     * late the moment the calendar moved past them.
     */
    private void seedSessions() {
        if (academicSessionRepository.count() > 0) {
            return;
        }

        LocalDate today = LocalDate.now();
        LocalDate start = today.minusMonths(2);
        LocalDate end = today.plusMonths(8);
        String label = start.getYear() + "-" + String.valueOf(start.plusYears(1).getYear()).substring(2);

        for (Programme programme : Programme.values()) {
            createSession(label, programme, start, end, true);
        }
    }

    /**
     * Guarded per session and phase rather than on a global count, so a database
     * that predates the phase split -- whose rows V14 backfilled as FINAL -- still
     * gains a PRE track on the next start without touching what is there.
     */
    private void seedMilestones() {
        LocalDate today = LocalDate.now();
        for (AcademicSession session : academicSessionRepository.findAll()) {
            for (DissertationPhase phase : DissertationPhase.values()) {
                if (!milestoneRepository.existsBySessionAndPhase(session, phase)) {
                    seedReviews(session, phase, today);
                }
            }
        }
    }

    /**
     * The three review presentations per semester the guidelines prescribe
     * (§4.12, §5.6), with the deliverable each one is filed against. Weightage is
     * the share the matching rubric row carries (Format 6 / Format 15), so the
     * student page's "% of the total" agrees with the mark sheet. Due dates are
     * spaced as the guidelines space the reviews: second, third and fourth month
     * of the semester.
     */
    private void seedReviews(AcademicSession session, DissertationPhase phase, LocalDate today) {
        if (phase == DissertationPhase.PRE) {
            createMilestone(session, phase, "Review 1 - Problem statement",
                    "Title finalisation, problem statement, literature survey and objectives. File the synopsis.",
                    today.plusDays(30), 10, 1);
            createMilestone(session, phase, "Review 2 - Synopsis and methodology",
                    "Synopsis with the proposed methodology and the first draft of the thesis.",
                    today.plusDays(75), 20, 2);
            createMilestone(session, phase, "Review 3 - Implementation and paper 1",
                    "Initial implementation, the first research paper and the second draft of the thesis.",
                    today.plusDays(120), 35, 3);
        } else {
            createMilestone(session, phase, "Review 1 - Implementation",
                    "Methodology in use and implementation status, with the third draft of the thesis.",
                    today.plusDays(30), 15, 1);
            createMilestone(session, phase, "Review 2 - Results and paper 2",
                    "Final implementation, result analysis, the second research paper and the pre-final draft.",
                    today.plusDays(75), 20, 2);
            createMilestone(session, phase, "Review 3 - Final thesis",
                    "Final thesis and documentation for evaluation.",
                    today.plusDays(120), 15, 3);
        }
    }

    private User createUser(String email,String rawPassword,String fullName,Set<Role> roles){
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setFullName(fullName);
        user.setRoles(new HashSet<>(roles));
        // Seeded accounts come from institute records, so their addresses count as
        // confirmed. The one on a public mail domain deliberately does not, which
        // gives the confirmation flow something real to demonstrate.
        if (!email.endsWith("@gmail.com")) {
            user.setEmailVerifiedAt(Instant.now());
        }
        return userRepository.save(user);
    }
    private void createStudentProfile(User user,String rollNo,Programme programme,String department,String batch,Integer semester){
        StudentProfile studentProfile = new StudentProfile();
        studentProfile.setUser(user);
        studentProfile.setRollNo(rollNo);
        studentProfile.setProgramme(programme);
        studentProfile.setDepartment(department);
        studentProfile.setBatch(batch);
        studentProfile.setSemester(semester);
        studentProfileRepository.save(studentProfile);
    }
    private void createSupervisorProfile(User user,String designation,String department,String researchInterests,int maxStudents){
        SupervisorProfile supervisorProfile = new SupervisorProfile();
        supervisorProfile.setUser(user);
        supervisorProfile.setDesignation(designation);
        supervisorProfile.setDepartment(department);
        supervisorProfile.setResearchInterests(researchInterests);
        supervisorProfile.setMaxStudents(maxStudents);
        supervisorProfileRepository.save(supervisorProfile);
    }
    private AcademicSession createSession(String label,Programme programme,LocalDate startDate,LocalDate endDate,boolean active){
        AcademicSession session = new AcademicSession();
        session.setLabel(label);
        session.setProgramme(programme);
        session.setStartDate(startDate);
        session.setEndDate(endDate);
        session.setActive(active);
        session.setCreatedAt(Instant.now());
        return academicSessionRepository.save(session);
    }
    private void createMilestone(AcademicSession session, DissertationPhase phase, String name, String description,
                                 LocalDate dueDate, int weightage, int sequenceNo) {
        Milestone milestone = new Milestone();
        milestone.setSession(session);
        milestone.setPhase(phase);
        milestone.setName(name);
        milestone.setDescription(description);
        milestone.setDueDate(dueDate);
        milestone.setWeightage(weightage);
        milestone.setSequenceNo(sequenceNo);
        milestone.setCreatedAt(Instant.now());
        milestoneRepository.save(milestone);
    }

    /**
     * Guarded per session and phase, for the same reason as the milestones: a
     * database seeded before the rubric had a phase keeps its rows and gains the
     * scheme it lacks.
     */
    private void seedRubrics() {
        for (AcademicSession session : academicSessionRepository.findAll()) {
            for (DissertationPhase phase : DissertationPhase.values()) {
                if (!rubricRepository.existsBySessionAndPhase(session, phase)) {
                    seedRubric(session, phase);
                }
            }
        }
    }

    /**
     * The internal marking schemes as the guidelines print them: Format 6 for the
     * Pre-Dissertation semester (100 marks) and Format 15 for the Final (200).
     * Rows, so the department can change them without a release. maxMarks equals
     * weightage so a total reads as marks out of the phase maximum. The CO and PO
     * codes are copied verbatim, including Format 15's CO4 and CO5 that the
     * guidelines' own outcome list never defines -- that is theirs to correct.
     */
    private void seedRubric(AcademicSession session, DissertationPhase phase) {
        if (phase == DissertationPhase.PRE) {
            createCriterion(session, phase, "Problem statement (Review 1)",
                    "Clearly defined problem statement meeting the objectives of the work.",
                    10, "CO1", "PO1,PO2,PO4,PO6,PO7,PO11", 1);
            createCriterion(session, phase, "Literature review (Review 2)",
                    "Recent papers relevant to the topic, with the gaps in knowledge identified.",
                    20, "CO1", "PO2", 2);
            createCriterion(session, phase, "Methodology and implementation (Review 3)",
                    "Methodology with defined input and expected output; concepts implemented against the objectives.",
                    35, "CO2", "PO1,PO2,PO3,PO4,PO5,PO7,PO11", 3);
            createCriterion(session, phase, "Presentation",
                    "Quality of the presentation and the result discussion.",
                    10, "CO3", "PO10", 4);
            createCriterion(session, phase, "Documentation",
                    "Report, first research paper and thesis draft, with every concept described.",
                    15, "CO2", "PO10,PO11", 5);
            createCriterion(session, phase, "Ethics",
                    "Originality, attribution and responsible conduct of the work.",
                    10, "CO3", "PO8,PO9", 6);
        } else {
            createCriterion(session, phase, "Implementation (Review 1)",
                    "Required concepts implemented, meeting the objectives.",
                    20, "CO1", "PO1,PO2,PO3,PO4,PO5,PO7,PO11", 1);
            createCriterion(session, phase, "Result analysis and outcomes (Review 2)",
                    "Results analysed with appropriate tools, linked to the objectives and validated.",
                    40, "CO2", "PO1,PO2,PO3,PO4,PO5,PO7,PO11", 2);
            createCriterion(session, phase, "Presentation (Review 3)",
                    "Quality of the presentation and the result discussion.",
                    30, "CO4", "PO10", 3);
            createCriterion(session, phase, "Documentation",
                    "Report submission, with every concept described.",
                    30, "CO4", "PO10,PO11", 4);
            createCriterion(session, phase, "Research paper, patent or thesis",
                    "SCI/Scopus paper or utility patent filed at the top; no publication or patent at the bottom.",
                    30, "CO5", "PO1,PO2,PO3,PO4,PO5,PO7,PO10,PO11", 5);
            createCriterion(session, phase, "Ethics",
                    "Originality, attribution and responsible conduct of the work.",
                    20, "CO3", "PO8,PO9", 6);
        }
    }

    private void createCriterion(AcademicSession session, DissertationPhase phase, String name, String description,
                                 int marks, String coCode, String poMapping, int sequenceNo) {
        RubricCriterion criterion = new RubricCriterion();
        criterion.setSession(session);
        criterion.setPhase(phase);
        criterion.setName(name);
        criterion.setDescription(description);
        criterion.setMaxMarks(marks);
        criterion.setWeightage(marks);
        criterion.setCoCode(coCode);
        criterion.setPoMapping(poMapping);
        criterion.setSequenceNo(sequenceNo);
        criterion.setCreatedAt(Instant.now());
        rubricRepository.save(criterion);
    }
}
