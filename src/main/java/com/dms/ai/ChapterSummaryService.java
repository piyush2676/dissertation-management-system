package com.dms.ai;

import com.dms.common.NotFoundException;
import com.dms.storage.StorageService;
import com.dms.submission.SubmissionService;
import com.dms.submission.SubmissionVersion;
import com.dms.submission.SubmissionVersionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Optional;

/**
 * A reviewer's first read of an uploaded chapter: what it claims, and what to ask.
 *
 * <p>Only the supervising guide may ask for one, and there is no student route --
 * this is a reading aid for the person reviewing, not feedback. The model is told
 * to summarise and to pose questions; it may not mark, band, grade or recommend,
 * which is the same line every AI feature here holds.
 *
 * <p>One summary per version, stored as an {@link AiReport} keyed by version id.
 * A version never changes after upload, so the summary cannot go stale, and a
 * second request reads the stored one instead of paying for another call.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChapterSummaryService {

    /** Enough for a long chapter's substance, well inside the model's input window. */
    static final int MAX_CHARS = 40_000;

    private static final String PROMPT = """
            You are helping a dissertation supervisor prepare to review a student's submitted
            document. You are NOT assessing it.

            Write plain text: no Markdown, no asterisks, no headings beyond the two labels below.

            Summary:
            About 200 words on what the document sets out to do, the method it describes, and
            what it reports or claims. Describe; do not evaluate.

            Questions for the reviewer:
            Five short questions a careful reviewer might put to the student about this
            document -- gaps to probe, claims to ask evidence for, choices to ask them to
            justify. Number them 1 to 5.

            Never give marks, a grade, a band, a pass or fail, or a recommendation, and do not
            call anything plagiarised. If the text is too short or garbled to summarise, say so
            in one sentence instead.

            The document text follows%s:

            %s
            """;

    private final SubmissionVersionRepository versionRepository;
    private final SubmissionService submissionService;
    private final StorageService storageService;
    private final AiReportRepository reportRepository;
    private final AiAvailability ai;

    @Value("${spring.ai.google.genai.chat.model:gemini-3.6-flash}")
    private String chatModelName;

    public boolean isAvailable() {
        return ai.chatAvailable();
    }

    /** The stored summary for a version, if one was generated. No call is made. */
    @Transactional(readOnly = true)
    public Optional<AiReport> existing(Long versionId) {
        return reportRepository.findByKindAndRefId(ReportKind.CHAPTER_SUMMARY, versionId);
    }

    /**
     * Summarises the version for its student's supervising guide, or returns the
     * summary already on record. Anyone else gets a 404, the same answer a stranger
     * gets for the file itself.
     */
    @Transactional
    public AiReport summarise(String supervisorEmail, Long submissionId, Long versionId) {
        SubmissionVersion version = versionRepository.findWithGraphById(versionId)
                .filter(v -> v.getSubmission().getId().equals(submissionId))
                .orElseThrow(() -> new NotFoundException("Submission version", versionId));
        if (!submissionService.isSupervisorOf(submissionId, supervisorEmail)) {
            throw new NotFoundException("Submission version", versionId);
        }

        Optional<AiReport> stored = existing(versionId);
        if (stored.isPresent()) {
            return stored.get();
        }

        if (!"application/pdf".equals(version.getContentType())) {
            throw new AiUnavailableException(
                    "Summaries work on PDF uploads only. Ask the student to file a PDF of this version.");
        }

        String text = extractText(storageService.load(version.getStoragePath()));
        if (text.isBlank()) {
            throw new AiUnavailableException(
                    "This PDF has no text layer -- it looks scanned -- so there is nothing to summarise.");
        }

        boolean truncated = text.length() > MAX_CHARS;
        String prompt = PROMPT.formatted(
                truncated ? " (the first " + MAX_CHARS + " characters only)" : "",
                truncated ? text.substring(0, MAX_CHARS) : text);

        String body;
        try {
            body = ai.chatModel().call(prompt);
        } catch (AiUnavailableException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("chapter summary failed for version {}: {}", versionId, ex.getMessage());
            throw new AiUnavailableException("The model did not answer. Try again shortly.", ex);
        }
        if (body == null || body.isBlank()) {
            throw new AiUnavailableException("The model returned an empty summary. Try again shortly.");
        }

        AiReport report = new AiReport();
        report.setKind(ReportKind.CHAPTER_SUMMARY);
        report.setRefId(versionId);
        report.setModel(chatModelName);
        report.setBody(body.strip() + (truncated
                ? "\n\n(Summarised from the first " + MAX_CHARS + " characters of the document.)" : ""));
        report.setAiGenerated(true);
        report.setCreatedAt(Instant.now());
        return reportRepository.save(report);
    }

    /** Plain text of the PDF, whitespace collapsed. Empty for a scanned document. */
    static String extractText(Resource pdf) {
        try (InputStream in = pdf.getInputStream();
             PDDocument document = Loader.loadPDF(in.readAllBytes())) {
            return new PDFTextStripper().getText(document).replaceAll("\\s+", " ").strip();
        } catch (IOException ex) {
            throw new AiUnavailableException("That file could not be read as a PDF.", ex);
        }
    }
}
