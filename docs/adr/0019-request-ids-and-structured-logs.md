# ADR 0019: Request IDs, JSON logs in production, and what is never logged

- **Status:** Accepted (roadmap item 3.9)
- **Code:** `logging/RequestIdFilter`, `logging/MdcTaskDecorator`, `logging/LogSafe`, `ErrorResponse.requestId`,
  `Dockerfile.backend` (`LOGGING_STRUCTURED_FORMAT_CONSOLE`), `RateLimiterService.limitName`

## Context

On Nov 18 a deploy failed, and the application's own output wasn't saved, so the cause had to be inferred from
timing (see ADR 0005, "Redis at startup"). Once logs were turned on, other problems showed up:

- **No request correlation.** With concurrent requests, nothing tied the lines of one request together, or a
  user's error to its log lines.
- **Background work lost context.** A shorten starts an `@Async` preview fetch, whose logs had no link back to
  the request.
- **Multi-line entries.** Plain-text stack traces span dozens of lines, which can't be searched as one event.
- **Sensitive values in logs:**
  - full destination URLs (a path or query can hold an access token);
  - rate-limit keys containing **client IPs and email addresses**;
  - user-controlled values that could forge log lines (CodeQL alerts #4–#9).
- **Twelve `System.out`/`System.err` calls** without levels.

## Options

| Option | For | Against |
|---|---|---|
| Distributed tracing (OpenTelemetry, Micrometer Tracing) | Spans across services, standard IDs | An exporter and backend to run; more than one service needs today |
| **A request-ID filter + MDC** | Tiny, no infrastructure; every log line and error body carries the ID | Not a trace: no timings or spans |
| JSON logs everywhere | One format | Unreadable in local runs and CI output |
| **JSON (ECS) only in the container** | Searchable fields in production, readable text locally | Two formats; one test guards the container's setting |

## Decision

- **Request ID filter:**
  - `RequestIdFilter` runs first, before security, so every request gets an ID.
  - It accepts a caller's `X-Request-Id` only if it matches `[A-Za-z0-9._-]{8,64}`, so the frontend or a proxy can follow one request across services. Anything else, including an ID with line breaks, is replaced by a UUID.
  - The ID goes into MDC (`requestId`), back in the response header, and into every `ErrorResponse`.
- **Background work:** `MdcTaskDecorator` copies the context to `@Async` work and restores the pooled thread's own context afterwards, so one task's ID can't leak into the next.
- **Log format:**
  - The image sets `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs`: one JSON object per entry, with `requestId` as a field.
  - Local and test logs stay plain text, with the ID in each line.
  - A test reads the Dockerfile's value and runs Spring Boot's encoder with it, because an unknown format stops startup.
- **What is never logged:**
  - Destination URLs: only the host is logged (`LogSafe.host`).
  - Rate-limit keys: only the limit's name (`rate:login`), never the IP or email at the end.
  - User-controlled values pass through `LogSafe.value`, which replaces line breaks and control characters and caps the length.

## Consequences

- (+) "Something failed at 10:42" becomes: find the request ID in the error body, then every line of that request,
  including its background work.
- (+) Closes CodeQL log-injection alerts #4–#9 without suppressing the rule, and removes IPs and emails from logs.
- (−) It's correlation, not tracing: no per-step timings. Phase 5 (metrics, tracing) builds on this ID.
- (−) A caller can choose its own ID, so IDs are for finding logs, never for security decisions.
- (−) The frontend can read the header only if CORS exposes it (`Access-Control-Expose-Headers`). That's a
  follow-up for when the UI shows the ID.
