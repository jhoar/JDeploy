package com.jdeploy.service;

import com.jdeploy.service.dto.DeploymentManifestDto;
import org.junit.jupiter.api.Test;

import org.springframework.data.neo4j.core.Neo4jClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class ManifestIngestionServiceTest {

    @Test
    void parseManifestReadsYamlIntoDto() {
        String yaml = """
                subnets:
                  - cidr: 10.0.0.0/24
                    vlan: app
                    routingZone: internal
                    nodes:
                      - hostname: app01
                        ipAddress: 10.0.0.10
                        type: VIRTUAL_MACHINE
                        roles: [kubernetes]
                environments:
                  - name: prod
                    type: PRODUCTION
                systems:
                  - name: Payments
                    components:
                      - name: payments-api
                        version: 1.2.3
                        deployments:
                          - environment: prod
                            hostname: app01
                links:
                  - fromHostname: app01
                    toHostname: app01
                    bandwidthMbps: 1000
                    latencyMs: 1
                """;

        ManifestIngestionService service = new ManifestIngestionService(mock(Neo4jClient.class));
        DeploymentManifestDto manifest = service.parseManifest(yaml);

        assertEquals(1, manifest.subnets().size());
        assertEquals("10.0.0.0/24", manifest.subnets().getFirst().cidr());
        assertEquals("Payments", manifest.systems().getFirst().name());
        assertEquals("payments-api", manifest.systems().getFirst().components().getFirst().name());
    }
}
