package com.jdeploy.repository;

import com.jdeploy.domain.HardwareNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

import java.util.List;

public interface HardwareNodeRepository extends Neo4jRepository<HardwareNode, Long> {

    @Query("""
        MATCH (n:HardwareNode)
        WHERE NOT (:Subnet)-[:CONTAINS_NODE]->(n)
        RETURN n
        """)
    List<HardwareNode> findNodesWithoutSubnet();
}
