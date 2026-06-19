package com.trustledgersaas.controller;

import com.trustledgersaas.dto.request.LoanCreateRequestDTO;
import com.trustledgersaas.dto.request.PaymentRecordRequestDTO;
import com.trustledgersaas.dto.response.GoldLoanResponseDTO;
import com.trustledgersaas.dto.response.PaymentResponseDTO;
import com.trustledgersaas.entity.GoldLoan;
import com.trustledgersaas.exception.ResourceNotFoundException;
import com.trustledgersaas.repository.GoldLoanRepository;
import com.trustledgersaas.security.JwtUtil;
import com.trustledgersaas.service.LoanService;
import com.trustledgersaas.service.PaymentService;
import com.trustledgersaas.service.PdfReceiptService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * LoanController — Handles all loan management endpoints for Shop Owners.
 *
 * This controller manages:
 * - Creating new loans (with date validation and human-readable loan ID generation)
 * - Viewing loan lists (all, overdue, due this week)
 * - Viewing individual loan details
 * - Loan lifecycle actions: close, seize, extend due date
 * - Recording cash payments against loans
 * - Viewing payment history for a loan
 * - Generating PDF closure receipts
 *
 * All endpoints require ROLE_SHOP_OWNER. The shop ID is extracted from the JWT
 * token to ensure data isolation — a shop owner can only manage their own loans.
 */
@Controller
@Slf4j
public class LoanController {

    @Autowired
    private LoanService loanService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PdfReceiptService pdfReceiptService;

    @Autowired
    private GoldLoanRepository goldLoanRepository;

    @Autowired
    private JwtUtil jwtUtil;

    // ==================== LOAN CRUD API ====================

    /**
     * Creates a new gold loan.
     *
     * The shop owner selects a customer, fills in loan details and gold item info.
     * The system generates a unique loan ID (e.g. "LN-2026-00341") and sets status
     * to ACTIVE.
     */
    @PostMapping("/api/shop/loans")
    @PreAuthorize("hasRole('SHOP_OWNER')")
    @ResponseBody
    public ResponseEntity<GoldLoanResponseDTO> createLoan(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody LoanCreateRequestDTO dto) {

        Long shopId = extractShopId(authHeader);
        GoldLoanResponseDTO loan = loanService.createLoan(dto, shopId);
        return ResponseEntity.ok(loan);
    }

    /**
     * Gets all loans for this shop (paginated).
     *
     * Supports optional status filter (e.g. ?status=OVERDUE to show only overdue loans).
     */
    @GetMapping("/api/shop/loans")
    @PreAuthorize("hasRole('SHOP_OWNER')")
    @ResponseBody
    public ResponseEntity<Page<GoldLoanResponseDTO>> getLoans(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status) {

        Long shopId = extractShopId(authHeader);
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        if (status != null && !status.isEmpty()) {
            return ResponseEntity.ok(loanService.getLoansByShopIdAndStatus(shopId, status, pageable));
        }
        return ResponseEntity.ok(loanService.getLoansByShopId(shopId, pageable));
    }

    /**
     * Gets a single loan's full details (including live interest).
     */
    @GetMapping("/api/shop/loans/{id}")
    @PreAuthorize("hasRole('SHOP_OWNER')")
    @ResponseBody
    public ResponseEntity<GoldLoanResponseDTO> getLoanById(@PathVariable Long id) {
        return ResponseEntity.ok(loanService.getLoanById(id));
    }

    /**
     * Gets all loans for a specific customer.
     *
     * Used on the customer detail page to show all their loans.
     */
    @GetMapping("/api/shop/customers/{customerId}/loans")
    @PreAuthorize("hasRole('SHOP_OWNER')")
    @ResponseBody
    public ResponseEntity<List<GoldLoanResponseDTO>> getLoansByCustomer(
            @PathVariable Long customerId,
            @RequestHeader("Authorization") String authHeader) {

        Long shopId = extractShopId(authHeader);
        return ResponseEntity.ok(loanService.getLoansByCustomerId(customerId, shopId));
    }

    /**
     * Gets loans due within the next 7 days.
     *
     * Used for the "Loans Due This Week" section on the shop dashboard.
     */
    @GetMapping("/api/shop/loans/due-this-week")
    @PreAuthorize("hasRole('SHOP_OWNER')")
    @ResponseBody
    public ResponseEntity<List<GoldLoanResponseDTO>> getLoansDueThisWeek(
            @RequestHeader("Authorization") String authHeader) {

        Long shopId = extractShopId(authHeader);
        return ResponseEntity.ok(loanService.getLoansDueThisWeek(shopId));
    }

    // ==================== LOAN LIFECYCLE ACTIONS ====================

    /**
     * Closes a loan (full repayment + gold returned).
     *
     * This is a MANUAL action by the shop owner. The system never closes a loan
     * on its own. Sets status to CLOSED and records the closure timestamp.
     */
    @PostMapping("/api/shop/loans/{id}/close")
    @PreAuthorize("hasRole('SHOP_OWNER')")
    @ResponseBody
    public ResponseEntity<Map<String, String>> closeLoan(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader) {

        Long shopId = extractShopId(authHeader);
        return ResponseEntity.ok(loanService.closeLoan(id, shopId));
    }

    /**
     * Marks a loan as SEIZED (gold seized, forced closure).
     *
     * This is a MANUAL action by the shop owner when the customer fails to pay
     * after prolonged overdue period. The system never seizes on its own —
     * it only shows warnings.
     */
    @PostMapping("/api/shop/loans/{id}/seize")
    @PreAuthorize("hasRole('SHOP_OWNER')")
    @ResponseBody
    public ResponseEntity<Map<String, String>> seizeLoan(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader) {

        Long shopId = extractShopId(authHeader);
        return ResponseEntity.ok(loanService.seizeLoan(id, shopId));
    }

    /**
     * Extends a loan's due date.
     *
     * This is a MANUAL action by the shop owner to give the customer more time.
     * If the loan was OVERDUE and the new date is in the future, it resets
     * back to ACTIVE and clears the seizure warning flag.
     */
    @PostMapping("/api/shop/loans/{id}/extend")
    @PreAuthorize("hasRole('SHOP_OWNER')")
    @ResponseBody
    public ResponseEntity<Map<String, String>> extendDueDate(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> body) {

        Long shopId = extractShopId(authHeader);
        String newDueDate = body.get("newDueDate");
        return ResponseEntity.ok(loanService.extendDueDate(id, newDueDate, shopId));
    }

    // ==================== PAYMENT RECORDING ====================

    /**
     * Records a manual cash payment against a loan.
     *
     * The shop owner enters the amount, date, and optional note when a customer
     * pays cash in person at the shop.
     */
    @PostMapping("/api/shop/loans/{loanId}/payments")
    @PreAuthorize("hasRole('SHOP_OWNER')")
    @ResponseBody
    public ResponseEntity<PaymentResponseDTO> recordCashPayment(
            @PathVariable Long loanId,
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody PaymentRecordRequestDTO dto) {

        Long shopId = extractShopId(authHeader);
        dto.setLoanId(loanId); // Set the loan ID from the URL path
        return ResponseEntity.ok(paymentService.recordCashPayment(dto, shopId));
    }

    /**
     * Gets all payments for a specific loan.
     *
     * Shows the full payment history (both cash and online) in chronological order.
     */
    @GetMapping("/api/shop/loans/{loanId}/payments")
    @PreAuthorize("hasRole('SHOP_OWNER')")
    @ResponseBody
    public ResponseEntity<List<PaymentResponseDTO>> getPaymentsByLoan(@PathVariable Long loanId) {
        return ResponseEntity.ok(paymentService.getPaymentsByLoanId(loanId));
    }

    /**
     * Gets all payments across all loans for this shop (paginated).
     *
     * Used for the shop owner's overall payment history view.
     */
    @GetMapping("/api/shop/payments")
    @PreAuthorize("hasRole('SHOP_OWNER')")
    @ResponseBody
    public ResponseEntity<Page<PaymentResponseDTO>> getAllPayments(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Long shopId = extractShopId(authHeader);
        Pageable pageable = PageRequest.of(page, size, Sort.by("paymentDate").descending());
        return ResponseEntity.ok(paymentService.getPaymentsByShopId(shopId, pageable));
    }

    // ==================== PDF RECEIPT GENERATION ====================

    /**
     * Generates and downloads a PDF closure receipt for a closed loan.
     *
     * This comprehensive receipt contains the complete payment history,
     * a "LOAN CLOSED" stamp, and gold item "RETURNED" label.
     */
    @GetMapping("/api/shop/loans/{id}/closure-receipt")
    @PreAuthorize("hasRole('SHOP_OWNER')")
    @ResponseBody
    public ResponseEntity<byte[]> downloadClosureReceipt(@PathVariable Long id) {

        GoldLoan loan = goldLoanRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Loan not found"));

        byte[] pdfBytes = pdfReceiptService.generateClosureReceipt(loan);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=closure-receipt-" + loan.getLoanId() + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    // ==================== HELPER METHODS ====================

    /** Extracts the shop ID from the JWT token in the Authorization header */
    private Long extractShopId(String authHeader) {
        String token = authHeader.substring(7); // Remove "Bearer "
        return jwtUtil.extractShopId(token);
    }
}
