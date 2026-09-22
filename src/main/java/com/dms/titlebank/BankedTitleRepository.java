package com.dms.titlebank;

import com.dms.user.SupervisorProfile;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BankedTitleRepository extends JpaRepository<BankedTitle, Long> {

    List<BankedTitle> findBySupervisorOrderByCreatedAtDesc(SupervisorProfile supervisor);

    long countBySupervisorAndStatus(SupervisorProfile supervisor, BankedTitleStatus status);

    boolean existsByIdAndSupervisorUserEmail(Long id, String email);

    @EntityGraph(attributePaths = {"supervisor", "supervisor.user"})
    List<BankedTitle> findByStatusOrderByCreatedAtDesc(BankedTitleStatus status);

    @EntityGraph(attributePaths = {"supervisor", "supervisor.user"})
    Optional<BankedTitle> findWithGraphById(Long id);

    /** The scholar's browse, scoped to what is on offer. */
    @EntityGraph(attributePaths = {"supervisor", "supervisor.user"})
    @Query("""
           select t from BankedTitle t
           where t.status = com.dms.titlebank.BankedTitleStatus.OPEN
             and (lower(t.title) like lower(concat('%', :q, '%'))
               or lower(t.domain) like lower(concat('%', :q, '%'))
               or lower(t.abstractText) like lower(concat('%', :q, '%')))
           order by t.createdAt desc
           """)
    List<BankedTitle> search(@Param("q") String q);
}
