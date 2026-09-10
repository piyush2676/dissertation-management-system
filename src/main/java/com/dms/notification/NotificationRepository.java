package com.dms.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByRecipientEmailOrderByCreatedAtDesc(String email, Pageable pageable);

    long countByRecipientEmailAndReadAtIsNull(String email);

    Optional<Notification> findByIdAndRecipientEmail(Long id, String email);

    @Modifying
    @Query("""
           update Notification n set n.readAt = :now
           where n.recipient.email = :email and n.readAt is null
           """)
    int markAllRead(@Param("email") String email, @Param("now") Instant now);
}
