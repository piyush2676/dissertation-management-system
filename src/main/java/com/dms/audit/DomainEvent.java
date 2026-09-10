package com.dms.audit;

/**
 * A thing that happened, worth recording. Services publish these instead of
 * writing audit rows themselves, so a new side effect -- a notification, a
 * vector re-index -- is a new listener rather than an edit to the service.
 */
public interface DomainEvent {

    String actorEmail();

    /** Verb, past tense, screaming snake: TOPIC_APPROVED, SUBMISSION_FILED. */
    String action();

    String entityType();

    Long entityId();

    default String oldValue() {
        return null;
    }

    default String newValue() {
        return null;
    }
}
