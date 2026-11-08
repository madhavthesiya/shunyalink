# Analytics deduplication retention

The pipeline is disabled while ingestion and recovery are being implemented. Keep all rows in
`analytics_applied_events` for now. Do not run a time-based DELETE: `applied_at` is a processing
timestamp, not proof that an older event cannot be replayed. Deleting a marker while its event is
still replayable lets the same click increase totals again.

Before enabling ingestion, implement and verify this pruning contract:

1. Persist each event's source generation and ordered stream position alongside its deduplication
   marker. Every retry and database fallback must preserve that identity.
2. Establish a durable replay floor per generation in PostgreSQL. All ingestion paths must reject
   events at or below that floor, including manual replay and recovery from backups.
3. Advance the floor only after all consumers have committed and acknowledged the covered events,
   no pending or dead-letter event remains below it, and the recovery policy retires those events.
   A lost Redis stream alone is not evidence that its events cannot return from another source.
4. After the floor is committed and enforced, prune covered markers in bounded transactions
   (for example, 5,000 rows at a time), using an index on the persisted generation/position. Never
   delete by age alone, and retain markers for unresolved events regardless of their age.
5. Test replay immediately before/after the floor, concurrent pruning and ingestion, consumer
   crashes, and backup recovery. Monitor marker count, oldest processing time, pending backlog,
   rejected retired events and pruning duration before scheduling the job.

Current UUID-only markers cannot enforce that contract yet. Automatic pruning remains off until
the source metadata, replay-floor checks and recovery tests exist. Storage growth must be measured
before live cutover; an unbounded table is not an acceptable long-term retention policy.

Payload fingerprints detect conflicting redelivery but do not establish source generation,
position or a replay floor. Adding a fingerprint does not authorize pruning. Older markers with a
null fingerprint remain unverifiable and require operator reconciliation if replayed.

Rollup and daily sketch retention is a separate product decision. Existing analytics history must
be preserved, and no aggregate cleanup is introduced by this change.

The draft consumer's in-memory XAUTOCLAIM scan cursor is not a durable replay floor. It only finds
pending work, including work abandoned by another consumer. Acknowledged payloads remain in the
stream for now; no XDEL or XTRIM cleanup is enabled. Stream storage also needs a reviewed retention
implementation before live cutover. If another actor deletes a pending payload, the consumer counts
the missing entry reported by Redis; this detects missing source data but does not prove a click was
lost, since its database transaction may already have committed.
