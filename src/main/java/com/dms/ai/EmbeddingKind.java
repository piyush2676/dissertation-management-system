package com.dms.ai;

/** What an embedding row describes. Keeps topics and supervisors in one table. */
public enum EmbeddingKind {
    TOPIC,
    SUPERVISOR_INTERESTS,
    /** A passage of the institute guidelines, keyed by its position in the document. */
    REGULATION_PASSAGE
}
