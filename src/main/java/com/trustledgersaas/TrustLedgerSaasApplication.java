package com.trustledgersaas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * TrustLedgerSaasApplication — Main entry point for the Trust Ledger SaaS platform.
 *
 * This is the root Spring Boot application class. When you run this class,
 * Spring Boot starts an embedded Tomcat server, scans all packages under
 * com.trustledgersaas for components (@Controller, @Service, @Repository, etc.),
 * and wires everything together automatically.
 *
 * @EnableScheduling — Enables Spring's scheduled task execution. This is needed
 * for the daily reminder/overdue-check job (ReminderSchedulerService) that runs
 * once per day to flip overdue loans and send reminder notifications.
 */
@SpringBootApplication
@EnableScheduling
public class TrustLedgerSaasApplication {

    public static void main(String[] args) {
        SpringApplication.run(TrustLedgerSaasApplication.class, args);
    }
}
