package com.dms.account;

import com.dms.user.User;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface AuthTokenRepository extends JpaRepository<AuthToken, Long> {

    @EntityGraph(attributePaths = "user")
    Optional<AuthToken> findByTokenHashAndPurpose(String tokenHash, TokenPurpose purpose);

    /**
     * Burns any outstanding token of the same purpose for that user. Issuing a new
     * link must invalidate the old one, or an intercepted earlier mail stays live.
     */
    @Modifying
    @Query("""
           update AuthToken t set t.usedAt = :now
           where t.user = :user and t.purpose = :purpose and t.usedAt is null
           """)
    int invalidateOutstanding(@Param("user") User user,
                              @Param("purpose") TokenPurpose purpose,
                              @Param("now") Instant now);

    long countByUserAndPurposeAndUsedAtIsNull(User user, TokenPurpose purpose);
}
