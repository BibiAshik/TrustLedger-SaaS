package com.trustledgersaas.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * ReminderLog — Tracks which overdue reminders have been sent for each loan.
 *
 * The daily scheduled job (ReminderSchedulerService) checks overdue loans and
 * sends reminders at specific day thresholds (Day 1, Day 7, Day 15, Day 30).
 * This entity prevents the same reminder from being sent twice for the same loan.
 *
 * Before sending any reminder, the scheduler checks: "Does a ReminderLog entry
 * already exist for this loan + this reminder type?" If yes, skip. If no, send
 * the reminder and create the log entry.
 *
 * Relationships:
 * - Many ReminderLogs belong to one GoldLoan — @ManyToOne with GoldLoan
 */
@Entity
@Table(name = "reminder_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReminderLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The loan this reminder was sent for.
     * Many reminders can be logged for one loan (at different day thresholds).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gold_loan_id", nullable = false)
    private GoldLoan goldLoan;

    /**
     * Which reminder stage this log entry represents.
     * Possible values: DAY_1, DAY_7, DAY_15, DAY_30_FINAL
     */
    @Column(nullable = false)
    private String reminderType;

    /** When the reminder was sent */
    @Builder.Default
    private LocalDateTime sentAt = LocalDateTime.now();

    /** Whether the email reminder was sent successfully */
    @Builder.Default
    private boolean emailSent = false;

    /** Whether the SMS reminder was sent */
    @Builder.Default
    private boolean smsSent = false;
}
