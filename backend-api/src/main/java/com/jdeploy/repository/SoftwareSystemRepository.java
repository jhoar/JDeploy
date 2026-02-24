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
}
