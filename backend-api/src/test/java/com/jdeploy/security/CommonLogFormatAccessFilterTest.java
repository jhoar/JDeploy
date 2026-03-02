package com.jdeploy.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CommonLogFormatAccessFilterTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void buildsCommonLogFormatLineForAuthenticatedUser() {
        TestingAuthenticationToken auth = new TestingAuthenticationToken("reader", "n/a", ApiRoles.READ_ONLY);
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/topology/systems");
        request.setQueryString("page=1");
        request.setProtocol("HTTP/1.1");
        request.setRemoteAddr("203.0.113.10");

        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);
        response.setHeader("Content-Length", "123");

        String line = CommonLogFormatAccessFilter.buildCommonLogEntry(request, response);

        assertTrue(line.matches("203\\.0\\.113\\.10 - reader \\[.+] \\\"GET /api/topology/systems\\?page=1 HTTP/1\\.1\\\" 200 123"));
    }

    @Test
    void usesSubmittedUsernameForFailedLoginAttempts() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
        request.setProtocol("HTTP/1.1");
        request.setRemoteAddr("198.51.100.20");
        request.addParameter("username", "alice");

        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(401);

        String line = CommonLogFormatAccessFilter.buildCommonLogEntry(request, response);

        assertTrue(line.matches("198\\.51\\.100\\.20 - alice \\[.+] \\\"POST /login HTTP/1\\.1\\\" 401 -"));
    }

    @Test
    void usesDashWhenUserIsAnonymousAndLengthUnknown() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
        request.setProtocol("HTTP/1.1");
        request.setRemoteAddr("198.51.100.15");

        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(401);

        String line = CommonLogFormatAccessFilter.buildCommonLogEntry(request, response);

        assertTrue(line.matches("198\\.51\\.100\\.15 - - \\[.+] \\\"POST /login HTTP/1\\.1\\\" 401 -"));
    }

    @Test
    void sanitizesUserControlledValuesToPreventLogInjection() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
        request.setQueryString("next=/topology\r\nsecond=true");
        request.setProtocol("HTTP/1.1");
        request.setRemoteAddr("203.0.113.5\r\nforged");
        request.addParameter("username", "alice\nadmin");

        HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.getStatus()).thenReturn(401);
        when(response.getHeader("Content-Length")).thenReturn("12\r\n999");

        String line = CommonLogFormatAccessFilter.buildCommonLogEntry(request, response);

        assertTrue(line.contains("203.0.113.5  forged - alice admin"));
        assertTrue(line.contains("\"POST /login?next=/topology  second=true HTTP/1.1\" 401 12999"));
        assertTrue(!line.contains("\n") && !line.contains("\r"));
    }
}
