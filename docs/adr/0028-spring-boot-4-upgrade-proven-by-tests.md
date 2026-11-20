# ADR 0028: Spring Boot 4.1 as a version bump on its own, proven by the tests rather than by the compiler

- **Status:** Accepted (roadmap item 3.13; 3.13a was the `@MockitoBean` change, PR #48)
- **Code:** `backend/pom.xml`, `application.properties`, `config/RedisConfig`, `safety/SafeBrowsingClient`,
  `cp/LlmIntegrationService`, `BaseIntegrationTest`, `ApplicationContextWiringTest`, the `@WebMvcTest` classes

## Context

The backend ran Spring Boot 3.5. The 3.5 line had its last open-source release in June 2026, so new security fixes
would no longer reach us. Spring Boot 4 is a major version: Spring Framework 7, Jakarta EE 11, Hibernate 7,
Jackson 3, Tomcat 11, Testcontainers 2, and Spring Boot itself split into many smaller modules.

## Options

1. **Stay on 3.5.** No work now, but no more fixes; every later CVE becomes an emergency.
2. **Jump to the newest 4.2.0.** Released a day before this work, so it fails the 14-day cooldown of ADR 0007.
3. **4.1.2** (released 22 October 2026): the newest release that passes the cooldown. **Chosen.**

## Decision

- **Upgrade to 4.1.2 as a change of its own.** The code changes are only the ones the upgrade forces. Deprecation
  clean-ups come in a separate PR afterwards, so if production misbehaves, the cause is clearly the upgrade.
- **"Done" means the full suite against real PostgreSQL and Redis, not "it compiles".** After the version bump, main code
  had **0** compile errors, and the first full run had **139** failing tests. Several of those failures were
  production problems that the compiler cannot see.

## What Boot 4 broke, and how each was found

| Change in Boot 4 | What would have happened | Found by | Fix |
|---|---|---|---|
| `RestClient.Builder` moved to its own module | The application would not start: `No qualifying bean of type 'RestClient$Builder'` (`Upstreams` needs it) | The suite (through `TestRestTemplate`, which needs the same module); then a mutation that removes the module from production wiring | `spring-boot-starter-restclient` |
| Flyway auto-configuration moved to its own module | Flyway never runs. On production's migrated database the app still starts, so the *next* migration would silently not run | Release notes, then a mutation: on an empty database, `Schema validation: missing table [password_reset_tokens]` | `spring-boot-starter-flyway` |
| `RestClient` reads JSON with **Jackson 3** | Safe Browsing replies could not be parsed into a Jackson 2 `JsonNode`, so every check failed open and phishing links listed by Google were let through, with nothing visible but a counter | `SafeBrowsingClientTest`: expected `UNSAFE`, was `UNAVAILABLE` | Our JSON code uses Jackson 3 (`tools.jackson`) |
| Date settings moved from `SerializationFeature` to `DateTimeFeature` | The application would not start (`spring.jackson.serialization.write-dates-as-timestamps` no longer binds) | Context failures | `spring.jackson.datatype.datetime.*` |
| Auto-configuration classes moved to new packages | `spring.autoconfigure.exclude` by class **name** silently ignored the old names; the wiring test tried a real database | `ApplicationContextWiringTest` | The new names |
| `@WebMvcTest` no longer supplies a no-op `CacheManager` | Every controller slice test failed, because `@EnableCaching` sat on the main class, which slices inherit | Slice tests | `@EnableCaching` moved next to the `CacheManager` in `RedisConfig` (the placement the Spring Boot docs recommend) |
| Test classes moved; `TestRestTemplate` is opt-in | Compile errors in tests | Compiler | New imports; `@AutoConfigureTestRestTemplate` on the base class (one shared context) |

## Checks beyond the suite

- **Properties migrator.** One boot of the full application with `spring-boot-properties-migrator` reported no renamed
  or removed keys. A planted removed key was reported, so the silence was real. Environment variables used by the
  deploy (`SPRING_DATASOURCE_URL`, `SPRING_DATA_REDIS_*`, `LOGGING_STRUCTURED_FORMAT_CONSOLE`) map to keys that still
  exist.
- **Jackson 3 defaults that change the API, read from the jars:**
  - Unknown request fields are still ignored.
  - `FAIL_ON_NULL_FOR_PRIMITIVES` is now on. The only request field it affects is `ShortenRequest.useAutoTitle`, and
    only for an explicit `null`; a missing field still means `false`. The frontend always sends a boolean. We keep
    the stricter default: a `null` boolean is a client bug.
  - Response properties are now sorted alphabetically. I wrote here that JSON clients do not depend on the order.
    **One did: our own deploy smoke test** (see "The first deploy" below).
- **Mutations.**
  - With `@EnableCaching` removed entirely, the new `caching_isOnInTheApplication_soRoastsComeFromTheCache` test fails.
    Without that test, moving the annotation could have switched caching off silently, and every portfolio view
    would have paid for a new AI roast.
  - Removing `spring-boot-starter-restclient` or `spring-boot-starter-flyway` makes the application fail to start in
    the tests (see the table), so neither module can be dropped by accident later.
- **Rollback.** No schema change. Hibernate 7 validates the same columns (`ddl-auto=validate` in the integration
  tests), so the automatic rollback of ADR 0006 can still start the previous image.

## The first deploy (PR #49, `ecf9d0c`, Nov 21) was rolled back, and the application was fine

The new build answered `/actuator/health` with **HTTP 200 and every component UP**, ten times in a row. The smoke test
still declared it unhealthy and rolled it back. It matched the body as text, `{"status":"UP"`, and with Jackson 3 the
body starts with `{"components":…`, because the properties are sorted. The rollback worked as designed: production kept
serving the previous build, with no downtime.

- **Fix (PR #51):** the smoke test parses JSON (`jq`) and reads the top-level `status` and `app.commit`, whatever their
  order.
- **Why it wasn't caught:** nothing tested the smoke test itself. `test-smoke-test.sh` now runs it in CI against a stub
  backend with both property orders, a DOWN component, a top-level `UNKNOWN` status where only a component says UP,
  and a body that isn't JSON. Run against the old script, the Spring Boot 4 case fails, which is the red.
- **We keep Jackson 3's sorted output** rather than switching it off. JSON object members have no defined order
  (RFC 8259), so a consumer that depends on the order is the bug, and fixing the consumer removes it for good.

## Consequences

- (+) A supported framework line that still receives security fixes.
- (+) Our code uses one JSON library: Jackson 3, the one Spring's HTTP layer uses. Jackson 2 stays on the classpath
  only for jjwt and Swagger.
- (−) The upgrade left deprecation warnings: Redis `TimeUnit` overloads, `HttpStatus.PAYLOAD_TOO_LARGE`, the old
  Testcontainers `PostgreSQLContainer`. **Cleaned up in 3.13c**, a separate PR, which leaves 0 compiler warnings. That
  PR also writes the legacy `last_access` key with its expiry in one command (`SET … EX`, previously `SET` and then
  `EXPIRE`). A lesson from it: when the code moved to the `Duration` overload, 5 of the 6 scheduler tests still passed.
  Their stubs targeted the old overload, and an unmatched stub returns `null`, which means "lock not acquired".
  Only one test failed loudly. All the stubs now target the new overload.
- (−) A lesson worth keeping: configuration that names classes or keys **as strings** (auto-configuration excludes,
  property names) fails silently after a major upgrade. Only a test that starts the application finds it. The same goes
  for anything that reads JSON as text: the deploy gate was such a reader, and nothing tested it until it misfired.
