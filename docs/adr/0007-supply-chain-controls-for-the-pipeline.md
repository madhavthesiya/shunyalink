# ADR 0007: Pin pipeline inputs and gate images on actionable vulnerabilities

- **Status:** Accepted (2026-11-07)
- **Code:** `.github/workflows/ci.yml`, `.github/workflows/deploy-backend-azure.yml`, `.github/dependabot.yml`, `.github/trivy-exceptions.yaml`

## Context

CI runs third-party code and deploys a container with transitive dependencies. A passing application
test cannot establish that an action tag has not moved or that an image has no known vulnerable
packages. In March 2025 attackers moved `tj-actions/changed-files` tags to malicious code; in March
2026 `trivy-action` tags were hijacked. Pinning alone does not make a chosen revision safe.

## Options

| Option | For | Against |
|---|---|---|
| Moving action tags and report-only scans | Simple and low maintenance | Tags can change unnoticed; reports do not stop a risky release |
| Block every finding, including unfixed or high findings | Strict | Can block all releases without an actionable fix; findings need applicability review |
| **Pinned actions, dependency review and a fixable critical image gate** | Immutable references and an actionable release gate | Updates and exceptional findings still need review |

## Decision

- Pin actions to full commit SHAs and label the version in a comment. Dependabot proposes updates
  for `github-actions` as well as Maven, npm and Docker. Pinning limits later tag movement, not a
  compromised revision chosen initially; review advisories and update compromised pins promptly.
- Apply a 14-day cooldown to ordinary dependency releases; security updates are not delayed. This
  allows time for broken or malicious releases to be discovered, at the cost of slower updates.
- Dependency review blocks newly introduced high or critical runtime advisories on pull requests.
- Use the Trivy container pinned by digest rather than `trivy-action`. Report fixable HIGH and
  CRITICAL findings; fail the deploy on fixable CRITICAL findings. Unfixed issues and HIGH findings
  are not a release gate. The scan uses a 20-minute timeout: a cold Java database download exceeded
  Trivy's default five minutes locally. A scanner error must fail the job.
- GitHub secret scanning and push protection are already enabled, so a second gitleaks action was
  not added. These controls do not replace review or secret rotation after exposure.

### Initial scan and exceptions

On 2026-11-07 the original image had 10 fixable critical and 39 fixable high findings. Upgrading
Spring Boot to 3.5.16, Springdoc to 2.8.17, Tomcat to 10.1.61 and Netty to 4.1.137.Final reduced
the result to 2 critical and 8 high. These versions predate the 14-day cooldown. The remaining
findings are in `spring-webmvc` 6.2.19; the public open-source fix is Framework 7.0.9, requiring a
major framework migration. Spring rates [CVE-2026-47884](https://spring.io/security/cve-2026-47884/)
medium and requires XSLT view rendering; it rates [CVE-2026-47890](https://spring.io/security/cve-2026-47890/)
low and requires server-sent events with view fragments. Source inspection found no XSLT view or
SSE implementation; controllers are REST controllers. This is evidence of limited applicability,
not proof that all future code is safe.

The gate has two package-specific exceptions expiring 2027-02-07. The unfiltered summary still
shows them. Reassess before expiry and remove the exceptions by migrating to a fixed supported
Spring Framework version. Review immediately if XSLT views or SSE view fragments are introduced.
The original image still fails the exception-aware gate with nine critical findings; the upgraded
image passes it. These findings and their severities can change as vulnerability databases change.

## Consequences

- (+) A moved tag cannot silently replace a pinned action, and fixable critical image findings stop deployment.
- (+) The two reviewed findings remain visible in the summary and automatically return to the gate on expiry.
- (−) A major Spring migration remains outstanding; the exceptions accept residual uncertainty until then.
- (−) A cold Trivy database download and image scan add deployment time and depend on registry availability.
- (−) Dependency review and image scanning cover different inputs and neither proves absence of vulnerabilities.
