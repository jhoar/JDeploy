package com.jdeploy.cli;

import com.jdeploy.service.PreconditionViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CliAuthenticationService {

    private final String mode;
    private final String username;
    private final String password;

    public CliAuthenticationService(@Value("${jdeploy.cli.auth.mode:trusted}") String mode,
                                    @Value("${jdeploy.cli.auth.service-account.username:cli-service}") String username,
                                    @Value("${jdeploy.cli.auth.service-account.password:cli-service-password}") String password) {
        this.mode = mode;
        this.username = username;
        this.password = password;
    }

    public void authenticate(String providedUser, String providedPassword) {
        if ("trusted".equalsIgnoreCase(mode)) {
            return;
        }
        if (!"service-account".equalsIgnoreCase(mode)) {
            throw new PreconditionViolationException("Unsupported CLI auth mode: " + mode);
        }
        if (providedUser == null || providedPassword == null) {
            throw new PreconditionViolationException("CLI service account credentials are required when jdeploy.cli.auth.mode=service-account");
        }
        if (!username.equals(providedUser) || !password.equals(providedPassword)) {
            throw new PreconditionViolationException("Invalid CLI service account credentials");
        }
    }
}
