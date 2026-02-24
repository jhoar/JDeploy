package com.jdeploy.api;

import com.jdeploy.artifact.ArtifactMetadata;
import com.jdeploy.artifact.ArtifactStorage;
import com.jdeploy.artifact.StoredArtifact;
import com.jdeploy.security.ApiRoles;
import com.jdeploy.service.DiagramGenerationService;
import com.jdeploy.service.ManifestContractValidator;
import com.jdeploy.service.ManifestIngestionService;
import com.jdeploy.service.dto.DeploymentManifestDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/artifacts")
@Tag(name = "Artifact Operations")
public class ArtifactController {

    private final ManifestIngestionService ingestionService;
    private final ManifestContractValidator contractValidator;
    private final DiagramGenerationService diagramGenerationService;
    private final ArtifactStorage artifactStorage;

    public ArtifactController(ManifestIngestionService ingestionService,
                              ManifestContractValidator contractValidator,
                              DiagramGenerationService diagramGenerationService,
                              ArtifactStorage artifactStorage) {
        this.ingestionService = ingestionService;
        this.contractValidator = contractValidator;
        this.diagramGenerationService = diagramGenerationService;
        this.artifactStorage = artifactStorage;
    }

    @PostMapping("/generate")
    @PreAuthorize("hasAuthority('" + ApiRoles.ARTIFACT_GENERATE + "')")
    @Operation(summary = "Generate deployment topology artifact from manifest")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = ArtifactMetadata.class)))
    public ArtifactMetadata generate(@RequestBody @NotBlank String manifestYaml) {
        DeploymentManifestDto manifest = ingestionService.parseManifest(manifestYaml);
        contractValidator.validateForIngestion(manifest);
        return diagramGenerationService.generateDeploymentDiagram(manifest);
    }

    @GetMapping("/{artifactId}")
    @PreAuthorize("hasAnyAuthority('" + ApiRoles.ARTIFACT_GENERATE + "','" + ApiRoles.READ_ONLY + "')")
    @Operation(summary = "Download generated deployment topology artifact")
    public ResponseEntity<byte[]> download(@PathVariable String artifactId) {
        StoredArtifact artifact = artifactStorage.read(artifactId);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + artifact.metadata().artifactId() + "\"")
                .body(artifact.content().getBytes(StandardCharsets.UTF_8));
    }
}
