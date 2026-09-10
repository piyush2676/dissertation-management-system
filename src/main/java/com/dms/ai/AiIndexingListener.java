package com.dms.ai;

import com.dms.audit.DomainEvents;
import com.dms.topic.Topic;
import com.dms.topic.TopicRepository;
import com.dms.topic.TopicStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Indexes a topic into the archive the moment it is approved.
 *
 * <p>This is the design's "new side effect is a new listener" claim paying off:
 * nothing in TopicService knows the AI package exists.
 *
 * <p>After commit and in its own transaction, deliberately. Embedding is a network
 * call to a third party -- holding the approval transaction open across it, or
 * letting a quota error roll an approval back, would be indefensible. A failure
 * here costs a log line and the topic gets indexed on the next novelty check.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AiIndexingListener {

    private final TopicRepository topicRepository;
    private final EmbeddingService embeddingService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onTopicDecided(DomainEvents.TopicDecided event) {
        if (!TopicStatus.APPROVED.name().equals(event.to())) {
            return;
        }
        if (!embeddingService.isAvailable()) {
            return;
        }

        try {
            Topic topic = topicRepository.findById(event.entityId()).orElse(null);
            if (topic == null) {
                return;
            }
            embeddingService.embedAndStore(EmbeddingKind.TOPIC, topic.getId(),
                    topic.getTitle() + ". " + topic.getAbstractText());
            log.debug("indexed approved topic {} into the archive", topic.getId());
        } catch (RuntimeException ex) {
            log.warn("could not index topic {}: {}", event.entityId(), ex.getMessage());
        }
    }
}
