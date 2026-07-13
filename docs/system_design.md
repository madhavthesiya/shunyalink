# System Design & Reliability

## Why These Architectural Choices

**Base62 on sequential IDs** — Random UUIDs fragment B-tree indexes on every insert. Sequential IDs give ordered inserts; Base62 gives compact, URL-safe codes.

**Lua script for rate limiting** — Two separate Redis commands (`INCR` + `EXPIRE`) have a race window. If the app crashes between them, the key lives forever, permanently blocking that IP. A Lua script executes both atomically on the server.

**Write-Behind over Write-Through** — Writing to PostgreSQL on every click would bottleneck the hot redirect path. Buffering in Redis and batch-flushing every 30s decouples read latency from write durability.

**Flyway over `ddl-auto=update`** — Hibernate's auto-update silently modifies production schemas. Flyway gives versioned, auditable migrations (13 migrations across users, auth, profiles, analytics, ordering, tags, and cloud storage).

**Partial unique index** — Idempotency for permanent URLs is enforced at the database level. Same URL → same short ID. No application-level dedup logic needed.

**Geo-IP Self-Healing** — If the external lookup returns "Unknown" and later resolves, the system retroactively shifts one count from "Unknown" to the real country.

**On-demand password reveal** — Link passwords are never included in list/stats API responses. A dedicated authenticated endpoint (`/reveal-password`) decrypts and returns the password only when the owner explicitly requests it — same pattern as Google Password Manager.

**Synchronous AI Phishing Detection** — Scammers abuse URL shorteners to hide malware links. By passing the long URL through a Gemini LLM prompt synchronously during the `/shorten` request, we intercept and block typosquatting and scam links before they are even created. We implemented a **Resilient Fallback Pattern**: if Gemini rate-limits or fails, the request automatically falls back to Groq (Llama 3), and if both fail, the system fails-open to prevent breaking the core shortening functionality.

---

## Reliability & Observability

*   **Health Monitoring**: Integrated **Spring Boot Actuator** to provide real-time status of the JVM, Hibernate connection pool, and Redis connectivity.
*   **Live Frontend Heartbeat**: Implemented a dashboard-level monitoring component that polls the backend health status every 30 seconds, providing real-time "System Operational" feedback to the user.
*   **Production Readiness**: Configured custom health indicators to ensure the application only accepts traffic when all downstream services (Redis/PostgreSQL) are fully operational.
*   **Graceful Shutdown**: The platform is tuned to ensure all pending **Redis-to-PostgreSQL analytics syncs** are completed atomically before the container process exits during a deployment or scale-down event.
