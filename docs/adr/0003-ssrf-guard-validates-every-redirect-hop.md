# ADR 0003: Server-side URL fetches validate every redirect hop

- **Status:** Accepted (2026-11-01)
- **Code:** `SafeUrlFetcher`, `BlockedUrlException`, `MetadataService`

## Context

When a link is shortened, the backend fetches the destination to read its title, then stores that
title where the user can see it. The URL is user-supplied, so this is a **read SSRF** primitive:
`http://169.254.169.254/...` (cloud instance metadata), `http://localhost:8080/actuator`,
`http://redis:6379`, or any private address, with the response's title echoed back to the attacker.

## Options

| Option | Why not (or why) |
|---|---|
| No guard (old) | Full read SSRF |
| Block hostnames like `localhost` | Bypassed by IP literals, decimal/IPv6 encodings, or any DNS name pointing at a private IP |
| Resolve and validate once, let the HTTP client follow redirects | Bypassed by a public page that redirects to an internal address (the classic SSRF bypass) |
| **Resolve and validate before every hop, follow redirects manually** | Chosen |
| Egress proxy / network policy that blocks internal ranges | Strongest, but infrastructure-level; a defence-in-depth follow-up, not a replacement |

## Decision

`SafeUrlFetcher` allows only `http`/`https`, ports 80/443, no embedded credentials, and rejects a
host if **any** of its resolved addresses is internal (shared `InternalAddresses` classifier, ADR 0002).
Redirects are followed manually, at most 3, re-validating each hop; bodies are capped at 512 KB.
A blocked URL is not retried by the fallback scraper. It is the only class allowed to call
`Jsoup.connect`.

## Consequences

- (+) Internal targets are rejected before any connection, including via redirects. Tests run
  against a real local HTTP server for the redirect cases.
- (−) **DNS rebinding** is not covered: Jsoup resolves the host again when connecting, so a record
  that flips between the check and the connection could slip through. Closing it requires connecting
  to the already-validated IP (custom socket factory) or an egress proxy.
- (−) Sites on non-standard ports (e.g. `:8080`) no longer get automatic titles. Acceptable: titles
  are a convenience and the link itself still works.
