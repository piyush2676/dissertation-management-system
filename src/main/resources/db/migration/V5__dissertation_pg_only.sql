UPDATE student_profiles
SET programme = 'MTECH'
WHERE programme = 'BTECH';

DELETE FROM milestones
WHERE session_id IN (SELECT id FROM academic_sessions WHERE programme = 'BTECH');

DELETE FROM academic_sessions
WHERE programme = 'BTECH';
