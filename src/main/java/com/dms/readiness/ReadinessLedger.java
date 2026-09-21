package com.dms.readiness;

import com.dms.session.DissertationPhase;

import java.time.Instant;
import java.util.List;

/**
 * Every rule the guidelines put between a student and the external viva, with the
 * fact that satisfies it, who verified that fact, and when. This is the whole
 * argument against a locked button: a reader can see *why*, and can check it.
 *
 * <p>Only one rule is enforced in code — internal marks (§7.1). The others are
 * reported for the coordinator to weigh; see {@link #vivaEligible()}.
 */
public record ReadinessLedger(
        Long allocationId,
        String rollNo,
        String studentName,
        String thesisCode,
        String topicTitle,
        String supervisorName,
        DissertationPhase phase,
        List<Rule> rules) {

    public enum State {
        MET, NOT_MET, NOT_APPLICABLE
    }

    /** One requirement. basis names the guideline section it comes from. */
    public record Rule(String code, String title, String basis, State state, String summary,
                       List<Evidence> evidence) {
        public boolean met() {
            return state == State.MET;
        }
    }

    /** One fact behind a rule: what, on whose authority, when, pinned by what. */
    public record Evidence(String fact, String by, Instant at, String ref) {
    }

    public static final String INTERNAL_MARKS = "INTERNAL_MARKS";
    public static final String PUBLICATION = "PUBLICATION";
    public static final String PLAGIARISM = "PLAGIARISM";
    public static final String LOGBOOK = "LOGBOOK";

    public Rule rule(String code) {
        return rules.stream().filter(r -> r.code().equals(code)).findFirst().orElse(null);
    }

    /** The one hard gate, guidelines §7.1: half the internal marks. */
    public boolean vivaEligible() {
        Rule marks = rule(INTERNAL_MARKS);
        return marks != null && marks.met();
    }

    public long metCount() {
        return rules.stream().filter(Rule::met).count();
    }

    public long applicableCount() {
        return rules.stream().filter(r -> r.state() != State.NOT_APPLICABLE).count();
    }

    /** Every applicable rule met: the Annexure-5 checklist is complete. */
    public boolean complete() {
        return rules.stream().noneMatch(r -> r.state() == State.NOT_MET);
    }
}
