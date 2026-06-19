package com.trustledgersaas.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * EmailService — Handles sending real emails via Gmail SMTP.
 *
 * All email sending logic is centralized here (never in controllers).
 * Uses Spring's JavaMailSender with Gmail SMTP configuration from
 * application.properties.
 *
 * This service is injected into AuthService, LoanService, and
 * ReminderSchedulerService wherever email notifications are needed.
 */
@Service
@Slf4j
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    /**
     * Sends a plain-text email.
     *
     * Purpose: Send email notifications (credentials, reminders, receipts).
     * Input: Recipient email, subject line, and email body text.
     * Output: None (sends email, logs success/failure).
     */
    public void sendEmail(String to, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("trustledger.app@gmail.com");
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);

            mailSender.send(message);
            log.info("Email sent successfully to: {} — Subject: {}", to, subject);
        } catch (Exception e) {
            // Log the error but don't crash the application — email failures
            // should not block the main business flow
            log.error("Failed to send email to: {} — Error: {}", to, e.getMessage());
        }
    }

    /**
     * Sends customer login credentials after account creation.
     */
    public void sendCustomerCredentials(String email, String name, String phone, String password, String shopName) {
        String subject = "Trust Ledger — Your Login Credentials";
        String body = "Dear " + name + ",\n\n" +
                "Your account has been created at " + shopName + " on Trust Ledger.\n\n" +
                "Login Details:\n" +
                "Phone/Email: " + phone + " or " + email + "\n" +
                "Password: " + password + "\n\n" +
                "Please change your password after your first login.\n\n" +
                "Login here: [Trust Ledger Customer Portal]\n\n" +
                "Regards,\n" +
                "Trust Ledger — Protect Gold. Preserve Trust.";

        sendEmail(email, subject, body);
    }

    /**
     * Sends an overdue loan reminder email.
     */
    public void sendOverdueReminder(String email, String customerName, String loanId,
                                     String goldItem, String shopName, int daysOverdue) {
        String subject = "Trust Ledger — Loan Payment Reminder (" + loanId + ")";
        String body = "Dear " + customerName + ",\n\n" +
                "This is a reminder that your gold loan " + loanId + " at " + shopName +
                " is " + daysOverdue + " day(s) overdue.\n\n" +
                "Gold Item: " + goldItem + "\n\n" +
                "Please make your payment at the earliest to avoid any further action.\n\n" +
                "Regards,\n" +
                "Trust Ledger — Protect Gold. Preserve Trust.";

        sendEmail(email, subject, body);
    }

    /**
     * Sends shop registration approval notification.
     */
    public void sendShopApprovedEmail(String email, String ownerName, String shopName, String baseUrl) {
        String subject = "Trust Ledger — Your Shop Has Been Approved!";
        String body = "Dear " + ownerName + ",\n\n" +
                "Great news! Your shop \"" + shopName + "\" has been approved on Trust Ledger.\n\n" +
                "You can now log in and purchase a subscription to activate your account.\n\n" +
                "Track your application: " + baseUrl + "/application-status?email=" + email + "\n\n" +
                "Regards,\n" +
                "Trust Ledger — Protect Gold. Preserve Trust.";

        sendEmail(email, subject, body);
    }

    /**
     * Sends shop registration rejection notification.
     */
    public void sendShopRejectedEmail(String email, String ownerName, String shopName,
                                      String reason, String baseUrl) {
        String subject = "Trust Ledger — Shop Registration Update";
        String body = "Dear " + ownerName + ",\n\n" +
                "Thank you for applying to join Trust Ledger with \"" + shopName + "\".\n\n" +
                "After reviewing your application, we are unable to approve your registration at this time.\n\n" +
                "Reason: " + reason + "\n\n" +
                "You can view the full status here: " + baseUrl + "/application-status?email=" + email + "\n\n" +
                "If you believe this was a mistake, please contact our support team.\n\n" +
                "Regards,\n" +
                "Trust Ledger — Protect Gold. Preserve Trust.";

        sendEmail(email, subject, body);
    }

    /**
     * Sends the final seizure warning email (Day 30 overdue).
     */
    public void sendSeizureWarning(String email, String customerName, String loanId,
                                    String goldItem, String shopName) {
        String subject = "URGENT: Final Warning — Loan " + loanId + " — Gold Seizure Notice";
        String body = "Dear " + customerName + ",\n\n" +
                "FINAL WARNING: Your gold loan " + loanId + " at " + shopName +
                " is now 30+ days overdue.\n\n" +
                "Gold Item: " + goldItem + "\n\n" +
                "If payment is not received within the next few days, the pledged gold item " +
                "will be seized as per the loan agreement.\n\n" +
                "Please contact " + shopName + " immediately to resolve this.\n\n" +
                "Regards,\n" +
                "Trust Ledger — Protect Gold. Preserve Trust.";

        sendEmail(email, subject, body);
    }
}
