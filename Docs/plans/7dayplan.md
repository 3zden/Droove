# Droove — The Mentor's Walkthrough

This document explains the Droove build the way a senior engineer would explain it to you in person, service by service. It does not tell you which file to open or which line to write. It tells you what each piece is for, why it exists, and when to stop touching it.

If you want the exact technical contract for a service (its API shapes, its database columns, its event schema), that's in here too — but it comes *after* the explanation, never before it. Read the "why" first. The "what" will make more sense once you have it.

---

## Before we start: the big picture

**The business problem.** Someone opens an app, taps a button, and a stranger with a car shows up to take them somewhere. That's it. Everything in this project — the microservices, the Kafka topics, the Redis geo-index, the ledger — exists to make that one moment work reliably, at speed, for thousands of rides happening at once.

**Why not just one big app?** You could build Droove as a single program: one codebase, one database, one deploy. For a toy version, that's genuinely simpler, and if you ever feel that pull, it's not a wrong instinct. We're not doing that here because part of the point of this project is to *feel* the reasons microservices exist, not just read about them: different parts of a ride-hailing system have wildly different jobs (matching a driver is a fast in-memory search, moving money needs strict correctness, streaming GPS is a constant firehose), and splitting them lets each one use the tool that actually fits its job.

**The shape of the system.** Picture three layers:

1. **The edge** — one gateway that every request from a phone or browser passes through. It checks "who are you" once, so nothing behind it has to ask again.
2. **The services** — small programs, each owning one slice of the business (users, trips, pricing, matching, payments, scheduling). Most talk over plain HTTP for things that need an answer *right now* (like a fare quote), and over Kafka events for things that can be announced and handled a moment later (like "a trip just finished").
3. **The shared infrastructure** — Postgres for anything that needs to be remembered forever and be consistent (money, trip history), Redis for anything that needs to be blazing fast and can tolerate being slightly ephemeral (driver positions, a scheduling queue), Kafka for anything that's an announcement rather than a question.

**A note on how this project teaches.** Some pieces below are built for you as scaffolding — the repetitive, "you'd write this the same way every time" parts. Others are left for you to implement by hand, on purpose: Redis geo-search, the Kafka pipeline, the money ledger, gateway security, wiring the services together, connecting the frontend, and the AWS deploy. Those aren't left out because they're unimportant — the opposite. They're the parts that actually come up in interviews, and the only way to *own* an answer to "walk me through how you built X" is to have actually built X with your own hands, including the part where it breaks and you fix it.

**A few ground rules that show up everywhere, explained once:**

- **Money is always a whole number of cents, never a decimal.** Computers can't represent money like 12.10 dollars exactly in floating-point — the same way you can't write ⅓ exactly as a decimal. Store 1210 cents instead, and the rounding errors that would otherwise creep into every transaction simply can't happen.
- **Every ID is a UUID, not an auto-incrementing number.** If ten independent services are all allowed to create records, they can't share one counter without constantly checking in with each other. Random unique IDs let every service generate valid IDs alone, with no coordination and no collisions.
- **Every timestamp is UTC.** "3pm" means something different depending on which service's server clock you ask. UTC is the one time zone everyone can agree on, so there's never a debate about which event happened first.
- **Secrets live in environment variables, never in code.** This project actually leaked a real secret into git history early on — once something is committed, it's in the repository's history forever, even if you delete it in the next commit. The only fix at that point is to revoke the secret, not to try to scrub history. Environment variables never get committed in the first place, so the mistake can't happen.
- **One shared JWT secret, 10-hour tokens, no refresh tokens in v1.** A JWT is just a signed note that says "this is user X, and I promise this note wasn't forged" — signed with a secret only your servers know. Skipping refresh tokens is a real, deliberate shortcut: sessions simply expire after 10 hours and the user logs back in. That's a fine v1 answer as long as you can say *why* it's a shortcut and what you'd add later (refresh tokens, rotation, revocation lists).

With that framing in place, let's go through each piece.

---

## 1. User Service — Who Are You?

### What problem are we solving?
Before anyone can request a ride or accept one, the system needs to know who they are. Anyone can claim to be anyone over the internet — you need a reliable way to say "prove it" and, once proven, a way to remember that proof for the rest of the conversation.

### Why does this service exist?
User Service has exactly one job: identity. It's the only place accounts are created, passwords are checked, and the "who is this" question gets answered. Every other service in the system trusts User Service's answer instead of re-implementing login logic themselves.

### What should this service do?
- Let someone register as a **rider** or a **driver**.
- Let a registered person log in with their email and password.
- Hand back proof of identity (a token) that the rest of the system can trust.
- Answer "who am I, based on this token" for a service that already has a valid token.

### What should it NOT do?
- It should not decide whether a request is *allowed* to happen (that's the gateway's job, once it trusts the token).
- It should not know anything about trips, prices, or payments.
- It should not ever send a password back in a response — not even hashed. Once a password comes in, it should never come back out.

### What are the main functionalities?
**Registering** turns "I want an account" into a real user record, and immediately gives back a token — so a new user is logged in the moment they sign up, no separate login step needed. **Logging in** takes an email and password, and either hands back a token (correct password) or refuses (wrong password) — and it actually has to *check* the password, which sounds obvious but is a real mistake this project caught early: an earlier version of this service accepted any password because the check was silently skipped. **"Who am I"** takes a token that's already been verified upstream and returns the profile that belongs to it.

| Endpoint | What it does |
|---|---|
| `POST /api/users/register` | `{email, password, firstName, lastName, role, vehiclePlate?}` → `201` + `{accessToken, user}` |
| `POST /api/users/login` | `{email, password}` → `200` + `{accessToken, user}`, or `401` if the password is wrong |
| `GET /api/users/me` | → `200` + the caller's profile |

### What business rules exist?
- Passwords are never stored as plain text — they're hashed with bcrypt, a one-way function designed to be slow on purpose (so guessing millions of passwords against a stolen database is impractical).
- Email must be unique — you can't register the same email twice.
- A `DRIVER` account carries a vehicle plate; a `RIDER` account doesn't need one.
- The response the client sees is never the raw database row — it's a separate shape that simply has no field for the password, so there's no way to accidentally leak it.

### What database tables/entities do we need?
A single `User` row needs: an id (so it can be referenced elsewhere without exposing the email), the email and hashed password (for login), first and last name (for display — "your driver, Amina, is arriving"), a role (`RIDER` or `DRIVER` — this decides what the rest of the app lets them do), and an optional vehicle plate (only meaningful for drivers, which is why it's nullable rather than a required field everyone has to fill in).

### Which events should it publish?
None. Registration and login are the *start* of a story other services care about, but nothing downstream needs to react to "someone signed up" in real time. If you later added a welcome email or a fraud-detection check on new signups, a `USER_REGISTERED` event would be the natural place to hook in — but nothing in this project needs it yet, so it doesn't exist. Don't build the event pipe before something needs to listen on it.

### Which events should it consume?
None. User Service doesn't react to anything happening elsewhere in the system — identity doesn't change because a trip completed.

### When is this service "good enough"?
When register, login, and "who am I" all work, passwords are hashed, wrong passwords are rejected, and nothing ever leaks a password in a response. **This is enough for now. Move to the next service.**

### Production improvements (optional)
Real systems add refresh tokens (so a session can last for weeks without a 10-hour re-login), rate limiting on login attempts (so someone can't brute-force a password by guessing millions of times), and email verification. All three are worth understanding *why* they matter — brute-force protection prevents password guessing at scale, refresh tokens balance security against constantly re-logging-in — but none of them are needed to prove the concept here.

### Common beginner mistakes
The most common mistake is checking that a user *exists* but forgetting to check that the *password matches* — which is exactly the bug this project shipped with initially. Another is returning the full database entity (including the hashed password) directly as the API response instead of a separate response shape. Both mistakes are invisible in a demo and dangerous in production — which is exactly why "it works when I click through it" isn't the same as "it's correct."

### Interview perspective
An interviewer wants to hear that you know *why* passwords are hashed and not encrypted (hashing is one-way — the server should never be able to recover the original password, even if it wanted to), why JWTs are signed rather than encrypted (anyone can read the contents, but nobody can forge them without the secret), and that you understand the difference between authentication ("who are you") and authorization ("what are you allowed to do") — the second one deliberately doesn't live here.

### Before moving on
- [ ] Register creates a user and returns a token in one step
- [ ] Login rejects a wrong password with a real 401, not a silent success
- [ ] No response, ever, contains a password field
- [ ] A `DRIVER` can carry a vehicle plate; a `RIDER` isn't forced to have one

If every box is checked: **stop here, this service is done. Move to the next one.**

---

## 2. Trip Service — The Heart of a Ride

### What problem are we solving?
A ride isn't one action, it's a *sequence*: someone asks for a ride, a driver is found, the driver arrives, the ride happens, it ends. Every other part of the system — matching, notifications, payments — needs to know exactly where in that sequence a given ride currently is, and needs to trust that answer completely.

### Why does this service exist?
Trip Service is the single source of truth for "what state is this ride in, right now." Nothing else is allowed to decide that a trip is `MATCHED` or `COMPLETED` — they ask Trip Service, or they tell Trip Service it happened and let Trip Service decide whether that's actually a legal thing to happen next.

### What should this service do?
- Create a new trip when a rider requests one.
- Let a trip move forward through its lifecycle, one legal step at a time.
- Refuse anything that isn't a legal next step.
- Announce every step change so the rest of the system can react.

### What should it NOT do?
- It should not calculate the fare (that's Pricing Service's job — Trip Service just asks for a number and stores it).
- It should not decide *which* driver gets assigned (that's Matching Service's job — Trip Service just accepts the assignment once it's made).
- It should not send notifications or move money directly — it announces what happened and lets the services that care react.

### What are the main functionalities?
**Requesting a ride** creates a trip in a brand new "just asked" state, and immediately puts out a call for a driver — it doesn't wait around for one to be found. **Reading a trip** lets the rider or driver involved check its current status. **Advancing a trip** is the core idea: a trip can only move to specific next states, never any state at all — you can't jump from "just requested" straight to "ride finished," the same way a traffic light can't jump from red to green without passing through amber. **Cancelling** is available at several points, but not after the ride has actually started.

| Endpoint | Who calls it | Effect |
|---|---|---|
| `POST /trips` | rider | Create a trip in `REQUESTED`, ask for a driver |
| `GET /trips/{id}` / `GET /trips/mine` | rider or driver on the trip | Read trip(s) |
| `POST /trips/{id}/cancel` | rider or driver on the trip | → `CANCELLED` |
| `PATCH /trips/{id}/assign` | **matching-service only** | `{driverId, offerId}` → `MATCHED` |
| `POST /trips/{id}/arrived` · `/start` · `/complete` | driver | → `DRIVER_ARRIVED` / `IN_PROGRESS` / `COMPLETED` |

### What business rules exist?
The legal path a trip can travel is fixed and enforced in one place, never scattered across the codebase:

| From | Allowed next states |
|---|---|
| `REQUESTED` | `MATCHED`, `CANCELLED`, `NO_DRIVERS_FOUND` |
| `MATCHED` | `DRIVER_ARRIVED`, `CANCELLED` |
| `DRIVER_ARRIVED` | `IN_PROGRESS`, `CANCELLED` |
| `IN_PROGRESS` | `COMPLETED` |

Anything not on this list — jumping straight from `REQUESTED` to `IN_PROGRESS`, or trying to un-complete a finished trip — is rejected outright, not quietly ignored. Two more rules matter just as much: only the rider or driver actually *on* a trip can act on it (a stranger can't cancel your ride), and `assign` can only ever be called by Matching Service internally — a rider or driver can never pick their own match.

### What database tables/entities do we need?
A `Trip` needs: an id; a rider id and a driver id (the driver id starts empty — nobody's assigned yet — and fills in once matched); pickup and drop-off location as **separate latitude and longitude numbers**, not bundled into one array, because pricing, matching, and the map on the frontend all need to read pickup latitude independently of pickup longitude; a fare in integer cents (never a decimal, for the same reason covered in the ground rules); a surge multiplier, recorded at request time so it doesn't silently change mid-ride; and a timestamp for each stage reached (requested, matched, started, completed) — each one empty until that stage actually happens, which is itself a record of the ride's history.

### Which events should it publish?
Every time a trip changes state, it announces exactly one event — `TRIP_REQUESTED`, `TRIP_MATCHED`, `DRIVER_ARRIVED`, `TRIP_STARTED`, `TRIP_COMPLETED`, `TRIP_CANCELLED`, or `NO_DRIVERS_FOUND`. Matching Service cares about `TRIP_REQUESTED` because that's its cue to start looking for a driver. Notification Service cares about almost all of them, because the rider and driver both want to know the moment anything changes. Payment Service cares specifically about `MATCHED` (hold the fare), `COMPLETED` (pay the driver), and `CANCELLED` (refund if money was held). Analytics cares about all of them, because it's just recording history. The exact shape of this announcement is a strict contract — every service listening was built expecting this precise structure, so it's covered once, in full, in the **Kafka Event Pipeline** chapter rather than repeated here.

One rule matters more than the shape itself: an event only goes out *after* the database change is actually saved. An announcement for something that then fails to save is worse than no announcement at all — it tells the rest of the system a lie.

### Which events should it consume?
None. Trip Service is upstream of almost everything — it's a source of announcements, not a listener. It does make one live request elsewhere (asking Pricing Service for a fare when a trip is created), but that's a direct question-and-answer call, not an event, because the rider is waiting on screen for the number *right now*. That distinction — synchronous calls for "the user is waiting," events for "announce and move on" — is one of the most important ideas in this whole project.

### When is this service "good enough"?
When every legal transition works, every illegal one is rejected, only the right people can act on a trip, money is always integer cents, and exactly one event fires per transition, only after it's actually saved. **This is enough for now. Move to the next service.**

### Production improvements (optional)
The biggest real gap here is the **transactional outbox pattern**. Right now, if the process crashes in the exact instant between "trip saved to the database" and "event sent to Kafka," that event is lost forever — the database says `MATCHED`, but nobody ever heard about it. The outbox pattern fixes this by writing the event into the *same database transaction* as the trip change (into an "outbox" table), then having a separate small process read that table and publish to Kafka, retrying until it succeeds. That guarantees the event can never be silently dropped. This project ships *without* it, on purpose, and documents the gap — knowing exactly where the gap is, and why, is worth more in an interview than never having shipped a gap at all.

### Common beginner mistakes
The most common mistake is representing trip state as a loose string or a boolean flag ("isMatched", "isStarted"...) instead of one enforced state machine — which quietly allows impossible combinations, like a trip that's both "not started" and "completed." A close second is storing the fare as a float, which works fine in every demo and then produces a one-cent discrepancy somewhere in the ledger that takes an afternoon to trace back to its actual source.

### Interview perspective
Be ready to explain *why* the state machine lives in one place instead of being re-checked in every endpoint (a single source of truth for "is this transition legal" — change the rule once, it's correct everywhere), and *why* events fire after the commit, not before (announcing something that might not have actually happened is a bug waiting to surface downstream, in someone else's service, far from where it was caused).

### Before moving on
- [ ] Every legal transition in the table succeeds; every illegal one is rejected
- [ ] A non-owner acting on someone else's trip is blocked
- [ ] `assign` is only reachable by Matching Service, never by a rider or driver directly
- [ ] Exactly one event fires per transition, and only after the database commit succeeds

If every box is checked: **stop here, this service is done. Move to the next one.**

---

## 3. Pricing Service — What Should This Ride Cost?

### What problem are we solving?
A rider needs to see a fair, believable price *before* they commit to a ride, and that price needs to reflect real conditions — distance, time, and how many other people are asking for rides in the same area right now.

### Why does this service exist?
Pricing Service owns the fare formula — one formula, in one place. If fare logic were scattered across trip creation, the receipt screen, and anywhere else a number gets shown, those numbers would eventually disagree with each other, and "why did I get charged more than the quote" becomes a real, unanswerable support ticket.

### What should this service do?
- Take a pickup and drop-off location and return a fare estimate.
- Factor in distance, estimated time, and current demand (surge).
- Enforce a sane floor so a trip around the corner is never charged 3 cents.

### What should it NOT do?
- It should not create or track trips — it just answers a pricing question.
- It should not know anything about who's asking, or whether they can afford it.
- It should not store anything permanently — it's a calculator, not a record-keeper.

### What are the main functionalities?
**Getting a quote** is the one thing this service does: given two points on a map, it works out how far apart they are, estimates how long that would take, and multiplies by a rate — then applies a surge multiplier if the pickup area is busy.

| Endpoint | What it does |
|---|---|
| `GET /api/pricing/quote?pickupLat&pickupLng&dropLat&dropLng` | → `{fareCents, surge, distanceKm, durationMin}` |

### What business rules exist?
- **There's a minimum fare (700 cents).** Without a floor, a two-block ride would round down to almost nothing, which doesn't cover the cost of dispatching a driver at all.
- **Surge is capped at 3×.** Demand pricing is meant to encourage more drivers to come online, not to gouge riders indefinitely — a cap keeps it within a defensible range.
- **The exact formula:** `fare = max(700, round((500 + 120×distanceKm + 30×durationMin) × surge))` cents, where duration is estimated from distance assuming a steady 30 km/h, distance comes from the haversine formula (straight-line distance across the Earth's curved surface, a reasonable stand-in for real routing without needing a paid maps API), and surge is `clamp(1.0 + 0.2×⌊busyCount/5⌋, 1.0, 3.0)` based on how many recent requests came from the same area.

### What database tables/entities do we need?
None. This is worth pausing on: **not every service needs a database.** Pricing Service reads one number from Redis (how busy the pickup area is right now) and computes an answer on the fly — there's nothing here that needs to be permanently remembered.

### Which events should it publish?
None. A price quote isn't news anyone else needs to hear about.

### Which events should it consume?
None. Pricing Service only answers direct questions — it's a calculator, not a listener.

### When is this service "good enough"?
When the formula matches the spec exactly, the minimum fare and surge cap both hold, and the distance calculation is accurate to within a couple of percent of reality. **This is enough for now. Move to the next service.**

### Production improvements (optional)
A real ride-hailing pricing engine adds real-time traffic data (instead of assuming a flat 30 km/h everywhere), a proper routing API for actual road distance instead of straight-line distance, and machine-learned demand prediction instead of a simple counter. Each of those is a genuinely large project on its own — understanding *why* they'd matter (a straight line across a river isn't a real route) is more valuable right now than building any of them.

### Common beginner mistakes
The most common mistake is letting the fare get calculated in more than one place — once when the quote is shown, and slightly differently when the trip is actually charged — which quietly produces a mismatch that erodes trust. The fix is architectural, not clever code: one service owns the formula, and everyone else asks it, every time, instead of re-implementing it.

### Interview perspective
Be ready to explain why this is a synchronous HTTP call rather than a Kafka event — the rider is staring at a loading spinner waiting for a number, so "eventually" isn't good enough here the way it is for "notify the driver the ride is cancelled." That sync-vs-async boundary, and being able to say precisely where it falls and why, is one of the most interview-relevant ideas in the entire project.

### Before moving on
- [ ] The formula matches the spec, including the minimum fare and the surge cap
- [ ] Distance calculation is accurate to within a couple percent on a known route
- [ ] The quote endpoint responds fast enough that a human doesn't notice a delay

If every box is checked: **stop here, this service is done. Move to the next one.**

---

## 4. API Gateway — The Front Door

### What problem are we solving?
Ten different services can't each independently re-verify "is this a real, logged-in user" and "are they sending too many requests too fast" — that's the same logic, duplicated ten times, and ten chances to get it slightly wrong in one of them.

### Why does this service exist?
The gateway is the single front door every request walks through before reaching any service. It checks identity once, checks request speed once, and passes trustworthy information to everything behind it — so nothing behind it has to re-check.

### What should this service do?
- Accept every request (and WebSocket connection) from the outside world.
- Verify the caller's token is real and not expired.
- Attach the caller's true identity to the request in a way downstream services can trust.
- Slow down anyone sending requests too fast.
- Route each request to the right service behind it.

### What should it NOT do?
- It should not know anything about trips, prices, or users beyond "is this token valid."
- It should not perform business logic — it's a checkpoint, not a decision-maker about the ride itself.

### What are the main functionalities?
**Routing** sends `/api/trips/**` to Trip Service, `/api/pricing/**` to Pricing Service, and so on — one predictable map, in one place. **Authentication** means every request (except login and register, which obviously can't require being already logged in) must carry a valid token, or it's rejected before it reaches any service at all. **Identity forwarding** is a subtle but important one: the gateway reads the *verified* identity out of the token and attaches it as a trusted header — and it deliberately **strips** any identity header the client tried to send itself first. **Rate limiting** slows down a single user hammering the trip-request endpoint, so one runaway client can't overwhelm the system.

### What business rules exist?
- No token, or a garbage/expired token → rejected immediately.
- A client that tries to claim an identity by sending its own `X-User-Id` header is ignored — the gateway always overwrites it with the identity it verified itself from the token.
- Login and register are the two routes allowed through without a token (you can't be asked to prove who you are before you're allowed to say who you are).
- Too many requests too fast from the same user get throttled with a clear "slow down" response, not silently dropped.

### What database tables/entities do we need?
None directly, though it does read from the same Redis instance the rest of the system uses, to track how many requests each user has made recently (that counter is the rate limiter's memory).

### Which events should it publish?
None. The gateway doesn't participate in the business event story — it's plumbing, not a business actor.

### Which events should it consume?
None, for the same reason.

### When is this service "good enough"?
When every route reaches the right service, an invalid token is always rejected, a client-forged identity header never survives, and a burst of requests gets throttled instead of overwhelming a backend service. **This is enough for now. Move to the next service.**

### Production improvements (optional)
Real deployments often add mTLS between the gateway and internal services (so a compromised internal network still can't forge identity headers convincingly), request tracing (a unique ID attached to a request so you can follow it across every service it touches), and circuit breakers (so a struggling backend service doesn't take the whole gateway down with it). Each solves a real production failure mode — worth knowing the failure mode even before you've needed the fix.

### Common beginner mistakes
The single most common and most dangerous mistake here is **trusting an identity header the client sent themselves**, instead of only trusting the identity the gateway itself extracted from a verified token. If a service ever reads `X-User-Id` without the gateway having stripped and replaced it first, anyone can claim to be any user just by adding a header to their own request.

### Interview perspective
Be ready to explain the difference between authentication (proving who you are) and authorization (deciding what you're allowed to do) and *why* this project puts authentication at the edge but leaves fine-grained authorization to individual services. Also be ready to explain why the rate limiter is a token bucket (which allows short bursts but enforces a steady average) rather than a hard fixed cap, and what breaks if the gateway's clock disagrees with a service's clock on how expired a token is.

### Before moving on
- [ ] Every route reaches the correct backend service
- [ ] Missing, garbage, or expired tokens are always rejected
- [ ] A client-sent identity header never survives — the gateway's own verified identity always wins
- [ ] A burst of requests gets throttled with a clear response, not silently dropped or crashed

If every box is checked: **stop here, this service is done. Move to the next one.**

---

## 5. Location Gateway — Where Is Every Driver Right Now?

### What problem are we solving?
Thousands of drivers' phones are sending their GPS position every couple of seconds. That's a constant, high-volume stream — completely different from "occasionally save a trip to a database" — and the rest of the system needs a fast answer to "who's near this pickup point right now."

### Why does this service exist?
Location Gateway's only job is to keep driver positions current and answer "who's nearby" fast. It's written in Python with async sockets specifically because holding open thousands of simultaneous connections is a different problem than handling a normal request-and-respond API, and Python's asyncio model is well-suited to exactly that.

### What should this service do?
- Accept a live connection from each online driver's phone.
- Record where they are, and whether they're still online.
- Let a rider watch one specific driver's position update live.

### What should it NOT do?
- It should not decide *which* driver gets matched to a ride (that's Matching Service).
- It should not store trip history — driver position is a "right now" fact, not a permanent record.

### What are the main functionalities?
**Coming online** marks a driver as available the moment their phone connects. **Pinging** happens every couple of seconds while the driver is online: it updates their position and proves they're still alive. **Going offline** — whether the driver taps a button or their connection just drops — removes them from consideration immediately. **Live tracking** lets a rider who's been matched with a driver watch that one driver's position update in real time, without seeing anyone else's location.

| Connection | Who | What happens |
|---|---|---|
| `WS /ws/location` | driver | Sends a position every ~2s while online |
| `WS /ws/track/{driverId}` | rider | Receives that one driver's live position |

### What business rules exist?
- **A driver is only "available" while pings keep arriving.** If pings stop — the app crashed, the phone lost signal — the driver should stop being considered nearby within seconds, not sit around looking available forever.
- **A ping while a driver is busy on a trip still proves they're alive, but must not overwrite their busy status.** Being alive and being available are two different facts, and updating one should never accidentally reset the other.

### What database tables/entities do we need?
Nothing in Postgres — this is Redis-only, and deliberately so, because none of this needs to survive a restart or be queried historically. A driver's position is written into a Redis structure built for "who is near this point" queries (covered fully in **Matching Service**, since that's what actually reads it). A separate key records "is this driver available, busy, or offline" with a short expiry — so if pings stop arriving, that key simply times out and disappears on its own, with no cleanup code required. A third channel broadcasts each new position live to anyone watching that specific driver.

### Which events should it publish?
None over Kafka — driver position is too high-frequency and too ephemeral to be a Kafka event (imagine publishing a durable, ordered event every two seconds for every driver in the city — nothing needs that much permanence for a fact that's stale a moment later). Instead it publishes over a lightweight Redis channel meant for exactly this kind of live, throwaway broadcast.

### Which events should it consume?
None. It only reacts to the driver's own phone connecting, pinging, and disconnecting.

### When is this service "good enough"?
When a driver connecting shows up as available, pings keep them available without losing their busy/available distinction, disconnecting removes them promptly, and a rider watching a specific driver sees their position update live. **This is enough for now. Move to the next service.**

### Production improvements (optional)
At real scale, one shared "all drivers in the world" index becomes a bottleneck — the real fix is sharding drivers by rough geographic area (city, or a coarse grid cell) so each shard only ever needs to search a manageable number of drivers, with a small amount of extra logic at the edges where a driver near a shard boundary might be relevant to two shards at once.

### Common beginner mistakes
A common trap is testing "did the driver time out" using an actual `sleep()` in a test — which makes the test suite slow and flaky. The better approach is to check the expiry countdown directly rather than waiting for real time to pass. Another common trap is conflating "storing position" with "proving liveness" as the same operation — they're two different signals living in two different places for a reason.

### Interview perspective
Be ready to explain why a short expiry ("TTL") is used as the liveness signal instead of an explicit health-check ping from the server — it's a passive pattern (silence means gone) rather than an active one (ask and wait for an answer), and it scales better because the server never has to remember to check on anyone. Also know the sharding answer above cold — "how would this handle ten million drivers" is a near-guaranteed question.

### Before moving on
- [ ] A connecting driver becomes available immediately
- [ ] Regular pings keep a driver alive without overwriting a busy status
- [ ] A dropped connection removes the driver from consideration within seconds
- [ ] A rider watching one driver sees only that driver's position updates

If every box is checked: **stop here, this service is done. Move to the next one.**

---

## 6. Matching Service — Finding You a Driver

### What problem are we solving?
A rider just asked for a ride. Somewhere nearby, several drivers are available. The system needs to pick one, offer them the ride, and handle the very real possibility that they say no — fast, and without two different riders accidentally being handed the same driver.

### Why does this service exist?
Matching Service owns the entire "find and offer" process: searching nearby, making an offer, handling accept/decline/timeout, and trying the next-best driver if needed. Nothing else in the system decides which driver a rider gets.

### What should this service do?
- Search for available drivers near a pickup point.
- Offer the ride to the best candidate.
- Handle acceptance, decline, and timeout, moving to the next candidate as needed.
- Give up gracefully after a few rounds if nobody's available.

### What should it NOT do?
- It should not track driver GPS itself — it asks Location Gateway's index for that.
- It should not create or modify trip records directly — it tells Trip Service the outcome and lets Trip Service enforce whether that's legal.

### What are the main functionalities?
**Finding nearby drivers** is a search: given a pickup point, return the closest available drivers, closest first. **Making an offer** reserves one specific driver for one specific rider *before* asking them, so two different ride requests can never both be offered to the same driver at the same moment. **Responding to an offer** — whether the driver accepts, declines, or simply never answers before the 15-second window closes — always ends with a clear next step, never a stuck state. **Giving up** happens after three rounds of "nobody available or nobody accepted" — at that point the rider is told plainly that no drivers were found, rather than waiting forever.

### What business rules exist?
- **A driver can never be offered two rides at once.** The moment a driver is offered a ride, they're marked busy immediately — not after they accept — so a second, simultaneous search can't also find and offer them. This has to be done as one atomic "check and set" operation, not two separate steps, because between "check if available" and "mark as busy" as two separate calls, another search could slip in and grab the same driver. This exact kind of bug (checking something, then acting on it, as two separate steps that another process can slip in between) is one of the most common and hardest-to-reproduce categories of concurrency bug — well worth internalizing here.
- **An offer expires after 15 seconds.** A driver who doesn't respond shouldn't block the ride forever — the system moves on and tries the next candidate.
- **Matching gives up after three rounds**, at which point the rider is told no drivers were found rather than waiting indefinitely.

### What database tables/entities do we need?
Nothing in Postgres — like Location Gateway, this lives entirely in Redis, because offers are short-lived by nature. A live "offer" record captures which trip, which driver, and what fare is being offered, set to automatically disappear after 30 seconds whether or not anyone answers, plus a small time-ordered structure the service polls once a second to notice when an offer's response window has closed without an answer.

### Which events should it publish?
When a driver is offered a ride, Matching Service publishes a `DRIVER_OFFER` notification, addressed to that one driver, so Notification Service can push it to their phone the instant it happens.

### Which events should it consume?
It listens for `match-requests` — a message meaning "a rider needs a driver," sent by Trip Service the moment a ride is requested, and also by Scheduling Service when a booked ride's pickup time arrives. Receiving one of these is what kicks off the entire search-and-offer process described above.

### When is this service "good enough"?
When nearest-driver search returns correctly ordered, available-only results; a driver can never receive two simultaneous offers; declines and timeouts correctly move to the next candidate; and giving up after three rounds correctly reports "no drivers found" back to the trip. **This is enough for now. Move to the next service.**

### Production improvements (optional)
Real systems often broadcast an offer to *several* nearby drivers at once (first to accept wins) instead of offering strictly one at a time — faster for the rider, but requires careful handling of "what happens to the other four offers the instant one driver accepts." Driver rating and acceptance-history weighting (offering better matches to more reliable drivers first) is another common real addition. Both are genuinely more complex than what's built here, and neither is required to demonstrate the core idea.

### Common beginner mistakes
The single most common mistake is exactly the one flagged above: checking "is this driver available" and then separately setting "now they're busy," as two operations instead of one atomic operation. It works perfectly in every manual test (you're only ever testing it alone) and then fails exactly once in front of real concurrent traffic, in a way that's very hard to reproduce afterward.

### Interview perspective
Be ready to explain what an atomic "compare-and-swap" is and why it prevents the double-offer race above, and be ready to talk through the measured search latency (tested here with ten thousand simulated drivers, keeping the 95th-percentile response comfortably under 100 milliseconds) — a *measured* number, not a guessed one, is a meaningfully stronger interview answer.

### Before moving on
- [ ] Nearest-driver search returns only available drivers, closest first
- [ ] Two simultaneous searches can never both grab the same driver
- [ ] A decline or a 15-second timeout correctly moves to the next candidate
- [ ] After three rounds with no success, the rider is told plainly that no drivers were found

If every box is checked: **stop here, this service is done. Move to the next one.**

---

## 7. The Kafka Event Pipeline — How Services Talk Without Knowing Each Other

This one isn't a single service — it's the connective tissue between several of them, which is exactly why it deserves its own explanation before you look at any one service's part in it.

### What problem are we solving?
When a trip is completed, at least three different things need to happen: the driver and rider get notified, the driver gets paid, and the event gets recorded for analytics. If Trip Service had to know about all three and call each one directly, it would need to know who exists downstream, wait for each of them to respond, and break if any one of them was temporarily down. That's fragile, and it gets worse every time a new downstream consumer is added.

### Why does this feature exist?
Kafka lets Trip Service simply announce "this happened" once, without knowing or caring who's listening. Anyone who cares can subscribe. Trip Service never has to change when a new listener is added later — that's the entire value of the pattern.

### What should it do?
- Carry announcements ("a trip was matched," "a trip was completed") from the service that caused them to every service that cares.
- Guarantee that events about the same trip arrive **in order**, even if the system is under heavy load.
- Let more than one downstream service listen to the same announcement independently, each at its own pace.

### What should it NOT do?
- It should not be used for things that need an answer right now (a fare quote is a direct question, not an announcement).
- It should not be treated as a guaranteed, exactly-once delivery mechanism without help — more on that below.

### What are the main functionalities?
Three topics carry three different kinds of announcement: `trip-events` carries every trip lifecycle change, and is read by Notification, Payment, and Analytics services, each independently. `match-requests` carries "a rider needs a driver," read by Matching Service. `notifications` carries "tell this specific person something," read by Notification Service. Every message is tagged with a key — for `trip-events` and `match-requests`, that key is the trip's ID — and that single choice is what guarantees all the events about one particular trip always arrive in the order they happened, even while thousands of other trips' events are flowing through the same topic at the same time.

The shape every `trip-events` message takes is a strict, versioned contract — every service consuming it was built expecting exactly this:

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

### What business rules exist?
- **A "consumer group" is how more than one service can read the same topic independently.** Notification, Payment, and Analytics each form their own group reading `trip-events` — each one moves through the messages at its own pace, and none of them affects the others' progress. That independence is the entire point of the pattern.
- **Delivery is "at-least-once," not "exactly-once."** Kafka can, under real failure conditions, redeliver the same message more than once. The honest, production answer to "how do you guarantee exactly-once delivery" is: you don't, at the transport level — you build consumers that don't care if they see the same message twice (this is called being **idempotent**), which is a far more achievable and far more common real-world pattern than trying to prevent redelivery outright.

### What database tables/entities do we need?
None here — this chapter has no database of its own. Each service that consumes an event owns its own database changes, which is exactly why each of them is explained in its own chapter.

### Which events should it publish/consume?
This chapter *is* the events — see the topic table above. The important open question isn't which events exist, it's what happens if publishing one fails partway.

### The gap worth knowing cold
Here's the honest weak point, and arguably the single most valuable thing to understand in this entire project: a trip's status gets saved to the database, and then, as a separate step, the announcement gets sent to Kafka. If the process crashes in the narrow window between those two steps, the database says the trip is `MATCHED` — but nobody downstream ever heard about it. No notification, no payment hold, nothing.

The real fix is called the **transactional outbox pattern**: instead of publishing to Kafka directly, the event gets written into an "outbox" table in the *same* database transaction as the trip change itself — so either both happen together, or neither does. A separate small process then reads that outbox table and publishes to Kafka, retrying as needed until it succeeds, and only then marks the outbox row as sent. This project ships *without* the outbox, on purpose, and documents the gap rather than pretending it isn't there — being able to describe this gap and its fix, unprompted, is worth more in an interview than having built it and being unable to explain why it mattered.

### Common beginner mistakes
Assuming Kafka guarantees a message is delivered exactly once, with no extra work, is the single most common misunderstanding — and it leads directly to bugs like a rider getting charged twice for one ride if a payment consumer isn't built to safely handle seeing the same event twice.

### Interview perspective
Be ready to draw the outbox pattern on a whiteboard, unprompted, the moment "what happens if the process crashes right after saving but before publishing" comes up — this exact question is very likely to be asked, precisely because it's the realistic failure mode every event-driven system has to reckon with.

### Before moving on
- [ ] You can explain, in one sentence, why the partition key is the trip ID
- [ ] You can explain what a consumer group is, and why three services can read one topic independently
- [ ] You can draw the transactional outbox pattern from memory and say exactly what gap it closes

If every box is checked: **you understand the pipeline. Now go build the services that plug into it.**

---

## 8. Notification Service — Telling People What Just Happened

### What problem are we solving?
A rider and driver are both staring at their phones, waiting to know the moment something changes — a driver accepted, arrived, the ride started. That has to feel instant, which rules out "refresh the page to check."

### Why does this service exist?
Notification Service holds a live connection open to every connected phone and pushes messages to the *right* person the instant something relevant happens, by listening to the same Kafka topics everyone else does.

### What should this service do?
- Keep a live connection open per connected user.
- Listen for trip and offer events, and push the relevant ones to the right person.
- Make sure a message reaches its recipient exactly once, even if Kafka redelivers it.

### What should it NOT do?
- It should not decide *what* happened — Trip Service and Matching Service already decided that; this service just delivers the news.
- It should not store trip history — it's a delivery mechanism, not a record-keeper.

### What are the main functionalities?
**Connecting** registers a phone's live connection against that user's ID, so messages can find them later. **Routing** takes an incoming event and figures out exactly who should see it — a `TRIP_MATCHED` event, for instance, needs to reach both the rider *and* the driver, from one single event. **Deduplicating** guards against the at-least-once delivery gap covered in the Kafka chapter: if the same event somehow arrives twice, the second copy is recognized and silently dropped instead of confusing the person on the other end with a duplicate push.

### What business rules exist?
- **An event is only marked "handled" after it's actually been delivered**, not the moment it's received. If that distinction is skipped and the service crashes mid-delivery, an event can be lost forever — Kafka thinks it was handled, but the message never actually reached anyone. This is a purposeful mirror of the AFTER_COMMIT rule in Trip Service: don't say something's done until it actually is.
- **The same event should never be delivered twice to the same person**, even though Kafka doesn't strictly guarantee that on its own — this service keeps a short memory of recently seen event IDs specifically to close that gap itself.

### What database tables/entities do we need?
None permanent — the live connection registry is in-memory (a map of user ID to their open connections), and it's fine for that to reset if the service restarts, since a phone will simply reconnect.

### Which events should it publish?
None — it's a pure consumer, the last stop for the events it receives.

### Which events should it consume?
`trip-events` (routing most trip lifecycle changes to whoever's involved) and `notifications` (specifically `DRIVER_OFFER` messages from Matching Service, routed to exactly the one driver being offered a ride).

### When is this service "good enough"?
When a relevant event reaches the right connected user(s) live, and a redelivered event is silently deduplicated instead of arriving twice. **This is enough for now. Move to the next service.**

### Production improvements (optional)
At real scale, this service would run as many replicas, and the "which connection belongs to which user" registry would need to live somewhere shared (like Redis) rather than in one process's memory, since a user's live connection might be held by any one of several running instances.

### Common beginner mistakes
The most common mistake is marking a Kafka message as "processed" as soon as it's received, rather than after it's actually been delivered — which silently loses messages exactly when the service is under stress and most needs to not lose them.

### Interview perspective
Be ready to explain consumer groups and what happens when this service is scaled from one instance to two — specifically, how Kafka splits the topic's partitions between the two instances so each message is still only handled by one of them, never both.

### Before moving on
- [ ] A connected user receives events addressed to them, live
- [ ] A `TRIP_MATCHED` event correctly reaches both the rider and the driver
- [ ] A redelivered event is silently deduplicated, never shown twice

If every box is checked: **stop here, this service is done. Move to the next one.**

---

## 9. Analytics Sink — Remembering Everything That Happened

### What problem are we solving?
Someone, eventually, is going to want to answer questions like "how many rides happened last week" or "what's our average fare by neighborhood." That requires a historical record of every event that's ever happened — not just the current state.

### Why does this service exist?
Analytics Sink's only job is to listen to the trip event stream and write every single event into a table, permanently, exactly as it happened. It doesn't interpret anything or compute anything — it's a scribe, not an analyst.

### What should this service do?
- Listen to every trip event.
- Write each one into a permanent record, without losing any and without duplicating any.

### What should it NOT do?
- It should not compute aggregates, dashboards, or reports — that's a separate concern for a separate tool, built on top of this raw data later, not now.
- It should not affect or block anything else in the system — it's a silent observer, not a participant.

### What are the main functionalities?
**Recording** simply takes each event off the topic and inserts it as a row. That's the entire job. The interesting part isn't the logic — it's what this service *demonstrates*: it's a completely independent second reader of the exact same `trip-events` topic that Notification and Payment also read, at its own pace, with zero effect on either of them. Watching two independent consumer groups read the same topic at their own speed is the clearest possible proof that this decoupled design actually works.

### What business rules exist?
- **Writing the same event twice must not create two rows.** Since Kafka can redeliver, the insert is written so that trying to insert an event with an ID that's already there simply does nothing, rather than erroring or duplicating.

### What database tables/entities do we need?
One simple table: an event ID (used to guarantee no duplicates), the event type, the trip it belongs to, when it happened, and the raw event payload — kept exactly as received, since the whole point is not to lose or reshape the historical record.

### Which events should it publish?
None — it's a pure sink, the end of the line.

### Which events should it consume?
`trip-events`, the same topic Notification and Payment also read — independently, as its own consumer group.

### When is this service "good enough"?
When every event lands as a row, and replaying the same event never creates a duplicate row. **This is enough for now. Move to the next service.** Locally, "the warehouse" is just a Postgres table — that's a completely honest, defensible v1. In a real production deployment this raw stream would usually flow into cloud storage and then into a proper analytics warehouse, which is worth being able to describe, but is not worth building for this project.

### Production improvements (optional)
Real analytics pipelines add streaming aggregation (rolling counts and averages computed continuously, not just raw rows) and a dedicated columnar warehouse built for fast queries over huge historical datasets. Neither is needed to prove the underlying idea here.

### Common beginner mistakes
The most common mistake is reaching for a heavy stream-processing framework for what is genuinely just "read a message, insert a row" — sometimes the simple, boring implementation actually is the correct one, and reaching for more machinery than the problem needs is itself the mistake.

### Interview perspective
Be ready to explain the fan-out story plainly: one event, multiple independent consumer groups, each moving at its own pace with zero coordination between them — that's the concrete payoff of the entire event-driven design, made visible in exactly one place.

### Before moving on
- [ ] Every trip event lands as a row
- [ ] Replaying the same event never creates a duplicate

If every box is checked: **stop here, this service is done. Move to the next one.**

---

## 10. Scheduling Service — Rides You Book for Later

### What problem are we solving?
Not every ride is "I need one right now" — some are "pick me up at the airport in three hours." The system needs to remember that promise and act on it automatically, at the right moment, even if nobody's watching.

### Why does this service exist?
Scheduling Service holds future bookings and is responsible for turning each one into a real trip request at the right moment — without ever double-triggering the same booking, and without ever losing one just because the service happened to restart.

### What should this service do?
- Accept a booking for a future pickup time.
- Wait until the right moment, then turn it into a real trip request.
- Let a rider see and cancel their upcoming bookings.

### What should it NOT do?
- It should not do the actual matching itself — once a booking's time comes, it hands off to the normal ride-request flow, the same one a live "right now" request uses.

### What are the main functionalities?
**Booking** takes a future pickup time — at least 15 minutes out, so "book for right now" doesn't quietly bypass the normal request flow — and holds onto it. **Triggering** is the part that runs continuously in the background: a short while before each booking's pickup time, it wakes that booking up and turns it into a real trip request, the same as if the rider had just tapped "request" at that exact moment. **Listing and cancelling** let a rider manage their own upcoming bookings.

| Endpoint | What it does |
|---|---|
| `POST /api/bookings` | `{pickupTime, pickupLat, pickupLng, dropLat, dropLng}`, pickup time must be ≥15 min ahead → `201 Booking` |
| `GET /api/bookings/mine` / `POST /api/bookings/{id}/cancel` | list / cancel a booking |

### What business rules exist?
- **A booking can only be claimed for triggering by exactly one process, even if several copies of this service are running at once.** If two instances both check "is this booking due" and both see yes, and both act on it, the rider gets two ride requests for one booking. The check-and-claim has to be one atomic step, not two — the exact same category of bug flagged in Matching Service, showing up again in a different shape, which is itself worth noticing: this pattern (check-then-act as two unsafe separate steps) recurs constantly in distributed systems.
- **A booking that was "due" while the service happened to be down still gets picked up the next time the service checks**, rather than being silently missed forever.
- **If turning a booking into a real trip request fails, it's retried a short while later, up to three times, before being marked failed** — rather than either silently giving up immediately or retrying forever.

### What database tables/entities do we need?
A `Booking` needs: an id, the rider, the requested pickup time and locations, a status (`SCHEDULED`, `TRIGGERED`, or `CANCELLED`), and — once triggered — the ID of the real trip it turned into, so the two records stay linked.

### Which events should it publish?
When a booking's time comes, it publishes the same `match-requests` message Trip Service publishes for an immediate ride — from Matching Service's point of view, a scheduled ride that just came due looks exactly like a brand new ride request, which is precisely the point: one matching pipeline serves both cases.

### Which events should it consume?
None directly — it works off its own internal time-ordered queue rather than reacting to other services' events.

### When is this service "good enough"?
When a booking correctly waits until it's due, two concurrent checks can never both claim and trigger the same booking, an overdue booking during downtime is still eventually picked up, and a failed trigger retries a bounded number of times before giving up cleanly. **This is enough for now. Move to the next service.**

### Production improvements (optional)
The one honestly-documented gap here: if the process crashes in the narrow window after claiming a booking but before successfully turning it into a trip, that booking is currently lost rather than retried. The fix is a "two-phase claim" — move a claimed booking into a separate pending queue instead of deleting it outright, so a crash mid-trigger leaves it recoverable rather than gone. This project ships without that refinement and says so plainly, the same honest-gap pattern as the Kafka outbox above.

### Common beginner mistakes
The most common mistake is implementing "check if due, then remove it" as two separate Redis operations instead of one atomic operation — which works in every single-instance test and then double-fires under real concurrent load, exactly like the matching-service driver race. A second common mistake is reaching for a fire-and-forget notification mechanism (something that only fires while something happens to be listening) instead of a durable, poll-based queue that survives the service being temporarily down.

### Interview perspective
Be ready to compare this design to something like a cloud delay queue (a message that only becomes visible after a set delay) — the underlying idea is the same, "hold this until a future moment, then act," and being able to draw that parallel shows you understand the pattern, not just this one implementation of it.

### Before moving on
- [ ] A booking waits until its pickup time approaches before doing anything
- [ ] Two concurrent checks can never both claim the same booking
- [ ] A booking that was due during downtime is still triggered afterward
- [ ] A failed trigger retries a bounded number of times, then stops cleanly

If every box is checked: **stop here, this service is done. Move to the next one.**

---

## 11. Payment Service — The Ledger (Where the Money Lives)

### What problem are we solving?
Real money (well, simulated money, but treated with the same seriousness) is moving between riders, drivers, and the platform. Every one of those movements has to be traceable, correct, and impossible to accidentally duplicate — "the balance looked right in testing" is not good enough for money.

### Why does this service exist?
Payment Service is the only place any balance ever changes. It doesn't just store a number per account — it keeps a permanent, append-only record of every single movement, so the current balance is always a *derived* fact, recomputable from history, rather than a number that gets blindly overwritten.

### What should this service do?
- Hold a rider's ride payment "in escrow" the moment they're matched with a driver.
- Split that held amount between the driver and the platform once the ride completes.
- Refund it back to the rider if the ride is cancelled.
- Guarantee that replaying the same instruction twice never moves money twice.

### What should it NOT do?
- It should not decide *when* a trip is matched, completed, or cancelled — it reacts to those facts, it doesn't determine them.
- It should not ever allow a wallet to go negative except for the one special account that represents money coming into the system from outside.

### What are the main functionalities?
**Topping up** adds money into a rider's wallet from outside the system (a fake, simulated deposit — there's no real payment processor here, and that's fine, since the point is the ledger mechanics, not real-world payment processing). **Holding funds in escrow** happens the instant a trip is matched: the fare moves out of the rider's wallet into a holding account tied specifically to that one trip, so the money is set aside before the ride even starts. **Disbursing** happens when the ride completes: the held amount splits, 80% to the driver and 20% to the platform. **Refunding** happens if a trip is cancelled while money is still held — but only if money was actually held in the first place; cancelling a trip that never got that far is a normal, harmless no-op.

| Endpoint | What it does |
|---|---|
| `POST /api/payments/wallet/topup` | `{amountCents}` → `200 {balanceCents}` |
| `GET /api/payments/wallet` / `GET /api/payments/ledger/trip/{tripId}` | current balance / this trip's transaction history |

### What business rules exist?
Every rule here exists because money is exactly the place where "close enough" causes real damage:

- **Every single movement is recorded as two linked entries that must sum to exactly zero** — money leaving one account and landing in another, never created or destroyed, only moved. This is the essence of double-entry bookkeeping, and it means the books can always be checked for internal consistency, at any time, just by summing.
- **Entries are never edited or deleted once written**, only ever added — the ledger *is* the audit trail. If a mistake happens, you record a correcting entry, you don't rewrite history.
- **The exact same instruction, replayed twice** (which Kafka's at-least-once delivery guarantees will eventually happen), must produce the exact same result as running it once — the second attempt returns the original outcome and changes nothing further.
- **Two transactions touching the same two accounts at the same time must never deadlock each other.** This is solved by always locking the accounts involved in one consistent, agreed-upon order (e.g., always the "lower" account ID first) — the same fix a database textbook would call "lock ordering," and a real, common concurrency bug when skipped.
- **A wallet or escrow account can never go negative.** The one deliberate exception is the account representing money entering the system from outside (a top-up) — that one is allowed to go arbitrarily "negative" because it isn't real money, it's the fictional starting point every top-up draws from.

### What database tables/entities do we need?
An `accounts` table holds one row per wallet, per escrow-for-one-trip, and two special system-wide accounts — one representing outside money coming in, one representing the platform's own revenue — plus a cached balance that's a convenience, not a source of truth (it's always double-checked against the actual entries, never trusted blindly). A `ledger_transactions` table records each *instruction* that was carried out (a top-up, an escrow hold, a disbursement, a refund), tagged with a unique idempotency key so a replayed instruction can be recognized and safely ignored. A `ledger_entries` table records the actual individual money movements that made up each transaction — append-only, never edited, because this table *is* the audit log.

### Which events should it publish?
None. Payment Service is a reactor to trip events, not a source of new announcements elsewhere in the system — its output is the ledger itself, not a broadcast.

### Which events should it consume?
`TRIP_MATCHED` triggers holding the fare in escrow. `TRIP_COMPLETED` triggers splitting that escrow 80/20 between driver and platform. `TRIP_CANCELLED` triggers a refund back to the rider, but only if a hold actually exists for that trip.

### When is this service "good enough"?
When every transaction's entries sum to zero, entries are never edited after being written, replaying the same instruction never double-processes it, concurrent transactions never deadlock, and no wallet or escrow account ever goes negative. **This is enough for now. Move to the next service.**

### Production improvements (optional)
Real payment ledgers add multi-currency support, fee schedules, chargebacks and disputes, and a formal accounting close process (periods that get locked once reconciled). All of that is a genuinely separate, larger project layered on top of the same double-entry foundation — understanding that the foundation here would support those additions without being redesigned is the valuable takeaway, not building them now.

### Common beginner mistakes
By far the most common mistake, for anyone who hasn't built a ledger before, is storing a single mutable "balance" column and just doing `balance = balance + amount` directly. It looks correct in every manual test and quietly breaks under concurrency (two simultaneous updates can overwrite each other, silently losing one of them) and destroys your audit trail (you can no longer answer "how did we get to this number," only "what is this number right now"). Double-entry, append-only bookkeeping exists specifically to make both of those failure modes structurally impossible rather than merely unlikely.

### Interview perspective
Be ready to walk through the whole ledger, out loud, in about five minutes, with no notes — a classic prompt is literally "design Stripe's ledger." Also know cold: what happens if the disbursement process crashes right after locking the accounts but before finishing (answer: the whole transaction rolls back, the locks release, nothing partial is left behind); why the odd cent in an 80/20 split matters (because the total must still sum to exactly zero — you can't just let a fraction of a cent silently vanish); and roughly how you'd shard a ledger at massive scale (by account, with two-phase commit for the harder case of a transfer that spans two different shards).

### Before moving on
- [ ] Every transaction's entries sum to exactly zero
- [ ] No entry can ever be edited or deleted after it's written
- [ ] Replaying the same instruction twice changes nothing the second time
- [ ] Two concurrent transactions on the same accounts never deadlock
- [ ] No wallet or escrow account ever goes negative

If every box is checked: **stop here, this service is done. Move to the next one.**

---

## 12. Frontend — Web & Mobile Apps

### What problem are we solving?
Everything built so far is invisible without a screen. A rider needs to see a map, request a ride, watch it happen, and pay for it; a driver needs to go online, get offered rides, and work through them — all through an interface a human can actually use, on a laptop or in their pocket.

### Why does this exist?
The frontend's only job is to be a faithful window into the backend: show what's true, send what the user asks for, and get out of the way. It should feel almost boring from an architecture standpoint — nearly all of the *interesting* logic already lives in the services behind it.

### What should it do?
- Let a rider or driver register and log in.
- Let a rider request a ride, watch it progress live, and see a receipt.
- Let a rider book a ride for later.
- Let a rider see their wallet and top it up.
- Let a driver go online, receive offers, and move a trip through its stages.

### What should it NOT do?
- It should not recompute a fare itself — it shows the number the backend already calculated.
- It should not decide whether a trip transition is legal — it calls the endpoint and shows whatever comes back, success or rejection.
- It should not manage complex global state for an app this size — plain component state and a small amount of shared context is enough; reaching for a heavy state-management library here would be solving a problem this app doesn't actually have.

### What are the main functionalities?
**Auth** is login and registration, including picking whether you're a rider or a driver. **Requesting a ride** shows a map, lets the rider mark pickup and drop-off, shows a live quote as those pins move, and sends the request. **Watching a ride progress** listens for live updates and shows the current status, the driver's live position once matched, and a receipt once it's done. **Scheduling** lets a rider book a future ride and manage their upcoming bookings. **Wallet** shows balance and lets a rider top up. **Driving** lets a driver go online (which starts streaming their position), receive and respond to offers with a visible countdown, and move an active trip forward through its stages with clear buttons for each step.

### What business rules exist?
The frontend enforces almost none itself — that's deliberate, not a shortcut. Every rule that actually matters (is this transition legal, is this the right price, is this booking far enough in the future) is already enforced by the backend service that owns it; the frontend's job is to show the backend's answer clearly, including when that answer is "no."

### What "entities" does it need?
No database of its own. It keeps two small pieces of local state worth naming: the logged-in session (the auth token and the current user, kept in the browser's local storage so a refresh doesn't log you out), and whatever's currently on screen (the active trip's live status, for instance) — which is refreshed from the backend, not invented locally.

### What does it call, and what does it listen for?
Instead of publishing and consuming Kafka events like a backend service, the frontend does the human-facing equivalent: it **calls** the REST endpoints described in every chapter above (register, login, get a quote, request a trip, top up a wallet, book a ride), and it **listens** to the live WebSocket connections for anything that should update the screen instantly without the user refreshing — trip status changes, a driver's live position, and a driver's incoming ride offer.

### When is this "good enough"?
When every screen above works end to end against the real, running backend — on a laptop *and* on an actual phone, not just a resized browser window. **This is enough for now. Move to the next piece.**

### Production improvements (optional)
Real production apps add offline support (queuing an action taken while briefly disconnected), push notifications (so the app doesn't need to be open to get an offer), and a proper design system shared across many screens. None of the three teach anything new about the backend architecture this project is actually about — they're worth knowing exist, not worth building here.

### Common beginner mistakes
The most common mistake is quietly reimplementing backend logic on the frontend — recalculating a fare client-side "just to show something faster," or guessing whether a trip transition should be allowed instead of just trying it and showing whatever the backend actually says. Both create a frontend that can disagree with its own backend, which is a strictly worse bug than a slightly slower screen.

### Interview perspective
Be ready to explain why this app doesn't use a heavy global-state library — for an app this size, plain component state plus a small shared context covers everything needed, and reaching for more is solving a problem you don't have yet. Also be ready to explain the deliberate duplication between the web and mobile API clients (copy the code once, adapt it, rather than immediately building a shared package) — a real, documented tradeoff between short-term speed and long-term reuse, not an oversight.

### Before moving on
- [ ] Every screen works end to end against the live backend
- [ ] The app works on a real phone, not just a resized browser window
- [ ] No fare, trip legality, or business rule is recalculated on the frontend — it's always shown from the backend's own answer

If every box is checked: **stop here, this piece is done. Move to the next one.**

---

## 13. AWS Deployment — Taking It Out of Your Laptop

### What problem are we solving?
Everything so far runs on one laptop. A real interview demo (and a real product) needs to run somewhere that isn't your personal machine — reachable by a URL, resilient if one piece restarts, and torn down cleanly when you're done so it doesn't quietly keep costing money.

### Why does this exist?
This is the step where "I can run this locally" becomes "I can run this in the cloud, and I understand every hop a request takes to get there" — which is a meaningfully different and more valuable claim in an interview.

### What should it do?
- Run every service in a real cloud environment, each in its own isolated container.
- Let services find each other by name, the same way they already do locally.
- Expose exactly one public entry point (the gateway), and nothing else.
- Be fully removable in one command, so it never keeps quietly billing you.

### What should it NOT do?
- It should not expose any service to the public internet except the gateway and the static frontend — every other service should only be reachable from inside the private network.
- It should not be built for high availability or massive scale — that's a different, much more expensive project than proving the concept works in the cloud.

### What are the main pieces?
A private network (with public-only subnets here, purely to avoid the extra cost of a NAT gateway for a demo — a real production setup would keep services in fully private subnets instead). A managed database and a managed Redis, each the smallest instance size available. A container cluster running one small task per service, all able to find each other by name exactly the way they do in local Docker Compose. A single public load balancer routing only to the gateway. A container image registry holding one image per service. A static file host for the web frontend. Tightly scoped network rules, so only the load balancer can reach the gateway, and only the gateway (plus other internal services) can reach anything behind it.

### What business rules exist?
- **Only the gateway is publicly reachable.** Every other service should be completely unreachable from outside the private network — the same "one front door" principle from the gateway chapter, now enforced at the network level as well as the application level.
- **This environment is meant to be temporary.** It's built to be stood up, demonstrated, and torn down the same day — running it continuously would cost real money for no real benefit at this stage.

### What don't we need here (instead of "entities")?
No new services, no new business entities — this chapter is entirely about taking the services already built and giving them somewhere real to run.

### What does it depend on, and what depends on it?
Everything: this is the one place the entire system comes together and actually has to work as a whole, which is exactly why it comes last, after every individual service has already been proven correct on its own.

### When is this "good enough"?
When the entire stack deploys successfully, the same end-to-end demo that works on your laptop also works against the real cloud URL, and — critically — the environment is torn down immediately afterward, verified at zero cost the next morning. **This is enough for now.**

### Production improvements (optional)
A real production deployment would use fully private subnets with a NAT gateway (so nothing except the load balancer is even theoretically reachable from outside, not just practically restricted by network rules), a managed Kafka service instead of running Kafka as just another container, multiple availability zones for real redundancy, and autoscaling. Every one of those is a direct, deliberate cost/complexity tradeoff against what's built here — know the tradeoff, don't feel obligated to build the expensive version to prove you understand it.

### Common beginner mistakes
The single most expensive beginner mistake in all of cloud infrastructure work is simply forgetting to tear an environment down after a demo — cloud resources bill continuously whether or not anyone's using them, and a small, cheap-looking environment left running for a month adds up. Tearing it down is a checklist item, not an afterthought.

### Interview perspective
A very likely question is some version of "trace a request from the browser all the way to a specific backend service, naming every hop" — be ready to answer that concretely, hop by hop, for this exact deployment: browser → load balancer → gateway → the target service → the database or cache it talks to. Also be ready to explain the difference between a task definition, a service, and a cluster in a container orchestration platform, since that distinction trips up almost everyone the first time.

### Before moving on
- [ ] The full stack deploys successfully in the cloud
- [ ] The same demo that works locally also works against the real cloud URL
- [ ] The environment is torn down immediately after, and verified at zero ongoing cost

If every box is checked: **this project is deployable, and you understand every hop. Well done.**

---

## Closing: turning this into interview material

Once every chapter above is checked off, the last step isn't more code — it's turning what you built into something you can talk about confidently under pressure. That means writing up, in your own words: a short elevator-pitch version of the whole project; an architecture walkthrough you could give at a whiteboard; the honest list of gaps this project shipped with on purpose (the Kafka outbox, the two-phase scheduling claim, no refresh tokens, no Flyway migrations) and exactly what you'd add to close each one; and a small set of "what if X goes down" failure scenarios, answered against how *this* system actually behaves, not how systems behave in general.

The gaps aren't embarrassing — being able to name them precisely, and explain exactly why they were deferred and what closes them, is a stronger signal than having built a system with no gaps and no ability to say why any particular decision was made.
