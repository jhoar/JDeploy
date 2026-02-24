package com.jdeploy.repository;

import com.jdeploy.domain.Site;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

import java.util.List;

public interface SiteRepository extends Neo4jRepository<Site, Long> {

    @Query("""
        MATCH (site:Site)-[:HAS_SUBNET]->(:Subnet)-[:CONTAINS_NODE]->(:HardwareNode {hostname: $hostname})
        RETURN DISTINCT site
        """)
    List<Site> findSitesByNodeHostname(String hostname);

    @Query("""
        MATCH (site:Site)-[:HAS_SUBNET]->(:Subnet)-[:CONTAINS_NODE]->(n:HardwareNode)
              <-[:TARGET_NODE]-(:DeploymentInstance)<-[:HAS_DEPLOYMENT]-(:SoftwareComponent)<-[:HAS_COMPONENT]-
              (:SoftwareSystem {name: $systemName})
        RETURN DISTINCT site
        """)
    List<Site> findSitesHostingSystem(String systemName);
}
