package com.jdeploy.service;

import com.jdeploy.api.dto.TopologyUpdateDtos;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

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
        String newKey = request.targetEnvironmentName() + "@" + request.targetHostname();
        ensureUnique("MATCH (d:DeploymentInstance {deploymentKey: $name}) WHERE $name <> $current RETURN count(d) = 0 AS unique", newKey, currentDeploymentKey, "Deployment key already exists");

        neo4jClient.query("""
                MATCH (d:DeploymentInstance {deploymentKey: $current})
                MATCH (e:ExecutionEnvironment {name: $envName})
                MATCH (n:HardwareNode {hostname: $hostname})
                OPTIONAL MATCH (d)-[oldEnv:TARGET_ENVIRONMENT]->(:ExecutionEnvironment)
                OPTIONAL MATCH (d)-[oldNode:TARGET_NODE]->(:HardwareNode)
                DELETE oldEnv, oldNode
                CREATE (d)-[:TARGET_ENVIRONMENT]->(e)
                CREATE (d)-[:TARGET_NODE]->(n)
                SET d.deploymentKey = $newKey
                """)
                .bind(currentDeploymentKey).to("current")
                .bind(request.targetEnvironmentName()).to("envName")
                .bind(request.targetHostname()).to("hostname")
                .bind(newKey).to("newKey")
                .run();
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
