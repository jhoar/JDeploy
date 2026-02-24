package com.jdeploy.ui.security;

import com.jdeploy.security.ApiRoles;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class VaadinActionAuthorizationService {

    @PreAuthorize("hasAuthority('" + ApiRoles.TOPOLOGY_INGEST + "')")
    public void assertCanSubmitTopology() {
    }

    @PreAuthorize("hasAuthority('" + ApiRoles.ARTIFACT_GENERATE + "')")
    public void assertCanGenerateArtifacts() {
    }

    @PreAuthorize("hasAnyAuthority('" + ApiRoles.ARTIFACT_GENERATE + "','" + ApiRoles.READ_ONLY + "')")
    public void assertCanDownloadArtifacts() {
    }

    @PreAuthorize("hasAnyAuthority('" + ApiRoles.EDITOR + "','" + ApiRoles.ADMIN + "')")
    public void assertCanEditTopology() {
    }
}
