# ADR 0008: Quality gates are ratchets on today's baseline, not fixed targets

- **Status:** Accepted (2026-11-03)
- **Code:** `backend/pom.xml` (JaCoCo `check`), `frontend/eslint-suppressions.json`

## Context

Phase 2 introduced gates to a codebase that already existed. Measured on day one:

- backend line coverage 58.8% overall (security 78%, url 69%, analytics 62%, auth 50%, cp 24%);
- 39 ESLint errors across 37 files once ESLint was actually installed.

A gate that fails on day one gets switched off. A gate that never fails protects nothing.

## Options

| Option | For | Against |
|---|---|---|
| Fixed target now (e.g. 70% coverage, zero lint errors) | Simple to explain | Blocks all work until reached; invites assertion-free tests and blanket `eslint-disable` comments |
| Report only, no gate | No friction | Numbers drift down; nobody reads reports |
| **Ratchet: fail only on regression from the measured baseline** | Green from day one, can only improve, no pressure to game the metric | Baseline needs occasional tightening by hand |

## Decision

- **Coverage:** JaCoCo floors a few points under the measured values (total 55%, security 75%,
  url 65%, analytics 58%, auth 47%). The headroom absorbs normal refactoring noise. Floors are raised
  when coverage improves and never lowered to make a build pass.
- **Lint:** existing violations are recorded with ESLint bulk suppressions. Any new violation fails
  CI. When one is fixed, ESLint requires pruning it from the file, so the list can only shrink. Six
  trivial errors were fixed outright instead of recorded.

## Consequences

- (+) The gates were green on day one and catch new lint violations and coverage falling below the floors.
- (+) The remaining debt is visible and counted (33 suppressed lint errors; per-package coverage in
  every CI run's summary) instead of hidden behind `ignoreBuildErrors`.
- (−) Coverage is still a proxy: a floor guarantees lines ran, not that behaviour was asserted. The
  integration tests and named regression tests for each security fix matter more than the number.
- (−) Floors must be raised by hand. Otherwise the headroom lets coverage slide back by a few points.
