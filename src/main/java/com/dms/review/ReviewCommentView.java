package com.dms.review;

import java.time.Instant;

/** One remark, ready for a template. */
public record ReviewCommentView(
        Long commentId,
        String reviewerName,
        Integer pageNo,
        String body,
        boolean resolved,
        Instant createdAt,
        Instant resolvedAt) {

    public String pageLabel() {
        return pageNo == null ? "General" : "Page " + pageNo;
    }
}
