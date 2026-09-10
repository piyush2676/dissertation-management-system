package com.dms.ai;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EmbeddingRepository extends JpaRepository<Embedding, Long> {

    Optional<Embedding> findByKindAndRefId(EmbeddingKind kind, Long refId);

    List<Embedding> findByKind(EmbeddingKind kind);

    List<Embedding> findByKindAndRefIdIn(EmbeddingKind kind, Collection<Long> refIds);

    long countByKind(EmbeddingKind kind);
}
