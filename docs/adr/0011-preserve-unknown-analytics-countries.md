# ADR 0011: Keep unresolved click countries unknown

- **Status:** Accepted (2026-11-08)
- **Code:** `AnalyticsService#recordClick`, `AnalyticsServiceGeoTest`

## Context

When a lookup failed, analytics counted a click as `Unknown`. A later successful lookup moved one
unknown count into the later visitor's country, even though those clicks could come from different
people. Historical aggregates contain no information to establish the earlier visitor's country.

## Options

| Option | For | Against |
|---|---|---|
| Reassign an unknown count on a later lookup | Fewer unknowns shown | Attributes past visits without evidence |
| **Keep each recorded result** | Honest counts; historical aggregates remain intact | Failed lookups remain unknown |
| Reprocess each failed lookup | Could improve accuracy | Requires retaining and linking sensitive data that is not available today |

## Decision

Increment only the country resolved for the current click. Preserve existing aggregates, including
any prior inaccuracies, because they cannot be reliably reconstructed. A later pipeline migration
must preserve historical charts without inventing country data.

## Consequences

- (+) A later visitor cannot rewrite an unrelated visitor's country.
- (−) Unknown results remain unknown; pre-existing misattributions cannot be corrected reliably.
- (−) The current external lookup and Redis-only analytics remain until the pipeline migration.
