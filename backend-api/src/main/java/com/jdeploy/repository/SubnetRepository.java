package com.jdeploy.repository;

import com.jdeploy.domain.Subnet;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

import java.util.List;

public interface SubnetRepository extends Neo4jRepository<Subnet, Long> {

    @Query("""
        MATCH (:Site {name: $siteName})-[:HAS_SUBNET]->(s:Subnet)
        RETURN DISTINCT s
        """)
    List<Subnet> findSubnetsBySite(String siteName);

    @Query("""
        MATCH (:SoftwareSystem {name: $systemName})-[:HAS_COMPONENT]->(:SoftwareComponent)
              -[:HAS_DEPLOYMENT]->(:DeploymentInstance)-[:TARGET_NODE]->(:HardwareNode)<-[:CONTAINS_NODE]-(s:Subnet)
        RETURN DISTINCT s
        """)
    List<Subnet> findSubnetsHostingSystem(String systemName);

    @Query("""
        MATCH (source:Subnet {cidr: $subnetCidr})-[:CONTAINS_NODE]->(sourceNode:HardwareNode)
              <-[:CONNECTS_FROM|CONNECTS_TO]-(:NetworkLink)-[:CONNECTS_FROM|CONNECTS_TO]->(peerNode:HardwareNode)
              <-[:CONTAINS_NODE]-(peerSubnet:Subnet)
        WHERE source <> peerSubnet
        RETURN DISTINCT peerSubnet
        """)
    List<Subnet> findLinkedSubnets(String subnetCidr);
}
