package com.dms.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Turns text into a stored vector, and refuses to pay twice for the same text.
 *
 * <p>Every embedding row carries the digest of the text it was built from. If a
 * topic abstract has not changed, the stored vector is reused and no call is made
 * -- which matters on a free tier with a request quota.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmbeddingService {

    private final EmbeddingRepository embeddingRepository;
    private final AiAvailability ai;

    @Value("${spring.ai.google.genai.embedding.text.model:text-embedding-004}")
    private String modelName;

    public boolean isAvailable() {
        return ai.embeddingsAvailable();
    }

    /**
     * Embeds the text and stores it against (kind, refId), replacing any earlier
     * vector for that record. Returns the stored row.
     */
    @Transactional
    public Embedding embedAndStore(EmbeddingKind kind, Long refId, String text) {
        String cleaned = clean(text);
        if (cleaned.isBlank()) {
            throw new AiUnavailableException("There is no text to analyse yet.");
        }

        String hash = sha256(cleaned);
        Optional<Embedding> existing = embeddingRepository.findByKindAndRefId(kind, refId);

        if (existing.isPresent() && hash.equals(existing.get().getSourceHash())) {
            return existing.get();
        }

        float[] raw = callModel(cleaned);

        Embedding embedding = existing.orElseGet(Embedding::new);
        embedding.setKind(kind);
        embedding.setRefId(refId);
        embedding.setModel(modelName);
        embedding.setDimensions(raw.length);
        embedding.setVector(box(raw));
        embedding.setSourceHash(hash);
        embedding.setCreatedAt(Instant.now());

        log.debug("embedded {} {} as {} dimensions", kind, refId, raw.length);
        return embeddingRepository.save(embedding);
    }

    /** Embeds without storing -- for a query vector that is not itself a record. */
    public double[] embedQuery(String text) {
        String cleaned = clean(text);
        if (cleaned.isBlank()) {
            throw new AiUnavailableException("There is no text to analyse yet.");
        }
        return unbox(callModel(cleaned));
    }

    @Transactional(readOnly = true)
    public Optional<Embedding> find(EmbeddingKind kind, Long refId) {
        return embeddingRepository.findByKindAndRefId(kind, refId);
    }

    private float[] callModel(String text) {
        try {
            return ai.embeddingModel().embed(text);
        } catch (AiUnavailableException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            // Quota, network, malformed key. Advisory features must degrade, never
            // take a page down with them.
            log.warn("embedding call failed: {}", ex.getMessage());
            throw new AiUnavailableException("The embedding service did not respond. Try again shortly.", ex);
        }
    }

    /** Models have an input ceiling and whitespace carries no meaning here. */
    private static String clean(String text) {
        if (text == null) {
            return "";
        }
        String collapsed = text.replaceAll("\\s+", " ").strip();
        return collapsed.length() <= 8000 ? collapsed : collapsed.substring(0, 8000);
    }

    private static List<Double> box(float[] raw) {
        List<Double> out = new ArrayList<>(raw.length);
        for (float f : raw) {
            out.add((double) f);
        }
        return out;
    }

    private static double[] unbox(float[] raw) {
        double[] out = new double[raw.length];
        for (int i = 0; i < raw.length; i++) {
            out[i] = raw[i];
        }
        return out;
    }

    private static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required but unavailable", ex);
        }
    }
}
