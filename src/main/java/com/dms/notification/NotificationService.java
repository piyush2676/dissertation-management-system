package com.dms.notification;

import com.dms.user.User;
import com.dms.user.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private static final int PAGE_SIZE = 25;

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    /**
     * Records one notification. Never throws at the caller: a listener failing to
     * notify must not be able to fail the action it was reacting to.
     */
    @Transactional
    public void notify(User recipient, NotificationType type, String title, String body, String link) {
        if (recipient == null) {
            return;
        }
        try {
            Notification notification = new Notification();
            notification.setRecipient(recipient);
            notification.setType(type);
            notification.setTitle(trim(title, 160));
            notification.setBody(trim(body, 500));
            notification.setLink(trim(link, 255));
            notification.setCreatedAt(Instant.now());
            notificationRepository.save(notification);
        } catch (RuntimeException ex) {
            log.warn("could not record a {} notification for {}: {}",
                    type, recipient.getEmail(), ex.getMessage());
        }
    }

    /** Convenience for listeners that hold an email rather than the User. */
    @Transactional
    public void notifyByEmail(String email, NotificationType type, String title, String body, String link) {
        userRepository.findByEmail(email)
                .ifPresent(user -> notify(user, type, title, body, link));
    }

    @Transactional(readOnly = true)
    public long unreadCountFor(String email) {
        return notificationRepository.countByRecipientEmailAndReadAtIsNull(email);
    }

    @Transactional(readOnly = true)
    public Page<Notification> pageFor(String email, int page) {
        return notificationRepository.findByRecipientEmailOrderByCreatedAtDesc(
                email, PageRequest.of(Math.max(0, page), PAGE_SIZE));
    }

    /**
     * Marks one as read and hands back where it points, so the controller can
     * mark-and-follow in a single click.
     */
    @Transactional
    public Optional<String> readAndFollow(String email, Long id) {
        Optional<Notification> found = notificationRepository.findByIdAndRecipientEmail(id, email);
        found.ifPresent(notification -> {
            if (notification.getReadAt() == null) {
                notification.setReadAt(Instant.now());
                notificationRepository.save(notification);
            }
        });
        return found.map(Notification::getLink);
    }

    @Transactional
    public int markAllRead(String email) {
        return notificationRepository.markAllRead(email, Instant.now());
    }

    private static String trim(String value, int max) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.length() <= max ? stripped : stripped.substring(0, max - 1) + "…";
    }
}
