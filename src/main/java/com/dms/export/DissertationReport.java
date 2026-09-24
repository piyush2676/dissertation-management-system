package com.dms.export;

import com.dms.user.Programme;

import java.time.Instant;
import java.util.List;

/**
 * Everything the head of the dissertation cell or of the department asks about one
 * programme's scholars, assembled once and then written as CSV, Excel or PDF --
 * so the three downloads can never disagree with each other.
 *
 * <p>Every cell is already a display string. The writers only lay text out; none
 * of them decides what a value means.
 */
public record DissertationReport(Programme programme, String sessionLabel, Instant generatedAt, List<Row> rows) {

    /** Column titles, in order, shared by all three formats. */
    public static final List<String> COLUMNS = List.of(
            "S.No", "Thesis ID", "Scholar", "Roll No", "Sign-in", "Semester", "Phase",
            "Thesis title", "Research domain", "Topic status", "Supervisor", "Co-supervisor", "Placement",
            "Milestones filed", "Latest submission", "Similarity", "Logbook meetings signed",
            "Verified outcomes", "Internal marks", "Grade band", "Examiners", "Readiness",
            "Viva", "Supervisor recommendation");

    public record Row(
            int serial,
            String thesisId,
            String scholar,
            String rollNo,
            String signIn,
            String semester,
            String phase,
            String thesisTitle,
            String researchDomain,
            String topicStatus,
            String supervisor,
            String coSupervisor,
            String placement,
            String milestonesFiled,
            String latestSubmission,
            String similarity,
            String logbookSigned,
            String outcomes,
            String internalMarks,
            String gradeBand,
            String examiners,
            String readiness,
            String viva,
            String recommendation) {

        /** The cells in {@link #COLUMNS} order. */
        public List<String> cells() {
            return List.of(String.valueOf(serial), thesisId, scholar, rollNo, signIn, semester, phase,
                    thesisTitle, researchDomain, topicStatus, supervisor, coSupervisor, placement,
                    milestonesFiled, latestSubmission, similarity, logbookSigned,
                    outcomes, internalMarks, gradeBand, examiners, readiness,
                    viva, recommendation);
        }
    }

    public String programmeLabel() {
        return programme == Programme.MTECH ? "M.Tech" : "Integrated M.Tech";
    }
}
