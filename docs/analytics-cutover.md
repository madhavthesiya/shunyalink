# Analytics cutover prerequisites

The new pipeline is prepared in the draft analytics PR. Its switches remain **off**. Do not enable
live ingestion until the remaining requirements below are implemented and verified.

## Selected policy: new data only

The owner chose to start fresh. A legacy audit/import and reconciliation are no longer release gates.
Leave old keys and counters in place; do not add orphaned counters or delete historical records.
The optional [legacy audit](analytics-legacy-audit.md) is diagnostic only.

Enabled ingestion defaults to `app.analytics.fresh-start=true`. It atomically records one collection
start and collection UUID before publishing. Restarting or scaling out reuses both, without resetting anything.
If draft pipeline aggregates already exist, activation fails without modifying them: they cannot
safely be split at a new timestamp at hourly/day resolution. Normal legacy data does not block it.

New per-link and platform projections provide clicks and last-access timestamps. The stats, list,
bio, CSV exports and public-click responses exclude legacy totals and pending counters. Public new clicks remain
after a link is deleted. `analyticsCollectionStartedAt` identifies the start in link responses. Old
events without the active collection UUID are excluded even if their clock claims a future time.
New tagged events remain eligible if the application/database clocks differ. The UUID is fixed before
the Redis attempt and survives fallback. Deliberate fallback exclusions have an `EXCLUDED` outcome,
not a false persistence/loss claim; exclusion counters count attempts. This collection identity
does not replace the deduplication retention/replay-floor protocol for new events.

The legacy sync job is disabled when stream ingestion is enabled. Enable reads and ingestion together
only after final cutover checks. `fresh-start=false` does not revert or reset an established boundary.

## Configuration prepared

| Setting | Default | Purpose |
|---|---|---|
| `app.analytics.pipeline.enabled` | `false` | Transactional database applier |
| `app.analytics.ingestion.enabled` | `false` | Select the stream producer instead of the legacy writer |
| `app.analytics.consumer.enabled` | `false` | Automatically poll/recover stream records |
| `app.analytics.reads.enabled` | `false` | Select committed stats, lists, bio summaries and public clicks for the established collection |
| `app.analytics.fresh-start` | `true` | Establish/reuse the fresh period when ingestion starts |
| `ANALYTICS_VISITOR_KEY` | unset | Base64 of exactly 32 cryptographically random bytes, dedicated to analytics |

Ingestion refuses to start without the applier and a valid visitor key. Store the key as an Azure
App Service secret/environment setting; use the same value across replicas. Never commit it, paste
it into PRs or log it. Keep it independent of authentication and link-password keys. The key creates
link/day-scoped pseudonyms, not cross-day distinct-person identities. Mid-day rotation may change
that day's estimates; plan rotations at a UTC day boundary.

The consumer also requires the applier. Its idle recovery threshold defaults to 60 seconds, polling
to one second, and maximum retry backoff to 30 seconds. An in-flight transaction and acknowledgement
drain during shutdown. Configure bounded Redis and database operations before relying on that drain
in production. Prepared defaults now set Redis connection/command timeouts to 2s/1s, database pool
acquisition/validation to 2s/1s, and PostgreSQL connect/socket/cancel-signal waits to 2s/5s/1s.
Event-application database transactions use a five-second timeout, including statement lock waits. These
are individual operation bounds, not an end-to-end redirect latency guarantee. PostgreSQL network
bounds apply to other database users and migrations as well; slower environments must review their
configuration before cutover. A socket/commit timeout remains an uncertain write outcome.

## Remaining gates before enablement

- Verify the fresh period consistently scopes all existing API fields and additive metrics. Leave
  the old reconciliation marker false in fresh mode; it is not a shortcut or prerequisite.
- Verify old counters/distributions remain excluded across restart, replay and link deletion.
- Switch all replicas away from legacy writers/schedulers during the controlled cutover. Legacy
  traffic during a rolling transition is not retroactively imported into the new collection.
- Review the prepared storage timeout defaults against the production network and load before cutover.
- Implement the [retention and replay-floor protocol](analytics-retention.md), including retired-source
  rejection and recovery tests. Payload fingerprints and XAUTOCLAIM cursors are not replay floors.
- Exercise fresh activation, replay, uncertain writes, failure recovery and the final read path together.

## Read resolution and preserved coverage

24h, 7d and 30d queries use UTC hourly buckets intersecting the lookback and new collection. Their actual start-inclusive
and end-exclusive boundaries are returned; the current hour is partially observed. Charts use hourly
labels for 24h and daily labels for longer ranges. In the selected fresh mode, all-time click totals
mean this new collection only. No old URL count, hash or retained series is added.

Legacy all-time dimension hashes do not establish dates, bot status or unique visitors. Preserve them
separately from the rolling timestamped observations; never distribute their totals over invented days.
New daily visitor estimates are per UTC day with usable IPs only. The first intersecting day's estimate
can include observations before the hour-filtered range, and the current day remains partial. Never
sum these values and label the result distinct visitors across the range. Link lists and bio summaries
do not query these estimates; their bot count is null rather than an invented zero.

## Routing behavior prepared

Redirects use the ID in the resolved database/cache snapshot. Old caches without that ID reload the
whole snapshot; IDs are never looked up separately and attached to an old destination. Cache failures
can resolve via PostgreSQL when available. Password resolution carries the authorized ID and URL
together and records only after a successful verification transaction.

With ingestion enabled, public previews become bot events and do not increase human click totals.
Password challenges and wrong passwords record no click. Analytics storage failures do not escape
into a successfully resolved redirect. If neither analytics path confirms success, its outcome is
unconfirmed: a timeout does not prove loss, and replay may still find a committed event.

Leaving the switches disabled preserves the legacy writer. Disabling ingestion after a future
cutover would select that writer again, so it is not a complete rollback procedure; reconcile pending
events and read/write boundaries before switching back. This document does not claim a live cutover.
