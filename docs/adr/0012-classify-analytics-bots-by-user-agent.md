# ADR 0012: Classify analytics bots using User-Agent markers

- **Status:** Accepted for the Phase 3 analytics pipeline (not yet integrated)
- **Code:** `BotClassifier`, `BotClassifierTest`

## Context

Automated requests distort visitor counts. The owner approved excluding bots and reporting approximate
unique visitors, while preserving historical analytics. Classifying a request from its User-Agent
does not prove who made it: clients can omit or spoof the header.

## Options

| Option | For | Against |
|---|---|---|
| Count all requests | No classification errors | Automated traffic inflates click counts |
| **Recognize common User-Agent markers** | Inexpensive and locally testable | Both false positives and false negatives; easily spoofed |
| External bot-detection service | More signals available | Cost, latency and sharing request data with another service |

## Decision

Classify common crawler, preview, scripting, headless-browser and deploy-smoke markers
case-insensitively. Treat missing headers as unclassified rather than automatically excluding them.
The future analytics pipeline will record bot counts separately. This is an analytics heuristic,
not an authorization or abuse-prevention control; it must not alter redirects or existing history.

## Consequences

- (+) Common automated requests can be separated without an external lookup.
- (−) Unrecognized bots are counted and some legitimate clients can match the broad markers.
- (−) Classification must be wired into the new pipeline and tested end to end before it affects counts.
