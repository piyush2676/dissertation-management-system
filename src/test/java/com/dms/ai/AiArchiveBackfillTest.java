package com.dms.ai;

import com.dms.topic.Topic;
import com.dms.topic.TopicRepository;
import com.dms.topic.TopicStatus;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiArchiveBackfillTest {

    @Mock private TopicRepository topicRepository;
    @Mock private EmbeddingService embeddingService;

    @InjectMocks private AiArchiveBackfill backfill;

    @Test
    void everyApprovedTopicIsIndexed() {
        when(topicRepository.findByStatus(TopicStatus.APPROVED)).thenReturn(List.of(topic(1L), topic(2L)));

        assertEquals(2, backfill.backfill());

        verify(embeddingService).embedAndStore(eq(EmbeddingKind.TOPIC), eq(1L), eq("Title 1. Abstract 1"));
        verify(embeddingService).embedAndStore(eq(EmbeddingKind.TOPIC), eq(2L), eq("Title 2. Abstract 2"));
    }

    @Test
    void theFirstFailureStopsTheRunRatherThanSpendingTheQuota() {
        when(topicRepository.findByStatus(TopicStatus.APPROVED))
                .thenReturn(List.of(topic(1L), topic(2L), topic(3L)));
        when(embeddingService.embedAndStore(any(), eq(1L), anyString()))
                .thenThrow(new AiUnavailableException("quota"));

        assertEquals(0, backfill.backfill());

        verify(embeddingService, times(1)).embedAndStore(any(), anyLong(), anyString());
    }

    @Test
    void withoutAKeyNothingIsRead() {
        when(embeddingService.isAvailable()).thenReturn(false);

        backfill.onReady();

        verifyNoInteractions(topicRepository);
        verify(embeddingService, never()).embedAndStore(any(), anyLong(), anyString());
    }

    private static Topic topic(Long id) {
        Topic topic = new Topic();
        topic.setId(id);
        topic.setTitle("Title " + id);
        topic.setAbstractText("Abstract " + id);
        return topic;
    }
}
