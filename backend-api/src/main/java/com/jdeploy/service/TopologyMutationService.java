package com.jdeploy.service;

import com.jdeploy.api.dto.TopologyUpdateDtos;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;

@Service
public class TopologyMutationService {

    private final Neo4jClient neo4jClient;

    public TopologyMutationService(Neo4jClient neo4jClient) {
        this.neo4jClient = neo4jClient;
    }

    public void updateSoftwareSystem(String existingName, TopologyUpdateDtos.SoftwareSystemUpdateRequest request) {
        ensureExists("MATCH (s:SoftwareSystem {name: $name}) RETURN count(s) > 0 AS found", "name", existingName, "SoftwareSystem not found");
        ensureUnique("MATCH (s:SoftwareSystem {name: $name}) WHERE $name <> $current RETURN count(s) = 0 AS unique", request.name(), existingName, "System name already exists");
        mutate("MATCH (s:SoftwareSystem {name: $current}) SET s.name = $name", existingName, request.name());
    }

    public void updateSoftwareComponent(String currentName, String currentVersion, TopologyUpdateDtos.SoftwareComponentUpdateRequest request) {
        boolean exists = neo4jClient.query("MATCH (c:SoftwareComponent {name: $name, version: $version}) RETURN count(c) > 0 AS found")
                .bind(currentName).to("name").bind(currentVersion).to("version")
                .fetchAs(Boolean.class).mappedBy((t, r) -> r.get("found").asBoolean()).one().orElse(false);
        if (!exists) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "SoftwareComponent not found");
        }

        boolean unique = neo4jClient.query("""
                MATCH (c:SoftwareComponent {name: $name, version: $version})
                WHERE NOT ($name = $currentName AND $version = $currentVersion)
                RETURN count(c) = 0 AS unique
                """)
                .bind(request.name()).to("name")
                .bind(request.version()).to("version")
                .bind(currentName).to("currentName")
                .bind(currentVersion).to("currentVersion")
                .fetchAs(Boolean.class).mappedBy((t, r) -> r.get("unique").asBoolean()).one().orElse(false);
        if (!unique) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Component name/version combination already exists");
        }

        neo4jClient.query("""
                MATCH (c:SoftwareComponent {name: $currentName, version: $currentVersion})
                SET c.name = $name, c.version = $version
                """)
                .bind(currentName).to("currentName")
                .bind(currentVersion).to("currentVersion")
                .bind(request.name()).to("name")
                .bind(request.version()).to("version")
                .run();
    }

    public void updateHardwareNode(String currentHostname, TopologyUpdateDtos.HardwareNodeUpdateRequest request) {
        ensureExists("MATCH (n:HardwareNode {hostname: $name}) RETURN count(n) > 0 AS found", "name", currentHostname, "HardwareNode not found");
        ensureUnique("MATCH (n:HardwareNode {hostname: $name}) WHERE $name <> $current RETURN count(n) = 0 AS unique", request.hostname(), currentHostname, "Hostname already exists");
        ensureUnique("MATCH (n:HardwareNode {ipAddress: $name}) WHERE n.hostname <> $current RETURN count(n) = 0 AS unique", request.ipAddress(), currentHostname, "IP address already exists");

        neo4jClient.query("""
                MATCH (n:HardwareNode {hostname: $current})
                SET n.type = $type, n.hostname = $hostname, n.ipAddress = $ip, n.roles = $roles
                """)
                .bind(currentHostname).to("current")
                .bind(request.type().name()).to("type")
                .bind(request.hostname()).to("hostname")
                .bind(request.ipAddress()).to("ip")
                .bind(request.roles()).to("roles")
                .run();
    }

    public void updateSubnet(String currentCidr, TopologyUpdateDtos.SubnetUpdateRequest request) {
        ensureExists("MATCH (s:Subnet {cidr: $name}) RETURN count(s) > 0 AS found", "name", currentCidr, "Subnet not found");
        ensureUnique("MATCH (s:Subnet {cidr: $name}) WHERE $name <> $current RETURN count(s) = 0 AS unique", request.cidr(), currentCidr, "CIDR already exists");
        neo4jClient.query("MATCH (s:Subnet {cidr: $current}) SET s.cidr = $cidr, s.vlan = $vlan, s.routingZone = $routingZone")
                .bind(currentCidr).to("current")
                .bind(request.cidr()).to("cidr")
                .bind(request.vlan()).to("vlan")
                .bind(request.routingZone()).to("routingZone")
                .run();
    }

    public void updateExecutionEnvironment(String currentName, TopologyUpdateDtos.ExecutionEnvironmentUpdateRequest request) {
        ensureExists("MATCH (e:ExecutionEnvironment {name: $name}) RETURN count(e) > 0 AS found", "name", currentName, "ExecutionEnvironment not found");
        ensureUnique("MATCH (e:ExecutionEnvironment {name: $name}) WHERE $name <> $current RETURN count(e) = 0 AS unique", request.name(), currentName, "Environment name already exists");
        neo4jClient.query("MATCH (e:ExecutionEnvironment {name: $current}) SET e.name = $name, e.type = $type")
                .bind(currentName).to("current")
                .bind(request.name()).to("name")
                .bind(request.type().name()).to("type")
                .run();
    }

    public void updateDeploymentInstance(String currentDeploymentKey, TopologyUpdateDtos.DeploymentInstanceUpdateRequest request) {
        ensureExists("MATCH (d:DeploymentInstance {deploymentKey: $name}) RETURN count(d) > 0 AS found", "name", currentDeploymentKey, "DeploymentInstance not found");
        ensureExists("MATCH (e:ExecutionEnvironment {name: $name}) RETURN count(e) > 0 AS found", "name", request.targetEnvironmentName(), "Target environment not found");
        ensureExists("MATCH (n:HardwareNode {hostname: $name}) RETURN count(n) > 0 AS found", "name", request.targetHostname(), "Target node not found");

        DeploymentKeyParts keyParts = loadDeploymentKeyParts(currentDeploymentKey);
        String newKey = canonicalDeploymentKey(
                request.targetEnvironmentName(),
                request.targetHostname(),
                keyParts.componentName(),
                keyParts.componentVersion());
        ensureUnique("MATCH (d:DeploymentInstance {deploymentKey: $name}) WHERE $name <> $current RETURN count(d) = 0 AS unique", newKey, currentDeploymentKey, "Deployment key already exists");

        neo4jClient.query("""
                MATCH (d:DeploymentInstance {deploymentKey: $current})
                MATCH (e:ExecutionEnvironment {name: $envName})
                MATCH (n:HardwareNode {hostname: $hostname})
                OPTIONAL MATCH (d)-[oldEnv:TARGET_ENVIRONMENT]->(:ExecutionEnvironment)
                OPTIONAL MATCH (d)-[oldNode:TARGET_NODE]->(:HardwareNode)
                OPTIONAL MATCH (d)-[oldTargets:TARGETS]->(oldTarget)
                WHERE oldTarget:ExecutionEnvironment OR oldTarget:HardwareNode
                DELETE oldEnv, oldNode, oldTargets
                CREATE (d)-[:TARGET_ENVIRONMENT]->(e)
                CREATE (d)-[:TARGET_NODE]->(n)
                CREATE (d)-[:TARGETS]->(e)
                CREATE (d)-[:TARGETS]->(n)
                SET d.deploymentKey = $newKey
                """)
                .bind(currentDeploymentKey).to("current")
                .bind(request.targetEnvironmentName()).to("envName")
                .bind(request.targetHostname()).to("hostname")
                .bind(newKey).to("newKey")
                .run();
    }

    private DeploymentKeyParts loadDeploymentKeyParts(String currentDeploymentKey) {
        DeploymentKeyParts fromComponentRelationship = neo4jClient.query("""
                MATCH (c:SoftwareComponent)-[:HAS_DEPLOYMENT]->(d:DeploymentInstance {deploymentKey: $deploymentKey})
                RETURN c.name AS componentName, c.version AS componentVersion
                LIMIT 1
                """)
                .bind(currentDeploymentKey).to("deploymentKey")
                .fetchAs(DeploymentKeyParts.class)
                .mappedBy((typeSystem, record) -> {
                    String componentName = record.get("componentName").isNull() ? null : record.get("componentName").asString();
                    String componentVersion = record.get("componentVersion").isNull() ? null : record.get("componentVersion").asString();
                    if (componentName == null || componentVersion == null) {
                        return null;
                    }
                    return new DeploymentKeyParts(componentName, componentVersion);
                })
                .one()
                .orElse(null);
        if (fromComponentRelationship != null) {
            return fromComponentRelationship;
        }

        return parseDeploymentKey(currentDeploymentKey);
    }

    private DeploymentKeyParts parseDeploymentKey(String deploymentKey) {
        String[] envAndRest = deploymentKey.split("@", 2);
        if (envAndRest.length != 2 || envAndRest[0].isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Deployment key format is invalid");
        }

        String[] hostAndComponent = envAndRest[1].split(":", 3);
        if (hostAndComponent.length != 3
                || hostAndComponent[0].isBlank()
                || hostAndComponent[1].isBlank()
                || hostAndComponent[2].isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Deployment key format is invalid");
        }

        return new DeploymentKeyParts(hostAndComponent[1], hostAndComponent[2]);
    }

    private String canonicalDeploymentKey(String environmentName, String hostname, String componentName, String componentVersion) {
        if (isBlank(environmentName) || isBlank(hostname) || isBlank(componentName) || isBlank(componentVersion)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Deployment key format is invalid");
        }
        return environmentName + "@" + hostname + ":" + componentName + ":" + componentVersion;
    }

    private boolean isBlank(String value) {
        return Objects.isNull(value) || value.isBlank();
    }

    private record DeploymentKeyParts(String componentName, String componentVersion) {
    }

    private void ensureExists(String query, String key, String value, String message) {
        boolean found = neo4jClient.query(query)
                .bind(value).to(key)
                .fetchAs(Boolean.class)
                .mappedBy((typeSystem, record) -> record.get("found").asBoolean())
                .one()
                .orElse(false);
        if (!found) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, message);
        }
    }

    private void ensureUnique(String query, String name, String current, String message) {
        boolean unique = neo4jClient.query(query)
                .bind(name).to("name")
                .bind(current).to("current")
                .fetchAs(Boolean.class)
                .mappedBy((typeSystem, record) -> record.get("unique").asBoolean())
                .one()
                .orElse(false);
        if (!unique) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, message);
        }
    }

    private void mutate(String query, String current, String name) {
        neo4jClient.query(query)
                .bind(current).to("current")
                .bind(name).to("name")
                .run();
    }
}
