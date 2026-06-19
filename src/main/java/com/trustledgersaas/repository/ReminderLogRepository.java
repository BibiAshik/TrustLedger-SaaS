package com.trustledgersaas.repository;

import com.trustledgersaas.entity.ReminderLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

/**
 * ReminderLogRepository — Data access layer for the ReminderLog entity.
 *
 * Used by the ReminderSchedulerService to check if a specific reminder
 * has already been sent for a loan, preventing duplicate notifications.
 */
@Repository
public interface ReminderLogRepository extends JpaRepository<ReminderLog, Long> {

    /**
     * Check if a specific reminder type has already been sent for a loan.
     * For example: has the "DAY_7" reminder already been sent for loan #42?
     * If a record exists, the scheduler skips sending it again.
     */
    Optional<ReminderLog> findByGoldLoanIdAndReminderType(Long goldLoanId, String reminderType);

    /**
     * Check existence — a faster alternative to findBy when we only need
     * to know if the reminder was already sent (true/false, not the full entity).
     */
    boolean existsByGoldLoanIdAndReminderType(Long goldLoanId, String reminderType);
}
