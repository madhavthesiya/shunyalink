# Project Structure

```
backend/src/main/java/com/shunyalink/
├── analytics/
│   ├── AnalyticsScheduler.java       # Write-behind sync with distributed lock
│   ├── AnalyticsService.java         # Time-series + Geo-IP recording & self-healing
│   ├── GlobalStatsEntity.java        # Aggregate click counters
│   └── GlobalStatsRepository.java
├── auth/
│   ├── AuthController.java           # Register, Login, Google OAuth, Email verification
│   ├── AuthService.java              # Core auth logic + password reset
│   ├── CloudinaryService.java        # Profile picture upload + auto-compression
│   ├── EmailService.java             # Transactional emails (verification, reset)
│   ├── ProfileController.java        # Bio-link CRUD + profile picture upload
│   └── UserEntity.java               # JPA user model
├── cache/
│   └── CacheWarmup.java              # Top 1000 URLs preloaded on startup (String + Hash)
├── config/
│   ├── AppConfig.java                # RestTemplate + async config
│   ├── AsyncConfig.java              # Bounded thread pool for analytics (10 core, 50 max)
│   ├── RedisConfig.java              # RedisTemplate serialization
│   └── OpenApiConfig.java            # Swagger UI config
├── cp/
│   ├── CpController.java             # Multi-platform stats aggregator + roast endpoint
│   ├── LeetCodeService.java          # LeetCode GraphQL integration
│   ├── CodeforcesService.java        # Codeforces API integration
│   ├── GithubService.java            # GitHub API integration
│   ├── CodeChefService.java          # Delegates to Puppeteer scraper microservice
│   ├── AtCoderService.java           # AtCoder API + Kenkoooo integration
│   └── LlmIntegrationService.java    # Hybrid Groq/Gemini AI for roasts & categorization
├── exception/
│   └── GlobalExceptionHandler.java   # Centralized error handling (400/403/404/409/410/429)
├── rate/
│   └── RateLimiterService.java       # Atomic Lua rate limiting (fail-open)
├── scheduler/
│   └── ExpiredLinkCleanupScheduler.java  # Hourly expired link purge
├── security/
│   ├── SecurityConfig.java           # Spring Security filter chain
│   ├── JwtService.java               # JWT creation + validation
│   ├── JwtBlacklistService.java      # Token revocation via Redis
│   └── JwtAuthenticationFilter.java  # Per-request JWT filter
├── url/
│   ├── DbUrlService.java             # Core business logic + AI categorization
│   ├── Base62IdEncoder.java          # Sequential ID → Base62
│   ├── RedirectController.java       # /{shortId} redirect + Redis Hash cache + OG tags
│   ├── UrlController.java            # REST API endpoints (25+ routes)
│   ├── CsvImportService.java         # Bulk CSV import with validation
│   ├── CsvExportService.java         # CSV data export
│   ├── MetadataService.java          # Thread-safe URL title scraping
│   ├── QrController.java             # QR code generation
│   └── ReorderRequest.java           # DTO for drag-and-drop ordering
└── util/
    └── EncryptionUtils.java          # AES-256 encryption/decryption

frontend/
├── app/
│   ├── layout.tsx                    # Root layout + SEO/OG metadata
│   ├── page.tsx                      # Landing page
│   ├── login/ & register/            # Auth pages
│   ├── forgot-password/ & reset-password/ # Password recovery
│   ├── dashboard/                    # Link management + insights + settings
│   ├── [username]/                   # Public bio-link profiles (server-side OG tags)
│   ├── cp/[username]/                # CP portfolio page (server-side OG tags)
│   └── p/                            # Password challenge page
└── components/
    ├── header.tsx & footer.tsx        # Site chrome
    ├── shortener-form.tsx             # URL shortening form + UTM builder
    ├── user-profile-settings.tsx      # Bio-link editor + live preview + avatar upload
    ├── profile-card.tsx               # Public bio-link card (Normal + Programmer modes)
    ├── dashboard/                     # Dashboard panels (links, analytics, import modal, system status)
    ├── cp/                            # CP widgets (LeetCode, Codeforces, CodeChef, AtCoder, GitHub, Roast)
    └── home/                          # Homepage sections (hero, features, bio showcase, stats)

nginx/
└── nginx.conf                         # Load balancer for 3 backend replicas

scraper/
├── index.js                           # Express + Puppeteer headless Chrome scraper
├── package.json                       # Dependencies: express, puppeteer
└── Dockerfile.scraper                 # Node 20 + system Chromium

docker-compose.yml                     # Full stack: PG + Redis + 3×Backend + Scraper + Nginx + Frontend (11 containers)
```
