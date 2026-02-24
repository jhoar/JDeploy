# JDeploy

JDeploy is a Spring Boot backend API (with Vaadin UI module in this repository) backed by Neo4j.

## Build executable package

Build the backend runnable JAR:

```bash
mvn -pl backend-api -am clean package
```

The executable artifact is generated at:

- `backend-api/target/backend-api-0.0.1-SNAPSHOT.jar`

Run it locally:

```bash
java -jar backend-api/target/backend-api-0.0.1-SNAPSHOT.jar
```

## Containerized startup (Docker Compose)

The provided `docker-compose.yml` starts both dependencies:

- `neo4j` (`7474`, `7687`)
- `backend-api` (`8080`)

Start everything:

```bash
docker compose up --build -d
```

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
| `SPRING_SECURITY_DEFAULT_USER` | `admin` | HTTP basic admin username |
| `SPRING_SECURITY_DEFAULT_PASSWORD` | `admin-change-me` | HTTP basic admin password |
| `JDEPLOY_INGEST_USER` | `ingest` | Ingestion role username |
| `JDEPLOY_INGEST_PASSWORD` | `ingest-password` | Ingestion role password |
| `JDEPLOY_GENERATOR_USER` | `generator` | Diagram generation role username |
| `JDEPLOY_GENERATOR_PASSWORD` | `generator-password` | Diagram generation role password |
| `JDEPLOY_READER_USER` | `reader` | Read-only role username |
| `JDEPLOY_READER_PASSWORD` | `reader-password` | Read-only role password |
| `JDEPLOY_UML_OUTPUT_PATH` | profile-specific path | PlantUML output directory |
| `JDEPLOY_QUALITY_REPORTING_ENABLED` | `true` | Enables quality reporting scheduler |
| `JDEPLOY_QUALITY_REPORTING_CRON` | `0 */15 * * * *` | Scheduler cron |

## Notes

- The backend container image is built via multi-stage Docker build in `backend-api/Dockerfile`.
- For production, rotate all default credentials and externalize secrets.
