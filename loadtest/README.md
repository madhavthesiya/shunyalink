# Load tests

[k6](https://k6.io/) scripts for the redirect hot path. Both default to the local stack
(`docker-compose up`, Nginx on `http://localhost`) and only hit another host when you
pass `BASE_URL` explicitly.

| Script | What it measures |
|---|---|
| `redirect-smoke.js` | One short link under a 20 → 50 VU ramp, with p95 and error-rate thresholds |
| `redirect-top20.js` | Redirects spread across the 20 most-clicked links |

```bash
# Local cluster
k6 run -e SHORT_ID=<an-existing-short-id> loadtest/redirect-smoke.js
k6 run loadtest/redirect-top20.js

# Another environment (be deliberate: this is real traffic)
k6 run -e BASE_URL=https://sl.madhavv.me -e SHORT_ID=ce loadtest/redirect-smoke.js
```

`redirect-top20.js` uses short IDs from the original benchmark database; edit `SHORT_IDS`
to match links that exist in yours. Requests use `redirects: 0`, so the numbers are the
redirect latency only, not the destination site's.
