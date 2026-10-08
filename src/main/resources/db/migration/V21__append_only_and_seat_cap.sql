-- Hardening: promises the application makes, held by the database as well.
--
-- Until now the audit trail and the submission version history were "immutable" only
-- because the Java code never updated them. Anyone holding the database login -- which on a
-- hosted database is one shared credential -- could rewrite either table. Supervisor
-- capacity was likewise checked in Java alone, so two requests for a guide's last seat could
-- both pass the check and both be written.
--
-- Maintenance escape hatch: the table owner or a superuser can switch one trigger off inside
-- a transaction (`ALTER TABLE submission_versions DISABLE TRIGGER ...`, then ENABLE it again
-- before commit), as scripts/demo-reset.sh does. Foreign-key cascades keep working that way,
-- which they would not under session_replication_role.

-- ---- append-only tables -----------------------------------------------------

CREATE FUNCTION forbid_row_change() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION '% is append-only: % is not allowed', TG_TABLE_NAME, TG_OP
        USING ERRCODE = 'integrity_constraint_violation';
END;
$$;

-- audit_log.actor_id is ON DELETE SET NULL, so removing a user rewrites that one column.
-- That is the only change an audit row may undergo.
CREATE FUNCTION audit_log_guard() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    IF TG_OP = 'UPDATE'
       AND NEW.actor_id IS NULL
       AND (to_jsonb(NEW) - 'actor_id') = (to_jsonb(OLD) - 'actor_id') THEN
        RETURN NEW;
    END IF;
    RAISE EXCEPTION 'audit_log is append-only: % is not allowed', TG_OP
        USING ERRCODE = 'integrity_constraint_violation';
END;
$$;

CREATE TRIGGER audit_log_append_only
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION audit_log_guard();
CREATE TRIGGER audit_log_no_truncate
    BEFORE TRUNCATE ON audit_log
    FOR EACH STATEMENT EXECUTE FUNCTION forbid_row_change();

CREATE TRIGGER submission_versions_append_only
    BEFORE UPDATE OR DELETE ON submission_versions
    FOR EACH ROW EXECUTE FUNCTION forbid_row_change();
CREATE TRIGGER submission_versions_no_truncate
    BEFORE TRUNCATE ON submission_versions
    FOR EACH STATEMENT EXECUTE FUNCTION forbid_row_change();

-- ---- a guide's seats --------------------------------------------------------

-- Seats are taken by ACCEPTED and COORDINATOR_ASSIGNED allocations (AllocationStatus
-- .OCCUPIES_A_SEAT), counted per guide per session, against the primary guide only. The
-- guide's profile row is locked first so two writers for the last seat take turns: the
-- second waits, then counts the first.
CREATE FUNCTION allocations_seat_cap() RETURNS trigger
    LANGUAGE plpgsql AS
$$
DECLARE
    cap   INT;
    taken INT;
BEGIN
    IF NEW.status NOT IN ('ACCEPTED', 'COORDINATOR_ASSIGNED') THEN
        RETURN NEW;
    END IF;
    IF TG_OP = 'UPDATE'
       AND OLD.status IN ('ACCEPTED', 'COORDINATOR_ASSIGNED')
       AND OLD.supervisor_id = NEW.supervisor_id
       AND OLD.session_id = NEW.session_id THEN
        RETURN NEW; -- already holds this seat
    END IF;

    SELECT max_students INTO cap FROM supervisor_profiles WHERE id = NEW.supervisor_id FOR UPDATE;
    SELECT count(*) INTO taken FROM allocations
     WHERE supervisor_id = NEW.supervisor_id
       AND session_id = NEW.session_id
       AND status IN ('ACCEPTED', 'COORDINATOR_ASSIGNED')
       AND id IS DISTINCT FROM NEW.id;

    IF taken >= cap THEN
        RAISE EXCEPTION 'supervisor capacity reached (% of % seats taken)', taken, cap
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER allocations_seat_cap
    BEFORE INSERT OR UPDATE OF status, supervisor_id, session_id ON allocations
    FOR EACH ROW EXECUTE FUNCTION allocations_seat_cap();
