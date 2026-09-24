package com.dms.ai;

import com.dms.common.NotFoundException;
import com.dms.storage.StorageService;
import com.dms.submission.Submission;
import com.dms.submission.SubmissionService;
import com.dms.submission.SubmissionVersion;
import com.dms.submission.SubmissionVersionRepository;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.core.io.ByteArrayResource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChapterSummaryServiceTest {

    private static final String GUIDE = "guide1@college.edu";

    @Mock private SubmissionVersionRepository versionRepository;
    @Mock private SubmissionService submissionService;
    @Mock private StorageService storageService;
    @Mock private AiReportRepository reportRepository;
    @Mock private AiAvailability ai;

    @InjectMocks private ChapterSummaryService service;

    @Test
    void onlyTheSupervisingGuideGetsASummary() {
        version("application/pdf");
        when(submissionService.isSupervisorOf(3L, "guide2@college.edu")).thenReturn(false);

        assertThrows(NotFoundException.class, () -> service.summarise("guide2@college.edu", 3L, 9L));
        verifyNoInteractions(ai, storageService);
    }

    @Test
    void aVersionFromAnotherSubmissionIsNotFound() {
        version("application/pdf");

        assertThrows(NotFoundException.class, () -> service.summarise(GUIDE, 4L, 9L));
        verifyNoInteractions(ai);
    }

    @Test
    void aStoredSummaryIsReturnedWithoutAnotherCall() {
        version("application/pdf");
        guide();
        AiReport stored = new AiReport();
        when(reportRepository.findByKindAndRefId(ReportKind.CHAPTER_SUMMARY, 9L)).thenReturn(Optional.of(stored));

        assertSame(stored, service.summarise(GUIDE, 3L, 9L));
        verifyNoInteractions(ai, storageService);
    }

    @Test
    void aWordFileIsRefusedWithTheReason() {
        version("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        guide();

        AiUnavailableException ex = assertThrows(AiUnavailableException.class, () -> service.summarise(GUIDE, 3L, 9L));

        assertTrue(ex.getMessage().startsWith("Summaries work on PDF uploads only"));
    }

    @Test
    void aScannedPdfWithNoTextIsRefusedRatherThanSummarisedFromNothing() throws IOException {
        version("application/pdf");
        guide();
        when(storageService.load("12/3/v1.pdf")).thenReturn(new ByteArrayResource(pdf(null)));

        AiUnavailableException ex = assertThrows(AiUnavailableException.class, () -> service.summarise(GUIDE, 3L, 9L));

        assertTrue(ex.getMessage().contains("no text layer"));
        verifyNoInteractions(ai);
    }

    @Test
    void thePdfTextGoesToTheModelAndTheSummaryIsKeptAgainstTheVersion() throws IOException {
        version("application/pdf");
        guide();
        when(storageService.load("12/3/v1.pdf"))
                .thenReturn(new ByteArrayResource(pdf("Federated learning for campus energy forecasting")));
        ChatModel chat = mock(ChatModel.class);
        when(ai.chatModel()).thenReturn(chat);
        when(chat.call(anyString())).thenReturn("Summary: it forecasts load.\n\nQuestions for the reviewer: 1. Why LSTM?");
        when(reportRepository.save(any(AiReport.class))).thenAnswer(inv -> inv.getArgument(0));

        AiReport report = service.summarise(GUIDE, 3L, 9L);

        assertEquals(ReportKind.CHAPTER_SUMMARY, report.getKind());
        assertEquals(9L, report.getRefId());
        assertTrue(report.isAiGenerated());
        verify(chat).call(argThat((String prompt) ->
                prompt.contains("Federated learning for campus energy forecasting")
                        && prompt.contains("Never give marks, a grade, a band")));
    }

    private void version(String contentType) {
        Submission submission = new Submission();
        submission.setId(3L);
        SubmissionVersion version = new SubmissionVersion();
        version.setId(9L);
        version.setSubmission(submission);
        version.setContentType(contentType);
        version.setStoragePath("12/3/v1.pdf");
        when(versionRepository.findWithGraphById(9L)).thenReturn(Optional.of(version));
    }

    private void guide() {
        when(submissionService.isSupervisorOf(3L, GUIDE)).thenReturn(true);
        lenient().when(reportRepository.findByKindAndRefId(ReportKind.CHAPTER_SUMMARY, 9L)).thenReturn(Optional.empty());
    }

    /** A one-page PDF with the given line of text, or with no text at all. */
    private static byte[] pdf(String line) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            if (line != null) {
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    content.newLineAtOffset(72, 700);
                    content.showText(line);
                    content.endText();
                }
            }
            document.save(out);
            return out.toByteArray();
        }
    }
}
