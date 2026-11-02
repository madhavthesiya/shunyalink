# ADR 0004: User-wide JWT revocation with an "issued-before" cutoff

- **Status:** Accepted (2026-11-01)
- **Code:** `JwtBlacklistService#revokeAllForUser` / `#isRevokedForUser`, `JwtAuthenticationFilter`

## Context

Authentication uses stateless JWTs valid for 24 hours. Two security flows need to sign a user out
**everywhere**, including tokens we have never seen since they were issued:

- **Password reset**, often a response to a compromised password.
- **Account takeover remediation**: an attacker pre-registers a victim's email and receives a token
  at registration; when the real owner later proves ownership via Google sign-in, the attacker's
  token must stop working.

Only per-token logout existed (the token string stored in a Redis blacklist).

## Options

| Option | For | Against |
|---|---|---|
| Per-token blacklist / `jti` | Already existed for logout | Requires knowing every outstanding token; we don't |
| `tokenVersion` column + claim | Precise | A DB read on every authenticated request, or a cache to keep in sync, plus a schema migration |
| Short-lived access token + rotating refresh token | The right long-term model | Larger change across backend and frontend (planned with cookie auth) |
| **Per-user "issued-before" cutoff in Redis** | One key per user, no schema change, O(1) check | One extra Redis read per authenticated request |

## Decision

`revokeAllForUser(userId)` stores the current epoch second under `jwt:revoked-before:{userId}`, with
a TTL equal to the token lifetime (after that, every older token has expired anyway). The filter
rejects any token whose `iat` is **strictly before** the cutoff.

JWT `iat` has one-second precision, so a token issued in the cutoff second itself stays valid. This
is deliberate: the fresh token returned in the *same request* as the revocation must keep working.
The window it leaves (another token issued in that same second) is negligible.

## Consequences

- (+) Password reset and Google takeover remediation sign out every existing session.
- (+) No schema change; the key expires on its own.
- (−) One more Redis read per authenticated request (sub-millisecond on the same Redis already used
  for the blacklist). If Redis is down, authenticated requests are rejected (fail closed), the same
  as the existing blacklist check (see ADR 0005).
- Superseded later by short-lived access + refresh tokens, where revocation becomes "revoke the
  refresh token family".
