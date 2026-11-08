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
Reject conflicting payloads sharing one UUID rather than selecting one arbitrarily. Persist a
SHA-256 fingerprint of sorted, length-prefixed v1 fields alongside each UUID. Compare fingerprints
on redelivery, including across batches and after producer fallback; the marker and rollups commit
together. A conflicting replay remains pending while independent valid records commit. Existing
UUID-only markers have unknown payload identity and remain pending for operator reconciliation;
never infer or overwrite a fingerprint. The digest contains no raw request data.

The [retention plan](../analytics-retention.md) requires a durable source generation/position and
an enforced replay floor before bounded pruning can be enabled. UUID-only markers are retained
until that protocol exists; there is no automatic two-day deletion.

### Producer and storage failure policy

The draft producer enriches events before one Redis stream append: only bounded country, referrer
host and device labels, bot classification and a scoped visitor pseudonym are stored. Raw IPs and
request headers stay out of the stream. Events identify the immutable database URL ID, never an
alias that may be reused. Versioned fields are validated before application.

If Redis fails or its reply is lost, the producer attempts the same event UUID through the
transactional database applier. A later stream replay then has no additional database effect.
If neither path confirms success, return an unconfirmed outcome and increment a low-cardinality
metric; do not throw a storage failure into the redirect. An unconfirmed write may have committed,
so the metric measures uncertainty, not an exact number of lost clicks. Do not trim unprocessed
stream events with MAXLEN. No live redirect wiring or consumer is enabled by this producer change.
Before cutover, review the prepared bounded storage timeouts and securely provision the HMAC key; no
sub-millisecond latency or guaranteed loss-free operation is claimed.

### Consumer processing and recovery

Create the group at `0-0` with MKSTREAM, retaining events queued before the first consumer starts.
Each explicit poll first uses Redis XAUTOCLAIM with a scan cursor, then reads fresh group entries.
Each part is capped at 500 records and gets its own transaction; fresh work has a separate budget
so malformed pending entries cannot monopolize every poll. The current Spring Data Redis version
does not expose XAUTOCLAIM through StreamOperations, so a small Lua invocation uses the server
command and its cursor/deleted-ID response directly.

The database transaction must return successfully before acknowledgement. A database error leaves
records pending; a crash or failed acknowledgement after commit causes replay with no additional
database effect. Invalid/unsupported records and conflicting payloads for one UUID within a batch
remain pending for inspection or a newer decoder. Do not log or copy their payloads. The v1 decoder
rejects timestamps outside years 1–9999 before database application. Malformed-record counters count
attempts, not distinct records; deleted pending payloads have a separate counter and cannot be
reconstructed from Redis metadata.

Automatic polling requires both `app.analytics.pipeline.enabled=true` and the separate
`app.analytics.consumer.enabled=true` switch. Both remain off by default. Each process uses a fresh
consumer UUID and an idle-claim threshold of at least 60 seconds. One worker polls serially, waits
one second between successful polls and exponentially backs off storage failures up to 30 seconds.
Failures expose a counter without logging exception payloads. Shutdown stops scheduling and drains
the current transaction and acknowledgement before reporting completion. Storage timeouts are
explicitly bounded as documented in the cutover prerequisites; analytics transactions have a
five-second deadline. Tests use zero recovery idle time deterministically. Retention cleanup,
live cutover and migration remain required; the draft consumer neither trims nor deletes stream data.

See the [cutover prerequisites](../analytics-cutover.md) for the prepared settings and remaining gates.

### Resolution identity and gated routing

The redirect hash now includes the immutable `urlId`. Older or incomplete hashes reload the full
database snapshot, including the destination, password and expiry status. Never fetch a current ID
and attach it to a stale cached destination: an alias may have been deleted and reused. Cache read
or write failures allow resolution from PostgreSQL when it is available. Warmup writes the same ID.
Password resolution returns the authorized ID and destination together; publishing occurs after
the verification/legacy-password-upgrade transaction returns, without another alias lookup.

`AnalyticsClickRecorder` selects one writer. With ingestion disabled it calls the existing async
legacy service; with ingestion enabled it calls the stream producer directly, without retaining raw
request data in an async queue. It never falls back to the legacy counters after a producer error,
because an uncertain write may already have committed. Unexpected enrichment/scheduling failures
increment a counter without logging payloads. Public social previews are published as bot events
only in the new pipeline; protected challenges and wrong passwords publish nothing.

Ingestion remains off by default and requires the transactional applier and a separate,
base64-encoded 32-byte visitor key. Invalid/missing keys fail startup without echoing their value.
All replicas must share this key. Changing it during a UTC day can increase that day's approximate
unique counts. The separate read, migration, production configuration and replay-retention requirements still
block live cutover; passing the routing tests alone does not authorize enabling ingestion.

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
