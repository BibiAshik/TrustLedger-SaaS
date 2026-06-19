package com.trustledgersaas.service;

import com.trustledgersaas.dto.request.PaymentRecordRequestDTO;
import com.trustledgersaas.dto.response.PaymentResponseDTO;
import com.trustledgersaas.entity.GoldLoan;
import com.trustledgersaas.entity.Payment;
import com.trustledgersaas.exception.InvalidRequestException;
import com.trustledgersaas.exception.ResourceNotFoundException;
import com.trustledgersaas.mapper.PaymentMapper;
import com.trustledgersaas.repository.GoldLoanRepository;
import com.trustledgersaas.repository.PaymentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * PaymentService — Handles all payment-related business logic.
 *
 * This includes:
 * - Recording cash payments (shop owner action)
 * - Recording online payments (after Razorpay verification)
 * - Generating receipt numbers
 * - Querying payment history by loan, customer, or shop
 */
@Service
@Slf4j
public class PaymentService {

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private GoldLoanRepository goldLoanRepository;

    // ==================== RECORD CASH PAYMENT ====================

    /**
     * Records a manual cash payment made by a customer.
     *
     * Purpose: Shop owner enters a cash payment they received in person.
     * Input: PaymentRecordRequestDTO with loan ID, amount, date, and optional note.
     * Output: PaymentResponseDTO of the newly recorded payment.
     *
     * Flow:
     * 1. Find the loan
     * 2. Verify the loan is active/overdue (can't pay on a closed/seized loan)
     * 3. Generate a unique receipt number
     * 4. Create the Payment record
     */
    @Transactional
    public PaymentResponseDTO recordCashPayment(PaymentRecordRequestDTO dto, Long shopId) {

        // Step 1: Find the loan
        GoldLoan loan = goldLoanRepository.findById(dto.getLoanId())
                .orElseThrow(() -> new ResourceNotFoundException("Loan not found"));

        // Verify this loan belongs to the shop owner's shop
        if (!loan.getShop().getId().equals(shopId)) {
            throw new InvalidRequestException("This loan does not belong to your shop.");
        }

        // Step 2: Check loan status — can't record payment on closed/seized loans
        if ("CLOSED".equals(loan.getStatus()) || "SEIZED".equals(loan.getStatus())) {
            throw new InvalidRequestException(
                    "Cannot record payment on a " + loan.getStatus().toLowerCase() + " loan.");
        }

        // Step 3: Generate receipt number
        String receiptNumber = generateReceiptNumber();

        // Step 4: Create the payment record
        Payment payment = Payment.builder()
                .goldLoan(loan)
                .amount(dto.getAmount())
                .paymentDate(LocalDate.parse(dto.getPaymentDate()))
                .paymentMode("CASH")
                .receiptNumber(receiptNumber)
                .note(dto.getNote())
                .build();
        payment = paymentRepository.save(payment);

        log.info("Cash payment recorded: {} — Loan: {}, Amount: ₹{}",
                receiptNumber, loan.getLoanId(), dto.getAmount());

        return PaymentMapper.toDTO(payment);
    }

    /**
     * Records an online payment after Razorpay verification.
     *
     * Purpose: After verifying a Razorpay payment's signature, record it in our
     * database.
     * Input: Loan ID, payment amount, Razorpay IDs, and payment mode.
     * Output: PaymentResponseDTO of the recorded payment.
     */
    @Transactional
    public PaymentResponseDTO recordOnlinePayment(Long loanId, BigDecimal amount,
            String razorpayPaymentId, String razorpayOrderId,
            String paymentMode) {
        GoldLoan loan = goldLoanRepository.findById(loanId)
                .orElseThrow(() -> new ResourceNotFoundException("Loan not found"));

        String receiptNumber = generateReceiptNumber();

        Payment payment = Payment.builder()
                .goldLoan(loan)
                .amount(amount)
                .paymentDate(LocalDate.now())
                .paymentMode(paymentMode)
                .razorpayPaymentId(razorpayPaymentId)
                .razorpayOrderId(razorpayOrderId)
                .receiptNumber(receiptNumber)
                .build();
        payment = paymentRepository.save(payment);

        log.info("Online payment recorded: {} — Loan: {}, Amount: ₹{}, Razorpay: {}",
                receiptNumber, loan.getLoanId(), amount, razorpayPaymentId);

        return PaymentMapper.toDTO(payment);
    }

    // ==================== GET PAYMENTS ====================

    /**
     * Gets all payments for a specific loan.
     *
     * Purpose: Payment history on the loan detail page.
     * Input: Loan ID.
     * Output: List of PaymentResponseDTOs in reverse chronological order.
     */
    public List<PaymentResponseDTO> getPaymentsByLoanId(Long loanId) {
        List<Payment> payments = paymentRepository.findByGoldLoanIdOrderByPaymentDateDesc(loanId);
        return payments.stream()
                .map(PaymentMapper::toDTO)
                .toList();
    }

    /**
     * Gets all payments for a shop (paginated).
     *
     * Purpose: Shop owner's full payment history view.
     * Input: Shop ID and Pageable.
     * Output: Page of PaymentResponseDTOs.
     */
    public Page<PaymentResponseDTO> getPaymentsByShopId(Long shopId, Pageable pageable) {
        Page<Payment> payments = paymentRepository.findByShopId(shopId, pageable);
        return payments.map(PaymentMapper::toDTO);
    }

    /**
     * Gets all payments for a specific customer (paginated).
     *
     * Purpose: Customer's payment history page.
     * Input: Customer ID and Pageable.
     * Output: Page of PaymentResponseDTOs.
     */
    public Page<PaymentResponseDTO> getPaymentsByCustomerId(Long customerId, Pageable pageable) {
        Page<Payment> payments = paymentRepository.findByCustomerId(customerId, pageable);
        return payments.map(PaymentMapper::toDTO);
    }

    /**
     * Gets recent payments for a shop (for dashboard display).
     *
     * Purpose: "Recent Payments" section on the shop dashboard.
     * Input: Shop ID and how many to show.
     * Output: List of PaymentResponseDTOs.
     */
    public List<PaymentResponseDTO> getRecentPayments(Long shopId, int count) {
        Pageable pageable = PageRequest.of(0, count);
        List<Payment> payments = paymentRepository.findRecentByShopId(shopId, pageable);
        return payments.stream()
                .map(PaymentMapper::toDTO)
                .toList();
    }

    // ==================== RECEIPT NUMBER GENERATION ====================

    /**
     * Generates a unique, sequential receipt number.
     *
     * Purpose: Each payment gets a receipt number for PDF generation.
     * Format: "REC-00001", "REC-00002", etc.
     * Input: None.
     * Output: A unique receipt number string.
     */
    private String generateReceiptNumber() {
        Optional<String> lastReceipt = paymentRepository.findLastReceiptNumber();
        long nextNumber = 1;

        if (lastReceipt.isPresent()) {
            String last = lastReceipt.get();
            // Extract the numeric part from "REC-00001"
            String numberPart = last.replace("REC-", "");
            try {
                nextNumber = Long.parseLong(numberPart) + 1;
            } catch (NumberFormatException e) {
                // If parsing fails, use the total count + 1 as fallback
                nextNumber = paymentRepository.count() + 1;
            }
        }

        return String.format("REC-%05d", nextNumber);
    }
}
