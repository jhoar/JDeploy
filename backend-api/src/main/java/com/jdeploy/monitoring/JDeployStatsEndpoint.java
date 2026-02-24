package com.jdeploy.monitoring;

import com.jdeploy.service.OperationMetricsService;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Endpoint(id = "jdeployStats")
public class JDeployStatsEndpoint {

    private final OperationMetricsService operationMetricsService;

    public JDeployStatsEndpoint(OperationMetricsService operationMetricsService) {
        this.operationMetricsService = operationMetricsService;
    }

    @ReadOperation
    public Map<String, Object> stats() {
        return operationMetricsService.snapshot();
    }
}
