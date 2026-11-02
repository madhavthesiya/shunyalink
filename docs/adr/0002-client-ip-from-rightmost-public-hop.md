# ADR 0002: Client IP = right-most public `X-Forwarded-For` hop

- **Status:** Accepted (2026-11-01)
- **Code:** `ClientIpResolver`, `InternalAddresses`

## Context

Rate limits (login, shorten, password unlock, email sending) and geo analytics are keyed on the
client IP. The app always sits behind a proxy (Azure App Service front end in production, Nginx
locally), so the socket address is the proxy's and the client must come from `X-Forwarded-For`.

The old code took the **left-most** entry. That entry is written by the client, so sending a
random `X-Forwarded-For` on each request gave an attacker a fresh rate-limit bucket every time.
The geo code also stripped ports from IPs, which suggests Azure appends `ip:port`.

## Options

| Option | Problem |
|---|---|
| Left-most entry (old) | Client-controlled: unlimited rate-limit bypass |
| Spring `server.forward-headers-strategy=native` (Tomcat `RemoteIpValve`) | Treats `203.0.113.7:51234` as the client *address*, so every new connection is a new bucket |
| Fixed trusted hop count (take entry *n* from the right) | Correct only while the number of infrastructure hops never changes |
| **Right-most public entry** | Assumes every infrastructure hop has an internal address |

## Decision

Walk `X-Forwarded-For` from the right, strip ports and IPv6 brackets, and return the first address
that is **not** internal (loopback, private, link-local, CGNAT, reserved, IPv6 ULA). Entries to its
left were supplied by the client and are ignored. If every entry is internal (local Docker stack),
use the nearest one. Header values are parsed as IP literals only and never DNS-resolved.
Addresses are normalized (`2001:0db8::0001` → `2001:db8:0:0:0:0:0:1`) so spellings share a bucket.

The same `InternalAddresses` classifier is used by the SSRF guard (ADR 0003), so "internal" means
one thing across the codebase.

## Consequences

- (+) A spoofed header can no longer choose the rate-limit bucket (covered by tests end to end).
- (+) Fixed two analytics bugs found on the way: `172.217.x` (Google) was classified as private
  because every `172.*` was, and IPv6 clients were cut to their first block and always "Unknown".
- (−) If an infrastructure hop ever had a public address, clients would share that hop's bucket.
  Verified after deploy by checking that a real click is geolocated to the right country.
- (−) An IPv6 client controls a whole /64 and can rotate addresses; keying IPv6 limits by /64 is a
  possible next step.
