# JDeploy

[![CI](https://img.shields.io/badge/CI-Primary%20workflow-2ea44f?logo=githubactions&logoColor=white)](.github/workflows/ci.yml)
[![Security Scans](https://img.shields.io/badge/Security-Scans-0969da?logo=githubactions&logoColor=white)](.github/workflows/security.yml)
[![Container](https://img.shields.io/badge/Container-Build-8250df?logo=docker&logoColor=white)](.github/workflows/container.yml)
[![Release](https://img.shields.io/badge/Release-Pipeline-f0883e?logo=githubactions&logoColor=white)](.github/workflows/release.yml)

JDeploy is a Spring Boot backend API (with Vaadin UI module in this repository) backed by Neo4j.

## Vaadin UI

The `vaadin-ui` module is a dedicated Spring Boot Vaadin application. It is **standalone from the backend runtime** and communicates with `backend-api` over HTTP; it is not embedded inside the backend process.

### Supported deployment topology

- **Supported**: backend and UI as separate processes/containers (same host or different hosts).
- **Supported**: backend only, without running UI.
- **Not supported/documented for production**: running Vaadin UI embedded in `backend-api` as a single Spring Boot process.

### Build and run

Build the UI module (and required reactor dependencies):

```bash
mvn -pl vaadin-ui -am clean package
```

Run the UI directly from Maven:

```bash
mvn -pl vaadin-ui spring-boot:run
```

Run packaged UI jar:

```bash
java -jar vaadin-ui/target/vaadin-ui-0.0.2.jar
```

By default, the UI is served on `http://localhost:8081` and uses Spring Security login.

### Backend dependency and UI auth credentials

The UI requires a reachable backend API (default `http://localhost:8080`) with users configured in `backend-api`.

Backend users and effective roles for UI operations are configured through environment variables/secrets.

- `JDEPLOY_READER_USER` / `JDEPLOY_READER_PASSWORD` → read-only topology browsing.
- `JDEPLOY_GENERATOR_USER` / `JDEPLOY_GENERATOR_PASSWORD` → diagram generation endpoints.
- `JDEPLOY_INGEST_USER` / `JDEPLOY_INGEST_PASSWORD` → manifest ingestion endpoints.

Use the configured backend credentials on the UI login screen; required permissions depend on features you access.

### Topology detail endpoint access contract

UI topology detail views depend on these backend routes being readable by all standard read-capable API roles (`READ_ONLY`, `EDITOR`, `ADMIN`):

- `GET /api/topology/systems/{name}`
- `GET /api/topology/nodes/{hostname}`
- `GET /api/topology/subnets/{cidr}`
- `GET /api/topology/environments/{name}`

Unauthenticated callers must receive `401 Unauthorized` for these routes. This contract is enforced by both URL security rules and controller method-level `@PreAuthorize` annotations.

### UI configuration (`jdeploy.backend.*`)

`vaadin-ui` uses `jdeploy.backend.*` properties to locate and authenticate to `backend-api`.

Required/important properties:

- `jdeploy.backend.base-url` (env: `JDEPLOY_BACKEND_BASE_URL`) — backend API URL (default `http://localhost:8080`).
- `jdeploy.backend.auth.mode` (env: `JDEPLOY_BACKEND_AUTH_MODE`) — one of:
  - `BASIC` (default): UI uses configured service credentials for every outbound backend call.
  - `PROPAGATE`: UI forwards auth/session from the current logged-in request context.
  - `NONE`: no outbound auth headers (useful only for local unsecured backends/tests).
- `jdeploy.backend.auth.basic.username` (env: `JDEPLOY_BACKEND_AUTH_USERNAME`) — required when mode is `BASIC`.
- `jdeploy.backend.auth.basic.password` (env: `JDEPLOY_BACKEND_AUTH_PASSWORD`) — required when mode is `BASIC`.
- `jdeploy.backend.auth.propagation.source` (env: `JDEPLOY_BACKEND_AUTH_PROPAGATION_SOURCE`) — required when mode is `PROPAGATE`; one of:
  - `REQUEST_AUTHORIZATION_HEADER` (default)
  - `REQUEST_SESSION_COOKIE`
  - `SECURITY_CONTEXT`

Startup fail-fast behavior:

- If `jdeploy.backend.auth.mode=BASIC` and username/password are missing or blank, `vaadin-ui` fails startup.

Example (service-account style basic auth):

```bash
export JDEPLOY_BACKEND_BASE_URL=http://localhost:8080
export JDEPLOY_BACKEND_AUTH_MODE=BASIC
export JDEPLOY_BACKEND_AUTH_USERNAME=$JDEPLOY_READER_USER
export JDEPLOY_BACKEND_AUTH_PASSWORD=$JDEPLOY_READER_PASSWORD
mvn -pl vaadin-ui spring-boot:run
```

Or via command-line property:

```bash
mvn -pl vaadin-ui spring-boot:run \
  -Dspring-boot.run.arguments="--jdeploy.backend.base-url=http://localhost:9090 --jdeploy.backend.auth.mode=BASIC --jdeploy.backend.auth.basic.username=${JDEPLOY_READER_USER} --jdeploy.backend.auth.basic.password=${JDEPLOY_READER_PASSWORD}"
```

Local dev example with split ports (backend on `8080`, UI on `8081`):

```bash
mvn -pl backend-api spring-boot:run
mvn -pl vaadin-ui spring-boot:run
```

Local dev example with backend on a custom port (`9090`) and UI on default port:

```bash
mvn -pl backend-api spring-boot:run -Dspring-boot.run.arguments=--server.port=9090
mvn -pl vaadin-ui spring-boot:run -Dspring-boot.run.arguments=--jdeploy.backend.base-url=http://localhost:9090
```


## Build executable package

Build the backend runnable JAR:

```bash
mvn -pl backend-api -am clean package
```

The executable artifact is generated at:

- `backend-api/target/backend-api-0.0.2.jar`

Run it locally:

```bash
java -jar backend-api/target/backend-api-0.0.2.jar
```

## Containerized startup (Docker Compose)

The provided `docker-compose.yml` starts both dependencies:

- `neo4j` (`7474`, `7687`)
- `backend-api` (`8080`)

Start everything:

```bash
docker compose up --build -d
```

> [!IMPORTANT]
> Use Docker Compose **v2** (`docker compose ...`) instead of the legacy Python-based
> `docker-compose` v1 binary. Some Docker Engine versions can fail with a traceback like
> `KeyError: 'ContainerConfig'` when recreating containers via `docker-compose` v1.
>
> If you hit this error, switch to `docker compose` and remove stale containers before
> retrying:
>
> ```bash
> docker compose down --remove-orphans
> docker rm -f jdeploy-backend-api jdeploy-neo4j 2>/dev/null || true
> docker compose up --build -d
> ```

Stop:

```bash
docker compose down
```

API endpoints:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/openapi.json`
- Neo4j Browser: `http://localhost:7474`

## Kubernetes startup

A single manifest is provided at `k8s/backend-api.yaml` that includes:

- `Secret` for application credentials
- Neo4j `Deployment` + `Service`
- Backend API `Deployment` + `Service`

Apply it:

```bash
kubectl apply -f k8s/backend-api.yaml
```

Port-forward backend:

```bash
kubectl port-forward service/jdeploy-backend-api 8080:8080
```

## Environment configuration

The backend reads environment variables from `backend-api/src/main/resources/application.yml`.

| Variable | Default | Purpose |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` (compose) | Spring profile selection (`dev`, `prod`) |
| `NEO4J_URI` | `bolt://localhost:7687` | Neo4j Bolt URI |
| `NEO4J_USERNAME` | `neo4j` | Neo4j username |
| `NEO4J_PASSWORD` | `changeit` | Neo4j password |
| `JDEPLOY_INGEST_USER` | _required_ | Ingestion role username |
| `JDEPLOY_INGEST_PASSWORD` | _required secret_ | Ingestion role password |
| `JDEPLOY_GENERATOR_USER` | _required_ | Diagram generation role username |
| `JDEPLOY_GENERATOR_PASSWORD` | _required secret_ | Diagram generation role password |
| `JDEPLOY_READER_USER` | _required_ | Read-only role username |
| `JDEPLOY_READER_PASSWORD` | _required secret_ | Read-only role password |
| `JDEPLOY_UML_OUTPUT_PATH` | profile-specific path | PlantUML output directory |
| `JDEPLOY_QUALITY_REPORTING_ENABLED` | `true` | Enables quality reporting scheduler |
| `JDEPLOY_QUALITY_REPORTING_CRON` | `0 */15 * * * *` | Scheduler cron |


### Required backend auth credentials

The backend now fails fast at startup when any `JDEPLOY_*` API credential is missing/blank. Password policy checks are enabled by default and require:

- minimum length: `12` characters (`JDEPLOY_SECURITY_PASSWORD_POLICY_MIN_LENGTH` / `jdeploy.security.password-policy.min-length`)
- at least `3` character classes among lowercase, uppercase, digits, symbols (`JDEPLOY_SECURITY_PASSWORD_POLICY_MIN_CHARACTER_CLASSES` / `jdeploy.security.password-policy.min-character-classes`)

Disable enforcement only if necessary with `JDEPLOY_SECURITY_PASSWORD_POLICY_ENFORCE=false` (`jdeploy.security.password-policy.enforce=false`).

Use `.env.example` as a template for required Compose variables.

## Notes

- The backend container image is built via multi-stage Docker build in `backend-api/Dockerfile`.
- For production, provide all required credentials through environment variables or secret managers (no in-app credential fallbacks).

## Branch protection policy (`main`)

Configure repository rules for `main` with the following settings:

1. **Require pull requests before merging** (disallow direct pushes).
2. **Require status checks to pass before merging** and include all of:
   - `quality`
   - `integration-tests`
   - `build`
   - `security`
3. **Require branches to be up to date before merging**.
4. **Require at least 1 approval** and **dismiss stale pull request approvals when new commits are pushed**.
5. Optional but recommended:
   - **Require signed commits**
   - **Require linear history**

## CLI usage (backend module)

The backend now exposes a Picocli-based CLI surface that runs in-process against the existing service layer (no HTTP controller calls).

### Start CLI mode

Use the `cli` profile and enable CLI execution:

```bash
java -jar backend-api/target/backend-api-0.0.2.jar \
  --spring.profiles.active=cli \
  --jdeploy.cli.enabled=true \
  ingest-manifest --file backend-api/src/test/resources/manifests/heterogeneous-topology.yaml
```

### Commands

#### `ingest-manifest --file <path>`

Ingests a YAML manifest via `ManifestIngestionService`.

```bash
java -jar backend-api/target/backend-api-0.0.2.jar \
  --spring.profiles.active=cli --jdeploy.cli.enabled=true \
  ingest-manifest --file ./manifest.yml
```

#### `deployments-by-subnet --subnet <id-or-cidr> [--format TEXT|JSON]`

Queries deployments mapped to nodes in the subnet via `TopologyQueryService`.

```bash
java -jar backend-api/target/backend-api-0.0.2.jar \
  --spring.profiles.active=cli --jdeploy.cli.enabled=true \
  deployments-by-subnet --subnet 10.0.1.0/24 --format JSON
```

#### `impact-by-node --node <id-or-hostname> [--format TEXT|JSON]`

Queries dependency impact records for the node via `TopologyQueryService`.

```bash
java -jar backend-api/target/backend-api-0.0.2.jar \
  --spring.profiles.active=cli --jdeploy.cli.enabled=true \
  impact-by-node --node app-node-01 --format TEXT
```

#### `generate-diagram --system <id> --output <path>`

Generates a PlantUML system deployment diagram and writes it to disk.

```bash
java -jar backend-api/target/backend-api-0.0.2.jar \
  --spring.profiles.active=cli --jdeploy.cli.enabled=true \
  generate-diagram --system billing --output ./artifacts/billing.puml
```

### CLI authentication strategy

CLI mode supports two auth modes:

- `trusted` (default for `cli` profile): local trusted execution for operators on secured hosts.
- `service-account`: requires CLI credentials on commands that mutate state (currently `ingest-manifest`).

Configuration:

- `JDEPLOY_CLI_AUTH_MODE` = `trusted` or `service-account`
- `JDEPLOY_CLI_AUTH_USER`
- `JDEPLOY_CLI_AUTH_PASSWORD`

Example service-account execution:

```bash
export JDEPLOY_CLI_AUTH_MODE=service-account
export JDEPLOY_CLI_AUTH_USER=cli-service
export JDEPLOY_CLI_AUTH_PASSWORD=change-me

java -jar backend-api/target/backend-api-0.0.2.jar \
  --spring.profiles.active=cli --jdeploy.cli.enabled=true \
  ingest-manifest --file ./manifest.yml --auth-user cli-service --auth-password change-me
```

### Error cases

- Missing required args, e.g. running `ingest-manifest` without `--file`, exits with argument validation errors.
- Invalid `--format` values fail command parsing.
- In `service-account` mode, missing or invalid `--auth-user/--auth-password` fails with authentication error.
- `generate-diagram` fails when `--output` points to an unwritable location.
