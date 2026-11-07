# ADR 0017: Bound analytics labels before queueing events

- **Status:** Accepted for Phase 3 (not yet connected to live traffic)
- **Code:** `AnalyticsLabels`, `AnalyticsLabelsTest`

## Context

Analytics dimensions are retained and must fit PostgreSQL columns. User-controlled headers may be
malformed, excessively long, or include paths and query parameters containing sensitive values.
Invalid dimensions must not poison an otherwise valid event batch.

## Options

| Option | For | Against |
|---|---|---|
| Store headers verbatim | Maximum detail | Sensitive data, unbounded cardinality and failed writes |
| **Store bounded, normalized labels** | Fits the schema and excludes referrer paths and queries | Loses detail and cannot eliminate all sensitive hostnames |
| Omit referrers and devices | Least retained information | Removes existing analytics features |

## Decision

The future producer will retain only normalized HTTP(S) referrer hosts, rejecting IP-literal hosts
and malformed or overlong labels. Device values use a fixed vocabulary; country labels are bounded
and must come from the local resolver. Missing or invalid inputs become `Direct` or `Unknown`.
These helpers are not yet wired into the event producer. A hostname is still client-controlled and
can contain sensitive information; further aggregation or retention controls may be needed.

## Consequences

- (+) Paths, queries and literal IP referrer hosts are not written as dimension labels.
- (−) Hostnames can still identify a source or encode sensitive data; this is not anonymization.
- (−) Some malformed referrers are grouped as Direct and device detection is approximate.
