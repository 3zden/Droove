# Matching Service Plan Review

Reviewed against:

- `Docs/plans/7dayplan.md`, section 6, "Matching Service"
- `Docs/architecture.md`
- `README.md`
- `Services/matching-service/src/main/java/**`
- `Frontends/web/src/api/matching.ts`

## Executive summary

The current matching service is a skeleton, not an implementation of the matching plan. It can query a Redis GEO index and construct an in-memory `Offer`, but it does not persist offers, claim drivers, filter by availability, process timeouts, retry candidates, notify Trip Service of an outcome, or safely authorize driver responses.

The most urgent defects are:

1. Spring dependencies are not injected because `@RequiredArgsConstructor` is used with non-`final` fields.
2. The Redis GEO key and coordinate order do not match the documented contract.
3. Matching sends an offer to every returned driver without an atomic availability claim.
4. Accept/decline reads an offer that was never stored and can throw a `NullPointerException`.
5. The documented 15-second offer lifecycle and three-round retry algorithm are completely missing.

## Critical implementation errors

### 1. Dependencies are null at runtime

**Files/lines:** `MatchingService.java:24-27`, `OfferController.java:18`, `MatchRequestConsumer.java:14`, `NotificationPublisher.java:15`

All injected fields are declared as ordinary non-final fields. Lombok's `@RequiredArgsConstructor` only generates constructor parameters for `final` or `@NonNull` fields, so these classes receive no constructor injection. Calls such as `matchingService.match(...)`, `notificationPublisher.publish(...)`, and `kafkaTemplate.send(...)` will fail with `NullPointerException`.

**Fix:** Make required dependencies `private final` and keep constructor injection. Remove unused `DriverRepo` unless it is implemented and required. Prefer package naming conventions such as `service`, `controller`, `consumer`, and `producer` if the project standard is being established now.

### 2. Redis GEO key does not match the contract

**Files/lines:** `MatchingService.java:52`; `Docs/architecture.md:70`; `README.md:47`

The plan and architecture use `drivers:geo`, while the implementation reads `drivers:locations`. It therefore searches a different key from the one populated by Location Gateway and will normally return no drivers.

**Fix:** Define the key once as configuration or a constant and use the same value in Location Gateway, Matching Service, scripts, and documentation. Add an integration/contract test that writes a known GEO member and verifies it can be found.

### 3. Latitude and longitude are reversed

**Files/lines:** `MatchingService.java:45-47`, `MatchingService.java:60-62`

Redis GEO uses `(longitude, latitude)`. The code constructs `new Point(lat, lng)`, but it must use `new Point(lng, lat)`. Redis result coordinates are also `(longitude, latitude)`, but the code maps X to `lat` and Y to `lng`, reversing the returned `DriverDto`.

**Fix:** Use explicit conversion helpers or clearly named variables:

```java
new Point(pickupLng, pickupLat)
new DriverDto(driverId, point.getY(), point.getX())
```

Validate the result with a known coordinate pair, including a test near the equator where the error is obvious.

### 4. Availability is never checked

**Files/lines:** `MatchingService.java:43-64`

The GEO query returns every member in the radius. It does not read `driver:{id}:status`, so BUSY, OFFLINE, stale, and otherwise invalid drivers can be offered. This directly violates the plan's "available-only" requirement and the Location Gateway contract.

**Fix:** Treat GEO as a location index only. Check the driver's liveness/status separately, or use a separate available-driver index that is maintained consistently. Do not rely on position data alone to imply availability.

### 5. No atomic driver claim / double-offer race

**Files/lines:** `MatchingService.java:30-39`

The implementation only searches and publishes. It never atomically changes `AVAILABLE` to `BUSY` before publishing. It also loops over every selected driver, so one trip can create simultaneous offers for all candidates and two concurrent match requests can select the same driver.

**Fix:** For each candidate, perform an atomic compare-and-set in Redis (`AVAILABLE` -> `BUSY`, with an owner/offer token and lease). Publish only after the claim succeeds. On decline, expiry, publish failure, or another terminal failure, release the claim safely using the same owner token. Add a concurrency test with two simultaneous match requests and one driver.

## Missing plan functionality

### 6. Offers are never persisted

**Files/lines:** `MatchingService.java:34-37`, `Offer.java:14-44`

`createOffer` returns an object and the object is published, but no `offer:{offerId}` value is written to Redis. The accept and decline endpoints therefore cannot retrieve the offer.

**Fix:** Persist the offer before or as part of dispatch, using the documented key `offer:{offerId}` and a 30-second TTL. Store the expiry deadline and enough state to correlate the offer with its trip, rider, driver, and claim token. Configure an explicit Redis serializer for the `Offer` representation rather than relying on an unspecified `RedisTemplate<String, Offer>` setup.

### 7. Wrong Redis key shape and missing expiry queue

**Files/lines:** `MatchingService.java:83-92`; `Docs/architecture.md:74-75`

The implementation uses the raw UUID as the key, while the contract specifies `offer:{offerId}`. It never writes to `offers:expiry`, so no worker can find timed-out offers.

**Fix:** Use the documented names and implement a scheduled expiry worker or a Redis-stream/queue-based equivalent. The expiry operation must be idempotent and atomic: transition `PENDING` to `EXPIRED`, release the matching claim only if this offer owns it, and start the next candidate or finish the request.

### 8. Accept/decline are unsafe and incomplete

**Files/lines:** `MatchingService.java:82-93`, `OfferController.java:20-27`

Problems:

- `getAndPersist` can return `null` because the offer was not stored.
- `getAndPersist` removes the key's expiry, allowing an offer to live indefinitely.
- There is no `PENDING` or expiry check.
- Any caller who knows an offer ID can accept or decline it; the driver identity is not verified.
- The read-modify-write sequence is not atomic, so accept and decline can overwrite each other.
- Repeated requests are not defined as idempotent.
- Acceptance does not release competing offers, finalize the driver state, or notify Trip Service.
- Decline does not release the claim or advance matching.
- Errors are returned as successful `200` responses rather than meaningful `404`, `409`, or `410` responses.

**Fix:** Authenticate the driver identity at the gateway and pass it to the service. Verify that the caller owns the offer. Use a Redis transaction or Lua script for a conditional state transition. Preserve idempotency for repeated requests, return an explicit conflict for a terminal offer, and publish/dispatch the accepted assignment to Trip Service through the documented internal contract.

### 9. No timeout, retry, or "no drivers found" path

**Files/lines:** `MatchingService.java:30-40`; plan `7dayplan.md:373-378, 389-405`

The code offers all selected drivers once and exits. It does not implement the 15-second response window, next-candidate behavior, three rounds, or the `NO_DRIVERS_FOUND` outcome.

**Fix:** Model matching as a durable state machine keyed by `tripId`: candidate selection, claim, offer dispatch, pending response, decline/expiry, next candidate, round count, and terminal success/failure. Make retries safe after consumer redelivery and service restarts.

### 10. No outcome is sent to Trip Service

**Files/lines:** `MatchingService.java`, `NotificationPublisher.java`

The plan explicitly says Matching Service must not modify trip records directly, but it must tell Trip Service the result so Trip Service can validate `PATCH /trips/{id}/assign` and the `NO_DRIVERS_FOUND` transition. The current code only publishes a notification.

**Fix:** Add the internal assignment/outcome integration, including authentication, correlation IDs, timeouts, retries, and idempotency. The accepted result must include `tripId`, `driverId`, and `offerId`; exhaustion must trigger the documented no-driver transition.

### 11. Notification payload does not implement the documented event contract

**Files/lines:** `NotificationPublisher.java:17-22`, `Offer.java:14-44`

The architecture describes a `DRIVER_OFFER` notification, but the code publishes a raw `Offer`. The `Offer` has no explicit event type, version, event ID, or occurrence timestamp. This makes consumers dependent on a private model class and leaves no stable envelope for deduplication.

**Fix:** Publish a versioned notification DTO/envelope with at least `v`, `eventId`, `eventType`, `targetUserId`, `occurredAt`, and an offer payload. Keep Kafka DTOs separate from Redis persistence models. Use `driverId` as the Kafka key as documented.

### 12. Kafka delivery errors are ignored

**Files/lines:** `NotificationPublisher.java:17-22`

`kafkaTemplate.send(...)` is fire-and-forget. The service does not observe the send result, retry, or release/reconcile a claim if publishing fails. This can leave a driver marked busy while the driver never receives the offer.

**Fix:** Handle the send future explicitly, define retry/backoff and dead-letter behavior, and make claim/offer state recoverable. Do not acknowledge the match request until the state transition is durable.

### 13. Match request processing is not idempotent

**Files/lines:** `MatchRequestConsumer.java:16-19`; `MatchRequest.eventId`

The request includes `eventId`, but the consumer does not use it. Kafka redelivery can create duplicate offers and duplicate claims.

**Fix:** Store or atomically mark processed `eventId`/`tripId` state with a bounded retention period, and make each matching transition safe to repeat. Configure an error handler and retry/dead-letter policy instead of allowing malformed messages to repeatedly block a partition.

## Data model and API gaps

### 14. `DriverStatus` is unusable

**File:** `Model/DriverStatus.java:3`

`DriverStatus` is an empty record and does not represent the documented `AVAILABLE`, `BUSY`, and `OFFLINE` states.

**Fix:** Make it an enum or a value object matching the Location Gateway contract. Include ownership/lease metadata somewhere in Redis if a claim must be safely released.

### 15. `DriverRepo` is dead code

**File:** `Repo/DriverRepo.java:8-10`

`findALl` is misspelled, returns `null`, and is unused. A repository abstraction is misleading because the plan says matching is Redis-based and has no Postgres repository.

**Fix:** Remove it, or replace it with a narrowly named Redis gateway whose methods express the actual operations: search, read status, claim, release, and update offer state. Never return `null` for a collection; return an empty list.

### 16. Controller mapping and response contract need alignment

**File:** `Controller/OfferController.java:14-27`

The controller uses `@Controller` without `@ResponseBody`; this can cause Spring MVC to interpret returned `ResponseEntity<Offer>` as a view response rather than a JSON API response. The route also includes `/api/matching`, while the frontend plan assumes bare `/offers/...` at the service port and lets the gateway add its prefix.

**Fix:** Use `@RestController` and align the path with the gateway/frontend contract. Document the final route once, then update the frontend client and tests. Add request authentication and authorization checks at this boundary.

### 17. Offer response leaks an internal mutable model

**File:** `Model/Offer.java`

The same mutable class is used as persistence data, Kafka payload, and HTTP response. It has setters for every field, including status and IDs, which makes accidental state mutation easy and couples all contracts to one class.

**Fix:** Use separate immutable DTOs for API responses, Kafka notifications, and Redis state. Expose only the fields clients need. Keep status transitions inside the service.

## Testing and observability gaps

### 18. Existing test does not test matching behavior and currently fails

**File:** `src/test/java/org/aezden/matchingservice/MatchingServiceApplicationTests.java`

Only a context-load test exists. It currently fails because JPA auto-configuration tries to create a datasource, but the matching service has no JDBC driver/configuration. This is also inconsistent with the plan's Redis-only design.

**Fix:** Either remove JPA auto-configuration/dependencies from this service or provide a deliberate test profile. Replace the smoke test with focused tests for coordinate order, key names, availability filtering, atomic claiming, offer persistence/TTL, authorization, idempotent responses, expiry, retries, and no-driver exhaustion. Add Testcontainers or an equivalent existing project approach for Redis/Kafka integration tests if available.

### 19. The documented performance claim is not backed by this service

**Plan:** `7dayplan.md:399`

The plan claims a measured p95 under 100 ms with 10,000 drivers, but no benchmark or test exists in the matching service.

**Fix:** Add a reproducible benchmark/load test that seeds 10,000 drivers using the real key and coordinate format, measures the actual search plus filtering/claim path, and records environment and Redis version. Do not retain the claim as completed until it is measured.

### 20. Logging and metrics are not production-safe

**File/lines:** `MatchingService.java:35`

`System.out.printf` is malformed (`"driver with id" + ...`) and writes directly to stdout. There are no structured logs, correlation IDs, metrics, or tracing around match latency, claim failures, offer outcomes, expiry, retries, or Kafka errors.

**Fix:** Use the project logging framework with structured fields (`tripId`, `offerId`, `driverId`, `eventId`). Add counters and timers for candidate searches, successful claims, declines, expiries, failures, and no-driver outcomes. Never log sensitive payloads unnecessarily.

## Documentation inconsistencies to correct

1. `Docs/architecture.md` links to `Docs/plans/2026-07-14-droove-7day.md`, but the repository contains `Docs/plans/7dayplan.md`. Fix the link or rename the source of truth.
2. The docs use `drivers:geo`; the implementation uses `drivers:locations`.
3. The docs specify `offer:{offerId}` and `offers:expiry`; the implementation uses only a raw UUID key and no expiry index.
4. The plan says an offer expires after 15 seconds but the Redis offer record is retained for 30 seconds. Document the distinction explicitly: response deadline versus cleanup TTL, and enforce both.
5. The plan says Matching Service publishes `DRIVER_OFFER`; the implementation publishes a raw `Offer` with no event envelope.
6. The frontend matching client is intentionally unimplemented and throws for both operations. Wire it only after the backend route, auth, and response contract are finalized.

## Recommended implementation order

1. Fix constructor injection, remove dead code, use `@RestController`, and make the service start without accidental JPA datasource auto-configuration.
2. Normalize Redis key constants and coordinate ordering across Location Gateway, Matching Service, scripts, and docs.
3. Define Redis offer/claim schemas, serializers, ownership tokens, TTLs, and atomic Lua/transaction operations.
4. Implement candidate search with available-only filtering and atomic `AVAILABLE -> BUSY` claims.
5. Persist offers before dispatch, publish a versioned `DRIVER_OFFER` envelope, and handle Kafka send failures.
6. Implement authenticated, conditional, idempotent accept/decline operations and the Trip Service outcome call.
7. Add expiry sweeping, claim release, next-candidate retries, three-round exhaustion, and `NO_DRIVERS_FOUND`.
8. Add focused unit, concurrency, Redis/Kafka integration, and benchmark tests.
9. Update the frontend client and all documentation/contracts only after the backend behavior is stable.

## Definition of done

- A valid `match-requests` event is processed idempotently.
- Search uses the documented key and correct `(longitude, latitude)` order.
- Only live `AVAILABLE` drivers are candidates.
- A driver can have at most one active claim/offer, including under concurrent requests.
- The offer is stored under `offer:{offerId}`, has a 15-second response deadline and a separate 30-second cleanup TTL.
- Accept/decline require the owning driver, are atomic and idempotent, and reject expired/terminal offers correctly.
- Decline and expiry release the claim and advance matching; three exhausted rounds produce `NO_DRIVERS_FOUND`.
- Acceptance reaches Trip Service with `tripId`, `driverId`, and `offerId`.
- Kafka notifications use the documented versioned envelope and failures are observable/recoverable.
- Tests cover the race, lifecycle, failure, and contract cases; the performance claim is measured rather than assumed.
