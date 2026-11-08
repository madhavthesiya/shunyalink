# ADR 0013: Resolve countries from a local DB-IP database

- **Status:** Accepted
- **Code:** `LocalCountryResolver`, `CountryResolverConfig`, `AnalyticsService`, `Dockerfile.backend`

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
October 2026 country database occupies about 8.3 MB uncompressed. Upstream removes older monthly
downloads, so the container build uses an unmodified copy retained as an asset on the repository's
`geoip-dbip-country-2026-10` GitHub release and verifies its SHA-256. The build pins asset ID
`723453875` through GitHub's public asset API with the octet-stream Accept header and bounded retries.
Release notes and an attached
NOTICE identify DB-IP, the original download, checksum, and CC BY 4.0 license. The Insights page
contains the required [DB-IP attribution](https://db-ip.com/db/lite.php).

To refresh, download a released monthly country MMDB from DB-IP, verify and record its checksum,
and publish the unmodified file with attribution and license information on a new data release.
Keep old release assets for reproducible builds; do not replace files under existing release URLs.
Open a PR updating both the asset ID/URL and checksum in `Dockerfile.backend`, test country lookups, and
build the image. Never fall back to an unchecked or moving upstream URL. Builds depend on GitHub
release availability, while data freshness is a separate manual maintenance task.

For local development, `app.geoip.database` can point at a downloaded database; a missing file
returns Unknown. Do not write raw IPs into persistent analytics events.

## Consequences

- (+) Lookups can occur without transmitting visitor IPs to an external geolocation API.
- (−) Database updates, licensing attribution and memory use become operational responsibilities.
- (+) Upstream monthly cleanup cannot remove the copy used by existing builds.
- (−) Repository maintainers must retain release assets; the checksum detects replacement but cannot
  restore a deleted asset or make GitHub available during an outage. Anonymous API rate limits can
  also fail a build; the build must fail rather than silently use an unverified file.
- (−) Location data is approximate, and missing data stays unknown rather than being guessed.
- (−) Existing historic geographic data is unchanged; new data uses the DB-IP database after deployment.
