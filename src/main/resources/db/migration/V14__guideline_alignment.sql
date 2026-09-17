-- Phase 12: align the model with the institute's M.Tech / M.Tech Int. dissertation
-- guidelines (docs/m.tech_m.tech int._dissertation_guidelines_v3.md).
--
-- A dissertation runs over two semesters with a different set of reviews and a
-- different marking scheme in each: Pre-Dissertation in the 3rd / 9th semester,
-- Final Dissertation in the 4th / 10th. The phase is a property of where the
-- student is in the programme, so it is derived from the profile's semester and
-- only the rows that differ per phase -- milestones and rubric criteria -- carry
-- it. The academic session stays one row per year and programme.

-- ---- milestones: one set of reviews per phase --------------------------------

ALTER TABLE milestones ADD COLUMN phase VARCHAR(16);

-- Rows that predate the phase were the single end-to-end track, which the mark
-- sheet and viva were exercised against. They keep working as the FINAL set;
-- the seeder adds a PRE set to any session that lacks one.
UPDATE milestones SET phase = 'FINAL' WHERE phase IS NULL;

ALTER TABLE milestones ALTER COLUMN phase SET NOT NULL;
ALTER TABLE milestones DROP CONSTRAINT uq_milestones_session_sequence;
ALTER TABLE milestones
    ADD CONSTRAINT uq_milestones_session_phase_sequence UNIQUE (session_id, phase, sequence_no);

-- ---- rubric: one scheme per phase, marks not percentages, outcome codes -------

ALTER TABLE rubric_criteria ADD COLUMN phase VARCHAR(16);
UPDATE rubric_criteria SET phase = 'FINAL' WHERE phase IS NULL;
ALTER TABLE rubric_criteria ALTER COLUMN phase SET NOT NULL;
ALTER TABLE rubric_criteria DROP CONSTRAINT uq_rubric_session_sequence;
ALTER TABLE rubric_criteria
    ADD CONSTRAINT uq_rubric_session_phase_sequence UNIQUE (session_id, phase, sequence_no);

-- The guidelines print a course outcome and the programme outcomes beside every
-- rubric row (Format 6, Format 15). Free text on purpose: the codes are the
-- department's to correct, and CO attainment in phase 16 groups on them.
ALTER TABLE rubric_criteria
    ADD COLUMN co_code    VARCHAR(8),
    ADD COLUMN po_mapping VARCHAR(64);

-- ---- topics: the Annexure-1 / Annexure-2 fields and the thesis code -----------

ALTER TABLE topics
    ADD COLUMN research_domain   VARCHAR(128),
    ADD COLUMN objectives        TEXT,
    ADD COLUMN sdg_alignment     VARCHAR(255),
    -- Annexure-2's tick list, stored as a comma-separated set of enum names. A
    -- join table would be a lazy collection on the entity, and templates never
    -- receive one of those.
    ADD COLUMN expected_outcomes VARCHAR(255),
    -- "MT26-001": programme prefix, two-digit year, sequence. Assigned once, on
    -- approval, because the progress and submission forms print it before any
    -- guide is placed.
    ADD COLUMN thesis_code       VARCHAR(16);

CREATE UNIQUE INDEX uq_topics_thesis_code ON topics (thesis_code) WHERE thesis_code IS NOT NULL;

-- ---- allocations: an optional co-supervisor ---------------------------------

-- Workload is counted against the primary guide only, so the partial unique
-- index and the capacity check are untouched. The one rule the database can
-- hold is that the two are different people.
ALTER TABLE allocations
    ADD COLUMN co_supervisor_id BIGINT REFERENCES supervisor_profiles (id) ON DELETE SET NULL,
    ADD CONSTRAINT ck_allocations_co_supervisor_differs
        CHECK (co_supervisor_id IS NULL OR co_supervisor_id <> supervisor_id);

CREATE INDEX idx_allocations_co_supervisor ON allocations (co_supervisor_id);
