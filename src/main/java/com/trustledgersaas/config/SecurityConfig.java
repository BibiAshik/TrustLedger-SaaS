package com.trustledgersaas.config;

import com.trustledgersaas.security.JwtFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * SecurityConfig — Central configuration for Spring Security in Trust Ledger.
 *
 * This class defines:
 * 1. Which URLs require authentication and which are public
 * 2. Which roles can access which URL patterns
 * 3. Where the JWT filter sits in the filter chain
 * 4. How passwords are encoded (BCrypt)
 * 5. Session management (stateless, since we use JWT)
 *
 * IMPORTANT: This uses BOTH URL-level rules (defined here) AND
 * method-level @PreAuthorize annotations (on controllers) — both
 * layers together for defense-in-depth security.
 *
 * @EnableMethodSecurity — Enables @PreAuthorize annotations on controller methods
 * so we can add role checks like @PreAuthorize("hasRole('SHOP_OWNER')") directly
 * on individual endpoints.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Autowired
    private JwtFilter jwtFilter;

    /**
     * Defines the security filter chain — the core of Spring Security configuration.
     *
     * Purpose: Configure URL access rules, session policy, and filter ordering.
     * Input: HttpSecurity builder provided by Spring.
     * Output: A configured SecurityFilterChain bean.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
            // ===== CSRF Protection =====
            // Disabled because we're using JWT tokens (stateless API).
            // CSRF protection is mainly needed for cookie-based session authentication,
            // which we don't use. JWT in the Authorization header is immune to CSRF attacks.
            .csrf(csrf -> csrf.disable())

            // ===== Session Management =====
            // STATELESS: Spring Security will NOT create or use HTTP sessions.
            // Every request is authenticated independently via the JWT token.
            // This is the correct approach for JWT-based authentication.
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // ===== URL-Level Authorization Rules =====
            // These define which URLs require which roles.
            // Rules are checked top-to-bottom; the FIRST matching rule wins.
            .authorizeHttpRequests(auth -> auth

                // ----- PUBLIC PAGES (no login required) -----
                // The homepage, login pages, registration page, and static assets
                // must be accessible to everyone without authentication.
                .requestMatchers("/", "/index.html").permitAll()
                .requestMatchers("/login", "/register-shop", "/register", "/forgot-password", "/reset-password").permitAll()
                .requestMatchers("/application-status").permitAll()
                .requestMatchers("/customer/login").permitAll()

                // ----- HIDDEN ADMIN LOGIN (must come BEFORE /admin/**) -----
                // The admin login page is hidden (no nav link) — only accessible
                // by typing /admin/login directly. It must be public so the admin
                // can reach the login form before they have a token.
                .requestMatchers("/admin/login").permitAll()

                // ----- PUBLIC API ENDPOINTS -----
                // Authentication-related APIs (login, register, forgot password)
                // must be public — you can't require a login to log in!
                .requestMatchers("/api/auth/**").permitAll()

                // ----- STATIC RESOURCES -----
                // CSS, JavaScript, images, and fonts must load without authentication
                // otherwise the login page itself won't render properly.
                .requestMatchers("/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()
                .requestMatchers("/uploads/**").permitAll()

                // ----- SERVER-RENDERED HTML PAGES -----
                // The frontend stores JWT tokens in localStorage and sends them
                // only on fetch() calls to /api/**. Normal browser navigation to
                // /shop/dashboard cannot include an Authorization header, so the
                // HTML shell pages must be allowed to load. Each page then calls
                // protected API endpoints with the JWT token before showing data.
                .requestMatchers("/admin/**").permitAll()
                .requestMatchers("/shop/**").permitAll()
                .requestMatchers("/customer/**").permitAll()

                // ----- SUPER ADMIN API ENDPOINTS -----
                // Only users with ROLE_SUPER_ADMIN can access admin data/actions.
                .requestMatchers("/api/admin/**").hasRole("SUPER_ADMIN")

                // ----- SHOP OWNER API ENDPOINTS -----
                // Only users with ROLE_SHOP_OWNER can access shop business data.
                .requestMatchers("/api/shop/**").hasRole("SHOP_OWNER")

                // ----- CUSTOMER API ENDPOINTS -----
                // Only users with ROLE_CUSTOMER can access customer portal data.
                .requestMatchers("/api/customer/**").hasRole("CUSTOMER")

                // ----- PAYMENT API (mixed roles) -----
                // Payment endpoints serve BOTH Shop Owners (subscription payments)
                // and Customers (loan interest payments). The specific role check
                // is enforced via @PreAuthorize on each controller method.
                .requestMatchers("/api/payment/**").authenticated()

                // ----- EVERYTHING ELSE -----
                // Any URL not explicitly listed above requires authentication.
                // This is a safety net — better to accidentally block something
                // than to accidentally expose it.
                .anyRequest().authenticated()
            )

            // ===== JWT Filter Positioning =====
            // Add our custom JWT filter BEFORE Spring Security's default
            // UsernamePasswordAuthenticationFilter. This means our filter runs first,
            // reads the JWT from the header, and sets up the SecurityContext.
            // Then Spring Security's normal filters run with that context already set.
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Password encoder bean — BCrypt is the industry standard for hashing passwords.
     *
     * Purpose: Encode passwords before storing them, and verify passwords during login.
     * BCrypt automatically handles salting (adding random data before hashing) so
     * even identical passwords produce different hashes.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * AuthenticationManager bean — the built-in Spring Security authentication manager.
     *
     * Purpose: Used by the AuthService to authenticate login requests.
     * This is the BUILT-IN manager (as required by the project spec) —
     * we do NOT write a custom AuthenticationProvider.
     *
     * How it works: When we call authManager.authenticate(token), Spring Security:
     * 1. Calls CustomUserDetailsService.loadUserByUsername() to load the user
     * 2. Compares the provided password against the stored BCrypt hash
     * 3. If they match, returns a fully authenticated token
     * 4. If they don't match, throws BadCredentialsException
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }
}
