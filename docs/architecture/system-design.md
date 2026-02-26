# JDeploy System Design Document

This document summarizes a static analysis of the JDeploy codebase and now includes controller/service-level execution models.

## 1) High-Level Architecture

JDeploy uses a **modular monolith backend plus a separate UI service**:

- **`backend-api`**: Spring Boot service exposing REST APIs, service-layer business logic, CLI commands, and operational endpoints.
- **`vaadin-ui`**: separate Spring Boot + Vaadin web app that calls backend REST APIs over HTTP.
- **Neo4j**: primary graph database for topology and deployment relationships.
- **Filesystem artifact storage**: backend persists generated PlantUML artifacts as files with retention metadata.

## 2) Component Breakdown

### Frontend (Vaadin UI)

- Route-driven server-rendered UI for dashboard, ingest, explorer, and diagrams.
- `RestClient` API clients call backend endpoints.
- Supports outbound auth modes (`BASIC`, `PROPAGATE`, `NONE`).

### Backend API

- **Controllers**: `ManifestController`, `TopologyController`, `TopologyQueryController`, `ArtifactController`.
- **Services**: `ManifestParserService`, `ManifestContractValidator`, `ManifestIngestionService`, `TopologyQueryService`, `TopologyMutationService`, `DiagramGenerationService`, `GraphInvariantValidator`, `GraphQualityGateService`, `DeploymentMappingService`, `ArtifactRetentionCleanupService`, `OperationMetricsService`.
- **Security**: HTTP Basic + method-level authorization.

### Database & Artifacts

- **Neo4j graph** nodes include systems/components/deployments/environments/subnets/nodes/clusters/network links.
- **Local artifact storage** keeps generated `.puml` plus `.retention` sidecar timestamps.

## 3) Data Flow and Interactions

### Manifest Ingestion
1. Client submits YAML.
2. Parse into `DeploymentManifestDto`.
3. Validate contract constraints.
4. Synchronize graph via ordered upsert + prune operations.
5. Record metrics and observations.

### Query and Diagram
1. UI/API queries topology or system diagram view.
2. Backend executes Cypher projections.
3. Optionally generate PlantUML and persist artifact.

## 4) Technology Stack and Rationale

- Java 21, Spring Boot, Spring Data Neo4j + Neo4jClient
- Spring Security, Spring Actuator, Micrometer + Prometheus
- Jackson YAML, Picocli CLI, PlantUML
- Vaadin for operator-facing server-side UI

## 5) Key Design Decisions and Trade-offs

1. **Graph-first model**: great for topology traversals; requires Cypher competency.
2. **Separate backend/UI processes**: independent deployment; more operational complexity.
3. **Cypher-heavy services**: high control and explicitness; less abstraction than repository-only patterns.
4. **In-memory role users from env**: simple bootstrap; weaker enterprise IAM integration.
5. **Filesystem artifact storage**: low complexity; not ideal for multi-instance HA.

## 6) Scalability, Security, Deployment Considerations

### Scalability
- Backend is largely stateless, but scheduled jobs and local artifact files need multi-replica coordination.
- Neo4j capacity and indexing are primary throughput constraints.

### Security
- Role-based endpoint and method authorization is enforced.
- Recommended future hardening: centralized identity/OIDC, managed secret rotation, careful auth-propagation usage.

### Deployment
- Local (Maven / Docker Compose), containerized backend image, Kubernetes manifests for backend+Neo4j+secrets.

---

## 7) Backend API Controller Design (Sequence + Pseudocode)

> Notes:
> - Each controller section shows a representative sequence for its primary workflow.
> - Exact endpoints vary by method; pseudocode summarizes core control flow.

### 7.1 `ManifestController`

#### Sequence (manifest ingest)
```mermaid
sequenceDiagram
    participant C as Client
    participant MC as ManifestController
    participant MP as ManifestIngestionService(parse)
    participant MV as ManifestContractValidator
    participant MI as ManifestIngestionService(sync)

    C->>MC: POST /api/manifests/ingest (yaml)
    MC->>MP: parseManifest(yaml)
    MP-->>MC: DeploymentManifestDto
    MC->>MV: validateForIngestion(dto)
    MV-->>MC: ok
    MC->>MI: synchronize(dto)
    MI-->>MC: done
    MC-->>C: 200 INGESTED
```

#### Pseudocode
```text
ingest(yaml):
  manifest = ingestionService.parseManifest(yaml)
  contractValidator.validateForIngestion(manifest)
  ingestionService.synchronize(manifest)
  return OperationResult("INGESTED", ...)

qualityGateManifest(yaml):
  manifest = parse
  validate contract
  return OperationResult("PASSED", ...)

qualityGateDeploymentTargets(yaml):
  manifest = parse
  validate contract
  return OperationResult("PASSED", ...)

qualityGateGraphSnapshot():
  snapshot = graphQualityGateService.latestReport()
  return snapshot
```

### 7.2 `TopologyController`

#### Sequence (update node)
```mermaid
sequenceDiagram
    participant C as Client
    participant TC as TopologyController
    participant TM as TopologyMutationService
    participant N as Neo4j

    C->>TC: PUT /api/topology/nodes/{hostname}
    TC->>TM: updateHardwareNode(current, request)
    TM->>N: ensure exists + uniqueness checks
    TM->>N: SET node fields
    TM-->>TC: void
    TC-->>C: 200/204
```

#### Pseudocode
```text
systems()/hardwareNodes()/subnets()/environments():
  run Cypher projection via Neo4jClient
  map rows to response records

system(name)/node(hostname)/subnet(cidr)/environment(name):
  query by identifier
  if missing -> 404
  map to update DTO

update*/patch* endpoints:
  delegate to mutationService with path id + validated request
```

### 7.3 `TopologyQueryController`

#### Sequence (impact by node)
```mermaid
sequenceDiagram
    participant C as Client
    participant TQC as TopologyQueryController
    participant TQS as TopologyQueryService
    participant N as Neo4j

    C->>TQC: GET /api/impact/node/{nodeId}
    TQC->>TQS: impactByNode(nodeId)
    TQS->>N: MATCH graph impact pattern
    N-->>TQS: rows
    TQS-->>TQC: ImpactView list
    TQC-->>C: 200 JSON
```

#### Pseudocode
```text
deploymentsBySubnet(subnetId):
  return topologyQueryService.deploymentsBySubnet(subnetId)

deploymentsInSubnet/deploymentsInSubnetQuery:
  reuse deploymentsBySubnet()

impactByNode(nodeId):
  return topologyQueryService.impactByNode(nodeId)

nodesInCluster / nodesInClusterAndSubnet:
  run direct Cypher query with Neo4jClient

systemDiagram(systemId):
  view = topologyQueryService.systemDiagram(systemId)
  return transformed view

systemsImpactedByNodeFailure(nodeId):
  run direct Cypher
  map impacted components by system
```

### 7.4 `ArtifactController`

#### Sequence (generate artifact)
```mermaid
sequenceDiagram
    participant C as Client
    participant AC as ArtifactController
    participant MP as ManifestIngestionService(parse)
    participant MV as ManifestContractValidator
    participant DG as DiagramGenerationService
    participant AS as ArtifactStorage

    C->>AC: POST /api/artifacts/generate (yaml)
    AC->>MP: parseManifest(yaml)
    AC->>MV: validateForIngestion(manifest)
    AC->>DG: generateDeploymentDiagram(manifest)
    DG->>AS: create(artifactId, puml, retention)
    AS-->>DG: ArtifactMetadata
    DG-->>AC: ArtifactMetadata
    AC-->>C: 200 metadata
```

#### Pseudocode
```text
generate(yaml):
  manifest = ingestionService.parseManifest(yaml)
  contractValidator.validateForIngestion(manifest)
  return diagramGenerationService.generateDeploymentDiagram(manifest)

download(artifactId):
  try artifactStorage.read(artifactId)
  map not found -> 404
  map expired -> 410
  return bytes with content-disposition
```

---

## 8) Backend Service Design (Sequence + Pseudocode)

### 8.1 `ManifestParserService`

#### Sequence
```mermaid
sequenceDiagram
    participant Caller
    participant MPS as ManifestParserService
    participant YAML as JacksonYAML
    participant MET as MeterRegistry/Counter

    Caller->>MPS: parseManifest(yamlText)
    MPS->>YAML: readValue(..., DeploymentManifestDto)
    YAML-->>MPS: DTO or parse error
    alt parse error
      MPS->>MET: increment ingestion error counter
      MPS-->>Caller: IllegalArgumentException
    else success
      MPS-->>Caller: DeploymentManifestDto
    end
```

#### Pseudocode
```text
parseManifest(yamlText):
  require non-null/non-blank
  try parse with yamlMapper
  if null -> postcondition error
  return dto
  catch parse exception -> increment counter; throw argument error

parseManifest(path):
  read file text
  delegate parseManifest(text)
```

### 8.2 `ManifestContractValidator`

#### Sequence
```mermaid
sequenceDiagram
    participant Caller
    participant MCV as ManifestContractValidator

    Caller->>MCV: validateForIngestion(manifest)
    MCV-->>Caller: ok or PreconditionViolationException
```

#### Pseudocode
```text
validateForIngestion(manifest):
  require manifest and at least one system
  assert unique environments
  collect hosts/ips from subnets and ensure uniqueness
  validate cluster names + referenced nodes + unique namespaces
  validate systems/components uniqueness and ownership
  validate each deployment references existing env/host/cluster/namespace rules
  validate network links reference existing hosts
```

### 8.3 `ManifestIngestionService`

#### Sequence
```mermaid
sequenceDiagram
    participant Caller
    participant MIS as ManifestIngestionService
    participant N as Neo4j
    participant OMS as OperationMetricsService

    Caller->>MIS: synchronize(manifest)
    MIS->>OMS: recordIngestionRequest
    MIS->>N: backfill implicit semantics
    MIS->>N: upsert environments
    MIS->>N: upsert subnets/nodes
    MIS->>N: upsert clusters/namespaces
    MIS->>N: upsert systems/components/deployments
    MIS->>N: upsert network links
    MIS->>N: prune obsolete artifacts
    MIS->>OMS: recordIngestionSuccess
    MIS-->>Caller: done
```

#### Pseudocode
```text
parseManifest(yaml|path):
  delegate to parserService
  ensure non-null result

synchronize(manifest):
  require manifest
  record request metric
  observe synchronizeManifest(manifest)
  on success -> record success metric
  on runtime error -> record error metric and rethrow

synchronizeManifest(manifest):
  backfillImplicitClusterSemantics()
  upsertEnvironments()
  upsertSubnetsAndNodes()
  upsertClusters()
  upsertSystemsComponentsAndDeployments()
  upsertNetworkLinks()
  pruneObsoleteArtifacts()
```

### 8.4 `TopologyQueryService`

#### Sequence
```mermaid
sequenceDiagram
    participant Caller
    participant TQS as TopologyQueryService
    participant N as Neo4j

    Caller->>TQS: deploymentsBySubnet(subnetId)
    TQS->>N: MATCH subnet->node<-deployment
    N-->>TQS: rows
    TQS-->>Caller: DeploymentView list
```

#### Pseudocode
```text
deploymentsBySubnet(subnetId):
  require subnetId
  run Cypher and map hostname/deploymentKey

impactByNode(nodeId):
  require nodeId
  run Cypher for component/deployment/peer node/cluster impact
  map collection fields to string lists

systemDiagram(systemId):
  require systemId
  query components for system
  query distinct target nodes
  return SystemDiagramView(systemId, components, nodes)
```

### 8.5 `TopologyMutationService`

#### Sequence
```mermaid
sequenceDiagram
    participant Caller
    participant TMS as TopologyMutationService
    participant N as Neo4j

    Caller->>TMS: updateX(...)
    TMS->>N: ensureExists()
    TMS->>N: ensureUnique()
    TMS->>N: apply mutation Cypher
    TMS-->>Caller: void or conflict/not-found error
```

#### Pseudocode
```text
updateSoftwareSystem(existingName, req):
  ensure system exists
  ensure target name unique
  mutate name

updateSoftwareComponent(currentName, currentVersion, req):
  ensure exists
  ensure name/version combination unique
  set new name/version

updateHardwareNode / updateSubnet / updateExecutionEnvironment:
  ensure exists
  ensure uniqueness constraints
  set fields

updateDeploymentInstance(currentKey, req):
  ensure deployment, env, node exist
  derive component identity from relationship or key parse
  compute new canonical key env@host:component:version
  ensure key unique
  replace target relationships + set new key
```

### 8.6 `DiagramGenerationService`

#### Sequence
```mermaid
sequenceDiagram
    participant Caller
    participant DGS as DiagramGenerationService
    participant AS as ArtifactStorage
    participant OMS as OperationMetricsService

    Caller->>DGS: generateDeploymentDiagram(manifest)
    DGS->>DGS: buildPlantUml(manifest)
    DGS->>AS: create(fileName, content, 7d)
    DGS->>OMS: recordArtifactGenerationSuccess
    DGS-->>Caller: ArtifactMetadata
```

#### Pseudocode
```text
generateDeploymentDiagram(manifest):
  require manifest
  observe:
    puml = buildPlantUml(manifest)
    artifactId = "deployment-topology-<epoch>.puml"
    metadata = artifactStorage.create(artifactId, puml, 7 days)
    ensure metadata non-null
    record success metric
    return metadata
  catch runtime:
    record error metric
    rethrow

buildPlantUml(manifest):
  emit @startuml headers
  render subnets and nodes (optionally grouped by cluster)
  render systems/components/artifacts/deployment arrows
  render network links and legend
  emit @enduml

buildSystemPlantUml(systemId):
  query system diagram context
  render system->components and target node cloud
```

### 8.7 `GraphInvariantValidator`

#### Sequence
```mermaid
sequenceDiagram
    participant Caller
    participant GIV as GraphInvariantValidator

    Caller->>GIV: validateDeploymentTarget(deployment)
    GIV-->>Caller: ok or invariant/precondition error
```

#### Pseudocode
```text
validateDeploymentTarget(deployment):
  require deployment
  require target environment and target node present
  require non-blank deployment key

validateSubnetMembership(subnet, node):
  require subnet and node
  ensure node hostname exists in subnet node set

requireClusterNodeRole(node):
  require node
  ensure node roles contains grid or kubernetes
```

### 8.8 `GraphQualityGateService`

#### Sequence
```mermaid
sequenceDiagram
    participant Scheduler
    participant GQS as GraphQualityGateService
    participant N as Neo4j

    Scheduler->>GQS: runScheduledQualityReport()
    GQS->>N: query orphan deployments
    GQS->>N: query nodes without subnet
    GQS->>N: query duplicate hostnames/IPs
    GQS->>N: query software missing env target
    GQS-->>Scheduler: snapshot cached + logs emitted
```

#### Pseudocode
```text
evaluateGraph():
  query findings lists for:
    orphanDeployments
    nodesWithoutSubnet
    duplicateHostnames
    duplicateIps
    softwareLinkedToMissingEnvironment
  return QualityGateReport(map)

runScheduledQualityReport():
  if reporting disabled -> return
  snapshot = evaluateAndStore()
  log pass or warn with finding counts

latestReport():
  return cached snapshot or evaluateAndStore()
```

### 8.9 `DeploymentMappingService`

#### Sequence
```mermaid
sequenceDiagram
    participant Caller
    participant DMS as DeploymentMappingService
    participant GIV as GraphInvariantValidator

    Caller->>DMS: mapToTarget(environment, node)
    DMS->>DMS: create DeploymentInstance(environment,node,null)
    DMS->>GIV: validateDeploymentTarget(instance)
    DMS-->>Caller: DeploymentInstance
```

#### Pseudocode
```text
mapToTarget(environment, node):
  require environment and node
  instance = new DeploymentInstance(environment, node, null)
  graphInvariantValidator.validateDeploymentTarget(instance)
  assert instance targets exactly provided inputs
  return instance
```

### 8.10 `ArtifactRetentionCleanupService`

#### Sequence
```mermaid
sequenceDiagram
    participant Scheduler
    participant ARCS as ArtifactRetentionCleanupService
    participant AS as ArtifactStorage

    Scheduler->>ARCS: cleanupExpiredArtifacts()
    ARCS->>AS: expireOlderThan(retentionGracePeriod)
    AS-->>ARCS: deleted artifact ids
    ARCS-->>Scheduler: logs cleanup summary
```

#### Pseudocode
```text
cleanupExpiredArtifacts():
  deleted = artifactStorage.expireOlderThan(retentionGracePeriod)
  if deleted not empty -> log deleted list
```

### 8.11 `OperationMetricsService`

#### Sequence
```mermaid
sequenceDiagram
    participant Caller
    participant OMS as OperationMetricsService
    participant MR as MeterRegistry

    Caller->>OMS: recordIngestionSuccess()
    OMS->>MR: increment counter
    OMS->>OMS: update last success epoch gauge backing value
```

#### Pseudocode
```text
constructor(meterRegistry):
  register counters for ingestion/artifact success+error
  register gauges for last success timestamps

recordIngestionRequest/Success/Error():
  increment relevant counter
  update timestamp for success

recordArtifactGenerationSuccess/Error():
  increment relevant counter
  update timestamp for success

snapshot():
  return map of current counters and timestamp values
```

---

## 9) Global Diagram Suggestions

### Runtime Topology

```mermaid
flowchart LR
    User[User Browser] --> UI[vaadin-ui :8081]
    UI -->|HTTP REST + Auth| API[backend-api :8080]
    API -->|Bolt| NEO4J[(Neo4j :7687)]
    API --> FS[(Artifact Filesystem)]
```

### Backend Layering

```mermaid
flowchart TD
    C[REST Controllers] --> S[Services]
    S --> Q[Neo4jClient/Cypher]
    S --> R[Neo4j Repositories]
    Q --> DB[(Neo4j)]
    S --> A[ArtifactStorage]
    A --> FS[(Local Filesystem)]
    S --> M[Metrics/Observability]
```
