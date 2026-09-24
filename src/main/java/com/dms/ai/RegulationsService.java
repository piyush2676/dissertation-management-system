package com.dms.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Answers questions about the dissertation rules from the guidelines themselves.
 *
 * <p>Retrieval first, then the model: the question is embedded, the closest
 * passages are fetched, and the model is told to answer from those passages only
 * and to name the sections it used. The page prints the passages under the answer,
 * so a reader checks the rule in the institute's own words rather than trusting a
 * paraphrase. If the retrieved passages do not cover the question, the prompt
 * requires the answer to say so and point at the dissertation cell.
 *
 * <p>Questions are not stored. Unlike the overlap check, a question is not a
 * record of anything, and keeping them would be a log of who asked what.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RegulationsService {

    static final int PASSAGES_PER_ANSWER = 5;

    private static final String PROMPT = """
            You answer questions about the M.Tech and integrated M.Tech dissertation guidelines
            of one institute. Use ONLY the numbered excerpts below. Do not use outside knowledge
            and do not guess at rules the excerpts do not state.

            Question: %s

            Excerpts from the guidelines:

            %s

            Answer in plain text (no Markdown, no asterisks), in at most 150 words. After each
            statement, name the section it comes from in square brackets, using the section
            names exactly as given, for example [4.11 Change of supervisor]. If the excerpts do
            not answer the question, say that the guidelines excerpts found do not cover it and
            that the dissertation cell can advise. Never decide an individual student's case --
            describe what the rule says.
            """;

    public record Answer(String question, String text, List<Cited> sources, String model) {
    }

    public record Cited(String heading, String text, int percent) {
    }

    private final RegulationCorpus corpus;
    private final EmbeddingService embeddingService;
    private final SimilarityProvider similarityProvider;
    private final AiAvailability ai;

    @Value("${spring.ai.google.genai.chat.model:gemini-3.6-flash}")
    private String chatModelName;

    private volatile boolean indexed;

    static final int INDEX_ATTEMPTS = 6;

    /** A little over the free tier's one-minute quota window. Package-private for the tests. */
    long retryDelayMillis = 65_000;

    public boolean documentLoaded() {
        return corpus.isLoaded();
    }

    public boolean aiAvailable() {
        return ai.embeddingsAvailable() && ai.chatAvailable();
    }

    public boolean indexed() {
        return indexed;
    }

    public int passageCount() {
        return corpus.passages().size();
    }

    public String documentName() {
        return corpus.fileName();
    }

    /**
     * Indexes the passages off the startup thread: a first run embeds a hundred-odd
     * passages, which should not hold up the rest of the application. Later starts
     * find every digest current and make no call.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void indexInBackground() {
        if (!corpus.isLoaded() || !ai.embeddingsAvailable()) {
            return;
        }
        Thread worker = new Thread(this::indexWithRetries, "regulations-index");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * The free tier embeds 100 texts a minute, fewer than the guidelines have
     * passages, so the first run stops at the quota. Each stop keeps what it stored;
     * waiting out the minute and running again picks up from there.
     */
    void indexWithRetries() {
        for (int attempt = 1; attempt <= INDEX_ATTEMPTS && !indexed; attempt++) {
            index();
            if (!indexed && attempt < INDEX_ATTEMPTS) {
                try {
                    Thread.sleep(retryDelayMillis);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    /** Brings the passage embeddings in line with the file. Package-private for the tests. */
    synchronized void index() {
        if (indexed) {
            return;
        }
        Map<Long, String> texts = new LinkedHashMap<>();
        for (RegulationCorpus.Passage p : corpus.passages()) {
            texts.put((long) p.index(), p.embeddingText());
        }
        try {
            embeddingService.embedAndStoreAll(EmbeddingKind.REGULATION_PASSAGE, texts);
            embeddingService.trim(EmbeddingKind.REGULATION_PASSAGE, texts.size());
            indexed = true;
            log.info("regulations Q&A: {} passages indexed", texts.size());
        } catch (RuntimeException ex) {
            log.warn("regulations Q&A: indexing did not finish, will retry on the next question: {}", ex.getMessage());
        }
    }

    public Answer ask(String question) {
        String q = question == null ? "" : question.strip();
        if (q.isEmpty()) {
            throw new AiUnavailableException("Type a question first.");
        }
        if (!corpus.isLoaded()) {
            throw new AiUnavailableException("The guidelines document is not installed on this server.");
        }
        if (!indexed) {
            index();
            if (!indexed) {
                throw new AiUnavailableException("The guidelines are still being indexed. Try again in a minute.");
            }
        }

        double[] query = embeddingService.embedQuery(q);
        List<Cited> sources = new ArrayList<>();
        StringBuilder excerpts = new StringBuilder();
        for (Scored<Long> hit : similarityProvider.mostSimilar(
                query, EmbeddingKind.REGULATION_PASSAGE, PASSAGES_PER_ANSWER, null)) {
            RegulationCorpus.Passage p = corpus.passage(hit.value().intValue());
            if (p == null) {
                continue;
            }
            sources.add(new Cited(p.heading(), p.text(), (int) Math.round(hit.score() * 100)));
            excerpts.append(sources.size()).append(". [").append(p.heading()).append("]\n")
                    .append(p.text()).append("\n\n");
        }

        // The passages are worth showing on their own, so a model failure costs the
        // written answer, not the page: text comes back null and the sources stand.
        String body = null;
        try {
            body = ai.chatModel().call(PROMPT.formatted(q, excerpts.toString().strip()));
        } catch (AiUnavailableException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("regulations answer failed: {}", ex.getMessage());
        }
        return new Answer(q, body == null || body.isBlank() ? null : body.strip(), sources, chatModelName);
    }
}
