-- Give the pre-phase-12 rubric rows their course outcome codes.
--
-- V14 split the rubric by phase and backfilled everything that already existed
-- as FINAL, which was right: those rows are what the mark sheet and the viva had
-- been exercised against. What it could not do was invent CO codes for them, so a
-- database seeded before phase 12 keeps five generic criteria with co_code null --
-- and CO attainment, which groups on exactly that column, reads empty for the
-- whole FINAL cohort. The seeder cannot fix it either: seedRubrics is guarded per
-- session and phase, so it never touches a scheme that is already there.
--
-- The codes below are the three the guidelines actually define for the Final
-- Dissertation (section 1.2), not Format 15's CO4 and CO5, which its own outcome
-- list never defines. These rows predate the guideline scheme; mapping them to
-- outcomes that exist is more honest than mapping them to ones that do not. The
-- PO strings are the ones phase 12 seeded for the nearest equivalent row.
--
--   CO1  Execute the research plan and implement a complete solution
--   CO2  Critically analyse and evaluate the work against existing methods
--   CO3  Present and defend the work, in writing and orally
--
-- Guarded on co_code IS NULL and matched by name, so this is a no-op on a
-- database seeded after phase 12 and cannot overwrite a code the department set.

UPDATE rubric_criteria
   SET co_code = 'CO1', po_mapping = 'PO1,PO2,PO4,PO6,PO7,PO11'
 WHERE co_code IS NULL AND phase = 'FINAL' AND name = 'Problem definition';

UPDATE rubric_criteria
   SET co_code = 'CO1', po_mapping = 'PO2'
 WHERE co_code IS NULL AND phase = 'FINAL' AND name = 'Literature and novelty';

UPDATE rubric_criteria
   SET co_code = 'CO1', po_mapping = 'PO1,PO2,PO3,PO4,PO5,PO7,PO11'
 WHERE co_code IS NULL AND phase = 'FINAL' AND name = 'Methodology';

UPDATE rubric_criteria
   SET co_code = 'CO2', po_mapping = 'PO1,PO2,PO3,PO4,PO5,PO7,PO11'
 WHERE co_code IS NULL AND phase = 'FINAL' AND name = 'Results and evaluation';

UPDATE rubric_criteria
   SET co_code = 'CO3', po_mapping = 'PO10'
 WHERE co_code IS NULL AND phase = 'FINAL' AND name = 'Presentation and viva';
