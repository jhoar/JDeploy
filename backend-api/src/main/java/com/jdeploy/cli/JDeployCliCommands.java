package com.jdeploy.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdeploy.service.DiagramGenerationService;
import com.jdeploy.service.ManifestIngestionService;
import com.jdeploy.service.TopologyQueryService;
import com.jdeploy.service.dto.DeploymentManifestDto;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Component
@CommandLine.Command(name = "jdeploy", mixinStandardHelpOptions = true,
        description = "JDeploy CLI",
        subcommands = {
                JDeployCliCommands.IngestManifestCommand.class,
                JDeployCliCommands.DeploymentsBySubnetCommand.class,
                JDeployCliCommands.ImpactByNodeCommand.class,
                JDeployCliCommands.GenerateDiagramCommand.class
        })
public class JDeployCliCommands implements Runnable {

    @Override
    public void run() {
        throw new CommandLine.ParameterException(new CommandLine(this), "A subcommand is required");
    }

    @CommandLine.Command(name = "ingest-manifest", description = "Ingest and synchronize a deployment manifest")
    static class IngestManifestCommand implements Runnable {

        @CommandLine.Option(names = "--file", required = true, description = "Path to YAML manifest file")
        private Path file;

        @CommandLine.Option(names = "--auth-user", description = "CLI service account username")
        private String authUser;

        @CommandLine.Option(names = "--auth-password", description = "CLI service account password")
        private String authPassword;

        private final ManifestIngestionService ingestionService;
        private final CliAuthenticationService authenticationService;

        IngestManifestCommand(ManifestIngestionService ingestionService, CliAuthenticationService authenticationService) {
            this.ingestionService = ingestionService;
            this.authenticationService = authenticationService;
        }

        @Override
        public void run() {
            authenticationService.authenticate(authUser, authPassword);
            DeploymentManifestDto manifest = ingestionService.parseManifest(file);
            ingestionService.synchronize(manifest);
            System.out.println("Manifest ingested successfully: " + file.toAbsolutePath());
        }
    }

    @CommandLine.Command(name = "deployments-by-subnet", description = "List deployments by subnet")
    static class DeploymentsBySubnetCommand implements Runnable {

        @CommandLine.Option(names = "--subnet", required = true, description = "Subnet CIDR")
        private String subnet;

        @CommandLine.Option(names = "--format", defaultValue = "TEXT", description = "Output format: ${COMPLETION-CANDIDATES}")
        private CliOutputFormat format;

        private final TopologyQueryService topologyQueryService;
        private final ObjectMapper objectMapper;

        DeploymentsBySubnetCommand(TopologyQueryService topologyQueryService, ObjectMapper objectMapper) {
            this.topologyQueryService = topologyQueryService;
            this.objectMapper = objectMapper;
        }

        @Override
        public void run() {
            List<TopologyQueryService.DeploymentView> rows = topologyQueryService.deploymentsBySubnet(subnet);
            print(format, rows,
                    List.of("HOSTNAME", "DEPLOYMENT_KEY"),
                    rows.stream().map(r -> List.of(r.hostname(), r.deploymentKey())).toList());
        }

        private void print(CliOutputFormat format, Object payload, List<String> headers, List<List<String>> rows) {
            if (format == CliOutputFormat.JSON) {
                try {
                    System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload));
                } catch (JsonProcessingException ex) {
                    throw new IllegalStateException("Unable to render JSON output", ex);
                }
                return;
            }
            System.out.print(CliTableFormatter.format(headers, rows));
        }
    }

    @CommandLine.Command(name = "impact-by-node", description = "Show impact by node")
    static class ImpactByNodeCommand implements Runnable {

        @CommandLine.Option(names = "--node", required = true, description = "Node hostname")
        private String node;

        @CommandLine.Option(names = "--format", defaultValue = "TEXT", description = "Output format: ${COMPLETION-CANDIDATES}")
        private CliOutputFormat format;

        private final TopologyQueryService topologyQueryService;
        private final ObjectMapper objectMapper;

        ImpactByNodeCommand(TopologyQueryService topologyQueryService, ObjectMapper objectMapper) {
            this.topologyQueryService = topologyQueryService;
            this.objectMapper = objectMapper;
        }

        @Override
        public void run() {
            List<TopologyQueryService.ImpactView> rows = topologyQueryService.impactByNode(node);
            if (format == CliOutputFormat.JSON) {
                try {
                    System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(rows));
                    return;
                } catch (JsonProcessingException ex) {
                    throw new IllegalStateException("Unable to render JSON output", ex);
                }
            }
            System.out.print(CliTableFormatter.format(
                    List.of("COMPONENT", "DEPLOYMENT", "PEER_NODES", "SOURCE_CLUSTERS", "PEER_CLUSTERS"),
                    rows.stream().map(r -> List.of(
                            r.componentName(),
                            r.deploymentKey(),
                            String.join(",", r.peerNodes()),
                            String.join(",", r.sourceClusters()),
                            String.join(",", r.peerClusters()))).toList()));
        }
    }

    @CommandLine.Command(name = "generate-diagram", description = "Generate PlantUML diagram for a system")
    static class GenerateDiagramCommand implements Runnable {

        @CommandLine.Option(names = "--system", required = true, description = "System identifier")
        private String system;

        @CommandLine.Option(names = "--output", required = true, description = "Output file path")
        private Path output;

        private final DiagramGenerationService diagramGenerationService;

        GenerateDiagramCommand(DiagramGenerationService diagramGenerationService) {
            this.diagramGenerationService = diagramGenerationService;
        }

        @Override
        public void run() {
            try {
                String puml = diagramGenerationService.buildSystemPlantUml(system);
                if (output.getParent() != null) {
                    Files.createDirectories(output.getParent());
                }
                Files.writeString(output, puml);
                System.out.println("Diagram generated at " + output.toAbsolutePath());
            } catch (Exception ex) {
                throw new IllegalStateException("Unable to generate diagram", ex);
            }
        }
    }

    @Component
    static class Factory implements CommandLine.IFactory {
        private final org.springframework.beans.factory.config.AutowireCapableBeanFactory beanFactory;

        Factory(org.springframework.beans.factory.config.AutowireCapableBeanFactory beanFactory) {
            this.beanFactory = beanFactory;
        }

        @Override
        public <K> K create(Class<K> clazz) {
            return beanFactory.createBean(clazz);
        }
    }

    @Component
    static class Runner implements org.springframework.boot.ApplicationRunner {
        private final Factory factory;
        private final JDeployCliCommands root;
        private final org.springframework.core.env.Environment environment;

        Runner(Factory factory, JDeployCliCommands root, org.springframework.core.env.Environment environment) {
            this.factory = factory;
            this.root = root;
            this.environment = environment;
        }

        @Override
        public void run(org.springframework.boot.ApplicationArguments args) {
            if (!Boolean.parseBoolean(environment.getProperty("jdeploy.cli.enabled", "false"))) {
                return;
            }
            String[] raw = args.getSourceArgs();
            List<String> forwarded = java.util.Arrays.stream(raw)
                    .filter(arg -> !arg.startsWith("--jdeploy.cli.enabled="))
                    .toList();
            CommandLine commandLine = new CommandLine(root, factory);
            commandLine.setErr(new PrintWriter(System.err, true));
            int exit = commandLine.execute(forwarded.toArray(String[]::new));
            if (exit != 0) {
                throw new IllegalStateException("CLI command failed with exit code " + exit);
            }
            System.exit(0);
        }
    }
}
