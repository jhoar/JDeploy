# CI Operations Guide

This document explains how CI is organized, what must pass before merge/release, and how to triage/recover common failures quickly.

## 1) Workflow map (which file does what)

| Workflow file | Workflow name | Trigger(s) | Purpose |
|---|---|---|---|
| `.github/workflows/ci.yml` | `CI` | PRs, pushes to `main`, manual dispatch | Main verification pipeline for toolchain sanity, code quality/unit tests, backend integration tests (Neo4j), and packaging. |
| `.github/workflows/security.yml` | `Security Scans` | PRs + pushes to `main` + weekly cron | Runs CodeQL, Trivy dependency vulnerability scans, and Gitleaks secret scanning. |
| `.github/workflows/container.yml` | `Container` | PRs, pushes to `main`, tags | Builds container image metadata and image (pushes on eligible push events). |
| `.github/workflows/release.yml` | `Release` | Version tags `v*.*.*` | Verifies release candidate, applies security gate, builds/pushes release container, publishes GitHub release assets. |
| `.github/workflows/post-release-verify.yml` | `Post Release Verify` | After `Release` completes (or manual) | Validates published release artifacts/image and optional SBOM/attestation checks; can produce rollback guidance. |
| `.github/workflows/dependabot-auto-merge.yml` | `Dependabot Auto-merge` | After successful `CI` workflow run | Enables auto-merge for open Dependabot PRs associated with the successful run. |

## 2) Required checks and expected runtime per job

### Branch-protection required checks (`main`)

Required checks are currently defined in `README.md` branch protection guidance:

- `quality`
- `integration-tests`
- `build`
- `security`

### Expected runtime by CI job (`ci.yml`)

| Job | Required for merge | Timeout | Typical runtime | Notes |
|---|---:|---:|---:|---|
| `validate` | No | none | 1-2 min | Toolchain and runner diagnostics only. |
| `quality (backend-api)` | Yes (`quality`) | none | 4-8 min | Static/format checks (if configured) + unit tests (module scope). |
| `quality (vaadin-ui)` | Yes (`quality`) | none | 5-10 min | Static/format checks (if configured) + unit tests (module scope). |
| `integration-tests` | Yes | 25 min | 8-18 min | Neo4j service startup + backend integration tests. |
| `build` | Yes | 30 min | 6-15 min | Full packaging and artifact verification/upload. |

> Runtime can increase during cache misses, dependency updates, or high GitHub-hosted runner load.

## 3) Common failures and remediation steps

### A) Neo4j startup failures (`integration-tests`)

**Typical symptoms**
- `Wait for Neo4j readiness` fails after max retries.
- Integration tests fail immediately with connection/auth errors.

**Remediation steps**
1. Open `integration-test-diagnostics` artifact and inspect:
   - `artifacts/neo4j/neo4j.log`
   - `artifacts/neo4j/neo4j-inspect.json`
   - `artifacts/neo4j/readiness.json` (if present)
2. Confirm env alignment in workflow:
   - URI `bolt://localhost:7687`
   - user/password `neo4j/changeit`
3. Re-run the failed job once (transient startup timing issues are possible).
4. If failure repeats, increase readiness tolerance (attempt count or wait interval) in `ci.yml` and open a follow-up issue.

### B) Flaky tests (unit/integration)

**Typical symptoms**
- Non-deterministic red/green behavior without code changes.
- Intermittent timeout/assertion mismatches.

**Remediation steps**
1. Download JUnit XML artifacts (`junit-reports-*` and integration diagnostics) and identify the recurring test class/case.
2. Re-run failed jobs first to confirm flakiness vs deterministic regression.
3. Quarantine strategy (short-term):
   - mark as flaky and create issue with owner and due date,
   - avoid silently disabling critical-path tests.
4. Fix strategy (long-term):
   - remove time/order/data coupling,
   - isolate shared mutable fixtures,
   - tighten test data setup/teardown.

### C) Cache issues (Maven/Trivy/GHA cache)

**Typical symptoms**
- Sudden dependency resolution failures,
- stale scanner DB behavior,
- unexpectedly long setup times on previously fast jobs.

**Remediation steps**
1. Re-run with cache-busting commit or workflow edit if corruption is suspected.
2. For Maven issues:
   - verify `actions/setup-java` cache settings,
   - inspect dependency mirror/network availability,
   - if needed, temporarily change cache key strategy.
3. For Trivy DB issues (`security.yml`):
   - check cache hit/miss step output,
   - force DB refresh path by invalidating date-based cache key.
4. Document recurring cache failures in issue tracker and assign CI owner.

## 4) How to rerun failed jobs and collect artifacts/logs

### GitHub UI path

1. Open **Actions** tab.
2. Select failing workflow run.
3. Use **Re-run jobs** (single failed job) or **Re-run all jobs**.
4. Download artifacts from the run summary page:
   - `junit-reports-backend-api`
   - `junit-reports-vaadin-ui`
   - `integration-test-diagnostics`
   - `packaged-artifacts`

### GitHub CLI path (examples)

```bash
# List recent runs
gh run list --limit 20

# View run details and failed jobs
gh run view <run-id> --log-failed

# Re-run only failed jobs
gh run rerun <run-id> --failed

# Re-run entire workflow run
gh run rerun <run-id>

# Download all artifacts for a run
gh run download <run-id> --dir ./artifacts/<run-id>
```

### Minimum log bundle for triage

Collect and attach the following in incident/issue reports:
- Failed job logs (full text),
- JUnit XML reports,
- Neo4j diagnostics artifact for integration failures,
- Commit SHA + workflow run URL,
- Whether rerun passed/failed.

## 5) Ownership and SLA for CI maintenance

| Area | Primary owner | Backup owner | SLA |
|---|---|---|---|
| CI workflow health (`ci.yml`) | Platform Engineering | Backend team lead | **P1 (blocking default branch merges):** acknowledge in 30 min, mitigation in 4 hours, fix in 1 business day. |
| Security scanning (`security.yml`) | Security Engineering | Platform Engineering | **P1:** acknowledge in 1 hour, mitigation in 8 hours, fix in 2 business days. |
| Release pipeline (`release.yml`, post-release verify) | Release owner on duty | Platform Engineering | **P0 on release day:** acknowledge in 15 min, mitigation/rollback decision in 60 min. |
| Container build/publish (`container.yml`) | Platform Engineering | Release owner | **P1:** acknowledge in 1 hour, mitigation in 8 hours. |

### Escalation policy

- If required checks are red for more than **2 hours** on `main`, escalate to Platform Engineering manager.
- If release verification fails on a production tag, escalate immediately and follow rollback guidance in `docs/engineering/release.md`.
