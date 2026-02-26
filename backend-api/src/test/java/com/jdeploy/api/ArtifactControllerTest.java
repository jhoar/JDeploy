package com.jdeploy.api;

import com.jdeploy.artifact.ArtifactExpiredException;
import com.jdeploy.artifact.ArtifactStorage;
import com.jdeploy.security.ApiRoles;
import com.jdeploy.service.DiagramGenerationService;
import com.jdeploy.service.ManifestContractValidator;
import com.jdeploy.service.ManifestIngestionService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ArtifactControllerTest {

    @Test
    void downloadReturnsGoneForExpiredArtifacts() {
        ArtifactStorage storage = mock(ArtifactStorage.class);
        when(storage.read("expired.puml")).thenThrow(new ArtifactExpiredException("Artifact expired"));

        ArtifactController controller = new ArtifactController(
                mock(ManifestIngestionService.class),
                mock(ManifestContractValidator.class),
                mock(DiagramGenerationService.class),
                storage
        );

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.download("expired.puml"));
        assertEquals(HttpStatus.GONE, ex.getStatusCode());
    }

    @Test
    void downloadAuthorizationAllowsGeneratorAndReaderRoles() throws NoSuchMethodException {
        Method downloadMethod = ArtifactController.class.getMethod("download", String.class);
        PreAuthorize preAuthorize = downloadMethod.getAnnotation(PreAuthorize.class);

        assertNotNull(preAuthorize);
        assertEquals("hasAnyAuthority('" + ApiRoles.ARTIFACT_GENERATE + "','" + ApiRoles.READ_ONLY + "')", preAuthorize.value());
    }
}
