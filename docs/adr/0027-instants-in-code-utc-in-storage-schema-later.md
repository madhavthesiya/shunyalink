# ADR 0027: Instants in the code and the API now; column types later, in a rollback-safe way

- **Status:** Accepted (roadmap item 3.10; the column change is deferred to Phase 4.1/4.2)
- **Code:** `application.properties` (`hibernate.jdbc.time_zone`, `hibernate.type.preferred_instant_jdbc_type`),
  `UrlEntity`, `UserEntity`, `VerificationToken`, `PasswordResetToken`, `ShortenResponse`, `UrlStatsResponse`,
  `util/UtcTimes`, `UrlIntegrationTest`, `UtcTimesTest`

## Context

Times were `LocalDateTime`: a date and a time with **no zone**.
- **The API sent them without a zone** (`"createdAt":"2026-11-20T14:38:32.64"`, seen in production). Browsers read such
  a string as *local* time, so for users in India every time was 5½ hours off, and a dashboard date could be the
  previous day.
- **`LocalDateTime.now()` wrote the JVM's local time:** UTC in the production container, but IST on a developer's
  machine. A test run locally stored `23:40` when the UTC time was `18:10`.
- Some times are also stored as **text in Redis** (a cached link's expiry, kept up to 24 h; the legacy "last access"),
  in the `LocalDateTime.toString()` format.

## Decision

1. **`Instant` everywhere in the code and the API.** Responses carry a zone (`…Z`), which every browser reads correctly.
2. **Storage unchanged.** The columns stay `timestamp` (no zone). Hibernate is told to store an `Instant` in them as
   UTC wall-clock time (`hibernate.jdbc.time_zone=UTC`, `preferred_instant_jdbc_type=TIMESTAMP`). That is exactly what
   production has always stored, so existing rows read correctly, and the server's time zone no longer matters.
3. **Redis text formats unchanged** (`UtcTimes`): the same UTC text without a zone, so the new and the old build read
   each other's cached values.

## Why not change the columns to `timestamptz` now

With `ddl-auto=validate`, Hibernate checks the column types at startup. After a migration to `timestamptz`, the
**previous build would refuse to start**, so the automatic rollback of a failed deploy (ADR 0006) would crash instead
of restoring service. A column-type change needs the expand/contract approach and the Flyway clean-up of Phase 4.1;
it is planned there (roadmap 4.2), not mixed into this change.

## Evidence

- Red first (on a machine in IST): the API returned `"createdAt":"2026-11-20T23:40:40.9130201"`, no zone, and in
  local time.
- Green: `…Z` in the API, and the database value within a minute of `now() AT TIME ZONE 'UTC'`.
- A mutation that removes `hibernate.jdbc.time_zone=UTC` fails that test (the stored value is off by the IST offset).
- The integration tests start the application with `ddl-auto=validate` against real PostgreSQL, so the mapping of
  `Instant` to the existing `timestamp` columns is checked.

## Consequences

- (+) Correct times for every user, whatever their zone, and the same stored values on every machine.
- (+) A failed deploy can still roll back.
- (−) The database still holds zoneless timestamps whose meaning (UTC) is a convention, written down here and in
  `application.properties`, until Phase 4.2.
