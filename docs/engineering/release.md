# Release Operations Guide

## Release success criteria

A release is considered successful only when all of the following pass:

1. `release.yml` completes with all required jobs green (`verify`, `security-gate`, `build-container`, `publish-release`).
2. Release artifacts are published with checksums (`SHA256SUMS.txt`) and expected binaries.
3. Container image tags are published and recorded in `release-container-tags.txt`.
4. `post-release-verify.yml` completes successfully for the released tag, including:
   - application startup smoke check,
   - `/actuator/health` check,
   - minimal API auth/access check,
   - checksum verification,
   - optional SBOM and attestation verification (when enabled).

## Post-release failure thresholds

Treat any of the following as a release-blocking failure that requires immediate triage:

- Startup smoke check fails (application cannot start from published image).
- Health endpoint does not report `UP`.
- Auth/access smoke check fails (unauthenticated request not rejected or authenticated request fails).
- Artifact checksum verification fails.
- SBOM/attestation checks fail when explicitly enabled for the run.

Severity guidance:

- **P0 (immediate rollback candidate):** startup failure, failed health check, checksum mismatch.
- **P1 (rollback likely, owner decision):** API auth/access regression affecting critical flows.
- **P2 (can be tolerated short term with mitigation):** optional SBOM/attestation check failures when runtime behavior is healthy.

## Rollback decision matrix

| Condition | Default decision | Owner responsible | Escalation |
|---|---|---|---|
| Startup or health smoke check fails | Roll back immediately to prior stable tag/image | Release owner + Platform owner | Incident commander notified immediately |
| Checksum verification fails | Roll back immediately and revoke affected artifacts | Release owner + Security owner | Security incident process |
| Auth/access smoke check fails | Roll back unless a documented and approved mitigation exists | Release owner | Product owner + Incident commander |
| Optional SBOM/attestation verification fails | Hold release if policy requires attestations; otherwise open follow-up remediation and monitor | Security owner | Platform owner for policy exception |
| All checks pass | Continue normal post-release observation window | Release owner | None |

## Owner responsibilities

### Release owner

- Triggers or monitors release and post-release verification workflows.
- Reviews machine-readable verification output and markdown summary.
- Declares release status in team channels.
- Initiates rollback workflow/issue when thresholds are exceeded.

### Platform owner

- Confirms rollback commands and deployment mechanics.
- Validates previous stable tag/image references.
- Executes platform-level rollback and verifies service restoration.

### Security owner

- Reviews checksum, SBOM, and attestation failures.
- Determines whether artifact trust has been compromised.
- Drives remediation actions and policy updates.

### Incident commander (when engaged)

- Coordinates communication and timeline.
- Confirms rollback completion criteria and customer-impact updates.
- Ensures post-incident action items are captured.

## Operational usage

- Automatic path: `post-release-verify.yml` runs after successful `release.yml` completion via `workflow_run`.
- Manual path: use `workflow_dispatch` for ad-hoc verification and optional strict checks (`verify_sbom`, `verify_attestations`).
- Rollback guidance is generated in the run summary and machine-readable artifact, including:
  - prior stable tag,
  - prior stable image reference,
  - sample rollback commands.
- Optional rollback issue automation is available from manual runs when verification fails.
