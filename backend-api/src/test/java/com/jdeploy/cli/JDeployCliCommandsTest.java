package com.jdeploy.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdeploy.service.DiagramGenerationService;
import com.jdeploy.service.ManifestIngestionService;
import com.jdeploy.service.TopologyQueryService;
import com.jdeploy.service.dto.DeploymentManifestDto;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JDeployCliCommandsTest {

    @Test
    void ingestManifest_requiresFileArgument() {
        CommandLine cmd = commandLine();
        int exit = cmd.execute("ingest-manifest");
        assertEquals(2, exit);
    }

    @Test
    void deploymentsBySubnet_jsonSuccess() {
        TopologyQueryService topologyQueryService = mock(TopologyQueryService.class);
        when(topologyQueryService.deploymentsBySubnet("10.0.0.0/24"))
                .thenReturn(List.of(new TopologyQueryService.DeploymentView("node-a", "payment:1.0@prod")));

        JDeployCliCommands.DeploymentsBySubnetCommand command =
                new JDeployCliCommands.DeploymentsBySubnetCommand(topologyQueryService, new ObjectMapper());

        int exit = new CommandLine(command).execute("--subnet", "10.0.0.0/24", "--format", "JSON");
        assertEquals(0, exit);
    }

    @Test
    void generateDiagram_writesOutput() throws Exception {
        DiagramGenerationService diagramGenerationService = mock(DiagramGenerationService.class);
        when(diagramGenerationService.buildSystemPlantUml("billing")).thenReturn("@startuml\n@enduml\n");
        Path output = Files.createTempFile("jdeploy-diagram", ".puml");

        JDeployCliCommands.GenerateDiagramCommand command =
                new JDeployCliCommands.GenerateDiagramCommand(diagramGenerationService);

        int exit = new CommandLine(command).execute("--system", "billing", "--output", output.toString());

        assertEquals(0, exit);
        assertTrue(Files.readString(output).contains("@startuml"));
    }

    @Test
    void ingestManifest_success() throws Exception {
        ManifestIngestionService ingestionService = mock(ManifestIngestionService.class);
        CliAuthenticationService auth = new CliAuthenticationService("trusted", "cli-service", "pw");
        Path file = Files.createTempFile("manifest", ".yml");
        when(ingestionService.parseManifest(any(Path.class))).thenReturn(mock(DeploymentManifestDto.class));

        JDeployCliCommands.IngestManifestCommand command =
                new JDeployCliCommands.IngestManifestCommand(ingestionService, auth);

        int exit = new CommandLine(command).execute("--file", file.toString());

        assertEquals(0, exit);
        verify(ingestionService).parseManifest(file);
        verify(ingestionService).synchronize(any(DeploymentManifestDto.class));
    }

    private CommandLine commandLine() {
        ManifestIngestionService ingestionService = mock(ManifestIngestionService.class);
        TopologyQueryService topologyQueryService = mock(TopologyQueryService.class);
        DiagramGenerationService diagramGenerationService = mock(DiagramGenerationService.class);
        CliAuthenticationService auth = new CliAuthenticationService("trusted", "cli-service", "pw");
        ObjectMapper objectMapper = new ObjectMapper();

        @CommandLine.Command(name = "jdeploy")
        class TestRootCommand implements Runnable {
            @Override
            public void run() {
                throw new CommandLine.ParameterException(new CommandLine(this), "A subcommand is required");
            }
        }
        CommandLine root = new CommandLine(new TestRootCommand());
        root.addSubcommand("ingest-manifest", new JDeployCliCommands.IngestManifestCommand(ingestionService, auth));
        root.addSubcommand("deployments-by-subnet", new JDeployCliCommands.DeploymentsBySubnetCommand(topologyQueryService, objectMapper));
        root.addSubcommand("impact-by-node", new JDeployCliCommands.ImpactByNodeCommand(topologyQueryService, objectMapper));
        root.addSubcommand("generate-diagram", new JDeployCliCommands.GenerateDiagramCommand(diagramGenerationService));
        return root;
    }
}
