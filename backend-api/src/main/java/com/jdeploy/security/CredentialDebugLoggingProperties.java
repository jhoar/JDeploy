package com.jdeploy.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jdeploy.debug.credentials")
public class CredentialDebugLoggingProperties {

    /**
     * Enables plaintext credential logging for local debugging only.
     */
    private boolean enabled;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}

