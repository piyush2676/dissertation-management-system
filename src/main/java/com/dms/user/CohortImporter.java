package com.dms.user;

import com.dms.allocation.Allocation;
import com.dms.allocation.AllocationRepository;
import com.dms.allocation.AllocationStatus;
import com.dms.session.AcademicSession;
import com.dms.session.AcademicSessionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Loads a real cohort from the department's own allocation list.
 *
 * <p>The department keeps its scholars in a spreadsheet: thesis id, name, roll
 * number, supervisor, co-supervisor. This reads that list, as CSV, and creates
 * the accounts, profiles and allocations to match, so the system can be shown
 * against the real cohort instead of six invented students.
 *
 * <p><b>Contact details are deliberately not imported.</b> The source sheet also
 * carries institutional mail ids and mobile numbers for scholars and faculty;
 * none of that is read here, and the CSV the importer consumes is generated
 * without those columns. Sign-in addresses are derived from the roll number and
 * the faculty name, so nothing in this database is a real contact address.
 *
 * <p>Off unless {@code dms.import.cohort-file} names a readable file, and
 * idempotent: a row whose roll number already exists is skipped, so a restart
 * does not duplicate anybody. The file itself is gitignored; this class is not.
 */
@Component
@RequiredArgsConstructor
@Order(20) // after DataSeeder, which needs an empty users table to do anything
@Slf4j
public class CohortImporter implements CommandLineRunner {

    /** Imported accounts share one password. Local demonstration only, never a deployment. */
    static final String DEFAULT_PASSWORD = "niet123";
    /**
     * Sign-in addresses are derived on the institute domain; the mail ids in the sheet
     * are still not read. See {@link InstituteMail} on what that means once SMTP is on.
     */
    static final String MAIL_DOMAIN = InstituteMail.DOMAIN;

    private final UserRepository userRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final SupervisorProfileRepository supervisorProfileRepository;
    private final AcademicSessionRepository academicSessionRepository;
    private final AllocationRepository allocationRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${dms.import.cohort-file:}")
    private String cohortFile;

    @Value("${dms.import.cohort-batch:2022-2027}")
    private String batch;

    @Value("${dms.import.cohort-semester:9}")
    private int semester;

    @Value("${dms.import.cohort-department:CSE}")
    private String department;

    /** A faculty member's supervision cap when the sheet does not say. */
    @Value("${dms.import.cohort-capacity:6}")
    private int capacity;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        if (cohortFile == null || cohortFile.isBlank()) {
            return;
        }
        Path path = Path.of(cohortFile);
        if (!Files.isReadable(path)) {
            log.warn("dms.import.cohort-file is set to {} but that file cannot be read; skipping the import.", path);
            return;
        }

        List<Row> rows = read(path);
        if (rows.isEmpty()) {
            log.warn("No rows in {}; nothing imported.", path);
            return;
        }

        AcademicSession session = academicSessionRepository
                .findByProgrammeAndActiveTrue(Programme.BTECH_MTECH_INTEGRATED)
                .orElse(null);
        if (session == null) {
            log.warn("No active session for the integrated programme; scholars will be imported without allocations.");
        }

        Map<String, SupervisorProfile> faculty = new LinkedHashMap<>();
        int students = 0;
        int placed = 0;
        int skipped = 0;

        for (Row row : rows) {
            if (row.rollNo().isBlank() || row.studentName().isBlank()) {
                continue;
            }
            if (studentProfileRepository.findByRollNo(row.rollNo()).isPresent()) {
                skipped++;
                continue;
            }

            User user = createUser(row.studentName(), scholarEmail(row.erpId(), row.rollNo()), Set.of(Role.STUDENT));
            StudentProfile student = new StudentProfile();
            student.setUser(user);
            student.setRollNo(row.rollNo());
            student.setProgramme(Programme.BTECH_MTECH_INTEGRATED);
            student.setDepartment(department);
            student.setBatch(batch);
            student.setSemester(semester);
            studentProfileRepository.save(student);
            students++;

            if (session == null || isPlaceholder(row.supervisor())) {
                continue;
            }
            SupervisorProfile guide = faculty.computeIfAbsent(row.supervisor(), this::facultyFor);
            SupervisorProfile co = isPlaceholder(row.coSupervisor()) ? null
                    : faculty.computeIfAbsent(row.coSupervisor(), this::facultyFor);
            if (co != null && co.getId().equals(guide.getId())) {
                co = null; // the sheet names the same person twice; the model refuses it
            }

            Allocation allocation = new Allocation();
            allocation.setStudent(student);
            allocation.setSupervisor(guide);
            allocation.setCoSupervisor(co);
            allocation.setSession(session);
            // The department allocated these; nobody requested and nobody accepted.
            allocation.setStatus(AllocationStatus.COORDINATOR_ASSIGNED);
            allocation.setRequestedAt(Instant.now());
            allocation.setDecidedAt(Instant.now());
            allocationRepository.save(allocation);
            placed++;
        }

        log.info("Cohort import from {}: {} scholars, {} placed with a guide, {} faculty, {} already on record.",
                path.getFileName(), students, placed, faculty.size(), skipped);
    }

    /** Finds the faculty member by their generated address, or creates them. */
    private SupervisorProfile facultyFor(String displayName) {
        String email = emailFor(displayName);
        Optional<SupervisorProfile> existing = supervisorProfileRepository.findByUserEmail(email);
        if (existing.isPresent()) {
            return existing.get();
        }
        User user = userRepository.findByEmail(email).orElseGet(() ->
                createUser(displayName, email, Set.of(Role.SUPERVISOR, Role.REVIEWER)));
        SupervisorProfile profile = new SupervisorProfile();
        profile.setUser(user);
        // The sheet carries no designation or interests; a title is the only hint.
        profile.setDesignation(displayName.startsWith("Dr") ? "Associate Professor" : "Assistant Professor");
        profile.setDepartment(department);
        profile.setMaxStudents(capacity);
        return supervisorProfileRepository.save(profile);
    }

    private User createUser(String fullName, String email, Set<Role> roles) {
        User user = new User();
        user.setEmail(email);
        user.setFullName(fullName);
        user.setPasswordHash(passwordEncoder.encode(DEFAULT_PASSWORD));
        user.setRoles(new HashSet<>(roles));
        // Imported from institute records, so the address counts as confirmed --
        // there is no self-registration here to verify.
        user.setEmailVerifiedAt(Instant.now());
        return userRepository.save(user);
    }

    /**
     * What the sheet writes where there is no guide: "NP" (not placed) and "LEFT" (the
     * guide has left the institute). Taken as names, each became a faculty account --
     * "Np", "Left" -- offered to every student as a guide.
     */
    private static final Set<String> PLACEHOLDERS =
            Set.of("", "np", "na", "n/a", "nil", "none", "tbd", "left", "-", "--");

    static boolean isPlaceholder(String name) {
        return name == null || PLACEHOLDERS.contains(name.replace('\u00a0', ' ').strip().toLowerCase(Locale.ROOT));
    }

    /**
     * A scholar signs in with their ERP ID on the institute domain, as the ERP does:
     * 0221MCSD006 becomes 0221mcsd006@niet.co.in (stored lower-case; sign-in ignores
     * case). A row without an ERP ID falls back to the roll number.
     */
    static String scholarEmail(String erpId, String rollNo) {
        String local = erpId == null || erpId.isBlank() ? rollNo : erpId;
        return (local.strip() + MAIL_DOMAIN).toLowerCase(Locale.ROOT);
    }

    /** Honorifics the sheet uses. Dropped so an address is the person, not their title. */
    private static final Set<String> TITLES = Set.of("dr", "mr", "mrs", "ms", "prof");

    /**
     * "Dr. Hitesh Singh" becomes hitesh.singh@niet.co.in -- derived from the name, never
     * read from the sheet.
     *
     * <p>Everything that is not a letter becomes a gap before anything else is
     * decided, so a full stop, a double space and the non-breaking space the sheet
     * carries inside some names all collapse the same way. That matters more than
     * it looks: one guide appears as "Dr. Megha Gupta" on one row and with a
     * non-breaking space on another, and if those resolved differently the import
     * would open two accounts and split one supervisor's students between them.
     */
    static String emailFor(String displayName) {
        String cleaned = Normalizer.normalize(displayName, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z]+", " ")
                .trim();
        List<String> words = new ArrayList<>();
        for (String word : cleaned.split(" ")) {
            if (!word.isBlank()) {
                words.add(word);
            }
        }
        if (!words.isEmpty() && TITLES.contains(words.get(0))) {
            words.remove(0);
        }
        return (words.isEmpty() ? "faculty" : String.join(".", words)) + MAIL_DOMAIN;
    }

    private record Row(String thesisId, String studentName, String rollNo,
                       String supervisor, String coSupervisor, String titleFormReceived, String erpId) {
    }

    /**
     * The CSV this reads is generated from the department's workbook with the
     * private columns already dropped. Quoted fields are supported because a name
     * can carry a comma; nothing else about the format is clever.
     */
    private List<Row> read(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        List<Row> rows = new ArrayList<>();
        Map<String, Integer> header = null;
        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            List<String> fields = splitCsv(line);
            if (header == null) {
                header = new LinkedHashMap<>();
                for (int i = 0; i < fields.size(); i++) {
                    header.put(fields.get(i).trim(), i);
                }
                continue;
            }
            rows.add(new Row(
                    at(fields, header, "thesisId"),
                    at(fields, header, "studentName"),
                    at(fields, header, "rollNo"),
                    at(fields, header, "supervisor"),
                    at(fields, header, "coSupervisor"),
                    at(fields, header, "titleFormReceived"),
                    at(fields, header, "erpId")));
        }
        return rows;
    }

    private static String at(List<String> fields, Map<String, Integer> header, String name) {
        Integer index = header.get(name);
        return index == null || index >= fields.size() ? "" : fields.get(index).trim();
    }

    private static List<String> splitCsv(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (quoted) {
                if (ch == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else if (ch == '"') {
                    quoted = false;
                } else {
                    field.append(ch);
                }
            } else if (ch == '"') {
                quoted = true;
            } else if (ch == ',') {
                out.add(field.toString());
                field.setLength(0);
            } else {
                field.append(ch);
            }
        }
        out.add(field.toString());
        return out;
    }
}
