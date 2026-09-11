package com.dms.provenance;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * The digest is the whole mechanism, so these are the properties it must have.
 *
 * <p>It has to be stable — the same facts must hash the same way months later, or
 * every certificate fails for no reason. And it has to be sensitive — any change
 * to a sealed fact must change it, or a certificate proves nothing.
 */
class ProvenanceDigestTest {

    @Test
    void theSameFactsAlwaysHashTheSameWay() {
        assertEquals(ProvenanceService.digestOf(facts()), ProvenanceService.digestOf(facts()));
    }

    @Test
    void itIsASha256Hex() {
        assertEquals(64, ProvenanceService.digestOf(facts()).length());
    }

    @Test
    void changingAMarkChangesTheDigest() {
        Map<String, String> tampered = facts();
        tampered.put("marks", "guide1@college.edu=95.00");

        assertNotEquals(ProvenanceService.digestOf(facts()), ProvenanceService.digestOf(tampered));
    }

    @Test
    void swappingASubmittedFileChangesTheDigest() {
        Map<String, String> tampered = facts();
        tampered.put("versions", "Synopsis v1 0000000000000000000000000000000000000000000000000000000000000000");

        assertNotEquals(ProvenanceService.digestOf(facts()), ProvenanceService.digestOf(tampered),
                "the certificate must cover the work, not only the marks");
    }

    @Test
    void changingTheStudentChangesTheDigest() {
        Map<String, String> tampered = facts();
        tampered.put("student", "Someone Else");

        assertNotEquals(ProvenanceService.digestOf(facts()), ProvenanceService.digestOf(tampered));
    }

    @Test
    void changingTheTopicChangesTheDigest() {
        Map<String, String> tampered = facts();
        tampered.put("topic", "A different dissertation entirely");

        assertNotEquals(ProvenanceService.digestOf(facts()), ProvenanceService.digestOf(tampered));
    }

    @Test
    void fieldsCannotBeSmuggledAcrossTheBoundary() {
        // Without a separator that cannot occur in a value, "ab" + "c" and "a" + "bc"
        // would hash alike and two different records could share one digest.
        Map<String, String> first = new LinkedHashMap<>();
        first.put("a", "xy");
        first.put("b", "z");

        Map<String, String> second = new LinkedHashMap<>();
        second.put("a", "x");
        second.put("b", "yz");

        assertNotEquals(ProvenanceService.digestOf(first), ProvenanceService.digestOf(second));
    }

    @Test
    void aMissingValueIsNotTheSameAsAMissingField() {
        Map<String, String> withEmpty = new LinkedHashMap<>();
        withEmpty.put("viva", "");

        Map<String, String> withNull = new LinkedHashMap<>();
        withNull.put("viva", null);

        // Null and empty mean the same thing here -- not scheduled -- so they must
        // agree, or a certificate would fail after an unrelated refactor.
        assertEquals(ProvenanceService.digestOf(withEmpty), ProvenanceService.digestOf(withNull));
    }

    @Test
    void anEmptyRecordStillHashes() {
        assertEquals(64, ProvenanceService.digestOf(new LinkedHashMap<>()).length());
    }

    private Map<String, String> facts() {
        Map<String, String> facts = new LinkedHashMap<>();
        facts.put("rollNo", "24MCS001");
        facts.put("student", "Avika Singh");
        facts.put("programme", "MTECH");
        facts.put("session", "2025-26");
        facts.put("guide", "Dr A Sharma");
        facts.put("topic", "Adaptive load balancing for edge inference clusters");
        facts.put("versions", "Synopsis v1 cfa3181c1ee3 | Synopsis v2 eed678deb2fc");
        facts.put("marks", "guide1@college.edu=80.00");
        facts.put("viva", "SCHEDULED 2027-02-11T05:00:00Z");
        return facts;
    }
}
