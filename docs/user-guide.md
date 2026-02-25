# JDeploy User Guide

This guide covers installation, local runtime, and daily usage for both systems in this repository:

- `backend-api` (Spring Boot + Neo4j REST API)
- `vaadin-ui` (Vaadin Flow UI)

It also documents the YAML ingestion format used by API, CLI, and UI ingestion/generation features.

## 1) Prerequisites

Install the following tools:

- Java 21
- Maven 3.9+
- Docker + Docker Compose (recommended for local Neo4j)
- Optional: `curl` for API examples

## 2) Repository build

From repository root:

```bash
mvn clean package
```

Or build only the backend executable:

```bash
mvn -pl backend-api -am clean package
```

Expected artifact:

- `backend-api/target/backend-api-0.0.1-SNAPSHOT.jar`

## 3) Run backend-api

### Option A: Docker Compose (recommended)

Start Neo4j + backend:

```bash
docker compose up --build -d
```

Stop:

```bash
docker compose down
```

Default endpoints:

- Backend API: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/openapi.json`
- Neo4j Browser: `http://localhost:7474`

### Option B: Run backend JAR locally

1. Start Neo4j separately (local or container).
2. Run backend:

```bash
java -jar backend-api/target/backend-api-0.0.1-SNAPSHOT.jar
```

### Core backend environment variables

| Variable | Default | Description |
|---|---|---|
| `NEO4J_URI` | `bolt://localhost:7687` | Neo4j Bolt URL |
| `NEO4J_USERNAME` | `neo4j` | Neo4j username |
| `NEO4J_PASSWORD` | `changeit` | Neo4j password |
| `JDEPLOY_UML_OUTPUT_PATH` | `examples/artifacts/uml` | Generated `.puml` output directory |
| `JDEPLOY_INGEST_USER` / `JDEPLOY_INGEST_PASSWORD` | `ingest` / `ingest-password` | Ingest + admin user |
| `JDEPLOY_GENERATOR_USER` / `JDEPLOY_GENERATOR_PASSWORD` | `generator` / `generator-password` | Diagram + editor user |
| `JDEPLOY_READER_USER` / `JDEPLOY_READER_PASSWORD` | `reader` / `reader-password` | Read-only user |

## 4) Run vaadin-ui

The UI module is launched with Spring Boot and uses backend classes on the same classpath.

### Start UI application

```bash
mvn -pl vaadin-ui -am \
  -Dspring-boot.run.main-class=com.jdeploy.JDeployApplication \
  spring-boot:run
```

The UI is available at:

- `http://localhost:8080/login`

> By default, UI API clients point to `http://localhost:8080` (`jdeploy.backend.base-url`), so backend and UI are expected on the same host/port in this run mode.

### UI login and authorization

Use one of the configured in-memory users:

- Ingest/admin: `${JDEPLOY_INGEST_USER}`
- Generator/editor: `${JDEPLOY_GENERATOR_USER}`
- Reader: `${JDEPLOY_READER_USER}`

Available views are role-aware in navigation:

- Topology Dashboard
- Manifest Ingest
- Infrastructure Explorer
- Diagram

## 5) API quick start

### Ingest manifest

```bash
curl -u ingest:ingest-password \
  -H 'Content-Type: application/x-yaml' \
  --data-binary @backend-api/src/test/resources/manifests/heterogeneous-topology.yaml \
  http://localhost:8080/api/manifests/ingest
```

### Validate quality gates (manifest contract)

```bash
curl -u reader:reader-password \
  -H 'Content-Type: application/x-yaml' \
  --data-binary @backend-api/src/test/resources/manifests/heterogeneous-topology.yaml \
  http://localhost:8080/api/quality-gates/manifest
```

### Generate PlantUML artifact from manifest

```bash
curl -u generator:generator-password \
  -H 'Content-Type: application/x-yaml' \
  --data-binary @backend-api/src/test/resources/manifests/heterogeneous-topology.yaml \
  http://localhost:8080/api/artifacts/generate
```

## 6) YAML ingestion format

The ingestion payload is YAML with this top-level structure:

- `subnets`: list of subnet blocks with embedded nodes
- `clusters`: optional list of grid/kubernetes clusters
- `environments`: runtime environments used by deployments
- `systems`: software systems, components, and deployments
- `links`: network links between hosts

### Full schema (field-level)

```yaml
subnets:
  - cidr: "10.10.0.0/24"                # required, subnet identifier
    vlan: "110"                         # required
    routingZone: "prod-core"            # required
    nodes:
      - hostname: "k8s-cp-01"           # required, unique globally
        ipAddress: "10.10.0.10"         # required, unique globally
        type: "KUBERNETES_CONTROL_PLANE" # required
        roles: ["kubernetes", "control-plane"]

clusters:                                  # optional
  - name: "prod-k8s"                     # required, unique globally
    type: "KUBERNETES"                   # required (e.g., GRID / KUBERNETES)
    nodes: ["k8s-cp-01", "k8s-worker-01"]
    namespaces: ["billing", "payments"] # optional, unique globally

environments:
  - name: "prod"                         # required, unique globally
    type: "PRODUCTION"                   # required

systems:
  - name: "Billing"                      # required, unique globally
    components:
      - name: "billing-api"              # required
        version: "2.1.0"                 # required
        deployments:
          - environment: "prod"          # required, must exist in environments
            hostname: "k8s-worker-01"    # required, must exist in subnets.nodes
            cluster: "prod-k8s"          # optional, must exist in clusters
            namespace: "billing"         # optional; if present, cluster is required and namespace must exist

links:
  - fromHostname: "k8s-worker-01"        # required, host must exist
    toHostname: "db-vm-01"               # required, host must exist
    bandwidthMbps: 1000                    # integer
    latencyMs: 3                           # integer
```

### Contract checks enforced at ingestion

Manifest validation rejects payloads with conditions such as:

- empty/missing `systems`
- duplicate environment names
- duplicate hostnames or IP addresses
- unknown cluster members
- duplicate cluster names
- duplicate namespaces
- deployment target environment/host/cluster/namespace not found
- namespace used without a cluster
- network links referencing unknown hosts
- same component (`name:version`) owned by multiple systems

### Working examples

Use these repository examples as templates:

- `backend-api/src/test/resources/manifests/heterogeneous-topology.yaml`
- `backend-api/src/test/resources/manifests/edge-regional-topology.yaml`

## 7) CLI usage (optional)

Run backend in CLI profile:

```bash
java -jar backend-api/target/backend-api-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=cli \
  --jdeploy.cli.enabled=true \
  ingest-manifest --file backend-api/src/test/resources/manifests/heterogeneous-topology.yaml
```

Other commands:

- `deployments-by-subnet --subnet <id-or-cidr> --format TEXT|JSON`
- `impact-by-node --node <id-or-hostname> --format TEXT|JSON`
- `generate-diagram --system <id> --output <path>`

## 8) Troubleshooting

- `401/403` on API/UI actions: verify you are using a user with the required authority.
- Neo4j connectivity errors: verify `NEO4J_URI`, username, and password.
- Empty topology queries: ingest a manifest first.
- Artifact generation errors: validate manifest contract and ensure `JDEPLOY_UML_OUTPUT_PATH` is writable.
