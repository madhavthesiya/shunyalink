# ShunyaLink

> An all-in-one Smart URL Shortener and Digital Identity Platform featuring write-behind analytics, Geo-IP tracking, Link-in-Bio profiles, and a real-time Competitive Programming dashboard.
> Engineered with distributed caching, horizontal scaling, and real-world security & AI trade-offs.

- 🔗 **Live:** https://shunyalink.madhavv.me
- 📖 **API Docs:** https://sl.madhavv.me/swagger-ui/index.html
- 🟢 **Health:** https://sl.madhavv.me/actuator/health

---

## Table of Contents

- [Engineering Highlights](#engineering-highlights)
- [Features](#features)
- [Tech Stack](#tech-stack)
- [Architecture & Deep Dives](docs/architecture.md)
- [Performance & Benchmarks](docs/performance.md)
- [System Design Decisions](docs/system_design.md)
- [API Reference](docs/api_reference.md)
- [Project Structure](docs/project_structure.md)
- [Deployment & Setup](#running-locally)

---

## Engineering Highlights

Built to scale horizontally and survive heavy traffic spikes, this project implements several enterprise-grade backend patterns:

* ⚡ **75% Latency Reduction via Parallel Pipelines:** Aggregated real-time competitive programming stats across 5 different platforms simultaneously using `CompletableFuture.allOf()`, cutting total wait times by 75%. Bypassed Cloudflare bot-detection using a pre-warmed, highly optimized Puppeteer microservice.
* 🚀 **Zero-DB Hot Path via Redis Hash Caching:** Reduced p95 redirect latency from 90ms down to 34.9ms (a 2.6x improvement) by caching all redirect-critical fields (URLs, passwords, expiry times) inside Redis Hashes, entirely eliminating PostgreSQL queries on cache hits. [👉 *Read the benchmark breakdown*](docs/performance.md)
* 🛡️ **Stateless Distributed Architecture:** Scaled across 3 Spring Boot replicas behind an Nginx load balancer. Implemented stateless authentication via JWTs with a Redis-backed Token Blacklist for secure revocation on logout. [👉 *View the Architecture Diagram*](docs/architecture.md)
* 🔄 **Write-Behind Analytics with Distributed Locks:** Decoupled the redirect hot-path from analytics tracking. Clicks are buffered in Redis Hash counters asynchronously and batch-flushed to PostgreSQL every 30 seconds by a cron job utilizing a Redis `SETNX` UUID Distributed Lock to prevent race conditions across the 3 backend replicas.
* 🔐 **Dual Security Model:** Secured the database using two distinct cryptographic strategies: **BCrypt (One-Way Hashing)** for irreversible user passwords, and **AES-256 (Two-Way Encryption)** for short-link passwords to support an "On-Demand Password Reveal" feature. [👉 *Read the System Design decisions*](docs/system_design.md)

---

## Features

| Category | Feature |
|----------|---------|
| **Core** | Base62 short codes · Custom aliases · Link expiration · Password protection · UTM builder |
| **Analytics** | Write-behind click tracking · Time-series charts · Geo-IP distribution with self-healing · Referrer source tracking · Device & browser analytics |
| **Identity** | JWT auth · Google OAuth 2.0 · Email verification · Password reset · Cloudinary profile pictures |
| **Programmer Portfolio** | **Bento-Box UI** · Aggregated stats from **LeetCode, Codeforces, CodeChef, AtCoder** · GitHub Contribution Sync · Public `/portfolio/{username}` |
| **AI Insights** | **AI Profile Roast** (Gemini/Groq) · **Auto-Categorization** of shortlinks · **AI Phishing Detection** |
| **Bio-Link** | Drag-and-Drop Link Reordering · Public `/@username` profiles · Dynamic OG tags for social sharing · Theme customization · Show/hide links toggle |
| **Data** | CSV bulk import (drag-and-drop) · CSV export · Global dashboard search (title, URL, short ID, tags) · Custom tags |
| **Infra** | 3-node cluster · Nginx LB · Lua rate limiting · Cache warmup (top 1000) · **Branded QR Generation** (with logo overlay) · **Puppeteer Scraper** (headless Chrome for anti-bot bypass) · Actuator lockdown |
| **Security** | On-demand password reveal · AES-256 encryption · AI-driven URL safety verification |

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Java 21, Spring Boot 3.3 |
| Frontend | Next.js 15, React 19, TypeScript |
| Database | PostgreSQL 16 |
| Cache | Redis 7 |
| Migrations | Flyway 10 (13 versioned migrations) |
| Auth | JWT + Google OAuth 2.0 |
| AI | Gemini 2.0 Flash + Groq (Llama 3) — categorization, roasts, phishing detection |
| Cloud Storage | Cloudinary CDN (profile pictures, auto-compression to WebP) |
| Load Balancer | Nginx (Docker, 3 upstream replicas) |
| Docs | SpringDoc OpenAPI (Swagger) |
| Build | Maven · Docker Compose |

---

## Production Deployment

| Component | Provider | Details |
|-----------|----------|---------|
| **Frontend** | Vercel | Next.js 15 SSR · Custom domain `shunyalink.madhavv.me` |
| **Backend** | Azure App Service | Spring Boot 3 · Java 21 · Custom domain `sl.madhavv.me` |
| **Database** | Supabase | PostgreSQL 17 · Connection pooling via Supavisor |
| **Cache** | Upstash | Serverless Redis · TLS · Write-behind analytics pipeline |
| **Scraper** | DigitalOcean App Platform | Puppeteer + Headless Chromium · Dockerized |

---

## Running Locally

**Prerequisites:** Docker & Docker Compose

```bash
# 1. Clone
git clone https://github.com/madhavthesiya/shunyalink.git
cd shunyalink

# 2. Set environment variables
cp .env.example .env
# Edit .env — fill in ALL values (see table below)

# 3. Start everything (PostgreSQL + Redis + 3×Backend + Scraper + Nginx + Frontend)
docker-compose up --build
```

### Required Environment Variables

| Variable | Description |
|----------|-------------|
| `DB_USERNAME` | PostgreSQL username |
| `DB_PASSWORD` | PostgreSQL password |
| `APP_BASE_URL` | Public backend URL (`http://localhost` for local) |
| `ALLOWED_ORIGIN` | Frontend origin for CORS (`http://localhost:3000` for local) |
| `JWT_SECRET` | 256-bit secret for JWT signing (`openssl rand -base64 32`) |
| `ENCRYPTION_SECRET` | 32-char key for AES-256 link password encryption |
| `MAIL_USERNAME` | Gmail address for transactional emails |
| `MAIL_PASSWORD` | Gmail App Password ([generate here](https://myaccount.google.com/apppasswords)) |
| `GEMINI_API_KEY` | Google Gemini API key (AI categorization + phishing detection) |
| `GROQ_API_KEY` | Groq API key (AI profile roasts) |
| `CLOUDINARY_URL` | Cloudinary environment URL (profile picture uploads) |

| Service | URL |
|---------|-----|
| Frontend | `http://localhost:3000` |
| Backend (via Nginx) | `http://localhost` |
| Swagger Docs | `http://localhost/swagger-ui/index.html` |
| System Health | `http://localhost/actuator/health` |
| PostgreSQL | `localhost:5432` |
| Redis | `localhost:6379` |
| Scraper | `http://localhost:3001` |

Flyway runs all 13 migrations automatically on first startup.

---

<div align="center">

**Made by [Madhav Thesiya](https://www.linkedin.com/in/madhavthesiya/)** — If this was useful, drop a ⭐<br>
**© 2026 Madhav Thesiya. All Rights Reserved.**

</div>
