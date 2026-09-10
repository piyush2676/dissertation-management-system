package com.dms.submission;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Set;

/**
 * The only route to an uploaded file.
 *
 * <p>The uploads directory is never exposed as a static resource, so a storage
 * path is not a URL and cannot be guessed into. Every read comes through here and
 * is re-checked against the requester.
 *
 * <p>Mounted outside /student and /supervisor deliberately: those prefixes carry
 * URL-level role rules, and this one file is legitimately read by a student, their
 * guide, the coordinator and the admin. Authorisation is by ownership, not by role.
 */
@Controller
@RequestMapping("/files/submissions")
@RequiredArgsConstructor
public class SubmissionDownloadController {

    private static final Set<String> PRIVILEGED = Set.of("ROLE_COORDINATOR", "ROLE_ADMIN");

    private final SubmissionService submissionService;

    @GetMapping("/versions/{versionId}")
    public ResponseEntity<Resource> download(@PathVariable Long versionId, Authentication authentication) {
        SubmissionDownload file = submissionService.download(
                versionId, authentication.getName(), isPrivileged(authentication));

        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(file.contentType());
        } catch (RuntimeException ex) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(file.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + file.filename() + "\"")
                .body(file.resource());
    }

    private static boolean isPrivileged(Authentication authentication) {
        return AuthorityUtils.authorityListToSet(authentication.getAuthorities())
                .stream().anyMatch(PRIVILEGED::contains);
    }
}
