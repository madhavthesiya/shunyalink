# ADR 0030: A link works at once; the AI check runs after it exists, and can block it

- **Status:** Accepted (roadmap 3.4c; owner decision, Nov 21, 2026). Part 1 (PR #54): the hybrid check. Part 2: a
  daily Safe Browsing re-check of active links (see the end of this record).
- **Code:** `V19__link_status.sql`, `url/LinkStatus`, `safety/LinkSafetyCheck`, `safety/LinkSafetyReview`,
  `url/DbUrlService`, `url/RedirectController`, `cache/RedirectCache`, frontend `app/blocked/[shortId]`,
  `LinkSafetyIntegrationTest`; part 2: `safety/LinkSafetyRecheck`, `SafeBrowsingClient.findListed`,
  `UrlRepository.findActiveAfter`

## Context

Shortening waited for two checks in a row:
1. Google Safe Browsing: about 0.4 s, and certain.
2. The AI phishing check: usually 1–3 s, and longer when a provider is slow.

So every shorten took seconds. Of the two, the AI check is the slower one and the less certain one.

## Options

- **A. Keep both before creation.** Simple, but shortening stays slow.
- **B. Create every link as "pending" and check in the background.** Safe, but a link shared immediately might not work
  for the first few seconds.
- **C. Hybrid. Chosen by the owner.** Safe Browsing stays before creation. The AI check runs after the link exists, and
  a link it flags stops redirecting.

## Decision

1. **Before creation: Safe Browsing only.** A URL on Google's lists is never shortened (`400`), and no AI call is spent
   on it.
2. **After the commit: the AI check**, on the application's async pool, for **newly created** links only. A link
   returned again for the same URL was reviewed when it was created. A full queue never fails link creation; it is
   counted.
   - The pool's 4 threads also cap concurrent AI calls. A CSV import of 50 links, which used to wait for 50 checks
     in a row, now queues them without overrunning the providers' rate limits.
3. **An AI flag sets `status = FLAGGED`** (`V19`: `ACTIVE`/`FLAGGED`, default `ACTIVE`). The update only changes an
   `ACTIVE` link, so a repeated or late verdict is a no-op.
4. **Every path that could reveal the destination respects the flag:**

   | Path | A flagged link |
   |---|---|
   | Redirect | `302` to the frontend's `/blocked/{id}` warning page; no click is recorded |
   | Redirect from the cache | Same. The status is part of the cached snapshot; entries written before this change lack it and reload from the database, so a flag is never missed |
   | Link-preview bots | A "Blocked link" preview without the destination |
   | Password unlock | `403`, even with the right password |
   | Public bio | Not listed (and public bio links never carry destinations: ADR 0029) |
   | Owner's dashboard | Listed with a "Blocked" badge |

5. **Cache invalidation that survives a race.** A redirect that read the link just before the flag can still be
   writing its cache entry when the flag lands, and that stale `ACTIVE` copy would redirect for the cache's 24 hours.
   So the entry is dropped at once **and again 5 seconds later** (a delayed double delete). The window between a
   redirect's database read and its cache write is milliseconds.
6. **Still fail-open** (ADR 0018): an unavailable AI leaves the link active. Outcomes are counted in
   `shunyalink.phishing.reviews{outcome=clear|flagged|not_active|not_queued|failed}`.

## Trade-offs accepted

- **A link that only the AI would catch works for a few seconds**, until its review finishes. Links on Google's lists
  are never created at all.
- **A rollback to the previous build** would redirect flagged links again for as long as it runs, because it doesn't
  know the status. The schema change itself is expand-only: the previous build ignores the column, and its inserts get
  the default.

## Evidence

- Red, on the code before this change, with the same two expectations:
  - Shortening took **6.6 s** while the AI check took 3 s, so the request waited for it.
  - A URL that only the AI flags got **400** instead of being created and reviewed.
- Green: `LinkSafetyIntegrationTest` (real PostgreSQL and Redis) checks the following:
  - Shortening returns in under 1.5 s while the AI takes 3 s.
  - A URL on Google's lists gets a 400, nothing is stored, and no AI call is made.
  - A link the AI flags redirects until the verdict. After it, the cached copy is gone, visitors and preview bots get
    the warning page, and the block itself is cached.
  - A flagged link's password unlock gets a 403 that doesn't contain the destination, and its owner sees it as
    `FLAGGED`.

  Unit tests cover the double eviction, the full-queue and database-error outcomes, the cache entries without a
  status, and the bio filter.

## Part 2: a daily Safe Browsing re-check of active links

A link that was safe when it was created can lead somewhere malicious later: the domain changes hands, or the page is
compromised. Once a day (03:30 UTC, `app.safety.recheck-cron`), every link that still redirects (active, not expired)
is checked against Safe Browsing, and a listed one is flagged exactly as an AI flag would be. That means the same
status, the same double eviction, and a count in `shunyalink.links.flagged{source=safe_browsing|ai}`.

- **Cost:** one request per 500 links (Google's limit per lookup), with duplicate URLs sent once. This is far inside the
  free daily quota.
- **Keyset pagination** (`id > last ORDER BY id`): each page is an index range scan, and links added or flagged
  during the run don't shift the pages.
- **An unknown answer is never "all clear":** if Google can't be asked, the run stops and reports how far it got
  (`Result.complete = false`). The next day's run covers everything again.
- **Two containers running it** (during a deploy) is harmless: flagging only changes an active link.

Evidence:
- Unit tests: paging over 500, one entry per duplicate URL, stopping on an outage, and no key means no work.
- An integration test against real PostgreSQL: only active, unexpired links are sent; a listed one flips to
  `FLAGGED`. Mutation: without the `status = ACTIVE` condition, an already-blocked link is sent to Google, and the test
  fails.
