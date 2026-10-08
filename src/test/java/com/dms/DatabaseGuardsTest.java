package com.dms;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Rules the application promises and the database now enforces too (migration V21). Each test
 * runs in a transaction that is rolled back, so nothing it writes survives.
 */
@SpringBootTest
@Transactional
class DatabaseGuardsTest {

    @Autowired private JdbcTemplate jdbc;

    private long auditRow() {
        return jdbc.queryForObject("""
                insert into audit_log (actor_email, action, entity_type, entity_id)
                values ('test@niet.co.in', 'TEST', 'Test', 1) returning id""", Long.class);
    }

    @Test
    void anAuditRowCanBeWrittenButNotChanged() {
        long id = auditRow();
        assertThatThrownBy(() -> jdbc.update("update audit_log set action = 'EDITED' where id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("append-only");
    }

    @Test
    void anAuditRowCannotBeDeleted() {
        long id = auditRow();
        assertThatThrownBy(() -> jdbc.update("delete from audit_log where id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("append-only");
    }

    @Test
    void theAuditTableCannotBeTruncated() {
        assertThatThrownBy(() -> jdbc.execute("truncate audit_log"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("append-only");
    }

    @Test
    void theOneAllowedAuditChangeIsDroppingAUsersLink() {
        long user = jdbc.queryForObject("select id from users order by id limit 1", Long.class);
        long id = jdbc.queryForObject("""
                insert into audit_log (actor_id, actor_email, action, entity_type, entity_id)
                values (?, 'test@niet.co.in', 'TEST', 'Test', 1) returning id""", Long.class, user);
        // What ON DELETE SET NULL does when an account is removed: still allowed ...
        assertThat(jdbc.update("update audit_log set actor_id = null where id = ?", id)).isEqualTo(1);
        // ... but nothing else about the row is.
        assertThatThrownBy(() -> jdbc.update("update audit_log set entity_id = 2 where id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void versionHistoryCannotBeRewrittenOrRemoved() {
        List<Long> any = jdbc.queryForList("select id from submission_versions limit 1", Long.class);
        if (any.isEmpty()) {
            // A fresh database has no versions; the trigger is still there to be asked about.
            assertThat(jdbc.queryForObject(
                    "select count(*) from pg_trigger where tgname = 'submission_versions_append_only'", Integer.class))
                    .isEqualTo(1);
            return;
        }
        assertThatThrownBy(() -> jdbc.update("update submission_versions set note = 'x' where id = ?", any.get(0)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("delete from submission_versions where id = ?", any.get(0)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aGuideCannotTakeMoreSeatsThanTheyHave() {
        long guide = jdbc.queryForObject("select id from supervisor_profiles order by id limit 1", Long.class);
        long session = jdbc.queryForObject("select id from academic_sessions order by id limit 1", Long.class);
        List<Long> students = jdbc.queryForList(
                "select id from student_profiles where id not in (select student_id from allocations) order by id limit 3",
                Long.class);
        assertThat(students).hasSize(3);

        // Free the guide of every seat in this transaction, then give them exactly two.
        jdbc.update("update allocations set status = 'WITHDRAWN' where supervisor_id = ? and session_id = ?"
                + " and status in ('ACCEPTED','COORDINATOR_ASSIGNED')", guide, session);
        jdbc.update("update supervisor_profiles set max_students = 2 where id = ?", guide);

        String insert = "insert into allocations (student_id, supervisor_id, session_id, status)"
                + " values (?, ?, ?, 'COORDINATOR_ASSIGNED')";
        jdbc.update(insert, students.get(0), guide, session);
        jdbc.update(insert, students.get(1), guide, session);
        assertThatThrownBy(() -> jdbc.update(insert, students.get(2), guide, session))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("supervisor capacity");
    }

    @Test
    void aSeatHeldAlreadyCanBeUpdatedWithoutBeingCountedTwice() {
        long guide = jdbc.queryForObject("select id from supervisor_profiles order by id limit 1", Long.class);
        long session = jdbc.queryForObject("select id from academic_sessions order by id limit 1", Long.class);
        long student = jdbc.queryForObject(
                "select id from student_profiles where id not in (select student_id from allocations) order by id limit 1",
                Long.class);
        jdbc.update("update allocations set status = 'WITHDRAWN' where supervisor_id = ? and session_id = ?"
                + " and status in ('ACCEPTED','COORDINATOR_ASSIGNED')", guide, session);
        jdbc.update("update supervisor_profiles set max_students = 1 where id = ?", guide);
        long alloc = jdbc.queryForObject("""
                insert into allocations (student_id, supervisor_id, session_id, status)
                values (?, ?, ?, 'COORDINATOR_ASSIGNED') returning id""", Long.class, student, guide, session);
        // The guide is now exactly full; touching the same allocation again must still work.
        assertThat(jdbc.update("update allocations set status = 'ACCEPTED' where id = ?", alloc)).isEqualTo(1);
    }
}
