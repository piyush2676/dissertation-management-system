package com.dms.ai;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EmbeddingRepository extends JpaRepository<Embedding, Long> {

    Optional<Embedding> findByKindAndRefId(EmbeddingKind kind, Long refId);

    List<Embedding> findByKind(EmbeddingKind kind);

    List<Embedding> findByKindAndRefIdIn(EmbeddingKind kind, Collection<Long> refIds);

    long countByKind(EmbeddingKind kind);

    /** Drops rows past the end of a corpus that shrank, e.g. an edited guidelines file. */
    @Modifying
    @Query("delete from Embedding e where e.kind = :kind and e.refId >= :from")
    int deleteByKindFrom(@Param("kind") EmbeddingKind kind, @Param("from") Long from);
}
