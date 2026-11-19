# ADR 0020: One owner for the redirect cache, negative caching and jittered expiry

- **Status:** Accepted (roadmap item 3.3)
- **Code:** `cache/RedirectCache`, `RedirectController`, `DbUrlService`, `CacheWarmup`,
  `RedirectCacheIntegrationTest`

## Context

- **Three copies of the cache code.** The redirect cache (`url:entity:{id}`, a hash of the redirect fields) was written by
  `RedirectController`, `DbUrlService` and `CacheWarmup`, each with its own copy of the fields, so the copies could drift apart.
- **A dead cache.** A second, string cache (`url:{id}`) was written on every shorten and warmup, but only `getLongUrl` read it,
  and nothing called `getLongUrl`.
- **Missing links always reached the database.** A request for a link that doesn't exist went to PostgreSQL every time.
  That's normal for a mistyped link, but costly for scanners guessing short IDs. The database is in Sydney while the app
  runs in India (about 120–150 ms per round trip), and the pool has 2 connections.
- **Synchronized expiry.** Every entry expired exactly 24 h after it was written. Links cached together (by the warmup,
  or a link going viral) expired together, and their next clicks reached the database at the same moment.

## Options

| Option | For | Against |
|---|---|---|
| Spring `@Cacheable` | Little code | Hides keys and TTLs; no negative caching or jitter per entry; hard to reason about failure behaviour |
| **One `RedirectCache` class, explicit keys** | One place for keys, fields, TTLs and failure policy | A small class to maintain |
| Bloom filter of existing IDs | Answers "missing" with no Redis key per ID | Must be rebuilt and kept in sync on create and delete; more than this traffic needs |
| **Negative cache entry per missing ID, 60 s** | Simple; bounded by request rate × 60 s | A newly created link must clear its marker |

## Decision

- **`RedirectCache` owns both key families:**
  - `url:entity:{id}`: TTL **24 h ± 30 min**, picked at random per write.
  - `url:404:{id}`: TTL **60 s**, written when PostgreSQL has no such link.
- **Redirects check the "missing" marker before PostgreSQL.**
- **Failure policy by operation:**
  - *Reads and fills* are best-effort through `RedisGuard`. If Redis is down, PostgreSQL answers.
  - *Invalidation on update and delete* (`forget`) throws on a Redis failure, so a changed or deleted link can't keep
    redirecting from a stale entry. The operation fails visibly instead.
  - *Clearing the marker on create* runs **after the commit** and is best-effort. The worst case is a 404 for up to
    60 s if Redis drops that one command.
- **The `url:{id}` string cache and `getLongUrl` are deleted.**

## Consequences

- (+) Repeated requests for a missing ID reach the database at most once per minute per ID. A test proves it
  by inserting the row behind the app's back and still getting a 404 from the cache.
- (+) Expiry of entries cached together is spread over an hour instead of one moment.
- (−) Redis holds one small key per missing ID that was requested in the last minute. That's bounded by request rate,
  and the redirect endpoint's rate limit belongs to 3.5 (abuse).
- (−) A tiny race remains. A redirect that read "missing" just before a create commits can re-mark the new ID, so the
  new link 404s for up to 60 s. That's accepted for a link created and opened within the same milliseconds.
