package com.dms.user;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
    @Override
    @Transactional
    public void run(String... args) throws Exception {
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
        createStudentProfile(student1, "21CSE001", BTECH, "CSE", "2021-2025", 8);
        createStudentProfile(student2, "24MCS007", MTECH, "CSE", "2024-2026", 4);
        createStudentProfile(student3, "21CSE042", BTECH, "CSE", "2021-2025", 8);
        createStudentProfile(student4, "21INT015", BTECH_MTECH_INTEGRATED, "CSE", "2021-2026", 9);

    }
            private User createUser(String email,String rawPassword,String fullName,Set<Role> roles){
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setFullName(fullName);
        user.setRoles(new HashSet<>(roles));
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
}
