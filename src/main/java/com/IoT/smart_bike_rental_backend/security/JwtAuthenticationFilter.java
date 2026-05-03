package com.IoT.smart_bike_rental_backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        // Skip JWT validation for Swagger/OpenAPI and public endpoints
        String requestPath = request.getRequestURI();
        if (isPublicPath(requestPath)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String jwt = getJwtFromRequest(request);

            if (jwt != null && jwtTokenProvider.validateToken(jwt) && !jwtTokenProvider.isTokenExpired(jwt)) {
                String userId = jwtTokenProvider.getUserIdFromToken(jwt);
                String email = jwtTokenProvider.getEmailFromToken(jwt);
                String role = jwtTokenProvider.getRoleFromToken(jwt);

                // Create authentication token
                Authentication authentication = new UsernamePasswordAuthenticationToken(
                        email, null, new ArrayList<>()
                );
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception e) {
            System.err.println("Could not set user authentication in security context: " + e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private boolean isPublicPath(String requestPath) {
        // Swagger/OpenAPI endpoints
        if (requestPath.startsWith("/swagger-ui") ||
                requestPath.startsWith("/v3/api-docs") ||
                requestPath.startsWith("/swagger-resources") ||
                requestPath.startsWith("/webjars") ||
                requestPath.equals("/swagger-ui.html") ||
                requestPath.equals("/swagger-ui/index.html") ||
                requestPath.equals("/favicon.ico") ||
                requestPath.equals("/test.html") ||
                requestPath.equals("/index.html")) {
            return true;
        }

        // Public authentication endpoints
        if (requestPath.equals("/api/auth/register") ||
                requestPath.equals("/api/auth/login") ||
                requestPath.equals("/api/auth/validate") ||
                requestPath.equals("/api/auth/forgot-password") ||
                requestPath.equals("/api/auth/reset-password") ||
                requestPath.equals("/api/auth/validate-reset-code")) {
            return true;
        }

        // Public bike listing endpoints
        if (requestPath.startsWith("/api/bikes") && !requestPath.matches(".*/api/bikes/\\d+/(book|return|details).*")) {
            return true;
        }

        return false;
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
