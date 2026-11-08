# ADR 0016: Keep daily unique-visitor sketches with transactional rollups

- **Status:** Accepted for the Phase 3 analytics pipeline (not yet integrated)
- **Code:** `UniqueVisitorSketch`, `UniqueVisitorSketchTest`

## Context

The owner approved approximate unique visitor counts. Visitor pseudonyms are scoped to a link and
UTC day (ADR 0015), while accepted analytics events and their rollups are committed in PostgreSQL
(ADR 0014). Redis-only sketches would be lost on a Redis flush, even for events already applied.

## Options

| Option | For | Against |
|---|---|---|
| Redis HyperLogLog | Fast and compact | Committed unique counts depend on Redis retention and recovery |
| **PostgreSQL-stored HyperLogLog sketches** | Can update sketches in the event transaction | More database writes, storage and implementation complexity |
| Exact pseudonym sets | Exact counts for the chosen identifiers | Greater storage and privacy exposure |

## Decision

Prepare Apache DataSketches HLL sketches with 16,384 registers and compact serialization. The
future event transaction must update each link/day sketch along with deduplication and rollups;
the component alone does not provide this guarantee. Version 6.2.0 runs without the Java preview
features required by later releases, subject to validation on the application's Java 21 runtime.

## Consequences

- (+) Committed sketches can survive a Redis flush when transactional persistence is implemented.
- (−) Estimates are approximate IP-based counts, not people; empty or invalid IPs are omitted.
- (−) Day-scoped pseudonyms cannot deduplicate visitors across days. Summing daily estimates is
  visitor-days, not unique visitors over a longer period.
- (−) Database update cost, sketch integrity, retention and migration need testing before rollout.
- (−) Events lost before database commit are not protected by storing committed sketches.
