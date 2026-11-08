# ADR 0014: Apply analytics events transactionally with stable identities

- **Status:** Accepted for the Phase 3 analytics pipeline (not yet connected to live traffic)
- **Code:** `AnalyticsBatchApplier`, `AnalyticsClick`, `V14__analytics_rollups.sql`

## Context

The current sync can leave renamed Redis counters behind after a crash. Retrying a batch without
deduplication can instead count the same event twice. The owner asked to preserve existing history,
exclude identified bots and keep redirects working when analytics storage is unavailable.

## Options

| Option | For | Against |
|---|---|---|
| Update counters without event identities | Simple | A retry can double-count |
| Deduplicate in Redis before database writes | Fast | A crash between stores can lose or duplicate work |
| **Deduplicate and update rollups in one database transaction** | A committed event has one database effect even if redelivered | More database work and retained deduplication state |

## Decision

The future consumer supplies a stable UUID per event and the original immutable URL database ID.
One transaction inserts previously unseen IDs and applies hourly human/bot counts, daily dimensions,
URL totals and the platform total. A deleted URL is not replaced by a newly created link with the same
alias. URL rows are locked for update in stable order before bucket rows, and the shared platform
total is updated once per batch. Daily unique sketches are updated in the same transaction (ADR 0016).
Buckets use UTC; a late event cannot move last access backwards. Additive tables leave existing Redis
history untouched. The applier is disabled unless explicitly configured, and no live writer or reader
uses these tables yet.

Keep deduplication records until a verified retention and replay boundary exists; a fixed two-day
expiry is unsafe if an older event can be retried. Producers must sanitize and bound labels, use the
same event UUID across retries, and avoid persisting raw IPs. Before enabling the consumer, implement
handling for malformed events and test recovery after a committed batch but before acknowledgement.

Process at most 500 events per transaction. Insert deduplication IDs in one parameterized statement
with `RETURNING`, aggregate only newly accepted events in memory, then batch writes per link/hour,
link/day/dimension/value and link/day sketch. Lock parent links in ID order before any bucket rows;
load each existing sketch once and merge the accepted pseudonyms in memory. This bounds heap use
and reduces writes for repeated keys, while transactions with many distinct keys still cost more.
Reject conflicting payloads sharing one UUID rather than selecting one arbitrarily.

The [retention plan](../analytics-retention.md) requires a durable source generation/position and
an enforced replay floor before bounded pruning can be enabled. UUID-only markers are retained
until that protocol exists; there is no automatic two-day deletion.

## Consequences

- (+) Replays and concurrent duplicate delivery have one committed database effect; a transaction
  failure rolls back the event marker and rollups together.
- (+) Existing charts and counts are not changed by adding these tables.
- (−) Deduplication storage grows until the documented cleanup protocol is implemented. Batching
  reduces repeated-key writes, but network latency and lock duration still require load measurement
  before high-volume use; a JDBC call count is not a production latency benchmark.
- (−) The transaction protects events only after they reach PostgreSQL; it cannot recover unprocessed
  events removed from Redis.
- (−) Migration, Redis stream retention, unique counts, read paths and crash recovery still need
  implementation and end-to-end tests before this component can be enabled.
