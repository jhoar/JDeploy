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

    @Query("""
        MATCH (:Subnet {cidr: $subnetCidr})-[:CONTAINS_NODE]->(n:HardwareNode)
        RETURN DISTINCT n
        """)
    List<HardwareNode> findNodesBySubnet(String subnetCidr);

    @Query("""
        MATCH (:SoftwareSystem {name: $systemName})-[:CLUSTER_MEMBER]->(n:HardwareNode)
        RETURN DISTINCT n
        """)
    List<HardwareNode> findClusterMembersBySystemName(String systemName);

    @Query("""
        MATCH (failed:HardwareNode {hostname: $hostname})
              <-[:CONNECTS_FROM|CONNECTS_TO]-(:NetworkLink)-[:CONNECTS_FROM|CONNECTS_TO]->(n:HardwareNode)
        RETURN DISTINCT n
        """)
    List<HardwareNode> findNodesImpactedByNodeFailure(String hostname);
}
