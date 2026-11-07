# ADR 0013: Resolve countries from a local DB-IP database

- **Status:** Accepted for the Phase 3 analytics pipeline (not yet integrated)
- **Code:** `LocalCountryResolver`, `LocalCountryResolverTest`

## Context

The existing analytics path sends public visitor IPs to an external geolocation API and depends on
its network availability and quota. The owner approved a local DB-IP Lite database and the required
attribution on the Insights page. No existing chart data should be discarded during migration.

## Options

| Option | For | Against |
|---|---|---|
| Continue remote lookup | No database distribution | Shares visitor IPs and adds a network dependency |
| **DB-IP Lite MMDB locally** | No per-visitor third-party lookup; no API key | Reduced coverage, attribution and regular updates required |
| Licensed commercial database | Potentially better coverage | Cost and operational commitments |

## Decision

Use a reusable local MMDB reader in the new pipeline. Parse IP literals without DNS; private addresses
are `Local/Dev`, invalid inputs and unavailable, corrupt or unmatched databases return `Unknown`.
Load the database into memory so files are not held open by a Windows memory mapping; the tested
October 2026 country database occupies about 8.3 MB uncompressed. The data has not yet been bundled
or wired into production. Before integration, provide a reproducible, checksum-verified database
distribution and update process and add the required [DB-IP attribution](https://db-ip.com/db/lite.php)
on pages displaying the results. Do not write raw IPs into persistent analytics events.

## Consequences

- (+) Lookups can occur without transmitting visitor IPs to an external geolocation API.
- (−) Database updates, licensing attribution and memory use become operational responsibilities.
- (−) Location data is approximate, and missing data stays unknown rather than being guessed.
- (−) The existing external lookup remains active until the pipeline is integrated and deployed.
