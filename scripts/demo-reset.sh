#!/usr/bin/env bash
#
# Puts one student back to a clean slate so the demo in docs/demo.md can be run
# again. Clears their topic, allocation, submissions, versions, comments,
# evaluation, viva and notifications -- nothing else is touched.
#
#   ./scripts/demo-reset.sh [STUDENT_EMAIL]
#
# Defaults to student4@gmail.com, the integrated M.Tech student the walkthrough
# uses. Safe to run repeatedly, and safe while the application is running.

set -u

STUDENT="${1:-student4@gmail.com}"
PSQL="/c/Program Files/PostgreSQL/18/bin/psql.exe"

# sed rather than grep -oP: this shell's grep refuses PCRE outside a UTF-8 locale.
PGPASSWORD=$(sed -n 's/^spring\.datasource\.password=//p' application-local.properties | tr -d '\r')
export PGPASSWORD

if [ -z "$PGPASSWORD" ]; then
  echo "Could not read the database password from application-local.properties." >&2
  exit 1
fi

# -w so a credential problem fails loudly instead of waiting on a prompt.
# tr -d '\r' because psql on Windows emits CRLF.
q() { "$PSQL" -w -h localhost -U postgres -d dms -t -A -c "$1" | tr -d '\r'; }

SID=$(q "select sp.id from student_profiles sp
         join users u on u.id = sp.user_id
         where u.email = '$STUDENT'")

if [ -z "$SID" ]; then
  echo "No student profile found for $STUDENT." >&2
  exit 1
fi

q "delete from review_comments where submission_version_id in (
     select v.id from submission_versions v
     join submissions s on s.id = v.submission_id
     join allocations a on a.id = s.allocation_id
     where a.student_id = $SID);
   delete from submission_versions where submission_id in (
     select s.id from submissions s
     join allocations a on a.id = s.allocation_id
     where a.student_id = $SID);
   delete from submissions where allocation_id in
     (select id from allocations where student_id = $SID);
   delete from evaluations where allocation_id in
     (select id from allocations where student_id = $SID);
   delete from viva_schedules where allocation_id in
     (select id from allocations where student_id = $SID);
   delete from allocations where student_id = $SID;
   delete from ai_reports where kind = 'TOPIC_NOVELTY' and ref_id in
     (select id from topics where student_id = $SID);
   delete from embeddings where kind = 'TOPIC' and ref_id in
     (select id from topics where student_id = $SID);
   delete from topics where student_id = $SID;
   delete from notifications where recipient_id =
     (select id from users where email = '$STUDENT');" > /dev/null

echo "Reset $STUDENT (student profile $SID)."
echo
echo "  topics        $(q "select count(*) from topics where student_id = $SID")"
echo "  allocations   $(q "select count(*) from allocations where student_id = $SID")"
echo "  notifications $(q "select count(*) from notifications n join users u on u.id = n.recipient_id where u.email = '$STUDENT'")"
echo
echo "Ready. Follow docs/demo.md."
