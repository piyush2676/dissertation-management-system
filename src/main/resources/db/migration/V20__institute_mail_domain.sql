-- Sign-in addresses move to the institute's own domain, which is also the ERP
-- sign-in domain (decided 2026-09-24). Seeded demo accounts and imported scholars
-- and faculty were all on the placeholder @college.edu; one demo student was on
-- @gmail.com so the confirmation banner had something to show, and a student on a
-- public mail domain is exactly what the department does not want.
--
-- Guarded so it cannot collide with an address already on the new domain.
--
-- Deliberately NOT touched:
--   audit_log.actor_email -- the trail records who acted under the address they
--                            held at the time; it is history, and it is never edited.
--
-- Certificates issued before this migration seal examiner addresses in their marks
-- fact, so they now verify as CHANGED. That is the designed response to a changed
-- register: the coordinator reissues.

UPDATE users u
SET email = replace(u.email, '@college.edu', '@niet.co.in')
WHERE u.email LIKE '%@college.edu'
  AND NOT EXISTS (SELECT 1 FROM users o WHERE o.email = replace(u.email, '@college.edu', '@niet.co.in'));

UPDATE users u
SET email = 'student4@niet.co.in',
    email_verified_at = COALESCE(u.email_verified_at, NOW())
WHERE u.email = 'student4@gmail.com'
  AND NOT EXISTS (SELECT 1 FROM users o WHERE o.email = 'student4@niet.co.in');
