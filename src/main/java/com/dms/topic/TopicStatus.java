package com.dms.topic;

import java.util.Map;
import java.util.Set;

public enum TopicStatus {
    DRAFT,

    PROPOSED,

    APPROVED,

    CHANGES_REQUESTED,

    REJECTED;

    private static final Map<TopicStatus, Set<TopicStatus>> TRANSITIONS = Map.of(
            DRAFT,             Set.of(PROPOSED),
            PROPOSED,          Set.of(APPROVED, CHANGES_REQUESTED, REJECTED),
            CHANGES_REQUESTED, Set.of(PROPOSED),
            // Phase 16: an approved title can be sent back for revision, but only
            // through ChangeRequestService.approve -- section 4.11's title change.
            // TopicService.decide cannot reach it: a decision starts from PROPOSED.
            APPROVED,          Set.of(CHANGES_REQUESTED),
            REJECTED,          Set.of()
    );
    public boolean canTransitionTo(TopicStatus target) {
        return TRANSITIONS.get(this).contains(target);
    }
    public boolean isTerminal() {
        return TRANSITIONS.get(this).isEmpty();
    }
    public Set<TopicStatus> allowedNext() {
        return TRANSITIONS.get(this);
    }
}
