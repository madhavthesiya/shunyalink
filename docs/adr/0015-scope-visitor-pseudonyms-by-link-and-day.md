# ADR 0015: Scope visitor pseudonyms to a link and UTC day

- **Status:** Accepted for the Phase 3 analytics pipeline (not yet integrated)
- **Code:** `VisitorHasher`, `VisitorHasherTest`

## Context

The owner approved approximate unique visitor counts but requested that raw IP addresses not be
persisted in analytics events. IP addresses have low entropy: an unsalted hash can be guessed or
matched against other datasets. An address also does not reliably identify a person.

## Options

| Option | For | Against |
|---|---|---|
| Persist raw IPs or plain hashes | Simple | Exposes visitor addresses or makes guessing easy |
| **HMAC with a secret, scoped to link and UTC day** | No raw IP in the pseudonym; limits direct cross-link/day correlation | Secret management is required; this is pseudonymous, not anonymous |
| Do not calculate uniques | Least data | Cannot provide the approved unique-visitor feature |

## Decision

For valid IP literals, calculate HMAC-SHA256 with a 32-byte secret over a version byte, immutable
URL ID, UTC epoch day and normalized address bytes. Missing or invalid addresses have no unique
identifier. Different spellings of the same IP share a pseudonym; different links and days do not.
The key is copied defensively and is not logged. The component is not wired into the application yet.
Before enabling it, provision a random secret securely and define key rotation and retention.

## Consequences

- (+) Persistent analytics events need not include raw IP addresses.
- (−) If the key leaks, low-entropy addresses can still be guessed; protect and rotate the key.
- (−) Shared networks and changing addresses mean an IP-based estimate is not a count of people.
- (−) Key rotation within a day can change estimates; deployment and rotation need an explicit plan.
