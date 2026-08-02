# Pricing-service — open problems and improvements

Companion to `7dayplan.md` §3 (which stays the source of truth for the business
problem and the formula). This file tracks what's still wrong or missing in the
implementation. Delete an entry when it's done.

Already fixed: duration was computed in hours and floored to a `long` (every trip
under 15 km lost its whole time component); query params and the endpoint path
didn't match the frontend contract.

## Must fix

### 1. `PricingResponse` is a Spring bean and is injected into `PricingService`

`PricingResponse` is annotated `@Component` and taken as a constructor argument.
A DTO is not a bean. The injected instance is assigned to a field and never read —
`getPricing` correctly builds a `new PricingResponse(...)` each call — so today it is
only dead weight.

The reason it matters is what happens if it ever *is* used: a `@Component` is a
singleton, so one mutable response object would be shared by every concurrent
request. Two riders quoting at the same moment would race on the same fields, and
whoever wrote last wins. This is the same class of bug as the matching-service
check-then-set race in §6 — it passes every manual test, because you only ever test
it alone.

Drop `@Component` and the constructor parameter. A response object is created per
request and thrown away; that is exactly what it should be.

### 2. `surge` is mutable state on a singleton service

`private float surge = 1f` is an instance field on a `@Service`, which Spring creates
once and shares across all request threads. Harmless while it's a hardcoded constant.
The moment it's read from Redis and assigned per request, two concurrent quotes will
overwrite each other's multiplier — a rider can be charged another rider's surge.

Surge is per-request data. It belongs as a local variable inside `getPricing`, passed
down, never stored on the service. The only fields a stateless service should hold
are its collaborators and true constants.

Related: use `double`, not `float`. Nothing else in the money path is `float`, and
mixing the two invites a silent widening somewhere it matters.

### 3. Coordinates are not validated

`spring-boot-starter-validation` is on the classpath and unused. `pickupLat=500`
currently produces a confident, completely wrong fare instead of a rejection.

Rules: latitude −90..90, longitude −180..180, all four params required. Reject with
400. This is a trust boundary — the request comes from a browser, and anything a
browser sends is attacker-controlled. Validation at the edge is not optional even
when everything downstream "would probably cope".

### 4. Rounding happens too early

`durationMin` is rounded to a `long` before being multiplied by 30 in the fare
expression. That injects up to ±15 cents of error into the fare for no reason.

Carry duration as a `double` through the arithmetic and round **once**, at the final
cents value. The response can still expose a rounded `durationMin` for display —
display rounding and money rounding are different concerns and should happen at
different points.

## Still missing

### 5. Surge is never read from Redis

The whole demand half of the formula is a hardcoded `1.0`. The contract:

- Key `surge:{cell}`, INT, TTL 300s (`architecture.md`)
- Written by trip-service on every `POST /trips`, for the **pickup** cell
- `surge = clamp(1.0 + 0.2 * floor(busyCount / 5), 1.0, 3.0)`

Two decisions to make deliberately:

- **The cell.** `geohash5` is what the doc says; rounding lat/lng to 2 decimals
  (~1.1 km) buys the same "same neighbourhood" bucket in one line. Either is
  defensible — what matters is being able to say why the size matters: too big and
  one busy street surges the whole city, too small and the counter never reaches 5.
- **Redis unreachable.** Fail open to `surge = 1.0`, do not 500. A quote is still
  useful without demand data; refusing to price a ride because a cache is down is the
  worse failure. This is the opposite of the policy for the fare itself — see the
  wiring note below.

### 6. No tests

The formula is a money path with a branch (the 700-cent floor), a clamp (the 3× cap),
and unit conversions that were already wrong once. It needs a test that fails when
any of those break.

Minimum: the floor holds for a very short trip; the cap holds at a high busy count;
a known distance produces the exact expected cents; invalid coordinates give 400.

## When trip-service calls this service

Not pricing's code, but it constrains pricing's contract, so it belongs here.

- Trip-service quotes over synchronous HTTP (`RestClient`) with **explicit connect and
  read timeouts**. The default JDK client has no read timeout; without one, a hung
  pricing-service blocks trip-service's request threads until the pool is exhausted
  and trip-service dies of someone else's problem.
- **If pricing is down, trip-service fails the request (503). It must not invent a
  fare.** Note the asymmetry with rule 5 above: fall back when the missing value is an
  *input* to a calculation (surge, road distance); fail when the missing value *is the
  money you will charge*. A wrong fare is a billing dispute, not a degraded experience.
- Quote **before** saving the trip, and **outside** the DB transaction — never hold a
  Postgres connection open across a network call.
- Trip-service bumps the surge counter **after** quoting, so a rider's own request
  doesn't inflate their own multiplier.
- The rider is quoted twice — once by the browser on the ride screen, once by
  trip-service at `POST /trips` — and surge can move in between. Server-side re-quote
  is the right starting choice (never trust a client-supplied fare). The production
  answer is a short-lived signed quote token that trip-service redeems, which is what
  upfront pricing actually is.

## Out of scope on purpose

Road distance via `routing-service` is designed separately in `routing-service.md`.
Real traffic data, ML demand prediction, and per-city rate cards are production work,
not interview-quality requirements.
