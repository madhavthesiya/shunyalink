# Performance

## Benchmarks

### Production (k6 → Azure, 50 concurrent users)

| Metric | Before (v1) | After (v2 — Redis Hash) | Improvement |
|--------|-------------|------------------------|-------------|
| Total Requests | 1,437 | **4,581** | 3.2x more |
| Avg Response | 3.12s | **893 ms** | **3.5x faster** |
| Min Response | — | **76 ms** | — |
| Error Rate | 0% | 0% | Perfect |

> Latency includes cross-region internet round-trip (India → Azure Free Tier). The Redis Hash optimization reduced avg response by 3.5x even on a resource-constrained cloud instance.

### Local Cluster (k6 → 3-node Docker, 50 concurrent users)

| Metric | Before (v1) | After (v2 — Redis Hash) | Improvement |
|--------|-------------|------------------------|-------------|
| Throughput | 483 req/s | **566 req/s** | +17% |
| Avg Response | 31.27 ms | **19.5 ms** | **37% faster** |
| p95 Response | 90.47 ms | **34.9 ms** | **2.6x faster** |
| Max Response | 653 ms | **325 ms** | 2x better |
| Error Rate | 0% | 0% | Perfect |
| Redis Cache Hit Rate | 99.99% | 99.99% | Near-perfect |

> **v1:** Redis cached only the URL string — the database was still queried on every redirect for password/title/expiry checks.<br/>
> **v2:** All redirect-critical fields cached as a Redis Hash — **zero database reads** on the hot path for cache hits.

## Failover Test

One backend node killed mid-traffic — Nginx reroutes to surviving replicas with zero downtime:

![Failover Demo](failover-demo.gif)

---

## Performance Optimization Deep Dive

### The Problem

During profiling, I discovered the redirect hot path was making a **redundant database query on every single request**, even with Redis caching enabled:

```java
// Step 1: ALWAYS hit PostgreSQL for the full entity (password, title, expiry checks)
UrlEntity entity = urlRepository.findByShortId(shortId);

// Step 2: Check Redis for the URL string (but we already have entity.getLongUrl()!)
String longUrl = urlService.getLongUrl(shortId);  // redundant
```

The Redis cache only stored the long URL string (`SET url:abc123 "https://..."`) — but the controller needed the **full entity** for password verification, social bot OG tags, and expiry checks. So PostgreSQL was hit on every redirect regardless.

### The Solution

**1. Redis Hash Entity Caching** — Cache all redirect-critical fields, not just the URL:

```
HSET url:entity:abc123
  longUrl      "https://google.com"
  hasPassword  "false"
  title        "Google"
  expiryTime   "2026-12-31T00:00:00"
```

On cache hit: **zero database reads.** On cache miss: query DB once, populate the Hash, all subsequent requests served from Redis.

**2. Eliminated Redundant Lookup** — Removed the unnecessary `getLongUrl()` call since the entity (or cached Hash) already provides the long URL.

**3. Bounded Async Thread Pool** — Replaced Spring's default `SimpleAsyncTaskExecutor` (which creates **unlimited threads**) with a bounded `ThreadPoolTaskExecutor` (10 core, 50 max, 500 queue) for analytics processing. `CallerRunsPolicy` ensures no analytics events are silently dropped.

**4. Cache Invalidation** — The entity Hash is invalidated on password changes, title updates, and link deletion to prevent stale data.

### Result

| Metric | Before | After |
|--------|--------|-------|
| p95 Latency | 90 ms | **35 ms (2.6x faster)** |
| Avg Latency | 31 ms | **19 ms (37% faster)** |
| Throughput | 483 req/s | **566 req/s (+17%)** |

Benchmarked with [k6](https://k6.io/) at 50 concurrent virtual users against the local 3-node Docker cluster.
