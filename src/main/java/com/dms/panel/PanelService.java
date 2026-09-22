package com.dms.panel;

import com.dms.allocation.Allocation;
import com.dms.allocation.AllocationRepository;
import com.dms.allocation.AllocationService;
import com.dms.allocation.AllocationStatus;
import com.dms.audit.DomainEvents;
import com.dms.common.NotFoundException;
import com.dms.user.Programme;
import com.dms.user.Role;
import com.dms.user.User;
import com.dms.user.UserRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Who assesses a student's review presentations, and who may not.
 *
 * <p>The guidelines put the supervisor's own assessment inside the internal marks
 * (§7.1), so the guide keeps scoring. What this service protects is the average:
 * the mark sheet is the mean of every examiner's total, so a guide appointed to
 * their own student's panel would count twice. That, and only that, is the
 * conflict rule.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PanelService {

    /** Guidelines §2.2.1: two faculty members per panel. Advisory, not enforced. */
    public static final int EXPECTED_SIZE = 2;

    private final PanelMemberRepository panelRepository;
    private final AllocationRepository allocationRepository;
    private final AllocationService allocationService;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher events;

    // ---- coordinator --------------------------------------------------------

    /** Every placed student in the programme with their panel, plus who is appointable. */
    @Transactional(readOnly = true)
    public PanelBoard board(Programme programme) {
        List<Allocation> cohort;
        String label;
        try {
            cohort = allocationService.cohortFor(programme).stream()
                    .filter(a -> a.getStatus().occupiesASeat())
                    .toList();
            label = cohort.isEmpty() ? null : cohort.get(0).getSession().getLabel();
        } catch (IllegalStateException ex) {
            return new PanelBoard(programme, null, List.of(), List.of());
        }

        // One read for the whole cohort rather than one per student.
        Map<Long, List<PanelBoard.MemberRow>> byAllocation = new HashMap<>();
        for (PanelMember member : panelRepository.findByAllocationInOrderByAddedAtAsc(cohort)) {
            byAllocation.computeIfAbsent(member.getAllocation().getId(), k -> new ArrayList<>())
                    .add(new PanelBoard.MemberRow(member.getMember().getId(),
                            member.getMember().getFullName(),
                            member.getMember().getEmail(),
                            member.getAddedAt()));
        }

        List<PanelBoard.Row> rows = new ArrayList<>();
        for (Allocation allocation : cohort) {
            rows.add(new PanelBoard.Row(
                    allocation.getId(),
                    allocation.getStudent().getRollNo(),
                    allocation.getStudent().getUser().getFullName(),
                    allocation.getSupervisor().getUser().getId(),
                    allocation.getSupervisor().getUser().getFullName(),
                    allocation.getCoSupervisor() == null ? null : allocation.getCoSupervisor().getUser().getId(),
                    allocation.getTopic() == null ? null : allocation.getTopic().getTitle(),
                    byAllocation.getOrDefault(allocation.getId(), List.of())));
        }
        return new PanelBoard(programme, label, rows, appointable());
    }

    /** Faculty who may sit on a panel: anyone holding SUPERVISOR or REVIEWER. */
    @Transactional(readOnly = true)
    public List<PanelBoard.MemberRow> appointable() {
        return userRepository.findAll().stream()
                .filter(u -> u.getRoles().contains(Role.REVIEWER) || u.getRoles().contains(Role.SUPERVISOR))
                .sorted((a, b) -> a.getFullName().compareToIgnoreCase(b.getFullName()))
                .map(u -> new PanelBoard.MemberRow(u.getId(), u.getFullName(), u.getEmail(), null))
                .toList();
    }

    public PanelMember add(String coordinatorEmail, Long allocationId, Long memberId) {
        Allocation allocation = allocationRepository.findWithGraphById(allocationId)
                .orElseThrow(() -> new NotFoundException("Allocation", allocationId));
        if (!allocation.getStatus().occupiesASeat()) {
            throw new IllegalStateException("That student does not have a live allocation.");
        }
        User member = userRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException("User", memberId));
        if (!member.getRoles().contains(Role.REVIEWER) && !member.getRoles().contains(Role.SUPERVISOR)) {
            throw new IllegalArgumentException("Only faculty can sit on a review panel.");
        }
        if (member.getId().equals(allocation.getSupervisor().getUser().getId())) {
            throw new IllegalArgumentException(
                    "The guide already scores their own student; putting them on the panel would count one opinion twice.");
        }
        if (allocation.getCoSupervisor() != null
                && member.getId().equals(allocation.getCoSupervisor().getUser().getId())) {
            throw new IllegalArgumentException("The co-supervisor cannot sit on this student's panel.");
        }

        User coordinator = userRepository.findByEmail(coordinatorEmail)
                .orElseThrow(() -> new NotFoundException("User " + coordinatorEmail + " not found"));

        PanelMember panelMember = new PanelMember();
        panelMember.setAllocation(allocation);
        panelMember.setMember(member);
        panelMember.setAddedBy(coordinator);
        panelMember.setAddedAt(Instant.now());
        try {
            PanelMember saved = panelRepository.save(panelMember);
            events.publishEvent(new DomainEvents.PanelMemberAdded(
                    coordinatorEmail, allocationId, member.getFullName()));
            return saved;
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalStateException(member.getFullName() + " is already on this panel.");
        }
    }

    /**
     * Removing a member does not remove their marks. An evaluation is a record of
     * what someone judged at a moment; it is the coordinator's to revisit on the
     * mark sheet, not this service's to delete quietly.
     */
    public void remove(String coordinatorEmail, Long allocationId, Long memberId) {
        Allocation allocation = allocationRepository.findWithGraphById(allocationId)
                .orElseThrow(() -> new NotFoundException("Allocation", allocationId));
        PanelMember member = panelRepository.findByAllocationAndMemberId(allocation, memberId)
                .orElseThrow(() -> new NotFoundException("Panel member", memberId));
        String name = member.getMember().getFullName();
        panelRepository.delete(member);
        events.publishEvent(new DomainEvents.PanelMemberRemoved(coordinatorEmail, allocationId, name));
    }

    // ---- panel member -------------------------------------------------------

    /** True when this person sits on that student's panel, which is what lets them score. */
    @Transactional(readOnly = true)
    public boolean isPanelMember(Long allocationId, String email) {
        return panelRepository.existsByAllocationIdAndMemberEmail(allocationId, email);
    }

    @Transactional(readOnly = true)
    public List<Assignment> assignmentsFor(String email) {
        List<Assignment> assignments = new ArrayList<>();
        for (PanelMember member : panelRepository.assignmentsFor(email, AllocationStatus.OCCUPIES_A_SEAT)) {
            Allocation allocation = member.getAllocation();
            assignments.add(new Assignment(
                    allocation.getId(),
                    allocation.getStudent().getRollNo(),
                    allocation.getStudent().getUser().getFullName(),
                    allocation.getSupervisor().getUser().getFullName(),
                    allocation.getTopic() == null ? null : allocation.getTopic().getTitle()));
        }
        return assignments;
    }

    public record Assignment(Long allocationId, String rollNo, String studentName,
                             String supervisorName, String topicTitle) {
    }

    // ---- readiness ----------------------------------------------------------

    @Transactional(readOnly = true)
    public List<PanelBoard.MemberRow> membersOf(Allocation allocation) {
        return panelRepository.findByAllocationOrderByAddedAtAsc(allocation).stream()
                .map(p -> new PanelBoard.MemberRow(p.getMember().getId(), p.getMember().getFullName(),
                        p.getMember().getEmail(), p.getAddedAt()))
                .toList();
    }
}
