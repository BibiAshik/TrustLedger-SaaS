package com.trustledgersaas.security;

import com.trustledgersaas.entity.User;
import com.trustledgersaas.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * CustomUserDetailsService — Loads user data from the database for Spring Security.
 *
 * Spring Security needs a way to load user details (email, password, roles)
 * from whatever data source we're using (in our case, MySQL via JPA).
 * This class implements UserDetailsService, which Spring Security calls
 * automatically during the login process.
 *
 * The main method, loadUserByUsername(), takes a "username" (which in our
 * case is an email address) and returns a UserDetails object that Spring
 * Security uses to verify the password and authorize the user.
 */
@Service
@Slf4j
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    /**
     * Loads a user from the database by their email address.
     *
     * Purpose: Spring Security calls this during authentication to get the user's
     *          stored password (for comparison) and their roles (for authorization).
     * Input: username — in our system, this is the user's email address.
     * Output: A UserDetails object containing email, hashed password, and role.
     * Throws: UsernameNotFoundException if no user with that email exists.
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // Try to find the user by email in the database
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> {
                    log.warn("Login attempt with non-existent email: {}", username);
                    // Use a generic message — don't reveal that the email doesn't exist
                    return new UsernameNotFoundException("Invalid email or password");
                });

        // Build and return Spring Security's UserDetails object
        // This contains everything Spring Security needs to authenticate and authorize the user
        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),                                              // username (email)
                user.getPassword(),                                           // BCrypt-hashed password
                Collections.singletonList(new SimpleGrantedAuthority(user.getRole()))  // granted authorities (role)
        );
    }
}
