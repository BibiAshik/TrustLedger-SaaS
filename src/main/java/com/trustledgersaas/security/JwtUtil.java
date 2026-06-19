package com.trustledgersaas.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JwtUtil — Utility class for creating and validating JSON Web Tokens (JWTs).
 *
 * A JWT is a compact, URL-safe token that carries claims (like who the user is
 * and what role they have). After a user logs in successfully, we generate a JWT
 * and send it back. The frontend stores it and sends it with every subsequent
 * request in the "Authorization: Bearer <token>" header.
 *
 * This class handles:
 * 1. Generating a new JWT token after successful login
 * 2. Validating an incoming JWT token (is it expired? was it tampered with?)
 * 3. Extracting claims from a valid token (email, role, shop ID, etc.)
 *
 * The secret key and expiry time are loaded from application.properties —
 * they are NEVER hardcoded in Java code.
 */
@Component
@Slf4j
public class JwtUtil {

    /**
     * The secret key used to sign JWTs. Loaded from application.properties.
     * This key must be kept secret — anyone who knows it can forge tokens.
     */
    @Value("${jwt.secret}")
    private String jwtSecret;

    /**
     * How long a JWT remains valid, in milliseconds. Loaded from application.properties.
     * Default: 86400000 (24 hours).
     */
    @Value("${jwt.expiration}")
    private long jwtExpiration;

    /**
     * Creates the cryptographic signing key from the secret string.
     * HMAC-SHA256 requires at least a 256-bit key.
     *
     * Purpose: Convert the secret string into a Key object that JJWT can use.
     * Input: Uses the jwtSecret field (loaded from properties).
     * Output: A SecretKey object for signing/verifying tokens.
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Generates a new JWT token for a successfully authenticated user.
     *
     * Purpose: Create a signed token containing the user's identity and role.
     * Input: email (user's login email), role (e.g. "ROLE_SHOP_OWNER"),
     *        userId (database ID), and optional shopId (null for Super Admin).
     * Output: A signed JWT string that the frontend stores and sends back.
     */
    public String generateToken(String email, String role, Long userId, Long shopId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpiration);

        // Build the JWT with claims (pieces of information embedded in the token)
        JwtBuilder builder = Jwts.builder()
                .subject(email)                      // "sub" claim: who is this token for?
                .claim("role", role)                  // Custom claim: what role does this user have?
                .claim("userId", userId)              // Custom claim: database user ID
                .issuedAt(now)                        // "iat" claim: when was this token created?
                .expiration(expiryDate)               // "exp" claim: when does this token expire?
                .signWith(getSigningKey());           // Sign with our secret key

        // Only add shopId claim if it's not null (Super Admin doesn't have a shop)
        if (shopId != null) {
            builder.claim("shopId", shopId);
        }

        return builder.compact();
    }

    /**
     * Extracts the email (subject) from a valid JWT token.
     *
     * Purpose: Get the user's email from the token for authentication.
     * Input: A JWT token string.
     * Output: The email address stored in the token's "sub" claim.
     */
    public String extractEmail(String token) {
        return parseClaims(token).getSubject();
    }

    /**
     * Extracts the user's role from a valid JWT token.
     *
     * Purpose: Get the user's role for authorization checks.
     * Input: A JWT token string.
     * Output: The role string (e.g. "ROLE_SHOP_OWNER").
     */
    public String extractRole(String token) {
        return parseClaims(token).get("role", String.class);
    }

    /**
     * Extracts the user's database ID from a valid JWT token.
     *
     * Purpose: Get the user ID without needing a database query.
     * Input: A JWT token string.
     * Output: The user's Long ID.
     */
    public Long extractUserId(String token) {
        return parseClaims(token).get("userId", Long.class);
    }

    /**
     * Extracts the shop ID from a valid JWT token (null for Super Admin).
     *
     * Purpose: Identify which shop the logged-in user belongs to.
     * Input: A JWT token string.
     * Output: The shop's Long ID, or null if user is Super Admin.
     */
    public Long extractShopId(String token) {
        return parseClaims(token).get("shopId", Long.class);
    }

    /**
     * Validates whether a JWT token is valid (not expired, not tampered with).
     *
     * Purpose: Check if a token should be trusted before granting access.
     * Input: A JWT token string.
     * Output: true if valid, false if expired/tampered/malformed.
     */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("JWT token has expired: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.warn("Invalid JWT token format: {}", e.getMessage());
        } catch (SecurityException e) {
            log.warn("JWT signature validation failed: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("JWT token is empty or null: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Parses and verifies the JWT token, returning its claims.
     * This is the internal method that does the actual cryptographic verification.
     *
     * Purpose: Verify the token's signature and extract its claims.
     * Input: A JWT token string.
     * Output: The Claims object containing all the data in the token.
     * Throws: Various JwtExceptions if the token is invalid.
     */
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
