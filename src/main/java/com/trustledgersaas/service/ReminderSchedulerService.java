package com.trustledgersaas.service;

import com.trustledgersaas.entity.GoldLoan;
import com.trustledgersaas.entity.ReminderLog;
import com.trustledgersaas.repository.GoldLoanRepository;
import com.trustledgersaas.repository.ReminderLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * ReminderSchedulerService — The ONLY scheduled task in the entire project.
 *
 * This service runs automatically once per day (early morning at 6:00 AM IST).
 * It performs exactly two jobs:
 *
 * 1. FLIP OVERDUE: Finds all ACTIVE loans whose dueDate has passed and
 *    changes their status to OVERDUE. This is the one thing that IS automated.
 *
 * 2. SEND REMINDERS: For all OVERDUE loans, checks how many days overdue
 *    they are and sends reminders at specific thresholds:
 *    - Day 1 overdue  → Reminder 1 (email + SMS)
 *    - Day 7 overdue  → Reminder 2
 *    - Day 15 overdue → Reminder 3
 *    - Day 30 overdue → FINAL WARNING (email + SMS + seizure warning flag)
 *
 * Uses ReminderLog to prevent sending the same reminder twice.
 *
 * IMPORTANT: This does NOT use Quartz Scheduler or any complex scheduling
 * framework — just a simple @Scheduled(cron) annotation, which is sufficient.
 */
@Service
@Slf4j
public class ReminderSchedulerService {

    @Autowired
    private GoldLoanRepository goldLoanRepository;

    @Autowired
    private ReminderLogRepository reminderLogRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private SmsService smsService;

    /**
     * The daily scheduled job — runs once per day at 6:00 AM.
     *
     * Purpose: Automate the overdue status transition and send reminder notifications.
     * Input: None (reads from the database).
     * Output: Updates loan statuses and sends emails and SMS notifications.
     *
     * Cron expression breakdown: "0 0 6 * * ?"
     *   0 — at second 0
     *   0 — at minute 0
     *   6 — at hour 6 (6:00 AM)
     *   * — every day of the month
     *   * — every month
     *   ? — any day of the week
     */
    @Scheduled(cron = "0 0 6 * * ?")
    @Transactional
    public void runDailyOverdueCheckAndReminders() {
        log.info("========== Daily Overdue Check & Reminder Job Started ==========");

        // ==================== STEP 1: FLIP ACTIVE → OVERDUE ====================
        // Find all loans that are still ACTIVE but whose due date has already passed
        flipOverdueLoans();

        // ==================== STEP 2: SEND REMINDERS FOR OVERDUE LOANS ====================
        // Check all OVERDUE loans and send reminders at the appropriate thresholds
        sendOverdueReminders();

        log.info("========== Daily Overdue Check & Reminder Job Completed ==========");
    }

    /**
     * Flips all ACTIVE loans whose due date has passed to OVERDUE status.
     *
     * Purpose: This is the ONLY automatic status change in the entire loan lifecycle.
     *          Everything else (CLOSED, SEIZED, extend due date) is a manual shop owner action.
     * Input: Reads ACTIVE loans from the database whose dueDate < today.
     * Output: Updates those loans' status to OVERDUE.
     */
    private void flipOverdueLoans() {
        LocalDate today = LocalDate.now();

        // Find all ACTIVE loans whose due date is strictly before today
        List<GoldLoan> overdueLoans = goldLoanRepository.findByStatusAndDueDateBefore("ACTIVE", today);

        if (overdueLoans.isEmpty()) {
            log.info("No ACTIVE loans have become overdue today.");
            return;
        }

        int count = 0;
        for (GoldLoan loan : overdueLoans) {
            loan.setStatus("OVERDUE");
            goldLoanRepository.save(loan);
            count++;

            log.info("Loan {} flipped from ACTIVE to OVERDUE — Customer: {}, Shop: {}",
                    loan.getLoanId(),
                    loan.getCustomer().getFullName(),
                    loan.getShop().getShopName());
        }

        log.info("Total loans flipped to OVERDUE today: {}", count);
    }

    /**
     * Sends reminder notifications for overdue loans at specific day thresholds.
     *
     * Purpose: Notify customers about their overdue loans at Day 1, 7, 15, and 30.
     * Input: Reads all OVERDUE loans from the database.
     * Output: Sends emails and SMS notifications and logs each reminder sent.
     *
     * Uses ReminderLog to ensure each reminder type is only sent ONCE per loan.
     * For example, if the Day 7 reminder was already sent for loan LN-2026-00341,
     * it will NOT be sent again the next day.
     */
    private void sendOverdueReminders() {
        // Get all OVERDUE loans across all shops
        List<GoldLoan> overdueLoans = goldLoanRepository.findByStatus("OVERDUE");

        if (overdueLoans.isEmpty()) {
            log.info("No OVERDUE loans found — no reminders to send.");
            return;
        }

        for (GoldLoan loan : overdueLoans) {
            // Calculate how many days this loan has been overdue
            long daysOverdue = ChronoUnit.DAYS.between(loan.getDueDate(), LocalDate.now());

            // Only process if actually overdue (daysOverdue > 0)
            if (daysOverdue <= 0) {
                continue;
            }

            // Get customer and shop details for the notification
            String customerName = loan.getCustomer().getFullName();
            String customerEmail = loan.getCustomer().getEmail();
            String customerPhone = loan.getCustomer().getPhone();
            String loanId = loan.getLoanId();
            String goldItem = loan.getGoldItemType();
            if (loan.getGoldItemDescription() != null && !loan.getGoldItemDescription().isEmpty()) {
                goldItem = goldItem + " (" + loan.getGoldItemDescription() + ")";
            }
            String shopName = loan.getShop().getShopName();

            // Check and send reminders at each threshold
            // Day 1 overdue — first reminder
            if (daysOverdue >= 1) {
                sendReminderIfNotAlreadySent(loan, "DAY_1", customerName, customerEmail,
                        customerPhone, loanId, goldItem, shopName, 1);
            }

            // Day 7 overdue — second reminder
            if (daysOverdue >= 7) {
                sendReminderIfNotAlreadySent(loan, "DAY_7", customerName, customerEmail,
                        customerPhone, loanId, goldItem, shopName, 7);
            }

            // Day 15 overdue — third reminder
            if (daysOverdue >= 15) {
                sendReminderIfNotAlreadySent(loan, "DAY_15", customerName, customerEmail,
                        customerPhone, loanId, goldItem, shopName, 15);
            }

            // Day 30 overdue — FINAL WARNING + seizure warning flag
            if (daysOverdue >= 30) {
                sendFinalWarningIfNotAlreadySent(loan, customerName, customerEmail,
                        customerPhone, loanId, goldItem, shopName);
            }
        }
    }

    /**
     * Sends a reminder notification if it hasn't already been sent for this loan.
     *
     * Purpose: Check ReminderLog to prevent duplicate reminders, then send via
     *          BOTH channels (email + SMS) as required by the spec.
     * Input: Loan, reminder type, and customer/shop details.
     * Output: Sends notifications and creates a ReminderLog entry.
     */
    private void sendReminderIfNotAlreadySent(GoldLoan loan, String reminderType,
                                               String customerName, String customerEmail,
                                               String customerPhone, String loanId,
                                               String goldItem, String shopName,
                                               int daysOverdue) {

        // Check if this reminder has already been sent for this loan
        boolean alreadySent = reminderLogRepository.existsByGoldLoanIdAndReminderType(
                loan.getId(), reminderType);

        if (alreadySent) {
            // Skip — this reminder was already sent
            return;
        }

        boolean emailSent = false;
        if (customerEmail != null && !customerEmail.isBlank()) {
            emailService.sendOverdueReminder(customerEmail, customerName, loanId,
                    goldItem, shopName, daysOverdue);
            emailSent = true;
        }

        boolean smsSent = smsService.sendReminderSms(customerPhone, customerName, loanId, daysOverdue);

        ReminderLog reminderLog = ReminderLog.builder()
                .goldLoan(loan)
                .reminderType(reminderType)
                .emailSent(emailSent)
                .smsSent(smsSent)
                .build();
        reminderLogRepository.save(reminderLog);

        log.info("Reminder {} sent for loan {} — Customer: {}", reminderType, loanId, customerName);
    }

    /**
     * Sends the Day 30 FINAL WARNING if not already sent, and sets the seizure warning flag.
     *
     * Purpose: This is special because in addition to sending notifications, it also
     *          sets the seizureWarningShown flag on the loan. When this flag is true,
     *          the customer's dashboard (Pro plan) displays a warning banner stating
     *          their gold item will be seized.
     * Input: Loan and customer/shop details.
     * Output: Sends final warning, sets seizureWarningShown = true, creates ReminderLog.
     */
    private void sendFinalWarningIfNotAlreadySent(GoldLoan loan, String customerName,
                                                   String customerEmail, String customerPhone,
                                                   String loanId, String goldItem,
                                                   String shopName) {

        boolean alreadySent = reminderLogRepository.existsByGoldLoanIdAndReminderType(
                loan.getId(), "DAY_30_FINAL");

        if (alreadySent) {
            return;
        }

        boolean emailSent = false;
        if (customerEmail != null && !customerEmail.isBlank()) {
            emailService.sendSeizureWarning(customerEmail, customerName, loanId, goldItem, shopName);
            emailSent = true;
        }

        String smsMessage = "FINAL WARNING: Your loan " + loanId + " at " + shopName +
                " is 30+ days overdue. Your gold item will be seized if payment is not received. " +
                "Contact " + shopName + " immediately.";
        boolean smsSent = smsService.sendSms(customerPhone, smsMessage);

        // Set the seizure warning flag on the loan
        // This causes the customer's dashboard to display a warning banner
        loan.setSeizureWarningShown(true);
        goldLoanRepository.save(loan);

        // Log the reminder
        ReminderLog reminderLog = ReminderLog.builder()
                .goldLoan(loan)
                .reminderType("DAY_30_FINAL")
                .emailSent(emailSent)
                .smsSent(smsSent)
                .build();
        reminderLogRepository.save(reminderLog);

        log.info("FINAL WARNING sent for loan {} — Customer: {}, Seizure warning flag set",
                loanId, customerName);
    }
}
