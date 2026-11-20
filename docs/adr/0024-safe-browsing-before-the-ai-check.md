# ADR 0024: Google Safe Browsing first, the AI check second

- **Status:** Accepted (roadmap item 3.4b)
- **Code:** `safety/SafeBrowsingClient`, `safety/LinkSafetyCheck`, `DbUrlService.shortenUrl`, `Upstreams` (`safebrowsing`),
  `SafeBrowsingClientTest`, `LinkSafetyCheckTest`, `DbUrlServiceTest`

## Context

New links were screened by an AI model only (ADR 0018). An AI verdict is a guess: it can be talked into an answer by
text inside the URL, it changes when the model changes, and in November both configured models had been retired
upstream without anyone noticing. Known malware and phishing pages are already listed by Google, and the lists are
updated continuously.

## Decision

- **Google Safe Browsing Lookup API (v4) runs first.** A URL on Google's lists (malware, social engineering, unwanted
  software, potentially harmful applications) is rejected, and the AI is not asked. The answer is already certain,
  and an AI call costs money and seconds.
- **Otherwise the AI check decides, exactly as before.** It still catches brand-new lookalike domains that are on no
  list yet. So Safe Browsing only adds blocks: nothing that was allowed before is blocked now unless Google lists it.
  Changing that (for example, the AI only flagging for review, never blocking) is a product decision for later.
- **Both fail open, visibly** (ADR 0018). A Safe Browsing outage, or a rejected key (400 or 403), is `UNAVAILABLE`,
  never `CLEAR`, and is counted in `shunyalink.safebrowsing.checks{outcome=unsafe|clear|unavailable}`. The AI check
  then decides alone.
- **Optional by configuration.** Without `SAFE_BROWSING_API_KEY` the check is off, and one warning at startup says so.
  The code could therefore ship before the key existed, and the key turns it on without a code change.
- **The key travels in the `X-Goog-Api-Key` header**, never in the URL, where proxies and exception messages keep it.
  The key is restricted to the Safe Browsing API in Google Cloud.
- **It runs before the database transaction**, like the AI check, and has its own entry in `Upstreams`: a 2 s read
  timeout and its own circuit breaker (ADR 0023).

## Options considered

- **Safe Browsing Update API** (download hash-prefix lists, check locally, ask Google only on a prefix match). More
  private: Google never sees full URLs. Rejected for now: it needs a local copy of the lists kept in sync and
  canonicalisation rules implemented exactly. The Lookup API sends the full URL to Google, which the AI check already
  does with its providers.
- **Caching verdicts per domain in Redis** (as the roadmap suggested). Rejected for Safe Browsing: its lists match
  full URLs and path prefixes, so one bad page on a shared host (a free hosting site, a document-sharing site) would
  mark the whole domain. Links are created rarely enough that a lookup per link is cheap.
- **Running Safe Browsing and the AI in parallel.** Would hide Safe Browsing's latency behind the slower AI call.
  Not done yet: measure first. Item 3.4c moves the whole scan off the request path anyway.
- **Web Risk API.** Google's commercial equivalent. Safe Browsing is free for non-commercial use, which fits this
  project; a commercial launch would switch.

## Consequences

- (+) Known-bad URLs are rejected deterministically, and without an AI call.
- (+) The security decision no longer rests on one model's output alone.
- (−) Every new link waits for one more external call (normally well under a second, at most 2 s).
- (−) Full URLs are sent to Google.
