package com.jdeploy.repository;

import com.jdeploy.domain.SoftwareComponent;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

import java.util.List;

public interface SoftwareComponentRepository extends Neo4jRepository<SoftwareComponent, Long> {

    @Query("""
        MATCH (c:SoftwareComponent)
        WHERE NOT (:SoftwareSystem)-[:HAS_COMPONENT]->(c)
        RETURN c
        """)
    List<SoftwareComponent> findOrphanComponents();

    @Query("""
        MATCH (:SoftwareSystem {name: $systemName})-[:HAS_COMPONENT]->(c:SoftwareComponent)
        RETURN DISTINCT c
        """)
    List<SoftwareComponent> findComponentsBySystem(String systemName);

    @Query("""
        MATCH (n:HardwareNode {hostname: $hostname})<-[:TARGET_NODE]-(:DeploymentInstance)
              <-[:HAS_DEPLOYMENT]-(c:SoftwareComponent)
        RETURN DISTINCT c
        """)
    List<SoftwareComponent> findImpactedComponentsByNodeFailure(String hostname);
}
