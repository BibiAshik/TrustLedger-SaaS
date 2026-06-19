package com.trustledgersaas.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * SmsService — Handles SMS notifications.
 *
 * Currently logs messages instead of calling a real SMS API because a live
 * SMS provider (Fast2SMS, MSG91, Twilio, etc.) and India DLT registration are
 * not configured yet.
 *
 * When you get a real SMS plan, change ONLY this class:
 * 1. Add your provider API key to application.properties
 * 2. Replace sendSms() with the provider's HTTP API call
 * 3. Register DLT templates for credential and reminder messages
 * No other service or controller needs to change.
 */
@Service
@Slf4j
public class SmsService {

    /**
     * Sends an SMS message.
     *
     * Today: logs the message (simulated delivery).
     * Production: replace the body with your SMS provider API call.
     */
    public boolean sendSms(String phoneNumber, String message) {
        log.info("========== SMS (simulated) ==========");
        log.info("To: {}", phoneNumber);
        log.info("Message: {}", message);
        log.info("Status: SIMULATED SUCCESS");
        log.info("====================================");

        return true;
    }

    public boolean sendCredentialsSms(String phoneNumber, String name, String password, String shopName) {
        String message = "Dear " + name + ", your Trust Ledger login for " + shopName + " is ready. "
                + "Phone: " + phoneNumber + ", Password: " + password
                + ". Please change your password after first login.";
        return sendSms(phoneNumber, message);
    }

    public boolean sendReminderSms(String phoneNumber, String name, String loanId, int daysOverdue) {
        String message = "Dear " + name + ", your loan " + loanId
                + " is " + daysOverdue + " day(s) overdue. Please pay at the earliest.";
        return sendSms(phoneNumber, message);
    }
}
