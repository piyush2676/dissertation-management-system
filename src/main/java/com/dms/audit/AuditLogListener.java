package com.dms.audit;

import com.dms.user.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Turns every published domain event into an audit row.
 *
 * <p>Deliberately a plain {@code @EventListener} and not an after-commit one: it
 * runs inside the publishing service's transaction, so an action that rolls back
 * leaves no trail entry, and no entry can describe something that never happened.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditLogListener {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    @EventListener
    public void on(DomainEvent event) {
        AuditLog entry = new AuditLog();
        entry.setActorEmail(event.actorEmail());
        entry.setActor(userRepository.findByEmail(event.actorEmail()).orElse(null));
        entry.setAction(event.action());
        entry.setEntityType(event.entityType());
        entry.setEntityId(event.entityId());
        entry.setOldValue(truncate(event.oldValue()));
        entry.setNewValue(truncate(event.newValue()));

        auditLogRepository.save(entry);
        log.debug("audit: {} {} {} by {}", event.action(), event.entityType(), event.entityId(),
                event.actorEmail());
    }

    /** The columns are 255; a long topic title must not fail the whole transaction. */
    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 255 ? value : value.substring(0, 252) + "...";
    }
}
