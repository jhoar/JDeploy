package com.jdeploy.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class CommonLogFormatAccessFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger("ACCESS_LOG");
    static final DateTimeFormatter COMMON_LOG_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MMM/yyyy:HH:mm:ss Z");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } finally {
            logger.info(buildCommonLogEntry(request, response));
        }
    }

    static String buildCommonLogEntry(HttpServletRequest request, HttpServletResponse response) {
        String remoteHost = sanitizeForLog(request.getRemoteAddr());
        String authUser = currentUser(request);
        String requestLine = sanitizeForLog(buildRequestLine(request));
        String timestamp = ZonedDateTime.now(ZoneId.systemDefault()).format(COMMON_LOG_DATE_FORMAT);
        String bytesSent = contentLength(response);

        return String.format("%s - %s [%s] \"%s\" %d %s",
                remoteHost,
                authUser,
                timestamp,
                requestLine,
                response.getStatus(),
                bytesSent);
    }

    private static String contentLength(HttpServletResponse response) {
        return sanitizeForLog(response.getHeader(HttpHeaders.CONTENT_LENGTH));
    }

    private static String buildRequestLine(HttpServletRequest request) {
        String query = request.getQueryString();
        String pathWithQuery = query == null ? request.getRequestURI() : request.getRequestURI() + "?" + query;
        return request.getMethod() + " " + pathWithQuery + " " + request.getProtocol();
    }

    private static String currentUser(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && !(authentication instanceof AnonymousAuthenticationToken)) {
            return sanitizeForLog(authentication.getName());
        }

        if (isLoginAttempt(request)) {
            return sanitizeForLog(request.getParameter("username"));
        }

        return "-";
    }

    private static boolean isLoginAttempt(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod()) && "/login".equals(request.getRequestURI());
    }

    private static String defaultValue(String value) {
        return (value == null || value.isBlank()) ? "-" : value;
    }

    private static String sanitizeForLog(String value) {
        String normalized = defaultValue(value);
        if ("-".equals(normalized)) {
            return normalized;
        }

        StringBuilder sanitized = new StringBuilder(normalized.length());
        for (int i = 0; i < normalized.length(); i++) {
            char current = normalized.charAt(i);
            if (current == '\r' || current == '\n' || Character.isISOControl(current)) {
                sanitized.append(' ');
            } else {
                sanitized.append(current);
            }
        }

        return sanitized.toString();
    }
}
