/*
 * ===========================================================================
 * REFERENCE ONLY -- read this, write your own in
 *     src/main/java/com/dms/user/DataSeeder.java
 *
 * This file lives in docs/ on purpose. Anything under src/main/java carrying
 * @Component becomes a Spring bean, so a copy here would run as a SECOND
 * seeder alongside yours. Outside the source root it is just text.
 * ===========================================================================
 */
package com.dms.user;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

// Static imports let you write ADMIN instead of Role.ADMIN below.
// Optional -- Role.ADMIN is arguably clearer. Pick one and be consistent.
import static com.dms.user.Programme.BTECH;
import static com.dms.user.Programme.MTECH;
import static com.dms.user.Role.ADMIN;
import static com.dms.user.Role.COORDINATOR;
import static com.dms.user.Role.REVIEWER;
import static com.dms.user.Role.STUDENT;
import static com.dms.user.Role.SUPERVISOR;

/**
 * Creates the demo accounts on first startup.
 *
 * <p>CommandLineRunner is a Spring Boot interface with a single method, run(...), which
 * Boot invokes once after the application context is fully built. That timing matters:
 * repositories and the PasswordEncoder are guaranteed to exist by then. It also means a
 * failure here happens AFTER "Started DissertationManagementSystemApplication" is logged,
 * so a broken seeder looks like a healthy app that then throws.
 *
 * <p>DEMO CREDENTIALS ONLY. Fine for a local demo; never for a deployment. Real
 * deployments create the first admin through an out-of-band bootstrap, not source code.
 */
@Component                  // registers this class as a Spring bean, so Boot finds it
@RequiredArgsConstructor    // generates a constructor taking every FINAL field -> injection
public class DataSeeder implements CommandLineRunner {

    /*
     * All four fields are final.
     *
     * @RequiredArgsConstructor only generates constructor parameters for final fields.
     * Drop the keyword on one of these and Lombok silently leaves it out, Spring never
     * injects it, and you get a NullPointerException the first time it is used -- with
     * no startup error to point at the cause.
     */
    private final UserRepository userRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final SupervisorProfileRepository supervisorProfileRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional  // org.springframework.transaction.annotation -- NOT the jakarta one.
                    // Both exist, both compile, IntelliJ offers jakarta first.
                    // 12 inserts across 3 repositories: if one fails, roll back all of it
                    // rather than leaving orphaned users behind.
    public void run(String... args) {

        /*
         * Idempotence guard. Without it, the second startup tries to insert
         * admin@college.edu again and dies on the unique email constraint.
         *
         * Note there is no "else" -- returning early makes the rest of the method the
         * else branch implicitly, and keeps the body flat instead of indented.
         */
        if (userRepository.count() > 0) {
            return;
        }

        // ---- accounts ---------------------------------------------------------------
        // The first two return values are discarded: nothing later references them.
        createUser("admin@college.edu", "admin123", "Dept Admin", Set.of(ADMIN));
        createUser("coordinator@college.edu", "coord123", "PG Coordinator", Set.of(COORDINATOR));

        /*
         * guide1 holds TWO roles. This is the case that justifies Set<Role> over a single
         * role column, and it is worth demonstrating: log in as guide1 and the navbar
         * shows both the supervisor and the reviewer links, driven purely by
         * sec:authorize in _navbar.html.
         */
        User guide1 = createUser("guide1@college.edu", "guide123", "Dr A Sharma",
                Set.of(SUPERVISOR, REVIEWER));
        User guide2 = createUser("guide2@college.edu", "guide123", "Dr B Verma",
                Set.of(SUPERVISOR));

        User student1 = createUser("student1@college.edu", "student123", "Ravi Kumar", Set.of(STUDENT));
        User student2 = createUser("student2@college.edu", "student123", "Neha Singh", Set.of(STUDENT));
        User student3 = createUser("student3@college.edu", "student123", "Amit Patel", Set.of(STUDENT));

        // ---- supervisor profiles ----------------------------------------------------
        /*
         * Capacities differ (5 and 3) deliberately. Phase 3's capacity rule is only
         * demonstrable if a supervisor can actually be filled: give guide2 three students
         * and the fourth allocation must be rejected. A demo where nothing is ever near
         * its limit proves nothing.         *
         * research_interests is free text now; Phase 7 embeds it to match students to
         * supervisors by meaning rather than by keyword.
         */
        createSupervisorProfile(guide1, "Associate Professor", "CSE",
                "machine learning, federated systems, privacy-preserving computation", 5);
        createSupervisorProfile(guide2, "Assistant Professor", "CSE",
                "distributed databases, query optimisation", 3);

        // ---- student profiles -------------------------------------------------------
        // Mixed BTECH and MTECH: same code path, different programme value. The two
        // differ only in their milestone rows later, never in logic.
        createStudentProfile(student1, "21CSE001", BTECH, "CSE", "2021-25", 8);
        createStudentProfile(student2, "24MCS007", MTECH, "CSE", "2024-26", 4);
        createStudentProfile(student3, "21CSE042", BTECH, "CSE", "2021-25", 8);
    }

    /**
     * Builds one account and saves it.
     *
     * <p>Returns the SAVED entity, not the one that was constructed. save() returns an
     * instance whose id has been populated by the database; the pre-save object still
     * has id == null. The profile helpers need a persisted User because user_id is
     * NOT NULL, so returning the saved instance is the whole point of this method
     * having a return type at all.
     *
     * <p>Hashing lives in here rather than at each call site so it is impossible to
     * forget on one of the seven users.
     */
    private User createUser(String email, String rawPassword, String fullName, Set<Role> roles) {

        User user = new User();
        user.setEmail(email);

        // NEVER store the raw string. Spring hashes the submitted password at login and
        // compares hashes; a plaintext value here fails with a generic "bad credentials"
        // and nothing in the log explaining why.
        user.setPasswordHash(passwordEncoder.encode(rawPassword));

        user.setFullName(fullName);

        /*
         * new HashSet<>(roles), not roles.
         *
         * Set.of(...) returns an IMMUTABLE set. Hand it straight to Hibernate and the
         * first time it tries to manage that collection you get an
         * UnsupportedOperationException thrown from deep inside the persistence layer,
         * with a stack trace that never mentions this class.
         */
        user.setRoles(new HashSet<>(roles));

        return userRepository.save(user);
    }

    /**
     * void, unlike createUser: nothing downstream references the profile.
     *
     * <p>Only add a return type when a caller actually needs the value.
     */
    private void createStudentProfile(User user, String rollNo, Programme programme,
                                      String department, String batch, Integer semester) {

        StudentProfile profile = new StudentProfile();

        // Must be the SAVED user. user_id is NOT NULL, so an unsaved User (id == null)
        // fails on the foreign key constraint.
        profile.setUser(user);

        profile.setRollNo(rollNo);
        profile.setProgramme(programme);
        profile.setDepartment(department);
        profile.setBatch(batch);

        // Integer, not int: semester is nullable in the schema, so null must be
        // expressible. A primitive would turn "not recorded" into a plausible-looking 0.
        profile.setSemester(semester);

        studentProfileRepository.save(profile);

        // Nothing to set on the other side: StudentProfile owns the foreign key via
        // @JoinColumn, and User has no field pointing back. No bidirectional sync needed.
    }

    private void createSupervisorProfile(User user, String designation, String department,
                                         String researchInterests, int maxStudents) {

        SupervisorProfile profile = new SupervisorProfile();
        profile.setUser(user);
        profile.setDesignation(designation);
        profile.setDepartment(department);
        profile.setResearchInterests(researchInterests);

        /*
         * int, not Integer: max_students is NOT NULL, so "no capacity recorded" is not a
         * state that exists. The primitive forces every caller to supply a value.
         *
         * Passing it explicitly also sidesteps a real trap: the SQL column has
         * DEFAULT 5, but a column default only applies when the INSERT omits the column,
         * and Hibernate never omits a mapped column. Rely on the SQL default and every
         * supervisor is created with capacity 0 -- then Phase 3 rejects every allocation
         * and the cause is nowhere near the symptom.
         */
        profile.setMaxStudents(maxStudents);

        supervisorProfileRepository.save(profile);
    }
}

/*
 * ===========================================================================
 * VERIFY AFTER RUNNING -- check the database before touching the browser.
 *
 *   SELECT email, full_name FROM users ORDER BY id;
 *      -> 7 rows
 *
 *   SELECT user_id, role FROM user_roles ORDER BY user_id;
 *      -> 8 rows: guide1 appears TWICE (SUPERVISOR + REVIEWER)
 *
 *   SELECT email, LEFT(password_hash, 4) FROM users LIMIT 1;
 *      -> starts with $2a$ or $2b$   (if it reads "admi", hashing was skipped)
 *
 *   SELECT u.email, s.max_students FROM supervisor_profiles s
 *     JOIN users u ON u.id = s.user_id;
 *      -> 5 and 3, not 0 and 0
 *
 * Then restart the app a second time. Nothing should be duplicated and no error
 * should appear -- that proves the count guard works.
 * ===========================================================================
 */