package com.jdeploy.repository;

import com.jdeploy.domain.DeploymentInstance;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

import java.util.List;

public interface DeploymentInstanceRepository extends Neo4jRepository<DeploymentInstance, Long> {

    @Query("""
        MATCH (s:Subnet {cidr: $subnetCidr})-[:CONTAINS_NODE]->(n:HardwareNode)<-[:TARGET_NODE]-(d:DeploymentInstance)
        RETURN DISTINCT d
        """)
    List<DeploymentInstance> findDeploymentsBySubnet(String subnetCidr);

    @Query("""
        MATCH (d:DeploymentInstance)-[:TARGET_NODE]->(n:HardwareNode {hostname: $hostname})
        RETURN DISTINCT d
        """)
    List<DeploymentInstance> findDeploymentsByNode(String hostname);

    @Query("""
        MATCH (d:DeploymentInstance)-[:TARGET_ENVIRONMENT]->(env:ExecutionEnvironment {name: $environmentName})
        RETURN DISTINCT d
        """)
    List<DeploymentInstance> findDeploymentsByEnvironment(String environmentName);

    @Query("""
        MATCH (:Subnet {cidr: $subnetCidr})-[:CONTAINS_NODE]->(subnetNode:HardwareNode)
              <-[:CONNECTS_FROM|CONNECTS_TO]-(:NetworkLink)-[:CONNECTS_FROM|CONNECTS_TO]->(n:HardwareNode)
              <-[:TARGET_NODE]-(d:DeploymentInstance)
        RETURN DISTINCT d
        """)
    List<DeploymentInstance> findDeploymentsImpactedBySubnetLinks(String subnetCidr);

    @Query("""
        MATCH (d:DeploymentInstance)
        WHERE NOT (d)-[:TARGET_ENVIRONMENT]->(:ExecutionEnvironment)
           OR NOT (d)-[:TARGET_NODE]->(:HardwareNode)
        RETURN d
        """)
    List<DeploymentInstance> findOrphanDeployments();
}
