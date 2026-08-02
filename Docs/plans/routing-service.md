# Routing-service — goals, not code

Not in `7dayplan.md` — this is an addition. It supersedes §3's "haversine distance,
flat 30 km/h duration" simplification for pricing. How you build it is up to you.

## Goal

`routing-service` owns the answer to "how far is it by road, and how long does it
take". Nothing else in the system asks a routing engine directly, and nothing else
decides what to do when routing is unavailable.

It is **thin**. The routing engine is GraphHopper (Apache 2.0), run as a container.
You are not writing a routing engine — you are writing the contract, the cache, and
the fallback around one.

## 1. Where it sits

```
pricing-service :8104  ──┐   distance + duration for the fare
matching-service :8103 ──┼──> routing-service :8107 ──> graphhopper :8989
frontend map           ──┘        │  (yours, thin)          │ (container)
                                  └─ Redis cache            └─ region.osm.pbf
                                                               + graph-cache volume
```

Synchronous HTTP, both hops. A rider is watching a spinner — same reasoning that
makes the pricing quote synchronous. No Kafka, no events published, none consumed.

**Why its own deployable, not a library inside pricing:** the road graph is
memory-heavy and slow to load; pricing is a cheap stateless calculator. Embedding
one in the other means sizing both for the worse case and turning a 1.4-second
cold start into minutes. They scale on different axes — routing on memory, pricing
on request rate — and on AWS that becomes two different task sizes.

**Why a wrapper, not pricing calling GraphHopper directly:** the fallback policy and
the cache have to live somewhere. If they live in pricing, matching-service
re-implements both, slightly differently, the week it starts ranking candidates by
real ETA. Same failure `7dayplan.md` §3 warns about for the fare formula.

## 2. What it owns / does not own

Owns: the internal contract, the cache, the fallback policy, mapping GraphHopper's
response shape to yours.

Does not own — and must never learn about: fares, surge, trips, riders, drivers.
It answers a geometry question. It does not know why anyone is asking.

## 3. Contract

| Endpoint | Returns |
|---|---|
| `GET /route?fromLat&fromLng&toLat&toLng` | `{ distanceMeters, durationSeconds, source }` |

`source` is `ROAD` or `FALLBACK`.

Base SI units deliberately — consumers convert and round **once**, at their own
final value. Rounding to km before multiplying by a cents-per-km rate injects error
into money for nothing.

Downstream call to the engine:

```
GET :8989/route?point={fromLat},{fromLng}&point={toLat},{toLng}&profile=car&calc_points=false
```

Read `paths[0].distance` (**meters**) and `paths[0].time` (**milliseconds**).
`calc_points=false` drops the route geometry, which is most of the response size —
pricing doesn't need a polyline. The frontend's map call later wants
`points_encoded=false` instead, to get plain coordinate pairs.

Invalid coordinates (lat outside -90..90, lng outside -180..180, missing params)
→ 400. Never a NaN distance.

## 4. The fallback — and making it visible

When the engine is unreachable, still importing, or the points are unroutable (a pin
dropped in the sea), return an estimate rather than an error: **haversine × 1.3**,
the usual urban circuity factor between straight-line and road distance. Refusing to
price a ride because a container is restarting is the worse failure.

Set `source: FALLBACK` when you do. **A degraded answer that looks identical to a
good answer is a bug you will never find.** It makes the degradation measurable, lets
pricing log it, and leaves the door open to policy later (e.g. don't apply surge on
top of an estimated distance).

## 5. Cache

Route between two points is highly cacheable — a city has a handful of busy
corridors. But the key granularity is the whole decision: full float precision gives
a ~0% hit rate, and too coarse quotes the wrong side of a one-way system. Somewhere
around 3 decimal places (~110 m) is the usual starting point. Your call — Redis is
yours.

Never cache a `FALLBACK` result, or one outage poisons quotes for its whole TTL.

## 6. Running the engine — done

`Docker/docker-compose.yml`, service `graphhopper`, image pinned to
`israelhikingmap/graphhopper:11.0`. There is no official GraphHopper image, and the
community `latest` is a daily build from master — not something to leave unpinned
underneath a fare calculation.

- Extract: `africa/morocco-latest.osm.pbf` from Geofabrik, 232 MB.
- First boot imports and builds the graph: **~90 s** here (35 s OSM read →
  1,410,408 nodes / 1,912,739 edges, then subnetworks and CH). Later boots load the
  prebuilt cache in **2 s** — because the cache directory is a mounted volume. Without
  one, every `up` re-imports.
- The `.pbf` and the 242 MB graph cache are gitignored; both are derived artefacts.
- The image ships a healthcheck on `/health`. Gate routing-service on
  `service_healthy`: during import the container is up but not answering, and that is
  a startup race, not the outage the fallback exists for.
- CH (contraction hierarchies) is already on in the image's stock config
  (`profiles_ch: - profile: car`), so no custom config file was needed.

## 7. What changes in pricing-service

The formula does not change. Only where its two inputs come from:

```
distanceKm  = routing.distanceMeters / 1000      (was: haversine)
durationMin = routing.durationSeconds / 60       (was: distanceKm / 30, flat speed)
fareCents   = max(700, round((500 + 120*distanceKm + 30*durationMin) * surge))
```

`Quote` stays `{fareCents, surge, distanceKm, durationMin}` — the frontend contract
does not move. Keep haversine in pricing: it becomes pricing's own last-resort path
if routing-service itself is unreachable, not dead code.

The trip still freezes `fareCents` and `surge` at request time. Routing is never
called again at completion — a recomputed route would make the charge disagree with
the quote.

## 8. Definition of done

- [x] A known city route is visibly longer than the haversine number for the same two
      points — Casablanca → Mohammedia: 22,931 m straight line vs **25,473 m** by road
- [x] Engine stopped → still returns a quote, `source: FALLBACK`, no 5xx
      (verified with the container actually down; nothing else proves it)
- [x] Invalid coordinates → 400
- [x] `docker compose` restart does not re-import — 2 s to healthy, graph cache reused
- [x] The frontend needed no change when the engine arrived: same contract, and the
      route simply went from a dashed grey estimate to a glowing 291-point road line
- [ ] Unroutable points (mid-ocean pin) → fallback, not a 500 *(covered by a unit test,
      not yet exercised against the live engine)*
- [ ] Second identical request served from cache; fallback results never cached
      *(no cache yet — Redis is yours)*
- [ ] **Pricing consumes it** — still the open one. The map draws the real route but
      the fare is still computed from pricing's own haversine, so the drawn line and
      the charged distance disagree. This is the next change.

## Not needed yet

- Live traffic — GraphHopper uses OSM speed limits, not current conditions
- Route polylines for the map — the engine already returns them, wire it when the
  map needs it
- ETA-based candidate ranking in matching-service — a later, separate change
- Multiple profiles (bike, foot) — one `car` profile is the whole requirement
