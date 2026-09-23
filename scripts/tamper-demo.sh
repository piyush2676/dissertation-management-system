#!/usr/bin/env bash
#
# Safety rail for the tamper demonstration in docs/demo.md.
#
# Typing an UPDATE by hand in front of an examiner is a good way to fumble a
# table name and lose the room. This wraps the three moves:
#
#   ./scripts/tamper-demo.sh show     [ALLOCATION_ID]   what the mark is now
#   ./scripts/tamper-demo.sh tamper   [ALLOCATION_ID]   quietly change it
#   ./scripts/tamper-demo.sh restore  [ALLOCATION_ID]   put it back
#
# Defaults to allocation 11. "restore" reads the original from the certificate's
# frozen payload, so it puts back the real mark rather than a number typed twice.

set -u

ACTION="${1:-show}"
ALLOCATION="${2:-11}"
TAMPERED_MARK="95.00"

PSQL="/c/Program Files/PostgreSQL/18/bin/psql.exe"
PGPASSWORD=$(sed -n 's/^spring\.datasource\.password=//p' application-local.properties | tr -d '\r')
export PGPASSWORD

if [ -z "$PGPASSWORD" ]; then
  echo "Could not read the database password from application-local.properties." >&2
  exit 1
fi

q() { "$PSQL" -w -h localhost -U postgres -d dms -t -A -c "$1" | tr -d '\r'; }

CODE=$(q "select code from certificates where allocation_id = $ALLOCATION")
CURRENT=$(q "select total from evaluations where allocation_id = $ALLOCATION")

if [ -z "$CURRENT" ]; then
  echo "Allocation $ALLOCATION has no marks recorded, so there is nothing to tamper with." >&2
  exit 1
fi

case "$ACTION" in
  show)
    echo "Allocation : $ALLOCATION"
    echo "Student    : $(q "select u.full_name from allocations a
                            join student_profiles sp on sp.id = a.student_id
                            join users u on u.id = sp.user_id where a.id = $ALLOCATION")"
    echo "Mark now   : $CURRENT"
    echo "Certificate: ${CODE:-none issued yet}"
    [ -n "$CODE" ] && echo "Verify at  : http://localhost:8080/verify/$CODE"
    ;;

  tamper)
    if [ -z "$CODE" ]; then
      echo "No certificate for allocation $ALLOCATION yet. Issue one first, or the" >&2
      echo "demonstration has nothing to compare against." >&2
      exit 1
    fi
    q "update evaluations set total = $TAMPERED_MARK where allocation_id = $ALLOCATION" > /dev/null
    echo "Mark changed from $CURRENT to $TAMPERED_MARK."
    echo "Reload http://localhost:8080/verify/$CODE -- it should now report a change."
    ;;

  restore)
    # The certificate froze the real mark at issue, so recover it from there
    # rather than trusting whatever is typed on the command line.
    ORIGINAL=$(q "select split_part(payload->>'marks', '=', 2) from certificates
                  where allocation_id = $ALLOCATION")
    if [ -z "$ORIGINAL" ]; then
      echo "No sealed mark to restore from. Set it by hand." >&2
      exit 1
    fi
    q "update evaluations set total = $ORIGINAL where allocation_id = $ALLOCATION" > /dev/null
    echo "Mark restored to $ORIGINAL."
    [ -n "$CODE" ] && echo "Reload http://localhost:8080/verify/$CODE -- it should pass again."
    ;;

  *)
    echo "Usage: $0 {show|tamper|restore} [ALLOCATION_ID]" >&2
    exit 1
    ;;
esac
