package com.dms.ai;

/** What an AI report is about. */
public enum ReportKind {
    TOPIC_NOVELTY,
    /** Keyed by submission version id: versions are immutable, so one summary each. */
    CHAPTER_SUMMARY
}
