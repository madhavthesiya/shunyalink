# Read-only legacy analytics audit

The owner has since selected a fresh collection period. This audit is optional and is no longer a
release prerequisite. Old counters/history stay excluded and untouched; do not run an import or
reconcile ambiguous orphan counters merely to enable the fresh collection.

Run this audit before deciding how to migrate active counters, orphaned processing keys and
historical distributions. A processing key may already be partly applied to URL/platform totals.
The report never applies, deletes, renames or acknowledges source data and never marks history ready.

## Isolated entry point

Use the dedicated main class below, **not** the normal web application with an extra profile. Its
context loads only JDBC, a transaction manager and Redis. It does not scan application services or
start schedulers, password backfills, web endpoints or Flyway. Database inspection runs inside an
enforced read-only repeatable-read transaction. The report handles missing draft rollup tables by
recording their presence explicitly, so the audit itself does not require schema deployment.

Provide the target stores through a private local environment. Prefer an existing read-only database
account and read-only Redis credentials where available. Do not paste credentials into commands,
chat, reports or PRs. The isolated context needs storage credentials only, not JWT, mail or visitor keys.

| Environment variable | Purpose |
|---|---|
| `SPRING_DATASOURCE_URL` | Target PostgreSQL JDBC URL without embedded credentials |
| `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` | Database credentials |
| `SPRING_DATA_REDIS_URL` | Target Redis URL, including TLS/authentication as required; keep private |
| `ANALYTICS_REPORT_PATH` | A new private output filename; existing files are never overwritten |

From `backend`, with the target environment already supplied securely:

```powershell
$env:ANALYTICS_REPORT_PATH = Join-Path $env:TEMP ('shunyalink-legacy-audit-' + [guid]::NewGuid().ToString('N') + '.json')
.\mvnw.cmd -B -ntp spring-boot:run '-Dspring-boot.run.main-class=com.shunyalink.analytics.LegacyAnalyticsReportCli'
```

The process exits after writing the report. Treat an error/invalid or incomplete JSON output as a
failed audit. Keep reports private; share only the findings needed for a reconciliation decision.

## Interpretation

- `authorizesMigration` is always false. Redis commands do not form an atomic cross-store snapshot;
  even a report without issues is not proof that old writers have drained or that cutover is safe.
- Link coverage distinguishes committed totals, observed pipeline human counts, the derived legacy
  baseline and active pending counters. All-time hash totals and retained time-series record counts
  remain separate. Missing dimensions do not acquire fabricated history, dates or visitor counts.
- `processingCounters` contains abandoned processing-key counters for owner reconciliation. Never
  blindly add them to the database: URL rows and the platform total may have different partial effects.
- `unmatchedActiveCounters` and `unassignedLegacySources` identify data without a current URL mapping.
  Never attach it automatically to a later link reusing the alias or delete it as cleanup.
- Dimension totals larger than available legacy clicks and daily rows without matching hourly
  coverage are flagged. These are evidence to investigate, not an automatic repair prescription.
- Invalid counter values become unknown (`null`) with an issue. Their payloads are not exported.
  The report includes counts and source identifiers only: no ZSET members, visitor pseudonyms, raw
  visitor IPs, dimension labels, User-Agent headers or referrer paths are copied into it. Unexpected
  alias/key shapes are redacted before export.

Inspection has explicit budgets: 10,000 links, 20,000 fields per hash, 10,000 processing keys and
100,000 scanned analytics keys. Exceeding a budget fails the audit rather than returning a truncated
success. Large stores need a paged maintenance job. Storage operation and database transaction
timeouts bound individual waits; they do not establish a strict wall-clock limit for the whole report.

Before any import, prepare a reviewed frozen-source boundary, idempotent database application,
explicit orphan resolutions and an end-to-end migration test. The current audit performs none of
those writes and cannot replace the retention/replay-floor work.
