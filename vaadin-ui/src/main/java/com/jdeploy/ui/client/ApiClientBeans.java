package com.jdeploy.ui.client;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(ApiClientConfiguration.class)
public class ApiClientBeans {

    @Bean
    RestClient restClient(RestClient.Builder builder, ApiClientConfiguration config) {
        return builder
                .baseUrl(config.baseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
