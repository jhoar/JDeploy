package com.jdeploy.repository;

import com.jdeploy.domain.ExecutionEnvironment;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

import java.util.List;

public interface ExecutionEnvironmentRepository extends Neo4jRepository<ExecutionEnvironment, Long> {

    @Query("""
        MATCH (:SoftwareSystem {name: $systemName})-[:HAS_COMPONENT]->(:SoftwareComponent)
              -[:HAS_DEPLOYMENT]->(:DeploymentInstance)-[:TARGET_ENVIRONMENT]->(env:ExecutionEnvironment)
        RETURN DISTINCT env
        """)
    List<ExecutionEnvironment> findBySystemName(String systemName);

    @Query("""
        MATCH (env:ExecutionEnvironment)<-[:TARGET_ENVIRONMENT]-(d:DeploymentInstance {deploymentKey: $deploymentKey})
        RETURN env
        """)
    ExecutionEnvironment findByDeploymentKey(String deploymentKey);
}
