package com.jdeploy.repository;

import com.jdeploy.domain.NetworkLink;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

import java.util.List;

public interface NetworkLinkRepository extends Neo4jRepository<NetworkLink, Long> {

    @Query("""
        MATCH (n:HardwareNode {hostname: $hostname})<-[:CONNECTS_FROM|CONNECTS_TO]-(l:NetworkLink)
        RETURN DISTINCT l
        """)
    List<NetworkLink> findLinksByNode(String hostname);

    @Query("""
        MATCH (:SoftwareSystem {name: $systemName})-[:HAS_COMPONENT]->(:SoftwareComponent)-[:HAS_DEPLOYMENT]->
              (:DeploymentInstance)-[:TARGET_NODE]->(node:HardwareNode)
        MATCH (node)<-[:CONNECTS_FROM|CONNECTS_TO]-(l:NetworkLink)
        RETURN DISTINCT l
        """)
    List<NetworkLink> findLinksSupportingSystem(String systemName);
}
