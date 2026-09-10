package com.dms.web;

import com.dms.allocation.Allocation;
import com.dms.allocation.AllocationBoard;
import com.dms.allocation.AllocationRepository;
import com.dms.allocation.AllocationService;
import com.dms.allocation.AllocationStatus;
import com.dms.audit.AuditLogRepository;
import com.dms.review.ReviewService;
import com.dms.submission.StudentSubmissionBoard;
import com.dms.submission.SubmissionRepository;
import com.dms.submission.SubmissionService;
import com.dms.submission.SubmissionVersionRepository;
import com.dms.topic.Topic;
import com.dms.topic.TopicRepository;
import com.dms.topic.TopicService;
import com.dms.topic.TopicStatus;
import com.dms.user.Programme;
import com.dms.user.StudentProfileRepository;
import com.dms.user.SupervisorProfile;
import com.dms.user.SupervisorProfileRepository;
import com.dms.user.UserRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final TopicService topicService;
    private final AllocationService allocationService;
    private final SubmissionService submissionService;
    private final ReviewService reviewService;

    private final TopicRepository topicRepository;
    private final AllocationRepository allocationRepository;
    private final SubmissionRepository submissionRepository;
    private final SubmissionVersionRepository versionRepository;
    private final SupervisorProfileRepository supervisorProfileRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;

    public Dashboards.Student student(String email) {
        Optional<Topic> topic = topicService.currentTopicFor(email);
        Optional<Allocation> allocation = allocationService.currentAllocationFor(email);
        StudentSubmissionBoard board = submissionService.boardFor(email);

        String nextMilestone = null;
        var nextDue = board.rows().stream()
                .filter(r -> !r.started())
                .findFirst();
        if (nextDue.isPresent()) {
            nextMilestone = nextDue.get().name();
        }

        return new Dashboards.Student(
                board.sessionLabel(),
                topic.map(Topic::getTitle).orElse(null),
                topic.map(t -> t.getStatus().name()).orElse(null),
                allocation.map(a -> a.getSupervisor().getUser().getFullName()).orElse(null),
                allocation.map(a -> a.getStatus().name()).orElse(null),
                board.rows().size(),
                board.submittedCount(),
                board.approvedCount(),
                board.rows().stream().filter(StudentSubmissionBoard.MilestoneRow::awaitingGuide).count(),
                reviewService.openCountForStudent(email),
                nextMilestone,
                nextDue.map(StudentSubmissionBoard.MilestoneRow::dueDate).orElse(null));
    }

    public Dashboards.Supervisor supervisor(String email) {
        SupervisorProfile profile = supervisorProfileRepository.findByUserEmail(email).orElse(null);

        long supervised = allocationRepository.countBySupervisorUserEmailAndStatusIn(
                email, AllocationStatus.OCCUPIES_A_SEAT);

        return new Dashboards.Supervisor(
                topicService.pendingFor(email).size(),
                allocationService.inboxFor(email).size(),
                submissionService.pendingCountFor(email),
                supervised,
                profile == null ? 0 : profile.getMaxStudents());
    }

    /**
     * Summed across both programmes. A programme with no active session is skipped
     * rather than failing the page -- the allocation board is where that is fixed.
     */
    public Dashboards.Coordinator coordinator() {
        long students = 0;
        long placed = 0;
        long guidesFull = 0;
        String label = null;

        for (Programme programme : Programme.values()) {
            try {
                AllocationBoard board = allocationService.board(programme);
                students += board.unallocated().size() + board.allocated().size();
                placed += board.allocated().size();
                if (label == null) {
                    label = board.sessionLabel();
                }
                guidesFull = board.supervisors().stream()
                        .filter(AllocationBoard.SupervisorLoad::isFull).count();
            } catch (IllegalStateException ex) {
                // no active session for this programme
            }
        }

        return new Dashboards.Coordinator(
                label,
                students,
                placed,
                students - placed,
                supervisorProfileRepository.count(),
                guidesFull,
                submissionRepository.count());
    }

    public Dashboards.Admin admin() {
        return new Dashboards.Admin(
                userRepository.count(),
                studentProfileRepository.count(),
                supervisorProfileRepository.count(),
                topicRepository.count(),
                allocationRepository.count(),
                submissionRepository.count(),
                versionRepository.count(),
                auditLogRepository.count());
    }

    /** Approved topics across the department, newest first. Used on the admin page. */
    public List<Topic> recentApprovedTopics() {
        return topicRepository.findTop5ByStatusOrderByDecidedAtDesc(TopicStatus.APPROVED);
    }
}
