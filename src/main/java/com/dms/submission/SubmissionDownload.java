package com.dms.submission;

import org.springframework.core.io.Resource;

/** A file cleared for one requester, with the name it should download as. */
public record SubmissionDownload(Resource resource, String filename, String contentType, long sizeBytes) {
}
