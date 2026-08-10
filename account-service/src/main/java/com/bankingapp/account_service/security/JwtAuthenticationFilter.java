package com.bankingapp.account_service.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends HttpFilter {

    private static final List<String> INTERNAL_ROUTES = List.of("/debit", "/credit");

    private final JwtUtil jwtUtil;
    private final String internalApiKey;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, @Value("${internal.api-key}") String internalApiKey) {
        this.jwtUtil = jwtUtil;
        this.internalApiKey = internalApiKey;
    }

    @Override
    protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        String path = request.getRequestURI();

        if (isInternalRoute(path)) {
            String providedKey = request.getHeader("X-Internal-Api-Key");
            if (providedKey == null || !providedKey.equals(internalApiKey)) {
                reject(response, "Forbidden: missing or invalid internal API key", HttpServletResponse.SC_FORBIDDEN);
                return;
            }
            chain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            reject(response, "Missing Authorization header", HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        String token = authHeader.substring(7);

        if (!jwtUtil.isTokenValid(token)) {
            reject(response, "Invalid or expired token", HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean isInternalRoute(String path) {
        return INTERNAL_ROUTES.stream().anyMatch(path::endsWith);
    }

    private void reject(HttpServletResponse response, String message, int status) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\": \"" + message + "\"}");
    }
}
