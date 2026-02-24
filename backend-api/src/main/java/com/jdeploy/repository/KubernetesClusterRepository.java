package com.jdeploy.repository;

import com.jdeploy.domain.HardwareNode;
import com.jdeploy.domain.KubernetesCluster;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

import java.util.List;

public interface KubernetesClusterRepository extends Neo4jRepository<KubernetesCluster, Long> {

    @Query("""
        MATCH (:KubernetesCluster {name: $clusterName})-[:HAS_NODE]->(n:HardwareNode)
        RETURN DISTINCT n
        ORDER BY n.hostname
        """)
    List<HardwareNode> findNodesByClusterName(String clusterName);

    @Query("""
        MATCH (:KubernetesCluster {name: $clusterName})-[:HAS_NODE]->(n:HardwareNode)
        MATCH (:Subnet {cidr: $subnetCidr})-[:CONTAINS_NODE]->(n)
        RETURN DISTINCT n
        ORDER BY n.hostname
        """)
    List<HardwareNode> findNodesByClusterNameAndSubnet(String clusterName, String subnetCidr);
}
