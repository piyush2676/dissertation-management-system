package com.dms.recommendation;

import com.dms.allocation.Allocation;
import com.dms.allocation.AllocationRepository;
import com.dms.audit.DomainEvents;
import com.dms.common.NotFoundException;
import com.dms.user.User;
import com.dms.user.UserRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Annexure-6: the supervisor's confidential summary of a dissertation.
 *
 * <p>Two parties may read it -- the supervisor who wrote it and the coordinator's
 * office -- and the student is not one of them. That is enforced here rather than
 * by leaving a route off a page, so a guessed URL fails the same way a missing
 * one does.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class RecommendationService {

    private final RecommendationRepository recommendationRepository;
    private final AllocationRepository allocationRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher events;

    /** Files it, or revises what the supervisor filed earlier. Only their own students. */
    public Recommendation file(String supervisorEmail, Long allocationId, RecommendationForm form) {
        Allocation allocation = loadSupervised(allocationId, supervisorEmail);
        if (!allocation.getStatus().occupiesASeat()) {
            throw new IllegalStateException("That student is not currently allocated to you.");
        }
        User supervisor = userRepository.findByEmail(supervisorEmail)
                .orElseThrow(() -> new NotFoundException("User " + supervisorEmail + " not found"));

        Optional<Recommendation> existing = recommendationRepository.findByAllocation(allocation);
        Recommendation recommendation = existing.orElseGet(Recommendation::new);
        boolean revised = existing.isPresent();

        recommendation.setAllocation(allocation);
        recommendation.setVerdict(form.getVerdict());
        recommendation.setOrganisation(blankToNull(form.getOrganisation()));
        recommendation.setTechnicalContent(blankToNull(form.getTechnicalContent()));
        recommendation.setStrengths(blankToNull(form.getStrengths()));
        recommendation.setQueries(blankToNull(form.getQueries()));
        recommendation.setVivaQuestions(blankToNull(form.getVivaQuestions()));
        if (!revised) {
            recommendation.setSubmittedBy(supervisor);
            recommendation.setSubmittedAt(Instant.now());
        }
        recommendation.setUpdatedAt(Instant.now());

        Recommendation saved = recommendationRepository.save(recommendation);
        events.publishEvent(new DomainEvents.RecommendationFiled(
                supervisorEmail, allocationId, saved.getVerdict().name(), revised));
        return saved;
    }

    /** What the supervisor sees when they open the form again. */
    @Transactional(readOnly = true)
    public Optional<RecommendationView> forSupervisor(String supervisorEmail, Long allocationId) {
        Allocation allocation = loadSupervised(allocationId, supervisorEmail);
        return recommendationRepository.findByAllocation(allocation).map(r -> view(allocation, r));
    }

    @Transactional(readOnly = true)
    public Allocation supervisedAllocation(String supervisorEmail, Long allocationId) {
        return loadSupervised(allocationId, supervisorEmail);
    }

    /** The coordinator's office reads every sheet; no ownership narrowing applies. */
    @Transactional(readOnly = true)
    public RecommendationView forCoordinator(Long allocationId) {
        Allocation allocation = allocationRepository.findWithGraphById(allocationId)
                .orElseThrow(() -> new NotFoundException("Allocation", allocationId));
        Recommendation recommendation = recommendationRepository.findByAllocation(allocation)
                .orElseThrow(() -> new NotFoundException("Recommendation", allocationId));
        return view(allocation, recommendation);
    }

    @Transactional(readOnly = true)
    public Optional<Verdict> verdictFor(Allocation allocation) {
        return recommendationRepository.findByAllocation(allocation).map(Recommendation::getVerdict);
    }

    @Transactional(readOnly = true)
    public Optional<RecommendationView> viewFor(Allocation allocation) {
        return recommendationRepository.findByAllocation(allocation).map(r -> view(allocation, r));
    }

    private RecommendationView view(Allocation allocation, Recommendation r) {
        return new RecommendationView(
                allocation.getId(),
                allocation.getStudent().getRollNo(),
                allocation.getStudent().getUser().getFullName(),
                allocation.getTopic() == null ? null : allocation.getTopic().getThesisCode(),
                allocation.getTopic() == null ? null : allocation.getTopic().getTitle(),
                allocation.getSupervisor().getUser().getFullName(),
                r.getVerdict(),
                r.getOrganisation(),
                r.getTechnicalContent(),
                r.getStrengths(),
                r.getQueries(),
                r.getVivaQuestions(),
                r.getSubmittedBy().getFullName(),
                r.getSubmittedAt(),
                r.getUpdatedAt());
    }

    private Allocation loadSupervised(Long allocationId, String supervisorEmail) {
        Allocation allocation = allocationRepository.findWithGraphById(allocationId)
                .orElseThrow(() -> new NotFoundException("Allocation", allocationId));
        if (!allocationRepository.existsByIdAndSupervisorUserEmail(allocationId, supervisorEmail)) {
            throw new NotFoundException("Allocation", allocationId);
        }
        return allocation;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
