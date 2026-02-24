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
}
