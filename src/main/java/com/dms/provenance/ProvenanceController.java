package com.dms.provenance;

import com.dms.allocation.AllocationRepository;
import com.dms.common.NotFoundException;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.Set;

/**
 * The provenance record for one dissertation.
 *
 * <p>Mounted outside the role prefixes, like downloads and search: a student,
 * their guide, the coordinator and the admin all legitimately read this, so
 * access is decided by ownership rather than by URL. Issuing a certificate is
 * narrower -- only the coordinator or admin seals a record.
 */
@Controller
@RequestMapping("/provenance")
@RequiredArgsConstructor
public class ProvenanceController {

    private static final Set<String> PRIVILEGED = Set.of("ROLE_COORDINATOR", "ROLE_ADMIN");

    private final ProvenanceService provenanceService;
    private final CertificateService certificateService;
    private final CertificatePdfRenderer pdfRenderer;
    private final AllocationRepository allocationRepository;

    @GetMapping("/{allocationId}")
    public String timeline(@PathVariable Long allocationId,
                           Authentication authentication,
                           Model model) {

        requireAccess(allocationId, authentication);

        model.addAttribute("timeline", provenanceService.timelineFor(allocationId));
        model.addAttribute("canIssue", isPrivileged(authentication));
        model.addAttribute("verifyBase", certificateService.verifyUrl(""));
        return "provenance/timeline";
    }

    @PostMapping("/{allocationId}/certificate")
    public String issue(@PathVariable Long allocationId,
                        Authentication authentication,
                        RedirectAttributes redirectAttributes) {

        if (!isPrivileged(authentication)) {
            throw new NotFoundException("Allocation", allocationId);
        }

        Certificate certificate = certificateService.issue(allocationId, authentication.getName());
        redirectAttributes.addFlashAttribute("success",
                "Certificate " + certificate.getCode() + " issued, sealing the record as it stands.");
        return "redirect:/provenance/" + allocationId;
    }

    @GetMapping("/{allocationId}/certificate.pdf")
    public ResponseEntity<byte[]> download(@PathVariable Long allocationId,
                                           Authentication authentication) throws IOException {

        requireAccess(allocationId, authentication);

        Certificate certificate = allocationRepository.findById(allocationId)
                .flatMap(certificateService::findFor)
                .orElseThrow(() -> new NotFoundException("Certificate for allocation", allocationId));

        byte[] pdf = pdfRenderer.render(certificate);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdf.length)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + certificate.getCode() + ".pdf\"")
                .body(pdf);
    }

    /**
     * A record nobody may read reports as not found rather than forbidden, so a
     * stranger learns nothing about whether it exists.
     */
    private void requireAccess(Long allocationId, Authentication authentication) {
        if (isPrivileged(authentication)) {
            return;
        }
        String email = authentication.getName();
        boolean owns = allocationRepository.existsByIdAndStudentUserEmail(allocationId, email)
                || allocationRepository.existsByIdAndSupervisorUserEmail(allocationId, email);
        if (!owns) {
            throw new NotFoundException("Allocation", allocationId);
        }
    }

    private static boolean isPrivileged(Authentication authentication) {
        return AuthorityUtils.authorityListToSet(authentication.getAuthorities())
                .stream().anyMatch(PRIVILEGED::contains);
    }
}
