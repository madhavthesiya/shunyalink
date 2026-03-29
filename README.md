# ShunyaLink ✂️

> A production-grade URL shortener built with Spring Boot, Redis, and React.  
> **3,578 requests · 0% error rate · 355ms avg response time.**

🔗 **Live:** [https://shunyalink.madhavv.me](https://shunyalink.madhavv.me)

---

## What is this?

ShunyaLink is a full-stack URL shortener engineered for production — not just a tutorial project. It features Redis write-behind caching, atomic rate limiting, distributed analytics syncing, Flyway schema migrations, and a clean React frontend. Every architectural decision was made with scalability and correctness in mind.

---

## Features

- **URL Shortening** — Base62 encoding on sequential DB IDs for compact, ordered short codes
- **Custom Aliases** — Choose your own slug (e.g. `shunyalink.madhavv.me/my-brand`)
- **Expiring Links** — Set TTL in days; expired links return `410 Gone`
- **Click Analytics** — Total clicks, last accessed time, created date per link
- **Idempotency** — Same permanent URL always returns the same short ID (enforced via partial DB index)
- **Rate Limiting** — Per-IP request limiting using an atomic Lua script
- **Cache Warmup** — Top 1000 most-clicked URLs preloaded into Redis on every startup
- **Auto Cleanup** — Expired links purged from the database automatically every hour

---

## Architecture

```
React Frontend (Vite)
       │
       │ REST API
       ▼
Spring Boot Backend
       │
       ├──► Redis       → URL cache + analytics counters + rate limit keys
       │
       └──► PostgreSQL  → Persistent URL storage (managed by Flyway)
```

### Redirect Flow

Every time someone visits a short link, the backend first checks Redis. On a cache hit, the click is counted in Redis and the user is redirected immediately — no database involved. On a cache miss, it queries PostgreSQL, checks expiry, writes the result to Redis with a dynamic TTL, and then redirects. Analytics counters in Redis are synced to PostgreSQL every 30 seconds by a background scheduler.

### Write-Behind Analytics

Clicks are never written to the database on the hot redirect path. Every redirect increments a Redis hash counter in microseconds. A scheduler runs every 30 seconds, acquires a distributed UUID lock (so only one instance syncs in a multi-server setup), renames the counter key atomically, flushes all counts to PostgreSQL in a batch, and releases the lock. This means zero DB write pressure during peak traffic.

### Why These Choices?

**Base62 on sequential IDs** — Random IDs cause B-tree index fragmentation. Sequential IDs give ordered inserts. Base62 gives compact, URL-safe codes.

**Lua script for rate limiting** — Two separate Redis commands (`INCR` + `EXPIRE`) have a race condition. If the app crashes between them, the key lives forever with no TTL, permanently blocking the user. A Lua script executes both atomically on the server.

**Dynamic cache TTL** — If a URL expires in 2 hours but is cached for 24, users get stale redirects. The TTL on the Redis key is set to exactly when the URL expires.

**Flyway over `ddl-auto=update`** — Hibernate's auto-update is dangerous in production. Flyway gives a versioned, auditable migration history and never surprises you with silent schema changes.

**Partial unique index** — Idempotency is enforced at the database level for permanent, non-custom URLs. The same URL always resolves to the same short ID without any application-level deduplication logic.

---

## Performance

Benchmark on `GET /{shortId}` redirect with Redis cache warm:

| Metric | Result |
|--------|--------|
| Total Requests | 3,578 |
| Error Rate | **0%** |
| Min Response | 247 ms |
| Avg Response | **355 ms** |
| p95 Response | 668 ms |
| Max Response | 700 ms |

~95%+ of requests served entirely from Redis — zero database reads on the hot path.

---

## API

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/v1/url/shorten` | Shorten a URL |
| `GET` | `/{shortId}` | Redirect to original URL |
| `GET` | `/api/v1/url/stats/{shortId}` | Get click analytics |

**Error codes:** `400` invalid input · `404` not found · `409` alias taken · `410` expired · `429` rate limited

Interactive docs available at `/swagger-ui.html` when running locally.

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Spring Boot 3.3, Java 21 |
| Database | PostgreSQL 18 |
| Cache | Redis 7 |
| Migrations | Flyway 10 |
| Frontend | React 18, Vite 7, Axios |
| Docs | SpringDoc OpenAPI (Swagger) |
| Build | Maven |

---

## Project Structure

```
src/main/java/com/example/demo/
├── analytics/
│   ├── AnalyticsScheduler.java          # Write-behind sync with distributed lock
│   └── ExpiredLinkCleanupScheduler.java # Hourly expired link purge
├── cache/
│   └── CacheWarmup.java                 # Top 1000 URLs preloaded on startup
├── config/
│   ├── CorsConfig.java                  # CORS whitelist for frontend
│   └── RedisConfig.java                 # RedisTemplate configuration
├── exception/
│   └── GlobalExceptionHandler.java      # Centralized error handling
└── url/
    ├── DbUrlService.java                # Core business logic
    ├── IdEncoder.java                   # Base62 encoding
    ├── RateLimiterService.java          # Atomic Lua rate limiting
    ├── RedirectController.java          # Handles /{shortId} redirects
    └── UrlController.java               # REST API endpoints

src/main/resources/
└── db/migration/
    └── V1__init.sql                     # Flyway schema (table + partial index)

url-shortener-frontend/
└── src/
    ├── App.jsx                          # Main React component
    └── App.css                          # Styles
```

---

## Running Locally

Requires Java 21, PostgreSQL, Redis, and Node.js.

Set environment variables `DB_USERNAME` and `DB_PASSWORD`, then start the Spring Boot app. Flyway will create the schema automatically on first run. For the frontend, run `npm install` and `npm run dev` inside the `url-shortener-frontend` folder.

Backend → `http://localhost:8080`  
Frontend → `http://localhost:5173`  
Swagger → `http://localhost:8080/swagger-ui.html`

---

---

**Made by Madhav Thesiya** — *If this was useful, drop a ⭐*
