package com.dms.ai;

import com.dms.user.SupervisorProfile;
import com.dms.user.SupervisorProfileRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ranks guides by how close their stated research interests sit to a topic.
 *
 * <p>Advisory only. It reorders a list the student was already free to choose
 * from; it does not filter anyone out, and capacity and the guide's own consent
 * still decide the outcome.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SupervisorMatchingService {

    private final SupervisorProfileRepository supervisorProfileRepository;
    private final EmbeddingService embeddingService;
    private final SimilarityProvider similarityProvider;

    public boolean isAvailable() {
        return embeddingService.isAvailable();
    }

    /**
     * Best matches for the given topic text, best first. Guides with no stated
     * interests are left out of the ranking rather than scored as a bad match --
     * an empty profile is missing data, not a poor fit.
     */
    @Transactional
    public List<Scored<SupervisorProfile>> match(String topicText, int topK) {
        List<SupervisorProfile> supervisors = supervisorProfileRepository.findAllBy();
        if (supervisors.isEmpty()) {
            return List.of();
        }

        indexInterests(supervisors);

        double[] query = embeddingService.embedQuery(topicText);

        Map<Long, SupervisorProfile> byId = new HashMap<>();
        for (SupervisorProfile supervisor : supervisors) {
            byId.put(supervisor.getId(), supervisor);
        }

        List<Scored<SupervisorProfile>> ranked = new ArrayList<>();
        for (Scored<Long> hit : similarityProvider.mostSimilar(
                query, EmbeddingKind.SUPERVISOR_INTERESTS, topK, null)) {
            SupervisorProfile supervisor = byId.get(hit.value());
            if (supervisor != null) {
                ranked.add(new Scored<>(supervisor, hit.score()));
            }
        }
        return ranked;
    }

    /**
     * Embeds any guide whose interests are not yet indexed, or whose text has
     * changed. Cheap after the first run -- the stored digest short-circuits it.
     */
    @Transactional
    public void indexInterests(List<SupervisorProfile> supervisors) {
        for (SupervisorProfile supervisor : supervisors) {
            String interests = supervisor.getResearchInterests();
            if (interests == null || interests.isBlank()) {
                continue;
            }
            try {
                embeddingService.embedAndStore(
                        EmbeddingKind.SUPERVISOR_INTERESTS, supervisor.getId(), interests);
            } catch (AiUnavailableException ex) {
                log.warn("could not index interests for supervisor {}: {}",
                        supervisor.getId(), ex.getMessage());
                throw ex;
            }
        }
    }
}
