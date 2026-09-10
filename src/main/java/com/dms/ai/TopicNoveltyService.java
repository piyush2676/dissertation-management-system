package com.dms.ai;

import com.dms.common.NotFoundException;
import com.dms.topic.Topic;
import com.dms.topic.TopicRepository;
import com.dms.topic.TopicStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reports how much a proposed topic overlaps the department's own approved
 * archive, and asks a model to describe the overlap in words.
 *
 * <p>Two hard constraints, both from the design and both enforced here rather
 * than in the template. The model never approves or rejects anything -- it is
 * given the retrieved neighbours and asked only to describe overlap and gaps. And
 * the similarity number is honestly labelled as overlap with the department
 * archive; it is not plagiarism detection and there is no web-wide corpus behind it.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TopicNoveltyService {

    private static final int NEIGHBOURS = 5;

    private static final String PROMPT = """
            You are advising a postgraduate dissertation committee. You are NOT deciding anything.

            A student has proposed this dissertation topic:

            Title: %s

            Abstract: %s

            These are the most similar topics already approved in this department's archive,
            with their measured similarity to the proposal:

            %s

            Write a short advisory note, at most 180 words, covering:
            1. Where the proposal overlaps work the department has already approved.
            2. What appears genuinely new about it.
            3. One concrete suggestion for sharpening the contribution.

            Do not approve or reject the topic and do not recommend that anyone else does.
            Do not describe this as plagiarism -- the only corpus here is this department's
            own archive. If the listed topics are not meaningfully similar, say so plainly.
            """;

    private final TopicRepository topicRepository;
    private final EmbeddingService embeddingService;
    private final SimilarityProvider similarityProvider;
    private final AiReportRepository reportRepository;
    private final AiAvailability ai;

    @Value("${spring.ai.google.genai.chat.model:gemini-2.5-flash}")
    private String chatModelName;

    public boolean embeddingsAvailable() {
        return embeddingService.isAvailable();
    }

    public boolean narrativeAvailable() {
        return ai.chatAvailable();
    }

    /** Any previously generated report, so the page can show it without a new call. */
    @Transactional(readOnly = true)
    public Optional<AiReport> existingReport(Long topicId) {
        return reportRepository.findByKindAndRefId(ReportKind.TOPIC_NOVELTY, topicId);
    }

    /**
     * Embeds the topic, finds its nearest approved neighbours, and -- when a chat
     * model is configured -- asks for a written note. The similarity half works on
     * its own; the narrative is additive.
     */
    @Transactional
    public NoveltyResult check(Long topicId) {
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new NotFoundException("Topic", topicId));

        String text = topic.getTitle() + ". " + topic.getAbstractText();
        embeddingService.embedAndStore(EmbeddingKind.TOPIC, topic.getId(), text);

        double[] query = embeddingService.embedQuery(text);
        List<Scored<Long>> hits = similarityProvider.mostSimilar(
                query, EmbeddingKind.TOPIC, NEIGHBOURS, topic.getId());

        List<NoveltyResult.Neighbour> neighbours = new ArrayList<>();
        for (Scored<Long> hit : hits) {
            topicRepository.findById(hit.value())
                    .filter(t -> t.getStatus() == TopicStatus.APPROVED)
                    .ifPresent(t -> neighbours.add(new NoveltyResult.Neighbour(
                            t.getId(), t.getTitle(), hit.score())));
        }

        String narrative = null;
        if (ai.chatAvailable()) {
            narrative = narrate(topic, neighbours);
        }

        return new NoveltyResult(topic.getId(), topic.getTitle(), neighbours, narrative, chatModelName);
    }

    private String narrate(Topic topic, List<NoveltyResult.Neighbour> neighbours) {
        StringBuilder listing = new StringBuilder();
        if (neighbours.isEmpty()) {
            listing.append("(none -- the archive holds no approved topic close to this one)");
        } else {
            for (NoveltyResult.Neighbour n : neighbours) {
                listing.append("- ").append(n.title())
                        .append(" (similarity ").append(n.percent()).append("%)\n");
            }
        }

        String prompt = PROMPT.formatted(topic.getTitle(), topic.getAbstractText(), listing.toString().strip());

        String body;
        try {
            body = ai.chatModel().call(prompt);
        } catch (AiUnavailableException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("novelty narrative failed: {}", ex.getMessage());
            // The similarity result is still useful on its own, so this is not fatal.
            return null;
        }

        if (body == null || body.isBlank()) {
            return null;
        }

        AiReport report = reportRepository.findByKindAndRefId(ReportKind.TOPIC_NOVELTY, topic.getId())
                .orElseGet(AiReport::new);
        report.setKind(ReportKind.TOPIC_NOVELTY);
        report.setRefId(topic.getId());
        report.setModel(chatModelName);
        report.setBody(body.strip());
        report.setAiGenerated(true);
        report.setCreatedAt(Instant.now());
        reportRepository.save(report);

        return body.strip();
    }
}
