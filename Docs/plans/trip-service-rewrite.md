# Trip-service rewrite — goals, not code

The current `trip-service` (from the "building trip service" commit) works but drifted from the contract in `Docs/plans/2026-07-14-droove-7day.md` §Contracts. This doc restates what it needs to do. How you build it is up to you — that's the point.

## Goal

`trip-service` owns the trip lifecycle. It's the source of truth for a trip's state, enforces which transitions are legal, and announces every transition to the rest of the system (matching, notifications, payments, analytics) without knowing who's listening.

## 1. The state machine

A trip moves through these states, and only these transitions are legal:

| From | To |
|---|---|
| REQUESTED | MATCHED, CANCELLED, NO_DRIVERS_FOUND |
| MATCHED | DRIVER_ARRIVED, CANCELLED |
| DRIVER_ARRIVED | IN_PROGRESS, CANCELLED |
| IN_PROGRESS | COMPLETED |

Anything not in this table (e.g. `REQUESTED → IN_PROGRESS`, or transitioning out of `COMPLETED`) must be rejected. `NO_DRIVERS_FOUND` is reached when matching exhausts its retry rounds — trip-service just needs to expose the transition, matching-service triggers it later.

**Why it matters:** every other service trusts that if a trip says `MATCHED`, a driver really is assigned; if the state machine is enforced loosely (or not centrally), that guarantee breaks and bugs show up two services downstream, not here.

## 2. Trip data

A trip needs to carry (naming is yours, but the *information* is required):

- Identity: trip id, rider id, driver id (nullable until matched)
- Geography: pickup lat/lng, drop lat/lng — **as discrete numeric fields**, not a bundled array. Every downstream consumer (pricing, matching, the frontend map) needs to read pickup lat and pickup lng independently.
- Money: fare — **integer cents**, never a float. This project's money rule everywhere is `long` cents; a `Double` fare will eventually produce a rounding bug that shows up in the ledger, not here, which makes it miserable to trace back.
- Timestamps: requested/matched/started/completed, each nullable until reached
- Surge multiplier applied at request time

## 3. Endpoints (via the gateway's `/api/trips/**`, but trip-service itself is unprefixed)

| Endpoint | Who calls it | Effect |
|---|---|---|
| `POST /trips` | rider | Create trip in `REQUESTED`; bump the surge counter for that pickup cell; hand off a match-request (stubbed for now — see §5) |
| `GET /trips/{id}` | rider or driver on the trip | Read one trip |
| `GET /trips/mine` | rider or driver | List trips for the caller |
| `POST /trips/{id}/cancel` | rider or driver on the trip | → `CANCELLED` |
| `PATCH /trips/{id}/assign` | **internal only** (matching-service) | `{driverId, offerId}` → `MATCHED` |
| `POST /trips/{id}/arrived` | driver | → `DRIVER_ARRIVED` |
| `POST /trips/{id}/start` | driver | → `IN_PROGRESS` |
| `POST /trips/{id}/complete` | driver | → `COMPLETED` |

Non-owner calling an action on someone else's trip → reject (403). Illegal transition attempted → reject (409), never silently ignored.

## 4. Fare at request time

`POST /trips` needs a fare before it can create the trip. Pricing-service already exists and owns the real formula — but wiring the live HTTP call is a separate, later step (M6a in the master plan). For now, trip-service just needs *a* fare source it can swap later without rewriting the caller — a seam, not a real integration yet. A fixed placeholder fare is fine to start.

## 5. Announcing what happened

Every transition needs to tell the rest of the system it happened — that's how matching-service learns a trip needs a driver, how notification-service tells the rider their driver arrived, how payment-service knows to move money. Kafka is the real transport (wired later, Day 4), but trip-service shouldn't need to change when that wiring lands — it should publish through a seam it doesn't know the implementation of yet. That seam gets a fake/no-op implementation for now.

The event trip-service announces (exact shape is contract, not negotiable — matching-service, payment-service, and analytics-sink will all be built to expect precisely this):

```json
{
  "v": 1,
  "eventId": "uuid",
  "eventType": "TRIP_REQUESTED | TRIP_MATCHED | DRIVER_ARRIVED | TRIP_STARTED | TRIP_COMPLETED | TRIP_CANCELLED | NO_DRIVERS_FOUND",
  "tripId": "uuid",
  "riderId": "uuid",
  "driverId": "uuid-or-null",
  "occurredAt": "ISO-8601 UTC",
  "payload": { "fareCents": 2350, "surge": 1.2 }
}
```

One event, exactly once, per transition — and only after the transition is actually committed to the database. An event for a transition that then fails to save is worse than no event.

`POST /trips` additionally needs to hand off a match-request so matching-service can start looking for a driver — same idea: a seam trip-service publishes through, real transport wired later.

## 6. Definition of done

You'll know each piece works when:

- [ ] Every legal transition in the table succeeds; every illegal one is rejected — for all of them, not just the ones you remembered to try
- [ ] The four ownership/validation rules hold: non-owner blocked, illegal transition blocked, `assign` only reachable internally, money is always integer cents
- [ ] `POST /trips` produces exactly one `TRIP_REQUESTED` announcement, and only after the trip is actually saved
- [ ] Swapping the fare source or the event/match-request publisher later requires touching only that one seam — not the controller, not the transition logic

## What this explicitly does not need yet

- Real Kafka producers — a fake/no-op stands in
- Real pricing-service HTTP calls — a fixed fare stands in
- Any of matching, payment, or notification services reacting to the events — that's their build, not this one
