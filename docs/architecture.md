# Architecture

```mermaid
graph TB
    Client([Browser / Mobile]):::client

    subgraph Edge["Edge Layer"]
        Nginx["Nginx Load Balancer<br/>Round-Robin · X-Forwarded-For"]
    end

    subgraph Cluster["Backend Cluster (×3 Replicas)"]
        SB1["Spring Boot :8080"]
        SB2["Spring Boot :8080"]
        SB3["Spring Boot :8080"]
    end

    subgraph Data["Data Layer"]
        Redis[("Redis 7<br/>Entity Hash Cache · Counters · Rate Limits · Geo")]
        PG[("PostgreSQL 16<br/>URLs · Users · Profiles · Migrations")]
    end

    subgraph Scraping["Scraping Layer"]
        Scraper["Puppeteer Scraper<br/>Headless Chrome · CodeChef"]
    end

    subgraph Frontend["Frontend"]
        Next["Next.js 15<br/>App Router · SSR"]
    end

    Client --> Nginx
    Client --> Next
    Nginx --> SB1 & SB2 & SB3
    SB1 & SB2 & SB3 --> Redis
    SB1 & SB2 & SB3 --> PG
    SB1 & SB2 & SB3 -->|"anti-bot scrape"| Scraper
    Next -->|REST API| Nginx

    classDef client fill:#6366f1,color:#fff,stroke:none
```

## Redirect Hot-Path

```mermaid
sequenceDiagram
    participant U as User
    participant N as Nginx
    participant SB as Spring Boot
    participant R as Redis
    participant PG as PostgreSQL

    U->>N: GET /abc123
    N->>SB: Proxy (round-robin)
    SB->>R: HGETALL url:entity:abc123

    alt Cache Hit (zero DB)
        R-->>SB: {longUrl, hasPassword, title, expiryTime}
        SB->>R: HINCRBY click_counts abc123 1 (async)
        SB-->>U: 302 Redirect
    else Cache Miss
        SB->>PG: SELECT * FROM urls
        PG-->>SB: row
        SB->>R: HSET url:entity:abc123 {longUrl, hasPassword, title, expiryTime}
        SB->>R: HINCRBY click_counts abc123 1 (async)
        SB-->>U: 302 Redirect
    end

    Note over R,PG: Scheduler syncs counters<br/>to PostgreSQL every 30s
```

> **Why Redis Hash instead of a simple string?** The redirect path needs more than just the long URL — it must check password protection, expiry time, and the page title (for social bot OG tags). Caching all redirect-critical fields as a Hash eliminates the database query entirely on cache hits. See [Performance Optimization](performance.md#performance-optimization) for the full story.

## Write-Behind Analytics

Clicks are **never written to the database on the redirect path.** Each redirect increments a Redis hash counter in microseconds via a bounded `ThreadPoolTaskExecutor` (10 core, 50 max threads). A background scheduler runs every 30 seconds, acquires a distributed UUID lock (ensuring only one instance syncs across the cluster), atomically renames the counter key, batch-flushes all counts to PostgreSQL, and releases the lock.

**Result:** Zero DB write pressure during peak traffic.
