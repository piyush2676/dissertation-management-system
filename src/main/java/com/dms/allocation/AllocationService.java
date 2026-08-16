package com.dms.allocation;

import com.dms.common.InvalidStateTransitionException;
import com.dms.common.NotFoundException;
import com.dms.session.AcademicSession;
import com.dms.session.AcademicSessionRepository;
import com.dms.topic.Topic;
import com.dms.topic.TopicRepository;
import com.dms.topic.TopicStatus;
import com.dms.user.Programme;
import com.dms.user.StudentProfile;
import com.dms.user.StudentProfileRepository;
import com.dms.user.SupervisorProfile;
import com.dms.user.SupervisorProfileRepository;
import com.dms.user.User;
import com.dms.user.UserRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class AllocationService {

    private final AllocationRepository allocationRepository;
    private final TopicRepository topicRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final SupervisorProfileRepository supervisorProfileRepository;
    private final AcademicSessionRepository academicSessionRepository;
    private final UserRepository userRepository;

    public Allocation request(String studentEmail, Long supervisorId) {
        StudentProfile student = student(studentEmail);
        AcademicSession session = activeSessionFor(student.getProgramme());

        Topic topic = topicRepository.findFirstByStudentOrderByCreatedAtDesc(student)
                .orElseThrow(() -> new IllegalStateException("Propose a topic before requesting a guide."));

        if (topic.getStatus() != TopicStatus.APPROVED) {
            throw new IllegalStateException("Your topic is not approved yet.");
        }

        if (allocationRepository.existsByStudentAndSessionAndStatusIn(
                student, session, AllocationStatus.LIVE)) {
            throw new IllegalStateException("You already have a guide request in play this session.");
        }

        SupervisorProfile supervisor = supervisorProfileRepository.findById(supervisorId)
                .orElseThrow(() -> new NotFoundException("Supervisor", supervisorId));

        assertHasCapacity(supervisor, session);

        Allocation allocation = new Allocation();
        allocation.setStudent(student);
        allocation.setSupervisor(supervisor);
        allocation.setSession(session);
        allocation.setTopic(topic);
        allocation.setStatus(AllocationStatus.REQUESTED);
        allocation.setRequestedAt(Instant.now());

        try {
            return allocationRepository.save(allocation);
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalStateException("You already have a guide request in play this session.");
        }
    }

    public Allocation accept(String supervisorEmail, Long allocationId) {
        Allocation allocation = loadOwnedBySupervisor(allocationId, supervisorEmail);
        SupervisorProfile supervisor = allocation.getSupervisor();

        if (!allocation.getStatus().canTransitionTo(AllocationStatus.ACCEPTED)) {
            throw new InvalidStateTransitionException(allocation.getStatus(), AllocationStatus.ACCEPTED);
        }

        assertHasCapacity(supervisor, allocation.getSession());

        allocation.setStatus(AllocationStatus.ACCEPTED);
        allocation.setDecisionReason(null);
        allocation.setDecidedAt(Instant.now());
        allocation.setAllocatedBy(supervisor.getUser());
        return allocationRepository.save(allocation);
    }

    public Allocation decline(String supervisorEmail, Long allocationId, String reason) {
        Allocation allocation = loadOwnedBySupervisor(allocationId, supervisorEmail);

        if (!allocation.getStatus().canTransitionTo(AllocationStatus.DECLINED)) {
            throw new InvalidStateTransitionException(allocation.getStatus(), AllocationStatus.DECLINED);
        }

        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A reason is required to decline a request.");
        }

        allocation.setStatus(AllocationStatus.DECLINED);
        allocation.setDecisionReason(reason.strip());
        allocation.setDecidedAt(Instant.now());
        allocation.setAllocatedBy(allocation.getSupervisor().getUser());
        return allocationRepository.save(allocation);
    }

    public Allocation withdraw(String studentEmail, Long allocationId) {
        Allocation allocation = allocationRepository.findById(allocationId)
                .orElseThrow(() -> new NotFoundException("Allocation", allocationId));

        if (!allocationRepository.existsByIdAndStudentUserEmail(allocationId, studentEmail)) {
            throw new NotFoundException("Allocation", allocationId);
        }

        if (!allocation.getStatus().canTransitionTo(AllocationStatus.WITHDRAWN)) {
            throw new InvalidStateTransitionException(allocation.getStatus(), AllocationStatus.WITHDRAWN);
        }

        allocation.setStatus(AllocationStatus.WITHDRAWN);
        allocation.setDecidedAt(Instant.now());
        allocation.setAllocatedBy(allocation.getStudent().getUser());
        return allocationRepository.save(allocation);
    }

    public Allocation assign(String coordinatorEmail, Long studentId, Long supervisorId) {
        User coordinator = userRepository.findByEmail(coordinatorEmail)
                .orElseThrow(() -> new NotFoundException("User " + coordinatorEmail + " not found"));

        StudentProfile student = studentProfileRepository.findById(studentId)
                .orElseThrow(() -> new NotFoundException("Student", studentId));

        AcademicSession session = activeSessionFor(student.getProgramme());

        SupervisorProfile supervisor = supervisorProfileRepository.findById(supervisorId)
                .orElseThrow(() -> new NotFoundException("Supervisor", supervisorId));

        if (allocationRepository.existsByStudentAndSessionAndStatusIn(
                student, session, AllocationStatus.LIVE)) {
            throw new IllegalStateException("That student already has a live allocation this session.");
        }

        assertHasCapacity(supervisor, session);

        Topic topic = topicRepository.findFirstByStudentOrderByCreatedAtDesc(student)
                .filter(t -> t.getStatus() == TopicStatus.APPROVED)
                .orElse(null);

        Instant now = Instant.now();

        Allocation allocation = new Allocation();
        allocation.setStudent(student);
        allocation.setSupervisor(supervisor);
        allocation.setSession(session);
        allocation.setTopic(topic);
        allocation.setStatus(AllocationStatus.COORDINATOR_ASSIGNED);
        allocation.setAllocatedBy(coordinator);
        allocation.setRequestedAt(now);
        allocation.setDecidedAt(now);

        try {
            return allocationRepository.save(allocation);
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalStateException("That student already has a live allocation this session.");
        }
    }

    @Transactional(readOnly = true)
    public Optional<Allocation> currentAllocationFor(String studentEmail) {
        StudentProfile student = student(studentEmail);
        return allocationRepository.findByStudentAndSessionAndStatusIn(
                student, activeSessionFor(student.getProgramme()), AllocationStatus.LIVE);
    }
    @Transactional(readOnly = true)
    public List<Allocation> historyFor(String studentEmail) {
        return allocationRepository.findByStudentOrderByRequestedAtDesc(student(studentEmail));
    }
    @Transactional(readOnly = true)
    public List<SupervisorProfile> selectableSupervisors(){
        return supervisorProfileRepository.findAllBy();
    }

    /**
     * Whether the student has any topic on record at all.
     *
     * Deliberately not existsByStudentAndStatusIn: request() resolves the topic
     * with findFirstByStudentOrderByCreatedAtDesc, which accepts a draft. A
     * stricter check here would hide a form the service would have accepted.
     */
    @Transactional(readOnly = true)
    public boolean hasTopic(String studentEmail) {
        return topicRepository.existsByStudent(student(studentEmail));
    }

    /**
     * Seats occupied per supervisor in the student's own active session, keyed by
     * supervisor profile id. Every selectable supervisor is present: guides with
     * no allocations are missing from the aggregate, so they are zero-filled
     * first and overwritten second, leaving callers with no absent keys to guard.
     *
     * Counts OCCUPIES_A_SEAT only (ACCEPTED, COORDINATOR_ASSIGNED). A pending
     * request has not taken a seat, so a guide reading "3 of 5" may still have
     * several students waiting on a decision.
     */
    @Transactional(readOnly = true)
    public Map<Long, Long> seatsTakenFor(String studentEmail) {
        StudentProfile student = student(studentEmail);
        AcademicSession session = activeSessionFor(student.getProgramme());

        Map<Long, Long> taken = new HashMap<>();
        for (SupervisorProfile supervisor : selectableSupervisors()) {
            taken.put(supervisor.getId(), 0L);
        }
        for (Object[] row : allocationRepository.countPerSupervisor(
                session, AllocationStatus.OCCUPIES_A_SEAT)) {
            taken.put((Long) row[0], (Long) row[1]);
        }
        return taken;
    }

    @Transactional(readOnly = true)
    public List<Allocation> inboxFor(String supervisorEmail) {
        return allocationRepository.findBySupervisorAndStatusOrderByRequestedAtAsc(
                supervisor(supervisorEmail), AllocationStatus.REQUESTED);
    }

    @Transactional(readOnly = true)
    public long loadFor(String supervisorEmail, Programme programme) {
        return allocationRepository.countBySupervisorAndSessionAndStatusIn(
                supervisor(supervisorEmail), activeSessionFor(programme), AllocationStatus.OCCUPIES_A_SEAT);
    }

    @Transactional(readOnly = true)
    public List<Allocation> cohortFor(Programme programme) {
        return allocationRepository.findBySessionOrderByRequestedAtDesc(activeSessionFor(programme));
    }

    private StudentProfile student(String email) {
        return studentProfileRepository.findByUserEmail(email)
                .orElseThrow(() -> new NotFoundException("Student profile for " + email + " not found"));
    }

    private SupervisorProfile supervisor(String email) {
        return supervisorProfileRepository.findByUserEmail(email)
                .orElseThrow(() -> new NotFoundException("Supervisor profile for " + email + " not found"));
    }

    private AcademicSession activeSessionFor(Programme programme) {
        return academicSessionRepository.findByProgrammeAndActiveTrue(programme)
                .orElseThrow(() -> new IllegalStateException(
                        "No active academic session for " + programme + "."));
    }

    private Allocation loadOwnedBySupervisor(Long allocationId, String supervisorEmail) {
        Allocation allocation = allocationRepository.findById(allocationId)
                .orElseThrow(() -> new NotFoundException("Allocation", allocationId));

        if (!allocationRepository.existsByIdAndSupervisorUserEmail(allocationId, supervisorEmail)) {
            throw new NotFoundException("Allocation", allocationId);
        }
        return allocation;
    }

    private void assertHasCapacity(SupervisorProfile supervisor, AcademicSession session) {
        long taken = allocationRepository.countBySupervisorAndSessionAndStatusIn(
                supervisor, session, AllocationStatus.OCCUPIES_A_SEAT);

        if (taken >= supervisor.getMaxStudents()) {
            throw new CapacityExceededException(
                    supervisor.getUser().getFullName(), taken, supervisor.getMaxStudents());
        }
    }
}
