package org.springframework.boot.autoconfigure.web.servlet;

/**
 * Compatibility shim for Vaadin versions that still reference Spring Boot 3's
 * {@code WebMvcAutoConfiguration} class in auto-configuration annotations.
 *
 * <p>Spring Boot 4 removed this type, which causes class resolution failures while
 * processing Vaadin auto-configuration metadata. Defining this no-op placeholder keeps
 * annotation metadata resolution working until Vaadin upgrades to Spring Boot 4 APIs.
 */
public final class WebMvcAutoConfiguration {

    private WebMvcAutoConfiguration() {
        // utility class
    }
}
