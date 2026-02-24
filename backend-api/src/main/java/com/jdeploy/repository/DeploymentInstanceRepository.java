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
        MATCH (d:DeploymentInstance)
        WHERE NOT (d)-[:TARGET_ENVIRONMENT]->(:ExecutionEnvironment)
           OR NOT (d)-[:TARGET_NODE]->(:HardwareNode)
        RETURN d
        """)
    List<DeploymentInstance> findOrphanDeployments();
}
