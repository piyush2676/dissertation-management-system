#!/usr/bin/env bash
#
# Phase 8 acceptance: drives the whole workflow over HTTP against a running app
# and asserts the state the database ends up in.
#
# The point is that the chain completes without touching the database by hand,
# and that every step leaves an audit row.
#
#   ./scripts/acceptance.sh [PORT] [STUDENT_EMAIL]
#
# Defaults to port 8081 and student3@college.edu, so it does not collide with an
# app you are running yourself on 8080 or with data an earlier run left behind.
#
# Git Bash note: MSYS2_ARG_CONV_EXCL stops MSYS mangling the multipart argument,
# which is why every path below is Windows-style.

set -u

PORT="${1:-8081}"
STUDENT="${2:-student3@college.edu}"
GUIDE="guide1@college.edu"
COORDINATOR="coordinator@college.edu"
B="http://localhost:${PORT}"

export MSYS2_ARG_CONV_EXCL='*'

WORK="${TMPDIR:-/tmp}/dms-acceptance"
mkdir -p "$WORK"
WORK_WIN=$(cd "$WORK" && pwd -W 2>/dev/null || echo "$WORK")

PSQL="/c/Program Files/PostgreSQL/18/bin/psql.exe"

# sed rather than grep -oP: this shell's grep refuses PCRE outside a UTF-8 locale,
# and an empty password makes psql sit waiting on stdin instead of failing.
PGPASSWORD=$(sed -n 's/^spring\.datasource\.password=//p' application-local.properties | tr -d '\r')
export PGPASSWORD

if [ -z "$PGPASSWORD" ]; then
  echo "Could not read the database password from application-local.properties." >&2
  exit 1
fi

pass=0
fail=0

# -w so a credential problem fails loudly rather than hanging on a prompt.
#
# tr -d '\r' is load-bearing: psql on Windows emits CRLF, so splitting a
# multi-row result leaves the carriage return glued to every value but the last.
# That turned a form field into scores[1\r], which Spring could not bind and the
# terminal rendered as if it were fine.
q()    { "$PSQL" -w -h localhost -U postgres -d dms -t -A -c "$1" | tr -d '\r'; }
tok()  { curl -s -b "$1" -c "$1" "$B$2" | grep -o 'name="_csrf" value="[^"]*"' | head -1 | sed 's/.*value="//;s/"//'; }
login() {
  rm -f "$2"
  local t
  t=$(curl -s -c "$2" "$B/login" | grep -o 'name="_csrf" value="[^"]*"' | head -1 | sed 's/.*value="//;s/"//')
  curl -s -b "$2" -c "$2" -o /dev/null -X POST "$B/login" \
    --data-urlencode "username=$1" --data-urlencode "password=$3" --data-urlencode "_csrf=$t"
}
check() {
  local label="$1" want="$2" got="$3"
  if [ "$got" = "$want" ]; then
    printf '  ok    %-46s %s\n' "$label" "$got"; pass=$((pass + 1))
  else
    printf '  FAIL  %-46s got %-22s want %s\n' "$label" "$got" "$want"; fail=$((fail + 1))
  fi
}

echo "Acceptance run against $B as $STUDENT"
echo

# --- fresh slate for this student ------------------------------------------
SID=$(q "select sp.id from student_profiles sp join users u on u.id=sp.user_id where u.email='$STUDENT'")
q "delete from review_comments where submission_version_id in (
     select v.id from submission_versions v join submissions s on s.id=v.submission_id
     join allocations a on a.id=s.allocation_id where a.student_id=$SID);
   delete from submission_versions where submission_id in (
     select s.id from submissions s join allocations a on a.id=s.allocation_id where a.student_id=$SID);
   delete from submissions where allocation_id in (select id from allocations where student_id=$SID);
   delete from evaluations where allocation_id in (select id from allocations where student_id=$SID);
   delete from viva_schedules where allocation_id in (select id from allocations where student_id=$SID);
   delete from allocations where student_id=$SID;
   delete from topics where student_id=$SID;" > /dev/null

AUDIT_BEFORE=$(q "select count(*) from audit_log")

login "$STUDENT"     "$WORK_WIN/s.jar" student123 > /dev/null
login "$GUIDE"       "$WORK_WIN/g.jar" guide123   > /dev/null
login "$COORDINATOR" "$WORK_WIN/c.jar" coord123   > /dev/null

SUP=$(q "select sp.id from supervisor_profiles sp join users u on u.id=sp.user_id where u.email='$GUIDE'")

printf '%%PDF-1.4\n1 0 obj<</Type/Catalog>>endobj\ntrailer<</Root 1 0 R>>\n%%%%EOF\n'        > "$WORK/v1.pdf"
printf '%%PDF-1.4\n1 0 obj<</Type/Catalog/Rev 2>>endobj\ntrailer<</Root 1 0 R>>\n%%%%EOF\n' > "$WORK/v2.pdf"

# --- 1. topic ---------------------------------------------------------------
T=$(tok "$WORK_WIN/s.jar" /student/topic/new)
curl -s -b "$WORK_WIN/s.jar" -o /dev/null -X POST "$B/student/topic/submit" \
  --data-urlencode "title=Acceptance run: energy-aware scheduling for edge inference" \
  --data-urlencode "abstractText=$(printf 'A%.0s' {1..250})" \
  --data-urlencode "proposedSupervisorId=$SUP" --data-urlencode "_csrf=$T"
TID=$(q "select id from topics where student_id=$SID")
check "1. topic proposed" "PROPOSED" "$(q "select status from topics where id=$TID")"

T=$(tok "$WORK_WIN/g.jar" /supervisor/topics)
curl -s -b "$WORK_WIN/g.jar" -o /dev/null -X POST "$B/supervisor/topics/$TID/decide" \
  --data-urlencode "decision=APPROVED" --data-urlencode "_csrf=$T"
check "2. guide approved the topic" "APPROVED" "$(q "select status from topics where id=$TID")"

# --- 2. allocation ----------------------------------------------------------
T=$(tok "$WORK_WIN/s.jar" /student/guide)
curl -s -b "$WORK_WIN/s.jar" -o /dev/null -X POST "$B/student/guide/request" \
  --data-urlencode "supervisorId=$SUP" --data-urlencode "_csrf=$T"
AID=$(q "select id from allocations where student_id=$SID")
check "3. guide requested" "REQUESTED" "$(q "select status from allocations where id=$AID")"

T=$(tok "$WORK_WIN/g.jar" /supervisor/requests)
curl -s -b "$WORK_WIN/g.jar" -o /dev/null -X POST "$B/supervisor/requests/$AID/decide" \
  --data-urlencode "decision=ACCEPTED" --data-urlencode "_csrf=$T"
check "4. guide accepted" "ACCEPTED" "$(q "select status from allocations where id=$AID")"

# --- 3. submission + review cycle -------------------------------------------
MS=$(q "select m.id from milestones m join academic_sessions s on s.id=m.session_id
        where s.programme='MTECH' order by m.sequence_no limit 1")

T=$(tok "$WORK_WIN/s.jar" /student/submissions)
curl -s -b "$WORK_WIN/s.jar" -o /dev/null -X POST "$B/student/submissions/$MS/upload" \
  -F "file=@$WORK_WIN/v1.pdf;type=application/pdf" -F "note=first cut" -F "_csrf=$T"
SUB=$(q "select s.id from submissions s join allocations a on a.id=s.allocation_id where a.student_id=$SID")
check "5. version 1 filed" "SUBMITTED 1" "$(q "select status||' '||current_version_no from submissions where id=$SUB")"

T=$(tok "$WORK_WIN/s.jar" /student/submissions)
curl -s -b "$WORK_WIN/s.jar" -o /dev/null -X POST "$B/student/submissions/$MS/upload" \
  -F "file=@$WORK_WIN/v1.pdf;type=application/pdf" -F "_csrf=$T"
check "6. identical re-upload refused" "1" "$(q "select count(*) from submission_versions where submission_id=$SUB")"

T=$(tok "$WORK_WIN/g.jar" "/supervisor/submissions/$SUB")
curl -s -b "$WORK_WIN/g.jar" -o /dev/null -X POST "$B/supervisor/submissions/$SUB/start" --data-urlencode "_csrf=$T"
check "7. review started" "UNDER_REVIEW" "$(q "select status from submissions where id=$SUB")"

VID=$(q "select id from submission_versions where submission_id=$SUB order by version_no desc limit 1")
T=$(tok "$WORK_WIN/g.jar" "/supervisor/submissions/$SUB")
curl -s -b "$WORK_WIN/g.jar" -o /dev/null -X POST "$B/review/comments?returnTo=/supervisor/submissions/$SUB" \
  --data-urlencode "versionId=$VID" --data-urlencode "pageNo=3" \
  --data-urlencode "body=Add a baseline comparison." --data-urlencode "_csrf=$T"
CID=$(q "select id from review_comments where submission_version_id=$VID")
check "8. guide left a comment" "f" "$(q "select resolved from review_comments where id=$CID")"

T=$(tok "$WORK_WIN/s.jar" "/student/submissions/$SUB")
curl -s -b "$WORK_WIN/s.jar" -o /dev/null -X POST "$B/review/comments/$CID/resolve?returnTo=/student/submissions/$SUB" \
  --data-urlencode "_csrf=$T"
check "9. student resolved it" "t" "$(q "select resolved from review_comments where id=$CID")"

T=$(tok "$WORK_WIN/g.jar" "/supervisor/submissions/$SUB")
curl -s -b "$WORK_WIN/g.jar" -o /dev/null -X POST "$B/supervisor/submissions/$SUB/decide" \
  --data-urlencode "decision=REVISION_REQUESTED" --data-urlencode "note=Tighten the objectives." --data-urlencode "_csrf=$T"
check "10. revision requested" "REVISION_REQUESTED" "$(q "select status from submissions where id=$SUB")"

T=$(tok "$WORK_WIN/s.jar" /student/submissions)
curl -s -b "$WORK_WIN/s.jar" -o /dev/null -X POST "$B/student/submissions/$MS/upload" \
  -F "file=@$WORK_WIN/v2.pdf;type=application/pdf" -F "note=reworked" -F "_csrf=$T"
check "11. version 2 filed, v1 intact" "2" "$(q "select count(*) from submission_versions where submission_id=$SUB")"

T=$(tok "$WORK_WIN/g.jar" "/supervisor/submissions/$SUB")
curl -s -b "$WORK_WIN/g.jar" -o /dev/null -X POST "$B/supervisor/submissions/$SUB/start" --data-urlencode "_csrf=$T"
T=$(tok "$WORK_WIN/g.jar" "/supervisor/submissions/$SUB")
curl -s -b "$WORK_WIN/g.jar" -o /dev/null -X POST "$B/supervisor/submissions/$SUB/decide" \
  --data-urlencode "decision=APPROVED" --data-urlencode "_csrf=$T"
check "12. submission approved" "APPROVED" "$(q "select status from submissions where id=$SUB")"

# --- 4. evaluation + viva ---------------------------------------------------
ARGS=()
for C in $(q "select rc.id from rubric_criteria rc join academic_sessions s on s.id=rc.session_id
              where s.programme='MTECH' order by rc.sequence_no"); do
  ARGS+=(--data-urlencode "scores[$C]=8")
done
T=$(tok "$WORK_WIN/g.jar" "/supervisor/evaluate/$AID")
curl -s -b "$WORK_WIN/g.jar" -o /dev/null -X POST "$B/supervisor/evaluate/$AID" \
  "${ARGS[@]}" --data-urlencode "remarks=Solid work." --data-urlencode "_csrf=$T"
check "13. scored 8/10 across the rubric" "80.00" "$(q "select total from evaluations where allocation_id=$AID")"

T=$(tok "$WORK_WIN/c.jar" /coordinator/viva)
curl -s -b "$WORK_WIN/c.jar" -o /dev/null -X POST "$B/coordinator/viva/schedule?programme=MTECH" \
  --data-urlencode "allocationId=$AID" --data-urlencode "scheduledAt=2027-02-11T10:30" \
  --data-urlencode "venue=Seminar Hall, CSE Block" --data-urlencode "panel=Dr A Sharma, Dr B Pandey" \
  --data-urlencode "_csrf=$T"
check "14. viva scheduled" "SCHEDULED" "$(q "select status from viva_schedules where allocation_id=$AID")"

# --- 5. downloads and the trail ---------------------------------------------
V1=$(q "select id from submission_versions where submission_id=$SUB order by version_no limit 1")
check "15. student downloads v1" "200" \
  "$(curl -s -b "$WORK_WIN/s.jar" -o /dev/null -w '%{http_code}' "$B/files/submissions/versions/$V1")"
check "16. guide2 cannot reach the submission" "404" \
  "$(login guide2@college.edu "$WORK_WIN/g2.jar" guide123 > /dev/null; \
     curl -s -b "$WORK_WIN/g2.jar" -o /dev/null -w '%{http_code}' "$B/supervisor/submissions/$SUB")"
check "17. mark sheet renders" "200" \
  "$(curl -s -b "$WORK_WIN/c.jar" -o /dev/null -w '%{http_code}' "$B/coordinator/marksheet")"
check "18. student result renders" "200" \
  "$(curl -s -b "$WORK_WIN/s.jar" -o /dev/null -w '%{http_code}' "$B/student/result")"

AUDIT_AFTER=$(q "select count(*) from audit_log")
WROTE=$((AUDIT_AFTER - AUDIT_BEFORE))
if [ "$WROTE" -ge 9 ]; then
  printf '  ok    %-46s %s new rows\n' "19. every step left an audit row" "$WROTE"; pass=$((pass + 1))
else
  printf '  FAIL  %-46s only %s new rows, expected 9+\n' "19. every step left an audit row" "$WROTE"; fail=$((fail + 1))
fi

echo
echo "passed $pass, failed $fail"
[ "$fail" -eq 0 ] || exit 1
