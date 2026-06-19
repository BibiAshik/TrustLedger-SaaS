package com.trustledgersaas.config;

import com.trustledgersaas.entity.User;
import com.trustledgersaas.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * DataInitializer — Seeds the Super Admin account on first application boot.
 *
 * There is exactly ONE Super Admin account (Bibi), and it is created here
 * at startup time — never through any public registration form.
 *
 * This runs once when the application starts. If the Super Admin already
 * exists (e.g. on subsequent restarts), it does nothing.
 *
 * CommandLineRunner: Spring Boot calls the run() method after the application
 * context is fully initialized. This is the simplest way to run startup logic.
 */
@Component
@Slf4j
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${app.superadmin.email}")
    private String superAdminEmail;

    @Value("${app.superadmin.password}")
    private String superAdminPassword;

    @Value("${app.superadmin.name}")
    private String superAdminName;

    /**
     * Seeds the Super Admin account if it doesn't already exist.
     *
     * Purpose: Ensure there is always exactly one Super Admin in the database.
     * Input: Credentials loaded from application.properties.
     * Output: Creates a User with ROLE_SUPER_ADMIN if one doesn't exist.
     */
    @Override
    public void run(String... args) {
        // Check if a Super Admin already exists
        if (userRepository.findByRole("ROLE_SUPER_ADMIN").isEmpty()) {

            User superAdmin = User.builder()
                    .email(superAdminEmail)
                    .password(passwordEncoder.encode(superAdminPassword))
                    .fullName(superAdminName)
                    .role("ROLE_SUPER_ADMIN")
                    .isFirstLogin(false)
                    .build();

            userRepository.save(superAdmin);

            log.info("========================================");
            log.info("Super Admin account created successfully!");
            log.info("Email: {}", superAdminEmail);
            log.info("Password: [set in application.properties]");
            log.info("Login URL: /admin/login");
            log.info("========================================");

        } else {
            log.info("Super Admin account already exists — skipping seed.");
        }
    }
}
