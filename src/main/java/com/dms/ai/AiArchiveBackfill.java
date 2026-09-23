package com.dms.ai;

import com.dms.topic.Topic;
import com.dms.topic.TopicRepository;
import com.dms.topic.TopicStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Brings the overlap archive up to date when the application starts.
 *
 * <p>{@link AiIndexingListener} only indexes a topic at the moment it is approved,
 * so every topic approved while the AI was switched off -- which, on a database
 * that ran for months without a key, is all of them -- never reached the archive,
 * and the overlap check reported "nothing close" against an empty corpus. This
 * closes that gap once per start. {@code embedAndStore} skips a topic whose text
 * and model are unchanged, so after the first run it costs no calls.
 *
 * <p>It stops at the first failure rather than trying every topic: a quota or key
 * problem will fail them all, and the next start picks up where this one stopped.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AiArchiveBackfill {

    private final TopicRepository topicRepository;
    private final EmbeddingService embeddingService;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (!embeddingService.isAvailable()) {
            return;
        }
        int done = backfill();
        log.info("overlap archive: {} approved topics indexed or already current", done);
    }

    /** Returns how many approved topics are now in the archive. */
    int backfill() {
        int done = 0;
        for (Topic topic : topicRepository.findByStatus(TopicStatus.APPROVED)) {
            try {
                embeddingService.embedAndStore(EmbeddingKind.TOPIC, topic.getId(),
                        topic.getTitle() + ". " + topic.getAbstractText());
                done++;
            } catch (RuntimeException ex) {
                log.warn("overlap archive backfill stopped at topic {}: {}", topic.getId(), ex.getMessage());
                break;
            }
        }
        return done;
    }
}
