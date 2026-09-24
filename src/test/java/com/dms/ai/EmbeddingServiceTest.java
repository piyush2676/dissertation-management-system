package com.dms.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmbeddingServiceTest {

    private static final String MODEL = "gemini-embedding-001";

    @Mock private EmbeddingRepository embeddingRepository;
    @Mock private AiAvailability ai;

    @InjectMocks private EmbeddingService service;

    @BeforeEach
    void setModel() {
        ReflectionTestUtils.setField(service, "modelName", MODEL);
    }

    @Test
    void unchangedTextFromTheSameModelIsNotEmbeddedAgain() {
        Embedding first = storeOnce();

        when(embeddingRepository.findByKindAndRefId(EmbeddingKind.TOPIC, 7L)).thenReturn(Optional.of(first));
        Embedding second = service.embedAndStore(EmbeddingKind.TOPIC, 7L, "Federated forecasting");

        assertSame(first, second);
        verify(ai, times(1)).embeddingModel();
    }

    @Test
    void aRowFromAnEarlierModelIsEmbeddedAgain() {
        Embedding old = storeOnce();
        old.setModel("text-embedding-004");

        when(embeddingRepository.findByKindAndRefId(EmbeddingKind.TOPIC, 7L)).thenReturn(Optional.of(old));
        Embedding redone = service.embedAndStore(EmbeddingKind.TOPIC, 7L, "Federated forecasting");

        assertEquals(MODEL, redone.getModel());
        verify(ai, times(2)).embeddingModel();
    }

    @Test
    void aBatchSkipsCurrentRowsAndSendsTheRestInOneRequest() {
        Embedding current = storeOnce();
        EmbeddingModel model = ai.embeddingModel();
        when(embeddingRepository.findByKindAndRefIdIn(eq(EmbeddingKind.TOPIC), any())).thenReturn(List.of(current));
        when(model.embed(anyList())).thenReturn(List.of(new float[] {0.4f, 0.5f, 0.6f}));

        Map<Long, String> texts = new LinkedHashMap<>();
        texts.put(7L, "Federated forecasting");
        texts.put(8L, "Edge scheduling");
        service.embedAndStoreAll(EmbeddingKind.TOPIC, texts);

        verify(model).embed(List.of("Edge scheduling"));
    }

    /** Embeds the text once from an empty store and returns the saved row. */
    private Embedding storeOnce() {
        EmbeddingModel model = mock(EmbeddingModel.class);
        when(ai.embeddingModel()).thenReturn(model);
        when(model.embed(anyString())).thenReturn(new float[] {0.1f, 0.2f, 0.3f});
        when(embeddingRepository.findByKindAndRefId(EmbeddingKind.TOPIC, 7L)).thenReturn(Optional.empty());
        when(embeddingRepository.save(any(Embedding.class))).thenAnswer(inv -> inv.getArgument(0));

        Embedding saved = service.embedAndStore(EmbeddingKind.TOPIC, 7L, "Federated forecasting");
        assertEquals(3, saved.getDimensions());
        return saved;
    }
}
