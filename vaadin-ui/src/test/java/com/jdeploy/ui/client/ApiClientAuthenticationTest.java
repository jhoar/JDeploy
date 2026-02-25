package com.jdeploy.ui.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ApiClientAuthenticationTest {

    private final ApiClientBeans beans = new ApiClientBeans();

    @Test
    void basicAuthIsAppliedToManifestTopologyAndArtifactClients() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();

        String expectedAuth = "Basic " + Base64.getEncoder().encodeToString("reader:reader-password".getBytes(StandardCharsets.UTF_8));

        server.expect(requestTo("http://backend.test/api/topology/systems"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, expectedAuth))
                .andRespond(withSuccess("[{\"name\":\"Telemetry\",\"componentCount\":1}]", MediaType.APPLICATION_JSON));

        server.expect(requestTo("http://backend.test/api/manifests/ingest"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, expectedAuth))
                .andRespond(withSuccess("{\"status\":\"OK\",\"message\":\"ingested\"}", MediaType.APPLICATION_JSON));

        server.expect(requestTo("http://backend.test/api/artifacts/demo-id"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, expectedAuth))
                .andRespond(withSuccess("artifact-content".getBytes(StandardCharsets.UTF_8), MediaType.APPLICATION_OCTET_STREAM));

        ApiClientConfiguration config = new ApiClientConfiguration(
                "http://backend.test",
                new ApiClientConfiguration.AuthConfiguration(
                        ApiClientConfiguration.AuthMode.BASIC,
                        new ApiClientConfiguration.BasicAuthConfiguration("reader", "reader-password"),
                        new ApiClientConfiguration.PropagationConfiguration(ApiClientConfiguration.TokenSource.REQUEST_AUTHORIZATION_HEADER)));

        RestClient restClient = beans.restClient(builder, config);
        TopologyApiClient topologyApiClient = new TopologyApiClient(restClient);
        ManifestApiClient manifestApiClient = new ManifestApiClient(restClient);
        ArtifactApiClient artifactApiClient = new ArtifactApiClient(restClient);

        List<TopologyApiClient.SoftwareSystemView> systems = topologyApiClient.systems();
        assertEquals("Telemetry", systems.getFirst().name());

        ManifestApiClient.ManifestIngestResult ingestResult = manifestApiClient.ingest("name: telemetry");
        assertEquals("OK", ingestResult.status());

        byte[] downloaded = artifactApiClient.download("demo-id");
        assertArrayEquals("artifact-content".getBytes(StandardCharsets.UTF_8), downloaded);

        server.verify();
    }

    @Test
    void callsFailWhenAuthenticationIsAbsent() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo("http://backend.test/api/topology/systems"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        ApiClientConfiguration config = new ApiClientConfiguration(
                "http://backend.test",
                new ApiClientConfiguration.AuthConfiguration(
                        ApiClientConfiguration.AuthMode.NONE,
                        new ApiClientConfiguration.BasicAuthConfiguration(null, null),
                        new ApiClientConfiguration.PropagationConfiguration(ApiClientConfiguration.TokenSource.REQUEST_AUTHORIZATION_HEADER)));

        RestClient restClient = beans.restClient(builder, config);
        TopologyApiClient topologyApiClient = new TopologyApiClient(restClient);

        assertThrows(HttpClientErrorException.Unauthorized.class, topologyApiClient::systems);
        server.verify();
    }

    @Test
    void callsFailWhenBasicCredentialsAreInvalid() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        String invalidAuth = "Basic " + Base64.getEncoder().encodeToString("reader:wrong-password".getBytes(StandardCharsets.UTF_8));
        server.expect(requestTo("http://backend.test/api/topology/systems"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, invalidAuth))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        ApiClientConfiguration config = new ApiClientConfiguration(
                "http://backend.test",
                new ApiClientConfiguration.AuthConfiguration(
                        ApiClientConfiguration.AuthMode.BASIC,
                        new ApiClientConfiguration.BasicAuthConfiguration("reader", "wrong-password"),
                        new ApiClientConfiguration.PropagationConfiguration(ApiClientConfiguration.TokenSource.REQUEST_AUTHORIZATION_HEADER)));

        RestClient restClient = beans.restClient(builder, config);
        TopologyApiClient topologyApiClient = new TopologyApiClient(restClient);

        assertThrows(HttpClientErrorException.Unauthorized.class, topologyApiClient::systems);
        server.verify();
    }
}
