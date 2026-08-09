#!/usr/bin/env bash
#
# Seeds Droove with fake riders, drivers, trips and bookings so the app can be
# exercised by hand.
#
#   Users go in through user-service's real /register endpoint - passwords have to be
#   bcrypt-hashed by the service, and the UUIDs it mints are what everything else keys on.
#   Trips and bookings go in via SQL, because there is no API today that can put a trip
#   into MATCHED/ONGOING/COMPLETED (assignDriver is still a stub), and fixtures need
#   every state, not just REQUESTED.
#
# Re-running is safe: existing users are logged into instead of re-registered, and only
# the rows this script owns (fixed UUID prefixes) are deleted before reinsert.
#
# Usage:
#   DB_PASSWORD=... ./scripts/seed.sh
#
set -euo pipefail

USER_API="${USER_API:-http://localhost:8101/api/users}"
SEED_PASSWORD="${SEED_PASSWORD:-Test1234!}"

export PGHOST="${PGHOST:-localhost}"
export PGPORT="${PGPORT:-5432}"
export PGUSER="${PGUSER:-postgres}"
export PGDATABASE="${PGDATABASE:-droovedb}"
export PGPASSWORD="${PGPASSWORD:-${DB_PASSWORD:?set DB_PASSWORD (or PGPASSWORD) - never hardcode it here}}"

OUT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/seeded-users.tsv"

# Enum columns are persisted as ordinals (no @Enumerated(EnumType.STRING) on either
# entity), so the SQL has to write ints. Named here so the fixtures stay readable -
# and so it is obvious what breaks the day someone reorders one of those enums.
declare -A TRIP_STATUS=(
  [REQUESTED]=0 [MATCHING]=1 [MATCHED]=2 [DRIVER_ARRIVED]=3
  [ONGOING]=4 [COMPLETED]=5 [CANCELLED]=6 [NO_DRIVER]=7
)
declare -A BOOKING_STATUS=([SCHEDULED]=0 [TRIGGERED]=1 [CANCELLED]=2)

# Real Casablanca landmarks. This matters: the routing engine is loaded with
# morocco-latest.osm.pbf, so anything outside Morocco silently degrades to the
# straight-line fallback instead of a real road route.
declare -A PLACE=(
  [casa_port]="33.5992,-7.6033"        [casa_voyageurs]="33.5876,-7.5817"
  [airport]="33.3675,-7.5898"          [morocco_mall]="33.5761,-7.7031"
  [mosque]="33.6083,-7.6325"           [maarif]="33.5865,-7.6325"
  [anfa_place]="33.5936,-7.6539"       [sidi_maarouf]="33.5145,-7.6528"
  [cfc]="33.5464,-7.6559"              [ain_sebaa]="33.6127,-7.5165"
  [derb_ghallef]="33.5686,-7.6229"     [bourgogne]="33.5990,-7.6440"
  [oasis]="33.5537,-7.6410"            [hay_hassani]="33.5510,-7.6800"
  [mohammedia]="33.6861,-7.3829"       [bouskoura]="33.4489,-7.6494"
  [marrakech]="31.6258,-7.9891"        [rabat_agdal]="34.0084,-6.8539"
)

lat() { echo "${PLACE[$1]%,*}"; }
lng() { echo "${PLACE[$1]#*,}"; }

# ---------------------------------------------------------------- personas ---
# key | first | last | role | plate | what this persona is for
PERSONAS=$(cat <<'EOF'
amina	Amina	Benali	RIDER		Loyal rider - three COMPLETED trips, for trip history + receipts
youssef	Youssef	Tazi	RIDER		Serial canceller - one cancel before match, one after
salma	Salma	El Fassi	RIDER		Commuter - future bookings + one ride in progress
omar	Omar	Cherkaoui	RIDER		Brand-new rider, zero history - empty-state UI
karim	Karim	Idrissi	RIDER		Mid-ride rider in MATCHED - live driver tracking
nadia	Nadia	Alaoui	RIDER		Requests from the outskirts - NO_DRIVER path
hicham	Hicham	Bennani	RIDER		Airport + intercity long hauls - big fares, surge
mehdi	Mehdi	Ouazzani	DRIVER	12345-A-6	AVAILABLE downtown - the driver matching should pick
rachid	Rachid	Sabri	DRIVER	48120-B-6	BUSY - currently on an ONGOING trip, must not match
fatima	Fatima	Zahra	DRIVER	77003-A-1	OFFLINE - stale heartbeat, must never be offered a trip
abdel	Abdelilah	Naciri	DRIVER	20456-B-6	Declines/ignores offers - offer expiry + next-round matching
sanae	Sanae	Berrada	DRIVER	91288-A-6	High-volume airport driver - driver earnings + payout ledger
jamal	Jamal	Lamrani	DRIVER	10101-A-6	Brand-new driver, zero trips - empty driver dashboard
noplate	Nour	Hakimi	DRIVER		Driver registered WITHOUT a plate - validation gap, see notes
EOF
)

declare -A UID_OF

# ------------------------------------------------------------ user-service ---
# No -f: a 401/405 means the service is alive and answering. Only a failure to connect
# at all (non-zero curl exit) is fatal.
if ! curl -s -o /dev/null --max-time 5 "$USER_API/login"; then
  cat >&2 <<EOM
user-service is not answering on $USER_API

Start it first (note the two overrides - its defaults point at a 'droove' database
that does not exist locally, and it has no server.port so it would grab 8080):

  cd Services/user-service && \\
  SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/droovedb \\
  POSTGRES_USER=$PGUSER POSTGRES_PASSWORD=\$DB_PASSWORD \\
  JWT_SECRET=dev-secret-at-least-32-bytes-long-ok \\
  SERVER_PORT=8101 ./mvnw spring-boot:run
EOM
  exit 1
fi

printf 'Registering users via %s\n\n' "$USER_API"
: > "$OUT"
printf 'key\temail\trole\tuserId\tpassword\n' >> "$OUT"

while IFS=$'\t' read -r key first last role plate note; do
  [ -z "${key:-}" ] && continue
  email="${key}.$(echo "$role" | tr '[:upper:]' '[:lower:]')@droove.test"

  body=$(jq -nc --arg f "$first" --arg l "$last" --arg e "$email" \
                --arg p "$SEED_PASSWORD" --arg r "$role" --arg v "$plate" \
    '{firstName:$f, lastName:$l, email:$e, password:$p, role:$r,
      vehiclePlate: (if $v == "" then null else $v end)}')

  resp=$(curl -s -X POST "$USER_API/register" -H 'Content-Type: application/json' -d "$body")
  id=$(jq -r '.user.id // empty' <<<"$resp")

  if [ -z "$id" ]; then                       # already registered -> log in instead
    resp=$(curl -s -X POST "$USER_API/login" -H 'Content-Type: application/json' \
      -d "$(jq -nc --arg e "$email" --arg p "$SEED_PASSWORD" '{email:$e,password:$p}')")
    id=$(jq -r '.user.id // empty' <<<"$resp")
    status="existing"
  else
    status="created"
  fi

  if [ -z "$id" ]; then
    printf '  !! %-9s %-34s FAILED: %s\n' "$key" "$email" "$resp" >&2
    continue
  fi

  UID_OF[$key]=$id
  printf '  %-8s %-9s %-34s %s\n' "$status" "$role" "$email" "$id"
  printf '%s\t%s\t%s\t%s\t%s\n' "$key" "$email" "$role" "$id" "$SEED_PASSWORD" >> "$OUT"
done <<<"$PERSONAS"

# ------------------------------------------------ trip + booking fixtures ---
# Fixed UUIDs so a re-run replaces exactly its own rows and nothing a human created.
t() { printf 'd0000000-0000-4000-8000-%012d' "$1"; }
b() { printf 'b0000000-0000-4000-8000-%012d' "$1"; }

# fare stays 0 for anything not COMPLETED - that mirrors the code today: Trip starts at
# fare 0 and nothing writes it until payment-service exists. The completed fares are the
# exact cents pricing-service quotes for those two points, so the fixtures agree with
# what the live service would have charged.
sql=$(cat <<SQL
begin;

delete from trips_svc.rides      where trip_id::text    like 'd0000000-0000-4000-8000-%';
delete from scheduling_svc.booking where booking_id::text like 'b0000000-0000-4000-8000-%';

insert into trips_svc.rides
  (trip_id, user_id, driver_id, trip_status, fare,
   pick_up_lat, pick_up_lon, destination_lat, destination_lon,
   requested_at, started_at, completed_at)
values
  -- Amina: three finished rides -> trip history, receipts, repeat-customer view
  ('$(t 1)','${UID_OF[amina]}','${UID_OF[sanae]}',${TRIP_STATUS[COMPLETED]},4941,
   $(lat maarif),$(lng maarif),$(lat airport),$(lng airport),
   now()-interval '9 days', now()-interval '9 days'+interval '6 min', now()-interval '9 days'+interval '44 min'),
  ('$(t 2)','${UID_OF[amina]}','${UID_OF[mehdi]}',${TRIP_STATUS[COMPLETED]},2227,
   $(lat casa_port),$(lng casa_port),$(lat morocco_mall),$(lng morocco_mall),
   now()-interval '4 days', now()-interval '4 days'+interval '4 min', now()-interval '4 days'+interval '27 min'),
  ('$(t 3)','${UID_OF[amina]}','${UID_OF[rachid]}',${TRIP_STATUS[COMPLETED]},1443,
   $(lat mosque),$(lng mosque),$(lat casa_voyageurs),$(lng casa_voyageurs),
   now()-interval '2 days', now()-interval '2 days'+interval '3 min', now()-interval '2 days'+interval '18 min'),

  -- Youssef: the two cancellation shapes the state machine allows
  ('$(t 4)','${UID_OF[youssef]}',null,${TRIP_STATUS[CANCELLED]},0,
   $(lat derb_ghallef),$(lng derb_ghallef),$(lat anfa_place),$(lng anfa_place),
   now()-interval '6 days', null, null),
  ('$(t 5)','${UID_OF[youssef]}','${UID_OF[mehdi]}',${TRIP_STATUS[CANCELLED]},0,
   $(lat bourgogne),$(lng bourgogne),$(lat oasis),$(lng oasis),
   now()-interval '1 day', null, null),

  -- Karim: driver assigned and en route -> live tracking / ws-track screen
  ('$(t 6)','${UID_OF[karim]}','${UID_OF[mehdi]}',${TRIP_STATUS[MATCHED]},0,
   $(lat sidi_maarouf),$(lng sidi_maarouf),$(lat cfc),$(lng cfc),
   now()-interval '3 min', null, null),

  -- Hicham: driver at the pickup point, waiting -> DRIVER_ARRIVED screen
  ('$(t 7)','${UID_OF[hicham]}','${UID_OF[sanae]}',${TRIP_STATUS[DRIVER_ARRIVED]},0,
   $(lat ain_sebaa),$(lng ain_sebaa),$(lat mohammedia),$(lng mohammedia),
   now()-interval '11 min', null, null),

  -- Salma: ride in progress. This is also what makes Rachid BUSY.
  ('$(t 8)','${UID_OF[salma]}','${UID_OF[rachid]}',${TRIP_STATUS[ONGOING]},0,
   $(lat maarif),$(lng maarif),$(lat hay_hassani),$(lng hay_hassani),
   now()-interval '22 min', now()-interval '14 min', null),

  -- Nadia: far outskirts, matching gave up after its rounds
  ('$(t 9)','${UID_OF[nadia]}',null,${TRIP_STATUS[NO_DRIVER]},0,
   $(lat bouskoura),$(lng bouskoura),$(lat marrakech),$(lng marrakech),
   now()-interval '35 min', null, null),

  -- Hicham: fresh request nobody has picked up yet -> the matching entry point
  ('$(t 10)','${UID_OF[hicham]}',null,${TRIP_STATUS[REQUESTED]},0,
   $(lat casa_voyageurs),$(lng casa_voyageurs),$(lat rabat_agdal),$(lng rabat_agdal),
   now()-interval '40 sec', null, null);

insert into scheduling_svc.booking
  (booking_id, rider_id, booking_status, pick_up_time, pick_up_lat, pick_up_lng, drop_lat, drop_lng)
values
  -- due in 45 min: the one the ZSET sweeper should fire during a demo
  ('$(b 1)','${UID_OF[hicham]}',${BOOKING_STATUS[SCHEDULED]}, now()+interval '45 min',
   $(lat anfa_place),$(lng anfa_place),$(lat airport),$(lng airport)),
  ('$(b 2)','${UID_OF[salma]}',${BOOKING_STATUS[SCHEDULED]}, date_trunc('day',now())+interval '1 day 7 hours 30 min',
   $(lat oasis),$(lng oasis),$(lat cfc),$(lng cfc)),
  ('$(b 3)','${UID_OF[salma]}',${BOOKING_STATUS[SCHEDULED]}, now()+interval '3 days 8 hours',
   $(lat cfc),$(lng cfc),$(lat oasis),$(lng oasis)),
  ('$(b 4)','${UID_OF[salma]}',${BOOKING_STATUS[CANCELLED]}, now()+interval '2 days',
   $(lat maarif),$(lng maarif),$(lat morocco_mall),$(lng morocco_mall)),
  -- already fired: what a booking looks like after the sweeper turned it into a trip
  ('$(b 5)','${UID_OF[amina]}',${BOOKING_STATUS[TRIGGERED]}, now()-interval '2 hours',
   $(lat casa_port),$(lng casa_port),$(lat mohammedia),$(lng mohammedia));

commit;
SQL
)

printf '\nInserting trip + booking fixtures into %s\n' "$PGDATABASE"
psql -v ON_ERROR_STOP=1 -q <<<"$sql"

psql -qtA -F$'\t' <<'SQL'
select 'trips',    trip_status::text, count(*) from trips_svc.rides
  where trip_id::text like 'd0000000-%' group by 2
union all
select 'bookings', booking_status::text, count(*) from scheduling_svc.booking
  where booking_id::text like 'b0000000-%' group by 2
order by 1,2;
SQL

printf '\nDone. Log in as any of these with password: %s\n' "$SEED_PASSWORD"
printf 'Full list (with user IDs): %s\n' "$OUT"
column -t -s$'\t' "$OUT"
