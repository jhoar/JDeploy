package com.jdeploy.api;

import com.jdeploy.security.ApiRoles;
import com.jdeploy.service.GraphQualityGateService;
import com.jdeploy.service.ManifestContractValidator;
import com.jdeploy.service.ManifestIngestionService;
import com.jdeploy.service.PreconditionViolationException;
import com.jdeploy.service.dto.DeploymentManifestDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Manifest Operations")
@SecurityRequirement(name = "basicAuth")
public class ManifestController {

    private final ManifestIngestionService ingestionService;
    private final ManifestContractValidator contractValidator;
    private final GraphQualityGateService graphQualityGateService;

    public ManifestController(ManifestIngestionService ingestionService,
                              ManifestContractValidator contractValidator,
                              GraphQualityGateService graphQualityGateService) {
        this.ingestionService = ingestionService;
        this.contractValidator = contractValidator;
        this.graphQualityGateService = graphQualityGateService;
    }

    @PostMapping("/manifests/ingest")
    @PreAuthorize("hasAuthority('" + ApiRoles.TOPOLOGY_INGEST + "')")
    @Operation(summary = "Ingest manifest and synchronize graph")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(mediaType = "application/x-yaml", schema = @Schema(type = "string", description = "Deployment manifest in YAML format")))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Manifest ingested", content = @Content(schema = @Schema(implementation = OperationResult.class))),
            @ApiResponse(responseCode = "400", description = "Manifest validation failed", content = @Content(schema = @Schema(implementation = OperationResult.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Insufficient privileges")
    })
    public OperationResult ingest(@RequestBody @NotBlank String manifestYaml) {
        DeploymentManifestDto manifest = ingestionService.parseManifest(manifestYaml);
        contractValidator.validateForIngestion(manifest);
        ingestionService.synchronize(manifest);
        return new OperationResult("INGESTED", "Manifest accepted and synchronized");
    }

    @PostMapping("/quality-gates/manifest")
    @PreAuthorize("hasAuthority('" + ApiRoles.READ_ONLY + "')")
    @Operation(summary = "Validate manifest contract and deployment quality gates")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(mediaType = "application/x-yaml", schema = @Schema(type = "string", description = "Deployment manifest in YAML format")))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Manifest checks passed", content = @Content(schema = @Schema(implementation = OperationResult.class))),
            @ApiResponse(responseCode = "400", description = "Manifest checks failed", content = @Content(schema = @Schema(implementation = OperationResult.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Insufficient privileges")
    })
    public OperationResult qualityGateManifest(@RequestBody @NotBlank String manifestYaml) {
        DeploymentManifestDto manifest = ingestionService.parseManifest(manifestYaml);
        contractValidator.validateForIngestion(manifest);
        return new OperationResult("PASSED", "Manifest passed contract checks");
    }


    @PostMapping("/quality-gates/deployment-targets")
    @PreAuthorize("hasAuthority('" + ApiRoles.READ_ONLY + "')")
    @Operation(summary = "Validate deployment target references")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(mediaType = "application/x-yaml", schema = @Schema(type = "string", description = "Deployment manifest in YAML format")))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Deployment target checks passed", content = @Content(schema = @Schema(implementation = OperationResult.class))),
            @ApiResponse(responseCode = "400", description = "Deployment target checks failed", content = @Content(schema = @Schema(implementation = OperationResult.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Insufficient privileges")
    })
    public OperationResult qualityGateDeploymentTargets(@RequestBody @NotBlank String manifestYaml) {
        DeploymentManifestDto manifest = ingestionService.parseManifest(manifestYaml);
        contractValidator.validateForIngestion(manifest);
        return new OperationResult("PASSED", "Deployment target checks passed");
    }

    @GetMapping("/quality-gates/graph")
    @PreAuthorize("hasAuthority('" + ApiRoles.READ_ONLY + "')")
    @Operation(summary = "Evaluate persisted graph quality gates")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Graph quality report", content = @Content(schema = @Schema(implementation = GraphQualityGateService.QualityGateReport.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Insufficient privileges")
    })
    public GraphQualityGateService.QualityGateReport qualityGateGraph() {
        return graphQualityGateService.evaluateGraph();
    }


    @GetMapping("/quality-gates/graph/latest")
    @PreAuthorize("hasAuthority('" + ApiRoles.READ_ONLY + "')")
    @Operation(summary = "Get latest scheduled graph quality report snapshot")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Latest graph quality report snapshot", content = @Content(schema = @Schema(implementation = GraphQualityGateService.QualityGateSnapshot.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Insufficient privileges")
    })
    public GraphQualityGateService.QualityGateSnapshot latestGraphQualityReport() {
        return graphQualityGateService.latestReport();
    }

    @ExceptionHandler({IllegalArgumentException.class, PreconditionViolationException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public OperationResult badRequest(Exception exception) {
        return new OperationResult("FAILED", exception.getMessage());
    }

    @Schema(name = "OperationResult", description = "Standard operation outcome payload")
    public record OperationResult(String status, String message) {
    }
}
