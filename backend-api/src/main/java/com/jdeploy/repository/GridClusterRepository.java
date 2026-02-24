package com.jdeploy.repository;

import com.jdeploy.domain.GridCluster;
import com.jdeploy.domain.HardwareNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

import java.util.List;

public interface GridClusterRepository extends Neo4jRepository<GridCluster, Long> {

    @Query("""
        MATCH (:GridCluster {name: $clusterName})-[:HAS_NODE]->(n:HardwareNode)
        RETURN DISTINCT n
        ORDER BY n.hostname
        """)
    List<HardwareNode> findNodesByClusterName(String clusterName);
}
