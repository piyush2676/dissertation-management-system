package com.dms.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegulationsServiceTest {

    private static final List<RegulationCorpus.Passage> PASSAGES = List.of(
            new RegulationCorpus.Passage(0, "4.11 Change of supervisor", "Request it in writing."),
            new RegulationCorpus.Passage(1, "5.2 Similarity check", "Under ten percent."));

    @Mock private RegulationCorpus corpus;
    @Mock private EmbeddingService embeddingService;
    @Mock private SimilarityProvider similarityProvider;
    @Mock private AiAvailability ai;

    @InjectMocks private RegulationsService service;

    @Test
    void withoutTheDocumentNoCallIsMade() {
        when(corpus.isLoaded()).thenReturn(false);

        AiUnavailableException ex = assertThrows(AiUnavailableException.class, () -> service.ask("How?"));

        assertEquals("The guidelines document is not installed on this server.", ex.getMessage());
        verifyNoInteractions(embeddingService, ai);
    }

    @Test
    void theAnswerIsWrittenFromTheRetrievedPassagesAndCitesThem() {
        loaded();
        ChatModel chat = mock(ChatModel.class);
        when(ai.chatModel()).thenReturn(chat);
        when(chat.call(anyString())).thenReturn("Apply in writing [4.11 Change of supervisor].");

        RegulationsService.Answer answer = service.ask("  How do I change my supervisor? ");

        assertEquals("How do I change my supervisor?", answer.question());
        assertEquals("Apply in writing [4.11 Change of supervisor].", answer.text());
        assertEquals("4.11 Change of supervisor", answer.sources().get(0).heading());
        assertEquals(71, answer.sources().get(0).percent());
        verify(chat).call(argThat((String prompt) ->
                prompt.contains("[4.11 Change of supervisor]") && prompt.contains("Use ONLY the numbered excerpts")));
    }

    @Test
    void aModelFailureStillShowsThePassages() {
        loaded();
        ChatModel chat = mock(ChatModel.class);
        when(ai.chatModel()).thenReturn(chat);
        when(chat.call(anyString())).thenThrow(new RuntimeException("503"));

        RegulationsService.Answer answer = service.ask("How do I change my supervisor?");

        assertNull(answer.text());
        assertEquals(1, answer.sources().size());
    }

    @Test
    void indexingEmbedsEveryPassageAndTrimsRowsFromALongerEarlierFile() {
        when(corpus.passages()).thenReturn(PASSAGES);

        service.index();

        verify(embeddingService).embedAndStoreAll(eq(EmbeddingKind.REGULATION_PASSAGE), argThat((Map<Long, String> m) ->
                m.size() == 2 && m.get(0L).startsWith("4.11 Change of supervisor\n")));
        verify(embeddingService).trim(EmbeddingKind.REGULATION_PASSAGE, 2);
        assertTrue(service.indexed());
    }

    @Test
    void aFailedIndexRunIsRetriedRatherThanMarkedDone() {
        when(corpus.passages()).thenReturn(PASSAGES);
        when(embeddingService.embedAndStoreAll(eq(EmbeddingKind.REGULATION_PASSAGE), anyMap()))
                .thenThrow(new AiUnavailableException("quota"));

        service.index();

        assertEquals(false, service.indexed());
        verify(embeddingService, never()).trim(eq(EmbeddingKind.REGULATION_PASSAGE), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void theBackgroundIndexWaitsOutTheQuotaAndCarriesOn() {
        when(corpus.passages()).thenReturn(PASSAGES);
        when(embeddingService.embedAndStoreAll(eq(EmbeddingKind.REGULATION_PASSAGE), anyMap()))
                .thenThrow(new AiUnavailableException("429 quota"))
                .thenReturn(2);
        service.retryDelayMillis = 0;

        service.indexWithRetries();

        assertTrue(service.indexed());
        verify(embeddingService, org.mockito.Mockito.times(2))
                .embedAndStoreAll(eq(EmbeddingKind.REGULATION_PASSAGE), anyMap());
    }

    private void loaded() {
        when(corpus.isLoaded()).thenReturn(true);
        when(corpus.passages()).thenReturn(PASSAGES);
        when(corpus.passage(0)).thenReturn(PASSAGES.get(0));
        double[] query = {1.0};
        when(embeddingService.embedQuery("How do I change my supervisor?")).thenReturn(query);
        when(similarityProvider.mostSimilar(query, EmbeddingKind.REGULATION_PASSAGE,
                RegulationsService.PASSAGES_PER_ANSWER, null)).thenReturn(List.of(new Scored<>(0L, 0.71)));
    }
}
