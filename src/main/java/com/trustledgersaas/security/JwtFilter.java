package com.trustledgersaas.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * JwtFilter — Intercepts every incoming HTTP request to check for a valid JWT token.
 *
 * This filter extends OncePerRequestFilter (as required by the project spec),
 * which guarantees it runs exactly once per request.
 *
 * How it works (simple, step-by-step):
 * 1. Read the "Authorization" header from the incoming HTTP request
 * 2. If the header exists and starts with "Bearer ", extract the token part
 * 3. Validate the token using JwtUtil (checks signature and expiry)
 * 4. If valid, extract the user's email and role from the token
 * 5. Set the user's authentication in Spring Security's SecurityContextHolder
 * 6. Pass the request to the next filter in the chain
 *
 * If there's no token or it's invalid, the request continues WITHOUT authentication
 * (Spring Security will then block it if the URL requires authentication).
 */
@Component
@Slf4j
public class JwtFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    /**
     * The main filter method — runs for every HTTP request.
     *
     * Purpose: Extract JWT from request, validate it, and set authentication.
     * Input: The incoming HTTP request and response objects.
     * Output: None (sets SecurityContextHolder and passes to next filter).
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // Step 1: Try to extract the JWT token from the Authorization header
        String token = extractTokenFromRequest(request);

        // Step 2: If a token exists and is valid, set up authentication
        if (token != null && jwtUtil.validateToken(token)) {

            // Step 3: Extract the user's email and role from the token
            String email = jwtUtil.extractEmail(token);
            String role = jwtUtil.extractRole(token);

            // Step 4: Create a Spring Security authentication object
            // This tells Spring Security "this user is authenticated with this role"
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            email,                                           // principal (who they are)
                            null,                                            // credentials (not needed, token already verified)
                            Collections.singletonList(new SimpleGrantedAuthority(role))  // authorities (what role they have)
                    );

            // Attach request details (IP address, session ID, etc.) for audit logging
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            // Step 5: Set this authentication in the SecurityContextHolder
            // After this line, Spring Security considers this user "logged in" for this request
            SecurityContextHolder.getContext().setAuthentication(authentication);

            log.debug("Authenticated user: {} with role: {}", email, role);
        }

        // Step 6: Pass the request to the next filter in the chain
        // (whether or not authentication was set — if it wasn't, Spring Security
        // will block the request if the URL requires authentication)
        filterChain.doFilter(request, response);
    }

    /**
     * Extracts the JWT token from the "Authorization" header.
     *
     * Purpose: Parse the raw header to get just the token string.
     * Input: The HTTP request.
     * Output: The token string (without "Bearer " prefix), or null if not found.
     *
     * Expected header format: "Authorization: Bearer eyJhbGciOi..."
     * We strip the "Bearer " prefix to get just the token part.
     */
    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");

        // Check if the header exists and starts with "Bearer "
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            // Return everything after "Bearer " (the actual token)
            return bearerToken.substring(7);
        }

        return null;
    }
}
