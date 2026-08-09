# Test data

`./scripts/seed.sh` fills Droove with fake riders, drivers, trips and bookings so the
app can be clicked through by hand. Everything below is what got seeded and what each
piece is there to exercise.

```bash
DB_PASSWORD=... ./scripts/seed.sh
```

Users go in through **user-service's real `/register`** — passwords must be bcrypt-hashed
by the service, and the UUIDs it mints are what trips and bookings key on. Trips and
bookings go in **via SQL**, because no API today can put a trip into `MATCHED` /
`ONGOING` / `COMPLETED` (`PATCH /trips/{id}/assign` is still a stub that just re-reads
the trip), and fixtures need every state, not only `REQUESTED`.

Re-running is safe. Existing users are logged into instead of re-registered, and only
rows with the seed's own UUID prefixes (`d0000000-…` trips, `b0000000-…` bookings) are
deleted before reinsert — nothing you created by hand is touched.

Output: `scripts/seeded-users.tsv` (key, email, role, userId, password).
**Password for every account: `Test1234!`**

---

## What each service does, and who tests it

| Service | Port | Purpose | Seeded coverage |
|---|---|---|---|
| `api-gateway` | 8080 | Only public door. Validates the JWT, strips client-sent `X-User-*`, injects trusted `X-User-Id`/`X-User-Role`, rate-limits, owns the `/api/*` prefix | Any persona's token; `noplate` is the one whose claims look wrong |
| `user-service` | 8101 | Register / login / me. bcrypt + HS256 JWT `{sub, role}` | All 14 personas — 7 riders, 7 drivers |
| `trip-service` | 8102 | Trip lifecycle state machine, source of truth for a ride. Emits `trip-events` + `match-requests` | 10 trips covering every reachable status |
| `matching-service` | 8103 | Nearest-driver search over Redis GEO, 30s offers, CAS on driver status | `mehdi` available / `rachid` busy / `fatima` offline / `abdel` declines |
| `pricing-service` | 8104 | `max(700, 500 + 120·km + 30·min) × surge`, integer cents | Trip fares follow this formula exactly; short trip #3 sits near the floor |
| `payment-service` | 8105 | Double-entry ledger, wallet, consumes `TRIP_COMPLETED` | 3 completed trips = 3 settlements owed; `sanae` is the earnings case |
| `scheduling-service` | 8106 | Future bookings, `schedule:bookings` ZSET sweeper → `match-requests` | 5 bookings: one due in 45 min, two future, one cancelled, one already triggered |
| `routing-service` | 8107 | Road distance/duration/geometry via GraphHopper, straight-line fallback flagged `source=FALLBACK` | Every coordinate is a real Casablanca landmark — see the warning below |
| `location-gateway` | 8201 | WebSocket GPS ingest → Redis GEO + `driver:pos:{id}` pub/sub | `mehdi` (en route to `karim`) is the driver to simulate |
| `notification-service` | 8202 | Kafka → WebSocket fan-out to rider and driver | `karim`'s MATCHED trip and `hicham`'s open REQUEST |
| `analytics-sink` | — | Consumes `trip-events` → batch insert to warehouse | The 3 completed + 2 cancelled trips are the backfill set |

> **Coordinates are Casablanca on purpose.** GraphHopper is loaded with
> `morocco-latest.osm.pbf`. Anything outside Morocco silently degrades to the
> straight-line fallback instead of a real road route — which is itself worth testing
> once (try `48.8566, 2.3522`, Paris, and check the response says `FALLBACK`).
>
> The gap is not cosmetic. Maarif → Airport is **24.7 km** as the crow flies but
> **31.2 km** by road (`source: ROAD`, 30 min). pricing-service still quotes off the
> straight line, so every seeded fare is ~20% under what the road distance implies —
> a real thing to look at once trip → pricing → routing is chained.

---

## Riders

| Login | Who they are | What they test |
|---|---|---|
| `amina.rider@droove.test` | Loyal rider, 3 completed trips | Trip history, receipts, repeat-customer view, analytics backfill |
| `youssef.rider@droove.test` | Serial canceller | Both legal cancel paths: before a driver was assigned, and after |
| `salma.rider@droove.test` | Commuter | Scheduled bookings list + one ride currently in progress |
| `omar.rider@droove.test` | Brand new, zero history | Empty states — no trips, no bookings, no receipts |
| `karim.rider@droove.test` | Mid-ride, status `MATCHED` | Live driver tracking over `/ws/track/{driverId}` |
| `nadia.rider@droove.test` | Requests from Bouskoura outskirts | `NO_DRIVER` — matching exhausted its rounds |
| `hicham.rider@droove.test` | Airport + intercity long hauls | Big fares, surge, a `DRIVER_ARRIVED` trip and one open `REQUESTED` |

## Drivers

| Login | Plate | What they test |
|---|---|---|
| `mehdi.driver@droove.test` | `12345-A-6` | `AVAILABLE` downtown — the driver matching should pick |
| `rachid.driver@droove.test` | `48120-B-6` | `BUSY` on an `ONGOING` trip — must not be offered another |
| `fatima.driver@droove.test` | `77003-A-1` | `OFFLINE`, stale heartbeat — must never be offered anything |
| `abdel.driver@droove.test` | `20456-B-6` | Ignores offers — offer expiry sweep and the next matching round |
| `sanae.driver@droove.test` | `91288-A-6` | High-volume airport driver — earnings, payout ledger |
| `jamal.driver@droove.test` | `10101-A-6` | Brand-new driver, zero trips — empty driver dashboard |
| `noplate.driver@droove.test` | *(none)* | A `DRIVER` with no plate. **The service accepts this today** — see notes |

Driver availability is a Redis fact (`driver:{id}:status`, 15s TTL), not a column, so
"available / busy / offline" above is the *intended* state. It only becomes real once
location-gateway is publishing heartbeats. Until then, set it by hand:

```bash
redis-cli SET "driver:$(grep ^mehdi scripts/seeded-users.tsv | cut -f4):status" AVAILABLE EX 15
redis-cli GEOADD drivers:geo -7.6325 33.5865 "$(grep ^mehdi scripts/seeded-users.tsv | cut -f4)"
```

## Trips

| # | Rider | Driver | Status | Route | Point |
|---|---|---|---|---|---|
| 1 | amina | sanae | `COMPLETED` | Maarif → Airport | 24.7 km, 4941 c — the long-haul fare |
| 2 | amina | mehdi | `COMPLETED` | Casa Port → Morocco Mall | 9.6 km, 2227 c |
| 3 | amina | rachid | `COMPLETED` | Hassan II Mosque → Casa Voyageurs | 5.2 km, 1443 c — near the 700 c floor |
| 4 | youssef | — | `CANCELLED` | Derb Ghallef → Anfa Place | Cancelled before any match |
| 5 | youssef | mehdi | `CANCELLED` | Bourgogne → Oasis | Cancelled after a driver was assigned |
| 6 | karim | mehdi | `MATCHED` | Sidi Maarouf → CFC | Driver en route — the live-tracking screen |
| 7 | hicham | sanae | `DRIVER_ARRIVED` | Ain Sebaa → Mohammedia | Driver waiting at pickup |
| 8 | salma | rachid | `ONGOING` | Maarif → Hay Hassani | In progress; this is what makes rachid busy |
| 9 | nadia | — | `NO_DRIVER` | Bouskoura → Marrakech | Matching gave up |
| 10 | hicham | — | `REQUESTED` | Casa Voyageurs → Rabat Agdal | Open request — the matching entry point |

Fare is `0` on everything except the completed trips. That mirrors the code as it
stands: `Trip` starts at `fare = 0` and nothing writes it until payment-service exists.

## Bookings

| # | Rider | Status | When | Point |
|---|---|---|---|---|
| 1 | hicham | `SCHEDULED` | +45 min | The one the ZSET sweeper should fire during a demo |
| 2 | salma | `SCHEDULED` | Tomorrow 07:30 | Normal commute booking |
| 3 | salma | `SCHEDULED` | +3 days | Far-future entry, should sit untouched in the ZSET |
| 4 | salma | `CANCELLED` | +2 days | Cancelled — must not fire even though its time comes |
| 5 | amina | `TRIGGERED` | 2 hours ago | What a booking looks like after it became a trip |

---

## Notes — things the seed ran into

Not part of the seed, but found while writing it and worth fixing:

1. **`user-service` cannot start on its own defaults.** `application.yml` points at a
   database called `droove`; the one that exists locally is `droovedb`. It also declares
   no `server.port`, so it binds 8080 — the gateway's port — instead of 8101. Both need
   overriding just to boot:
   ```bash
   SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/droovedb \
   POSTGRES_USER=postgres POSTGRES_PASSWORD=$DB_PASSWORD \
   JWT_SECRET=dev-secret-at-least-32-bytes-long-ok \
   SERVER_PORT=8101 ./mvnw spring-boot:run
   ```
   `trip-service` has the mirror problem: its properties say `server.port=8082`, while
   the gateway routes and the frontend both expect 8102.

2. **Enums are stored as ordinals.** `trips_svc.rides.trip_status` and
   `scheduling_svc.booking.booking_status` are both `smallint` — neither `Trip` nor
   `Booking` has `@Enumerated(EnumType.STRING)`. Insert a value into the middle of
   `TripStatus` one day and every existing row silently means something else. The seed
   has to write ints for this reason. `User.role` does it right; these two should match.

3. **A stale `public.users` table** is sitting in `droovedb` from an earlier run, with no
   `role` or `vehicle_plate` column. The live table is `users_svc.users`. Safe to drop.

4. **`GET /bookings/mine` reads the rider id from a request body.** A GET with a body is
   ignored by most proxies and clients, and it also means any caller can ask for anyone's
   bookings. `trip-service` already does this correctly — `@RequestHeader("X-User-Id")`.
   Same for `POST /bookings/{bookingId}/cancel`, which declares `{bookingId}` in the path
   but reads it with `@RequestParam`, so the path variable is dead and the id has to come
   in as a query string.

5. **`POST /register` accepts a `DRIVER` with no `vehiclePlate`** (that is what
   `noplate.driver@droove.test` is). There is no validation anywhere in the request
   DTOs — no `@NotBlank` on email or password either, and no email-format check.

6. **Money leaves trip-service as a float.** `Trip.fare` is a `long` (correct), but
   `TripResponse.fare` is a `double`, so the API returns `4941.0`. The project's own
   interview-prep notes say "money is always integer cents, never a float" — the entity
   follows that rule and the DTO undoes it on the way out.
