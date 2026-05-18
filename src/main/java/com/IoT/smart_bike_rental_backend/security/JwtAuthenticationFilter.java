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
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        if (isPublicPath(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String jwt = extractToken(request);
            if (jwt != null && jwtTokenProvider.validateToken(jwt) && !jwtTokenProvider.isTokenExpired(jwt)) {
                request.setAttribute("userId", Long.parseLong(jwtTokenProvider.getUserIdFromToken(jwt)));
                request.setAttribute("email",  jwtTokenProvider.getEmailFromToken(jwt));
                request.setAttribute("role",   jwtTokenProvider.getRoleFromToken(jwt));

                Authentication auth = new UsernamePasswordAuthenticationToken(
                        jwtTokenProvider.getEmailFromToken(jwt), null, new ArrayList<>());
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        } catch (Exception e) {
            System.err.println("Could not set user authentication: " + e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private boolean isPublicPath(String path) {
        return path.startsWith("/swagger-ui")
            || path.startsWith("/v3/api-docs")
            || path.startsWith("/swagger-resources")
            || path.startsWith("/webjars")
            || path.equals("/swagger-ui.html")
            || path.equals("/swagger-ui/index.html")
            || path.equals("/favicon.ico")
            || path.equals("/test.html")
            || path.equals("/index.html")
            || path.equals("/api/auth/register")
            || path.equals("/api/auth/login")
            || path.equals("/api/auth/validate")
            || path.equals("/api/auth/forgot-password")
            || path.equals("/api/auth/reset-password")
            || path.equals("/api/auth/validate-reset-code")
            || (path.startsWith("/api/bikes")
                && !path.matches(".*/api/bikes/\\d+/(book|return|details).*"));
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
