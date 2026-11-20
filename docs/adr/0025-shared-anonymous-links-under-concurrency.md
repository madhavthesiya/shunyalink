# ADR 0025: Let the database decide which identical link wins, then return the winner

- **Status:** Accepted
- **Code:** `DbUrlService.shortenUrl` (`winnerOfARace`, `previewAfterCommit`), `GlobalExceptionHandler.handleDataIntegrity`,
  `UrlIntegrationTest`, `DbUrlServiceTest`, `GlobalExceptionHandlerTest`

## Context

A plain anonymous link (no alias, password or expiry) is shared: one URL, one link. The code checks for an existing
link, and creates one if there is none. A unique index (`idx_long_url_non_custom`) backs the rule.

On Nov 20 the production logs showed what happens when two such requests arrive together. Both checked, both found
nothing, and both inserted. The index let one commit; the other request failed with **409 "This conflicts with
existing data"**, though nothing was wrong with it (a double-click is enough). Two more problems showed up in the same
log lines:
- The losing request had already queued a link preview for its short ID inside the transaction, so a preview ran for
  a link that was rolled back.
- The conflict was logged with PostgreSQL's detail, which repeats the full URL. URLs can carry reset tokens or
  session IDs, and ADR 0019 says they never reach the logs.

An integration test with two concurrent requests (the pool has 2 connections, as in production) reproduced the 409 in
4 of 5 rounds.

## Options

1. **Lock first:** `SELECT ... FOR UPDATE` cannot lock a row that doesn't exist yet. An advisory lock on a hash of
   the URL would work, but adds a lock per link creation for a rare case.
2. **Serializable isolation** with retries: correct, but every link creation pays for it.
3. **`INSERT ... ON CONFLICT DO NOTHING`:** native SQL beside the JPA mapping, for one statement.
4. **Let the unique index decide, then return the winner:** on a uniqueness failure for a shareable link, read the
   link that committed and return it.

## Decision

Option 4. The database already enforces the rule, so the application stops trying to win the race and only handles
losing it. A loser gets exactly what it would have got a moment later. Any other conflict (an alias, a password or an
expiry is never shared) is still reported as 409.

Also:
- **Previews are queued after the commit** (`TransactionSynchronization.afterCommit`), so a rolled-back link never
  gets one and a preview never starts before its row exists. A preview is optional, so a full queue no longer fails
  link creation.
- **Conflicts are logged with the constraint's name only.**

## Consequences

- (+) A double-click or two identical requests both succeed with the same link. The fast path has no extra locking.
- (+) Fewer wasted preview fetches; no user values in conflict logs.
- (−) The losing request used up one ID from the sequence (a gap), which is harmless: IDs need to be unique, not
  dense.
- (−) The pattern relies on the unique index existing. A test with real PostgreSQL guards it.
