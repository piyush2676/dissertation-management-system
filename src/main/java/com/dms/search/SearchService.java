package com.dms.search;

import com.dms.allocation.AllocationRepository;
import com.dms.allocation.AllocationStatus;
import com.dms.submission.SubmissionRepository;
import com.dms.topic.TopicRepository;
import com.dms.user.StudentProfileRepository;
import com.dms.user.UserRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Search, scoped to what the searcher is already allowed to see.
 *
 * <p>The scoping is the whole design. Every query carries its own where clause --
 * a guide's search runs against the allocations they supervise, a student's
 * against their own records -- rather than searching widely and filtering the
 * results afterwards. A filter can be forgotten on one branch; a where clause
 * cannot return the row in the first place.
 *
 * <p>Search is therefore not a way around the ownership rules the pages enforce.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SearchService {

    /** Enough to be useful in a dropdown, short enough not to become a report. */
    private static final int PER_GROUP = 6;

    /** One character matches half the database and helps nobody. */
    private static final int MIN_LENGTH = 2;

    private final TopicRepository topicRepository;
    private final AllocationRepository allocationRepository;
    private final SubmissionRepository submissionRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final UserRepository userRepository;

    public SearchResults search(String rawQuery, String email, Set<String> roles) {
        String q = rawQuery == null ? "" : rawQuery.strip();
        if (q.length() < MIN_LENGTH) {
            return SearchResults.empty(q);
        }

        List<SearchResults.Group> groups = new ArrayList<>();
        boolean privileged = roles.contains("ROLE_COORDINATOR") || roles.contains("ROLE_ADMIN");

        if (roles.contains("ROLE_STUDENT")) {
            addOwnTopics(groups, q, email);
            addOwnSubmissions(groups, q, email);
        }

        if (roles.contains("ROLE_SUPERVISOR")) {
            addSupervisedStudents(groups, q, email);
            addProposedTopics(groups, q, email);
            addSupervisedSubmissions(groups, q, email);
        }

        if (privileged) {
            addAllStudents(groups, q);
            addAllTopics(groups, q);
        }

        if (roles.contains("ROLE_ADMIN")) {
            addAccounts(groups, q);
        }

        groups.removeIf(SearchResults.Group::isEmpty);
        return new SearchResults(q, groups);
    }

    // ---- student ------------------------------------------------------------

    private void addOwnTopics(List<SearchResults.Group> groups, String q, String email) {
        List<SearchResults.Hit> hits = topicRepository.searchOwnedBy(q, email).stream()
                .limit(PER_GROUP)
                .map(t -> new SearchResults.Hit(
                        t.getTitle(),
                        "Your topic",
                        "/student/topic",
                        t.getStatus().name().replace('_', ' ')))
                .toList();
        groups.add(new SearchResults.Group("My topic", hits));
    }

    private void addOwnSubmissions(List<SearchResults.Group> groups, String q, String email) {
        List<SearchResults.Hit> hits = submissionRepository.searchOwnedBy(q, email).stream()
                .limit(PER_GROUP)
                .map(s -> new SearchResults.Hit(
                        s.getMilestone().getName(),
                        "Version " + s.getCurrentVersionNo(),
                        "/student/submissions/" + s.getId(),
                        s.getStatus().name().replace('_', ' ')))
                .toList();
        groups.add(new SearchResults.Group("My submissions", hits));
    }

    // ---- guide --------------------------------------------------------------

    private void addSupervisedStudents(List<SearchResults.Group> groups, String q, String email) {
        List<SearchResults.Hit> hits = allocationRepository
                .searchSupervised(q, email, AllocationStatus.OCCUPIES_A_SEAT).stream()
                .limit(PER_GROUP)
                .map(a -> new SearchResults.Hit(
                        a.getStudent().getUser().getFullName(),
                        a.getStudent().getRollNo()
                                + (a.getTopic() == null ? "" : " · " + a.getTopic().getTitle()),
                        "/supervisor/evaluate/" + a.getId(),
                        null))
                .toList();
        groups.add(new SearchResults.Group("My students", hits));
    }

    private void addProposedTopics(List<SearchResults.Group> groups, String q, String email) {
        List<SearchResults.Hit> hits = topicRepository.searchProposedTo(q, email).stream()
                .limit(PER_GROUP)
                .map(t -> new SearchResults.Hit(
                        t.getTitle(),
                        t.getStudent().getUser().getFullName() + " · " + t.getStudent().getRollNo(),
                        "/supervisor/topics",
                        t.getStatus().name().replace('_', ' ')))
                .toList();
        groups.add(new SearchResults.Group("Topics proposed to me", hits));
    }

    private void addSupervisedSubmissions(List<SearchResults.Group> groups, String q, String email) {
        List<SearchResults.Hit> hits = submissionRepository.searchSupervised(q, email).stream()
                .limit(PER_GROUP)
                .map(s -> new SearchResults.Hit(
                        s.getMilestone().getName(),
                        s.getAllocation().getStudent().getUser().getFullName()
                                + " · " + s.getAllocation().getStudent().getRollNo(),
                        "/supervisor/submissions/" + s.getId(),
                        s.getStatus().name().replace('_', ' ')))
                .toList();
        groups.add(new SearchResults.Group("Submissions to review", hits));
    }

    // ---- coordinator and admin ----------------------------------------------

    private void addAllStudents(List<SearchResults.Group> groups, String q) {
        List<SearchResults.Hit> hits = studentProfileRepository.search(q).stream()
                .limit(PER_GROUP)
                .map(s -> new SearchResults.Hit(
                        s.getUser().getFullName(),
                        s.getRollNo() + " · " + s.getProgramme().name().replace('_', ' '),
                        "/coordinator/allocate?programme=" + s.getProgramme().name(),
                        null))
                .toList();
        groups.add(new SearchResults.Group("Students", hits));
    }

    private void addAllTopics(List<SearchResults.Group> groups, String q) {
        List<SearchResults.Hit> hits = topicRepository.searchAll(q).stream()
                .limit(PER_GROUP)
                .map(t -> new SearchResults.Hit(
                        t.getTitle(),
                        t.getStudent().getUser().getFullName() + " · " + t.getStudent().getRollNo(),
                        "/coordinator/allocate",
                        t.getStatus().name().replace('_', ' ')))
                .toList();
        groups.add(new SearchResults.Group("Topics", hits));
    }

    private void addAccounts(List<SearchResults.Group> groups, String q) {
        List<SearchResults.Hit> hits = userRepository.search(q).stream()
                .limit(PER_GROUP)
                .map(u -> new SearchResults.Hit(
                        u.getFullName(),
                        u.getEmail(),
                        "/admin/users",
                        u.getRoles().isEmpty() ? null : u.getRoles().iterator().next().name()))
                .toList();
        groups.add(new SearchResults.Group("Accounts", hits));
    }
}
