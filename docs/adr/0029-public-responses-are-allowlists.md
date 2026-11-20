# ADR 0029: Public responses are allowlists

- **Status:** Accepted (found while preparing roadmap item 3.4c)
- **Code:** `auth/PublicBioLink`, `auth/ProfileService.publicProfile`, `PublicProfileIntegrationTest`

## Context

The public bio API (`GET /api/v1/profile/{username}`, no sign-in) returned each bio link as the owner's full
`UrlStatsResponse`. That is **22 fields** per link, including `longUrl` and internal analytics fields (seen in
production on Nov 21).

A password protects a link at its short URL: the redirect sends visitors to the password page. So for a protected
link shown on a bio, **anyone could read the destination from the public API without the password.** No production
profile had a protected link on its bio at the time, so nothing private had been exposed, but the hole was open for
every user. The same hole would let a link blocked by the phishing check (3.4c) keep spreading its destination.

The bio page itself only uses `shortId`, `title` and `category`. Every link points to the short URL, where the password
and any block are enforced.

## Decision

The public profile's links are a separate, minimal type, `PublicBioLink(shortId, title, category)`: an
**allowlist** of what the page shows. The owner's dashboard keeps the full `UrlStatsResponse`.

## Why an allowlist, not hiding one field

Setting `longUrl` to `null` would close this hole, but leave the pattern: the next field added to the owner's stats
type would be public by default. With an allowlist, exposing a field has to be a deliberate change to the public type.

## Evidence

- Red: an integration test puts a password-protected link on a bio. The public profile JSON contained its destination.
- Green: the body contains neither the destination nor a `longUrl` field. A unit test checks that the mapping never even
  reads `getLongUrl()`.

## Consequences

- (+) Public responses expose only what the page needs. Destinations stay behind the short URL, where passwords and
  blocks are enforced.
- (−) Public bio links no longer carry click counts or analytics. The frontend never used them; adding a count back
  would be an explicit field on `PublicBioLink`.
- Separate, still open: an unknown username answers 400 instead of 404.
