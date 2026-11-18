# ADR 0018: Run the phishing check before the transaction, and fail open visibly

- **Status:** Accepted (step 1 of roadmap item 3.4; a deterministic primary check and asynchronous
  scanning come later)
- **Code:** `DbUrlService.shortenUrl`, `LlmIntegrationService.isPhishing`, `LlmIntegrationServiceTest`,
  `UrlIntegrationTest.phishingCheck_runsBeforeAnyTransactionOrConnectionIsOpened`

## Context

Every new link is checked for phishing by asking an AI provider (Gemini, then Groq). Three problems:

1. The check ran inside `@Transactional shortenUrl`, so the request held a database connection
   while waiting for the provider. The HTTP client allows 3 s to connect and 3 s to read, so a
   failing provider followed by a slow fallback could hold that connection for about 12 s. The pool
   has **2** connections, and redirects that miss the cache need one.
2. Both model IDs were hard-coded, and both providers have since retired them (`gemini-1.5-flash`,
   and Groq's `llama-3.1-8b-instant`, whose shutdown was announced for Aug 16, 2026). The check then
   answered "not phishing" for every URL, and nothing recorded that.
3. Replies were parsed by prefix (`startsWith("YES")`), and the URL was pasted into the prompt as
   plain text. If Gemini had no key, Groq was never asked.

## Options

| Option | For | Against |
|---|---|---|
| Keep `@Transactional`, add timeouts | Smallest change | Still holds a connection for seconds per link |
| **Check first, then a programmatic transaction (`TransactionOperations`)** | No connection during the AI call; same API for the controller and CSV import | The boundary is in code, not an annotation, so it needs a test to stay true |
| Asynchronous scan with a `PENDING_SCAN` link status | Fast responses; links can be re-scanned later | Schema change and product decisions (what a pending link does); planned as 3.4c |
| Fail closed when no provider answers | A malicious link is never created without a check | One provider outage blocks every new link |
| **Fail open, but count and log it** | An AI outage doesn't take down link creation | A malicious link can get through while the check is down |

## Decision

`shortenUrl` asks for a verdict first, with no transaction, and then does only the database work
inside `TransactionOperations.execute`. Model IDs come from `app.gemini.model` / `app.groq.model`
(environment variables `GEMINI_MODEL` / `GROQ_MODEL`). The Gemini key goes in the
`x-goog-api-key` header.

A verdict counts only if the reply is exactly `{"phishing": <boolean>}`. Both providers are asked
for JSON output. Any other reply is not trusted, and the next configured provider decides. The URL is
passed between `<url>` tags, with characters that could close the block or start a new line
percent-encoded.

When no provider gives a verdict, the link is allowed. The metric
`shunyalink.phishing.checks{outcome=unavailable}` and a WARN log record that it happened.

## Consequences

- (+) A slow or failing provider no longer holds a database connection. The integration test asserts
  that no transaction or connection is bound during the call, and fails if `@Transactional` comes back.
- (+) A retired model is now a configuration change, and a dead check shows up in metrics.
- (+) An answer injected into the URL can't be accepted as the verdict by accident.
- (−) Fail-open remains: while both providers are down, phishing links can be created. Alert on the
  `unavailable` count once metrics are scraped (Phase 5). Step 3.4b adds Google Safe Browsing as a
  deterministic primary check, so the AI becomes a second opinion.
- (−) The request still waits for the provider (up to about 12 s in the worst case). Moving the
  check off the request path is 3.4c.
