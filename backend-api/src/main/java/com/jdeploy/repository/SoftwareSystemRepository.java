package com.jdeploy.repository;

import com.jdeploy.domain.SoftwareSystem;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

import java.util.List;

public interface SoftwareSystemRepository extends Neo4jRepository<SoftwareSystem, Long> {

    @Query("""
        MATCH (n:HardwareNode {hostname: $hostname})<-[:TARGET_NODE]-(d:DeploymentInstance)
              <-[:HAS_DEPLOYMENT]-(c:SoftwareComponent)<-[:HAS_COMPONENT]-(s:SoftwareSystem)
        RETURN DISTINCT s
        """)
    List<SoftwareSystem> findImpactedSystemsByNodeFailure(String hostname);

    @Query("""
        MATCH (s:SoftwareSystem)-[:HAS_COMPONENT]->(:SoftwareComponent)-[:HAS_DEPLOYMENT]->(:DeploymentInstance)
              -[:TARGET_ENVIRONMENT]->(env:ExecutionEnvironment {name: $environmentName})
        RETURN DISTINCT s
        """)
    List<SoftwareSystem> findSystemsByEnvironment(String environmentName);

    @Query("""
        MATCH (s:SoftwareSystem)-[:HAS_COMPONENT]->(:SoftwareComponent)-[:HAS_DEPLOYMENT]->(:DeploymentInstance)
              -[:TARGET_NODE]->(:HardwareNode)<-[:CONTAINS_NODE]-(:Subnet {cidr: $subnetCidr})
        RETURN DISTINCT s
        """)
    List<SoftwareSystem> findSystemsBySubnet(String subnetCidr);

    @Query("""
        MATCH (:Subnet {cidr: $subnetCidr})-[:CONTAINS_NODE]->(n:HardwareNode)
              <-[:TARGET_NODE]-(:DeploymentInstance)<-[:HAS_DEPLOYMENT]-(:SoftwareComponent)<-[:HAS_COMPONENT]-(s:SoftwareSystem)
        RETURN DISTINCT s
        """)
    List<SoftwareSystem> findImpactedSystemsBySubnetOutage(String subnetCidr);
}
