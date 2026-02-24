package com.jdeploy.api;

import com.jdeploy.security.ApiRoles;
import com.jdeploy.service.ManifestContractValidator;
import com.jdeploy.service.ManifestIngestionService;
import com.jdeploy.service.PreconditionViolationException;
import com.jdeploy.service.dto.DeploymentManifestDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Manifest Operations")
public class ManifestController {

    private final ManifestIngestionService ingestionService;
    private final ManifestContractValidator contractValidator;

    public ManifestController(ManifestIngestionService ingestionService, ManifestContractValidator contractValidator) {
        this.ingestionService = ingestionService;
        this.contractValidator = contractValidator;
    }

    @PostMapping("/manifests/ingest")
    @PreAuthorize("hasAuthority('" + ApiRoles.TOPOLOGY_INGEST + "')")
    @Operation(summary = "Ingest manifest and synchronize graph")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = OperationResult.class)))
    public OperationResult ingest(@RequestBody @NotBlank String manifestYaml) {
        DeploymentManifestDto manifest = ingestionService.parseManifest(manifestYaml);
        contractValidator.validateForIngestion(manifest);
        ingestionService.synchronize(manifest);
        return new OperationResult("INGESTED", "Manifest accepted and synchronized");
    }

    @PostMapping("/quality-gates/manifest")
    @PreAuthorize("hasAuthority('" + ApiRoles.READ_ONLY + "')")
    @Operation(summary = "Validate manifest contract and deployment quality gates")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = OperationResult.class)))
    public OperationResult qualityGateManifest(@RequestBody @NotBlank String manifestYaml) {
        DeploymentManifestDto manifest = ingestionService.parseManifest(manifestYaml);
        contractValidator.validateForIngestion(manifest);
        return new OperationResult("PASSED", "Manifest passed contract checks");
    }


    @PostMapping("/quality-gates/deployment-targets")
    @PreAuthorize("hasAuthority('" + ApiRoles.READ_ONLY + "')")
    @Operation(summary = "Validate deployment target references")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = OperationResult.class)))
    public OperationResult qualityGateDeploymentTargets(@RequestBody @NotBlank String manifestYaml) {
        DeploymentManifestDto manifest = ingestionService.parseManifest(manifestYaml);
        contractValidator.validateForIngestion(manifest);
        return new OperationResult("PASSED", "Deployment target checks passed");
    }

    @ExceptionHandler({IllegalArgumentException.class, PreconditionViolationException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public OperationResult badRequest(Exception exception) {
        return new OperationResult("FAILED", exception.getMessage());
    }

    @Schema(name = "OperationResult")
    public record OperationResult(String status, String message) {
    }
}
