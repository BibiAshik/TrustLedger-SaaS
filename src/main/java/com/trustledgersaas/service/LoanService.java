package com.trustledgersaas.service;

import com.trustledgersaas.dto.request.LoanCreateRequestDTO;
import com.trustledgersaas.dto.response.GoldLoanResponseDTO;
import com.trustledgersaas.entity.Customer;
import com.trustledgersaas.entity.GoldLoan;
import com.trustledgersaas.entity.Shop;
import com.trustledgersaas.exception.InvalidRequestException;
import com.trustledgersaas.exception.ResourceNotFoundException;
import com.trustledgersaas.mapper.GoldLoanMapper;
import com.trustledgersaas.repository.CustomerRepository;
import com.trustledgersaas.repository.GoldLoanRepository;
import com.trustledgersaas.repository.PaymentRepository;
import com.trustledgersaas.repository.ShopRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * LoanService — Handles all gold loan business logic.
 *
 * This includes:
 * - Creating loans with human-readable IDs and date validation
 * - Viewing loans with live interest calculations
 * - Closing loans, marking seizure, extending due dates
 * - Status transitions (ACTIVE → OVERDUE handled by scheduler, rest manual)
 */
@Service
@Slf4j
public class LoanService {

    @Autowired
    private GoldLoanRepository goldLoanRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ShopRepository shopRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    // ==================== CREATE LOAN ====================

    /**
     * Creates a new gold loan.
     *
     * Purpose: Shop owner creates a loan when a customer pledges a gold item.
     * Input: LoanCreateRequestDTO with customer, loan, and gold item details.
     * Output: GoldLoanResponseDTO of the newly created loan.
     *
     * Flow:
     * 1. Validate loan date (must not be in the past)
     * 2. Verify customer belongs to this shop
     * 3. Generate a unique, human-readable Loan ID (e.g. "LN-2026-00341")
     * 4. Create the GoldLoan entity
     * 5. Return the loan DTO with live interest (which will be ₹0 on day 1)
     */
    @Transactional
    public GoldLoanResponseDTO createLoan(LoanCreateRequestDTO dto, Long shopId) {

        // Step 1: Validate loan date — MUST NOT be in the past
        LocalDate loanDate = LocalDate.parse(dto.getLoanDate());
        if (loanDate.isBefore(LocalDate.now())) {
            throw new InvalidRequestException("Loan date cannot be in the past. " +
                    "This protects the integrity of the interest calculation.");
        }

        LocalDate dueDate = LocalDate.parse(dto.getDueDate());
        if (dueDate.isBefore(loanDate)) {
            throw new InvalidRequestException("Due date cannot be before the loan date.");
        }

        // Step 2: Verify customer belongs to this shop
        Customer customer = customerRepository.findById(dto.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        if (!customer.getShop().getId().equals(shopId)) {
            throw new InvalidRequestException("This customer does not belong to your shop.");
        }

        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new ResourceNotFoundException("Shop not found"));

        // Step 3: Generate human-readable Loan ID
        String loanId = generateLoanId();

        // Step 4: Create the loan entity
        GoldLoan loan = GoldLoan.builder()
                .loanId(loanId)
                .loanAmount(dto.getLoanAmount())
                .interestRate(dto.getInterestRate())
                .loanDate(loanDate)
                .dueDate(dueDate)
                .status("ACTIVE")
                .goldItemType(dto.getGoldItemType())
                .goldItemDescription(dto.getGoldItemDescription())
                .goldWeight(dto.getGoldWeight())
                .goldPurity(dto.getGoldPurity())
                .estimatedValue(dto.getEstimatedValue())
                .customer(customer)
                .shop(shop)
                .build();
        loan = goldLoanRepository.save(loan);

        log.info("New loan created: {} — Customer: {}, Amount: ₹{}, Shop: {}",
                loanId, customer.getFullName(), dto.getLoanAmount(), shop.getShopName());

        // Step 5: Return DTO with live interest
        return GoldLoanMapper.toDTO(loan, BigDecimal.ZERO);
    }

    // ==================== GET LOANS ====================

    /**
     * Gets all loans for a specific shop (paginated).
     *
     * Purpose: Shop owner views their full loan list.
     * Input: Shop ID (from JWT) and Pageable.
     * Output: Page of GoldLoanResponseDTOs with live interest.
     */
    public Page<GoldLoanResponseDTO> getLoansByShopId(Long shopId, Pageable pageable) {
        Page<GoldLoan> loans = goldLoanRepository.findByShopId(shopId, pageable);
        return loans.map(loan -> {
            BigDecimal totalPaid = paymentRepository.sumPaymentsByLoanId(loan.getId());
            return GoldLoanMapper.toDTO(loan, totalPaid);
        });
    }

    /**
     * Gets loans for a shop filtered by status (paginated).
     *
     * Purpose: Shop owner views overdue loans, active loans, etc.
     * Input: Shop ID, status filter, and Pageable.
     * Output: Page of GoldLoanResponseDTOs.
     */
    public Page<GoldLoanResponseDTO> getLoansByShopIdAndStatus(Long shopId, String status, Pageable pageable) {
        Page<GoldLoan> loans = goldLoanRepository.findByShopIdAndStatus(shopId, status, pageable);
        return loans.map(loan -> {
            BigDecimal totalPaid = paymentRepository.sumPaymentsByLoanId(loan.getId());
            return GoldLoanMapper.toDTO(loan, totalPaid);
        });
    }

    /**
     * Gets all loans for a specific customer.
     *
     * Purpose: Customer views their own loans, or shop owner views a customer's
     * loans.
     * Input: Customer ID.
     * Output: List of GoldLoanResponseDTOs with live interest.
     */
    public List<GoldLoanResponseDTO> getLoansByCustomerId(Long customerId) {
        List<GoldLoan> loans = goldLoanRepository.findByCustomerId(customerId);
        return loans.stream()
                .map(loan -> {
                    BigDecimal totalPaid = paymentRepository.sumPaymentsByLoanId(loan.getId());
                    return GoldLoanMapper.toDTO(loan, totalPaid);
                })
                .toList();
    }

    public List<GoldLoanResponseDTO> getLoansByCustomerId(Long customerId, Long shopId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        if (!customer.getShop().getId().equals(shopId)) {
            throw new InvalidRequestException("This customer does not belong to your shop.");
        }
        return getLoansByCustomerId(customerId);
    }

    public BigDecimal getTotalLoanVolume(Long shopId) {
        return goldLoanRepository.sumLoanAmountsByShopId(shopId);
    }

    /**
     * Gets a single loan's full details.
     *
     * Purpose: Loan detail page — shows all info including live interest.
     * Input: Loan database ID.
     * Output: GoldLoanResponseDTO with all fields.
     */
    public GoldLoanResponseDTO getLoanById(Long loanId) {
        GoldLoan loan = goldLoanRepository.findById(loanId)
                .orElseThrow(() -> new ResourceNotFoundException("Loan not found with ID: " + loanId));

        BigDecimal totalPaid = paymentRepository.sumPaymentsByLoanId(loan.getId());
        return GoldLoanMapper.toDTO(loan, totalPaid);
    }

    /**
     * Gets loans due within the next 7 days for a shop.
     *
     * Purpose: "Loans due this week" section on the shop dashboard.
     * Input: Shop ID.
     * Output: List of GoldLoanResponseDTOs.
     */
    public List<GoldLoanResponseDTO> getLoansDueThisWeek(Long shopId) {
        LocalDate today = LocalDate.now();
        LocalDate nextWeek = today.plusDays(7);
        List<GoldLoan> loans = goldLoanRepository.findByShopIdAndStatusAndDueDateBetween(
                shopId, "ACTIVE", today, nextWeek);
        return loans.stream()
                .map(loan -> {
                    BigDecimal totalPaid = paymentRepository.sumPaymentsByLoanId(loan.getId());
                    return GoldLoanMapper.toDTO(loan, totalPaid);
                })
                .toList();
    }

    // ==================== LOAN LIFECYCLE ACTIONS ====================

    /**
     * Closes a loan (full repayment + gold returned to customer).
     *
     * Purpose: Shop owner marks a loan as fully settled.
     * Input: Loan ID and the shop ID (for ownership verification).
     * Output: Map with success message.
     */
    @Transactional
    public Map<String, String> closeLoan(Long loanId, Long shopId) {
        GoldLoan loan = goldLoanRepository.findById(loanId)
                .orElseThrow(() -> new ResourceNotFoundException("Loan not found"));

        // Verify ownership
        if (!loan.getShop().getId().equals(shopId)) {
            throw new InvalidRequestException("This loan does not belong to your shop.");
        }

        if ("CLOSED".equals(loan.getStatus()) || "SEIZED".equals(loan.getStatus())) {
            throw new InvalidRequestException("This loan is already " + loan.getStatus().toLowerCase() + ".");
        }

        loan.setStatus("CLOSED");
        loan.setClosedAt(LocalDateTime.now());
        goldLoanRepository.save(loan);

        log.info("Loan closed: {} — Customer: {}", loan.getLoanId(), loan.getCustomer().getFullName());

        Map<String, String> response = new HashMap<>();
        response.put("message", "Loan " + loan.getLoanId() + " has been closed. Gold item marked as RETURNED.");
        return response;
    }

    /**
     * Marks a loan as SEIZED (gold seized, forced closure).
     *
     * Purpose: Shop owner seizes the gold due to prolonged non-payment.
     * Input: Loan ID and shop ID.
     * Output: Map with success message.
     */
    @Transactional
    public Map<String, String> seizeLoan(Long loanId, Long shopId) {
        GoldLoan loan = goldLoanRepository.findById(loanId)
                .orElseThrow(() -> new ResourceNotFoundException("Loan not found"));

        if (!loan.getShop().getId().equals(shopId)) {
            throw new InvalidRequestException("This loan does not belong to your shop.");
        }

        if ("CLOSED".equals(loan.getStatus()) || "SEIZED".equals(loan.getStatus())) {
            throw new InvalidRequestException("This loan is already " + loan.getStatus().toLowerCase() + ".");
        }

        loan.setStatus("SEIZED");
        loan.setClosedAt(LocalDateTime.now());
        goldLoanRepository.save(loan);

        log.info("Loan seized: {} — Customer: {}", loan.getLoanId(), loan.getCustomer().getFullName());

        Map<String, String> response = new HashMap<>();
        response.put("message", "Loan " + loan.getLoanId() + " has been marked as SEIZED.");
        return response;
    }

    /**
     * Extends a loan's due date.
     *
     * Purpose: Shop owner gives the customer more time to pay.
     * Input: Loan ID, new due date, and shop ID.
     * Output: Map with success message.
     */
    @Transactional
    public Map<String, String> extendDueDate(Long loanId, String newDueDateStr, Long shopId) {
        GoldLoan loan = goldLoanRepository.findById(loanId)
                .orElseThrow(() -> new ResourceNotFoundException("Loan not found"));

        if (!loan.getShop().getId().equals(shopId)) {
            throw new InvalidRequestException("This loan does not belong to your shop.");
        }

        LocalDate newDueDate = LocalDate.parse(newDueDateStr);
        if (newDueDate.isBefore(LocalDate.now())) {
            throw new InvalidRequestException("New due date must be in the future.");
        }

        loan.setDueDate(newDueDate);
        // If the loan was OVERDUE and the new due date is in the future, reset to
        // ACTIVE
        if ("OVERDUE".equals(loan.getStatus()) && newDueDate.isAfter(LocalDate.now())) {
            loan.setStatus("ACTIVE");
            loan.setSeizureWarningShown(false); // Reset seizure warning
        }
        goldLoanRepository.save(loan);

        log.info("Due date extended for loan: {} — New date: {}", loan.getLoanId(), newDueDate);

        Map<String, String> response = new HashMap<>();
        response.put("message", "Due date for loan " + loan.getLoanId() + " extended to " + newDueDate + ".");
        return response;
    }

    // ==================== LOAN ID GENERATION ====================

    /**
     * Generates a unique, human-readable Loan ID.
     *
     * Purpose: Create IDs like "LN-2026-00341" for display to users.
     * Format: LN-{year}-{5-digit sequential number}
     * Input: None.
     * Output: A unique loan ID string.
     */
    private String generateLoanId() {
        int year = LocalDate.now().getYear();
        long totalLoans = goldLoanRepository.count();
        String sequenceNumber = String.format("%05d", totalLoans + 1);
        return "LN-" + year + "-" + sequenceNumber;
    }
}
