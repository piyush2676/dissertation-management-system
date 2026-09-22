package com.dms.readiness;

import com.dms.allocation.Allocation;
import com.dms.allocation.AllocationRepository;
import com.dms.allocation.AllocationService;
import com.dms.common.NotFoundException;
import com.dms.evaluation.Evaluation;
import com.dms.evaluation.EvaluationRepository;
import com.dms.evaluation.EvaluationService;
import com.dms.evaluation.MarkSheet;
import com.dms.evaluation.RubricCriterion;
import com.dms.logbook.LogbookBoard;
import com.dms.logbook.LogbookService;
import com.dms.outcome.OutcomeBoard;
import com.dms.outcome.OutcomeIndexing;
import com.dms.outcome.OutcomeKind;
import com.dms.outcome.OutcomeService;
import com.dms.panel.PanelBoard;
import com.dms.panel.PanelService;
import com.dms.readiness.ReadinessLedger.Evidence;
import com.dms.recommendation.RecommendationService;
import com.dms.readiness.ReadinessLedger.Rule;
import com.dms.readiness.ReadinessLedger.State;
import com.dms.session.DeliverableType;
import com.dms.session.DissertationPhase;
import com.dms.session.Milestone;
import com.dms.session.MilestoneRepository;
import com.dms.submission.PlagiarismCheck;
import com.dms.submission.Submission;
import com.dms.submission.SubmissionRepository;
import com.dms.submission.SubmissionService;
import com.dms.submission.SubmissionStatus;
import com.dms.submission.SubmissionVersion;
import com.dms.submission.SubmissionVersionRepository;
import com.dms.user.Programme;
import com.dms.user.StudentProfile;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Assembles the readiness ledger: each guideline requirement with the fact that
 * meets it. Reads only; the one rule that is enforced is enforced by VivaService
 * asking {@link #internalMarksMet(Allocation)}.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReadinessService {

    /** §4.4: the indexings that make a journal paper count. */
    static final Set<OutcomeIndexing> JOURNAL_INDEXINGS = Set.of(OutcomeIndexing.SCI, OutcomeIndexing.SCOPUS);
    /** §4.4: the indexings that make a conference paper count. */
    static final Set<OutcomeIndexing> CONFERENCE_INDEXINGS = Set.of(OutcomeIndexing.SCOPUS, OutcomeIndexing.IEEE);

    private final AllocationRepository allocationRepository;
    private final AllocationService allocationService;
    private final EvaluationService evaluationService;
    private final EvaluationRepository evaluationRepository;
    private final SubmissionRepository submissionRepository;
    private final SubmissionVersionRepository versionRepository;
    private final SubmissionService submissionService;
    private final MilestoneRepository milestoneRepository;
    private final OutcomeService outcomeService;
    private final LogbookService logbookService;
    private final PanelService panelService;
    private final RecommendationService recommendationService;

    public Optional<ReadinessLedger> ledgerForStudent(String studentEmail) {
        return allocationService.currentAllocationFor(studentEmail)
                .filter(a -> a.getStatus().occupiesASeat())
                .map(a -> ledgerFor(a.getId()));
    }

    public ReadinessLedger ledgerFor(Long allocationId) {
        Allocation allocation = allocationRepository.findWithGraphById(allocationId)
                .orElseThrow(() -> new NotFoundException("Allocation", allocationId));
        return ledgerFor(allocation);
    }

    /** One row per placed student in the programme, for the coordinator's overview. */
    public List<ReadinessLedger> ledgersFor(Programme programme) {
        List<ReadinessLedger> ledgers = new ArrayList<>();
        try {
            for (Allocation allocation : allocationService.cohortFor(programme)) {
                if (allocation.getStatus().occupiesASeat()) {
                    ledgers.add(ledgerFor(allocation));
                }
            }
        } catch (IllegalStateException ex) {
            // no active session for this programme: an empty overview, not an error page
        }
        return ledgers;
    }

    /** The hard gate: guidelines §7.1. */
    public boolean internalMarksMet(Allocation allocation) {
        return internalMarks(allocation).met();
    }

    // ---- assembly -----------------------------------------------------------

    ReadinessLedger ledgerFor(Allocation allocation) {
        StudentProfile student = allocation.getStudent();
        DissertationPhase phase = DissertationPhase.forSemester(student.getProgramme(), student.getSemester())
                .orElse(null);

        List<Rule> rules = new ArrayList<>();
        rules.add(internalMarks(allocation));
        rules.addAll(deliverables(allocation));
        rules.add(publication(allocation));
        rules.add(plagiarism(allocation));
        rules.add(logbook(allocation));
        rules.add(panel(allocation));
        rules.add(recommendation(allocation));

        return new ReadinessLedger(
                allocation.getId(),
                student.getRollNo(),
                student.getUser().getFullName(),
                allocation.getTopic() == null ? null : allocation.getTopic().getThesisCode(),
                allocation.getTopic() == null ? null : allocation.getTopic().getTitle(),
                allocation.getSupervisor().getUser().getFullName(),
                phase,
                rules);
    }

    /** §7.1: at least half the internal marks, averaged across examiners, against the phase maximum. */
    Rule internalMarks(Allocation allocation) {
        List<RubricCriterion> rubric = evaluationService.rubricFor(allocation);
        int maxTotal = rubric.stream().mapToInt(RubricCriterion::getWeightage).sum();
        List<Evaluation> evaluations = evaluationRepository.findByAllocation(allocation);
        List<Evidence> evidence = new ArrayList<>();
        for (Evaluation e : evaluations) {
            evidence.add(new Evidence(e.getTotal().toPlainString() + " / " + maxTotal,
                    e.getExaminer().getEmail(), e.getSubmittedAt(), null));
        }
        if (maxTotal == 0) {
            return new Rule(ReadinessLedger.INTERNAL_MARKS, "Internal marks", "§7.1",
                    State.NOT_MET, "No rubric applies to this student yet.", evidence);
        }
        if (evaluations.isEmpty()) {
            return new Rule(ReadinessLedger.INTERNAL_MARKS, "Internal marks", "§7.1",
                    State.NOT_MET, "Not yet marked.", evidence);
        }
        BigDecimal average = evaluations.stream().map(Evaluation::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(evaluations.size()), 2, RoundingMode.HALF_UP);
        BigDecimal percent = average.multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(maxTotal), 1, RoundingMode.HALF_UP);
        boolean met = percent.compareTo(MarkSheet.PASS_PERCENT) >= 0;
        return new Rule(ReadinessLedger.INTERNAL_MARKS, "Internal marks", "§7.1",
                met ? State.MET : State.NOT_MET,
                "Average " + average.toPlainString() + " / " + maxTotal + " (" + percent.toPlainString()
                        + "%); " + MarkSheet.PASS_PERCENT.toPlainString() + "% is the minimum for the external viva.",
                evidence);
    }

    /**
     * §2.2.3: the documents, one rule each. A document is met when the slot that
     * collects it is APPROVED; the evidence is the approved version's digest. The
     * two research papers are read from verified outcomes, not from uploads.
     */
    List<Rule> deliverables(Allocation allocation) {
        Map<DeliverableType, Milestone> slots = new EnumMap<>(DeliverableType.class);
        for (Milestone m : milestoneRepository.findBySessionOrderBySequenceNoAsc(allocation.getSession())) {
            if (m.getDeliverable() != null) {
                slots.putIfAbsent(m.getDeliverable(), m);
            }
        }
        Map<Long, Submission> byMilestone = new HashMap<>();
        for (Submission s : submissionRepository.findByAllocationOrderByMilestoneSequenceNoAsc(allocation)) {
            byMilestone.put(s.getMilestone().getId(), s);
        }

        List<Rule> rules = new ArrayList<>();
        for (DeliverableType type : DeliverableType.values()) {
            String code = "DOC_" + type.name();
            Milestone slot = slots.get(type);
            if (slot == null) {
                rules.add(new Rule(code, type.getLabel(), "§2.2.3", State.NOT_APPLICABLE,
                        "No review slot collects this document this session.", List.of()));
                continue;
            }
            Submission submission = byMilestone.get(slot.getId());
            if (submission == null || submission.getStatus() != SubmissionStatus.APPROVED) {
                String summary = submission == null
                        ? "Not filed against " + slot.getName() + "."
                        : "Filed against " + slot.getName() + "; " + submission.getStatus().name().toLowerCase().replace('_', ' ') + ".";
                rules.add(new Rule(code, type.getLabel(), "§2.2.3", State.NOT_MET, summary, List.of()));
                continue;
            }
            SubmissionVersion latest = versionRepository.findFirstBySubmissionOrderByVersionNoDesc(submission).orElse(null);
            List<Evidence> evidence = new ArrayList<>();
            if (latest != null) {
                evidence.add(new Evidence("v" + latest.getVersionNo() + " approved",
                        submission.getDecidedBy() == null ? null : submission.getDecidedBy().getEmail(),
                        submission.getDecidedAt(), latest.getSha256()));
            }
            rules.add(new Rule(code, type.getLabel(), "§2.2.3", State.MET,
                    "Approved against " + slot.getName() + ".", evidence));
        }

        // Research papers 1 and 2: the first two verified, achieved papers, in the order reported.
        List<OutcomeBoard.Row> papers = outcomeService.countedRowsFor(allocation).stream()
                .filter(r -> r.kind().isPaper())
                .toList();
        for (int i = 0; i < 2; i++) {
            String code = "PAPER_" + (i + 1);
            String title = "Research paper " + (i + 1);
            if (papers.size() > i) {
                OutcomeBoard.Row p = papers.get(i);
                rules.add(new Rule(code, title, "§2.2.3", State.MET,
                        p.title() + " — " + p.kind().getLabel().toLowerCase() + ", " + p.indexing().getLabel()
                                + ", " + p.status().getLabel().toLowerCase() + ".",
                        List.of(new Evidence("Verified outcome", p.verifiedByName(), p.verifiedAt(), p.reference()))));
            } else {
                rules.add(new Rule(code, title, "§2.2.3", State.NOT_MET,
                        "No verified paper on record yet.", List.of()));
            }
        }
        return rules;
    }

    /** §4.4: one SCI/Scopus journal paper, or two Scopus/IEEE conference papers. Verified and achieved only. */
    Rule publication(Allocation allocation) {
        List<OutcomeBoard.Row> counted = outcomeService.countedRowsFor(allocation);
        List<OutcomeBoard.Row> journals = counted.stream()
                .filter(r -> r.kind() == OutcomeKind.JOURNAL_PAPER && JOURNAL_INDEXINGS.contains(r.indexing()))
                .toList();
        List<OutcomeBoard.Row> conferences = counted.stream()
                .filter(r -> r.kind() == OutcomeKind.CONFERENCE_PAPER && CONFERENCE_INDEXINGS.contains(r.indexing()))
                .toList();
        List<Evidence> evidence = new ArrayList<>();
        for (OutcomeBoard.Row r : journals) {
            evidence.add(new Evidence("Journal: " + r.title() + " (" + r.indexing().getLabel() + ")",
                    r.verifiedByName(), r.verifiedAt(), r.reference()));
        }
        for (OutcomeBoard.Row r : conferences) {
            evidence.add(new Evidence("Conference: " + r.title() + " (" + r.indexing().getLabel() + ")",
                    r.verifiedByName(), r.verifiedAt(), r.reference()));
        }
        boolean met = !journals.isEmpty() || conferences.size() >= 2;
        return new Rule(ReadinessLedger.PUBLICATION, "Publication requirement", "§4.4, §7.2",
                met ? State.MET : State.NOT_MET,
                journals.size() + " qualifying journal paper(s), " + conferences.size()
                        + " qualifying conference paper(s); one journal or two conferences required.",
                evidence);
    }

    /** §8.3: the latest version of the final thesis, under 10% similarity and 0% AI, on the guide's reading. */
    Rule plagiarism(Allocation allocation) {
        Submission thesis = null;
        for (Submission s : submissionRepository.findByAllocationOrderByMilestoneSequenceNoAsc(allocation)) {
            if (s.getMilestone().getDeliverable() == DeliverableType.FINAL_THESIS) {
                thesis = s;
            }
        }
        if (thesis == null) {
            return new Rule(ReadinessLedger.PLAGIARISM, "Similarity report", "§8.3", State.NOT_MET,
                    "No final thesis filed yet.", List.of());
        }
        Optional<PlagiarismCheck> check = submissionService.latestCheckFor(thesis);
        SubmissionVersion latest = versionRepository.findFirstBySubmissionOrderByVersionNoDesc(thesis).orElse(null);
        if (check.isEmpty()) {
            return new Rule(ReadinessLedger.PLAGIARISM, "Similarity report", "§8.3", State.NOT_MET,
                    "No report recorded against the latest thesis version"
                            + (latest == null ? "." : " (v" + latest.getVersionNo() + ")."), List.of());
        }
        PlagiarismCheck c = check.get();
        return new Rule(ReadinessLedger.PLAGIARISM, "Similarity report", "§8.3",
                c.passes() ? State.MET : State.NOT_MET,
                "Similarity " + c.getSimilarityPercent().toPlainString() + "%, AI-generated "
                        + c.getAiPercent().toPlainString() + "% on v" + (latest == null ? "?" : latest.getVersionNo())
                        + "; under " + PlagiarismCheck.MAX_SIMILARITY_PERCENT.toPlainString() + "% and "
                        + PlagiarismCheck.MAX_AI_PERCENT.toPlainString() + "% required.",
                List.of(new Evidence((c.getTool() == null ? "Report" : c.getTool()) + " read by the guide",
                        c.getCheckedBy().getEmail(), c.getCheckedAt(), latest == null ? null : latest.getSha256())));
    }

    /** §2.2.1: a review panel of two, none of whom is the student's own guide. */
    Rule panel(Allocation allocation) {
        List<PanelBoard.MemberRow> members = panelService.membersOf(allocation);
        List<Evidence> evidence = new ArrayList<>();
        for (PanelBoard.MemberRow m : members) {
            evidence.add(new Evidence(m.name() + " appointed", m.email(), m.addedAt(), null));
        }
        boolean met = members.size() >= PanelService.EXPECTED_SIZE;
        return new Rule(ReadinessLedger.PANEL, "Review panel", "§2.2.1",
                met ? State.MET : State.NOT_MET,
                members.size() + " of " + PanelService.EXPECTED_SIZE + " faculty appointed; the student's own"
                        + " guide cannot sit on it.",
                evidence);
    }

    /** Annexure-6: the supervisor's summary sheet, and what it recommends. */
    Rule recommendation(Allocation allocation) {
        return recommendationService.viewFor(allocation)
                .map(view -> new Rule(ReadinessLedger.RECOMMENDATION, "Supervisor's recommendation", "Annexure-6",
                        view.verdict().clearsForDefence() ? State.MET : State.NOT_MET,
                        "[" + view.verdict().getCode() + "] " + view.verdict().getLabel() + ".",
                        List.of(new Evidence("Summary sheet filed", view.submittedByName(), view.submittedAt(), null))))
                .orElseGet(() -> new Rule(ReadinessLedger.RECOMMENDATION, "Supervisor's recommendation", "Annexure-6",
                        State.NOT_MET, "Not filed yet.", List.of()));
    }

    /** Annexure-4: the progress report card goes in with the thesis, so at least one countersigned meeting. */
    Rule logbook(Allocation allocation) {
        List<LogbookBoard.Row> signed = logbookService.signedRowsFor(allocation);
        List<Evidence> evidence = new ArrayList<>();
        for (LogbookBoard.Row r : signed) {
            evidence.add(new Evidence("Meeting " + r.meetingNo() + " countersigned", r.signedByName(), r.signedAt(), r.entryDigest()));
        }
        return new Rule(ReadinessLedger.LOGBOOK, "Progress report card", "Annexure-4",
                signed.isEmpty() ? State.NOT_MET : State.MET,
                signed.size() + " countersigned meeting(s) on record.",
                evidence);
    }
}
