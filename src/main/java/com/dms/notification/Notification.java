package com.dms.notification;

import com.dms.user.User;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

/**
 * One thing a user should know about.
 *
 * <p>The link is stored rather than derived from the type and id, so changing a
 * route later cannot silently break every historical row's target.
 */
@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", nullable = false)
    User recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 48)
    NotificationType type;

    @Column(nullable = false, length = 160)
    String title;

    @Column(length = 500)
    String body;

    @Column(length = 255)
    String link;

    /** Null until the recipient has seen it. */
    @Column(name = "read_at")
    Instant readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt = Instant.now();

    public boolean isRead() {
        return readAt != null;
    }
}
