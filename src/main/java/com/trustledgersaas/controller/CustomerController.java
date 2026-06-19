package com.trustledgersaas.controller;

import com.trustledgersaas.dto.response.CustomerResponseDTO;
import com.trustledgersaas.dto.response.GoldLoanResponseDTO;
import com.trustledgersaas.dto.response.PaymentResponseDTO;
import com.trustledgersaas.entity.Customer;
import com.trustledgersaas.entity.Payment;
import com.trustledgersaas.exception.ResourceNotFoundException;
import com.trustledgersaas.repository.CustomerRepository;
import com.trustledgersaas.repository.PaymentRepository;
import com.trustledgersaas.security.JwtUtil;
import com.trustledgersaas.service.CustomerService;
import com.trustledgersaas.service.LoanService;
import com.trustledgersaas.service.PaymentService;
import com.trustledgersaas.service.PdfReceiptService;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CustomerController — Handles all customer portal endpoints.
 *
 * This controller serves the CUSTOMER-facing pages and API endpoints.
 * These features are ONLY available when the customer's parent shop is on the
 * PRO plan (this check happens at login time in AuthService).
 *
 * The customer can:
 * - View their dashboard (active loans with live interest)
 * - View individual loan details
 * - View payment history across all loans
 * - Download PDF receipts for individual payments
 * - View their profile
 * - Change their password
 *
 * The customer ID is derived from the JWT token (never from a URL parameter)
 * to prevent one customer from viewing another's data.
 */
@Controller
@Slf4j
public class CustomerController {

    @Autowired
    private CustomerService customerService;

    @Autowired
    private LoanService loanService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PdfReceiptService pdfReceiptService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private JwtUtil jwtUtil;

    // ==================== DASHBOARD API ====================

    /**
     * Gets the customer's dashboard data.
     *
     * Returns: customer profile, list of active loans with live interest,
     * and the customer's shop info.
     */
    @GetMapping("/api/customer/dashboard")
    @PreAuthorize("hasRole('CUSTOMER')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getDashboardData(
            @RequestHeader("Authorization") String authHeader) {

        Long userId = extractUserId(authHeader);
        CustomerResponseDTO customer = customerService.getCustomerByUserId(userId);

        // Get all loans for this customer
        List<GoldLoanResponseDTO> loans = loanService.getLoansByCustomerId(customer.getId());

        Map<String, Object> dashboard = new HashMap<>();
        dashboard.put("customer", customer);
        dashboard.put("loans", loans);

        return ResponseEntity.ok(dashboard);
    }

    // ==================== LOAN DETAIL API ====================

    /**
     * Gets a specific loan's full details for the customer.
     *
     * Includes live interest calculation, gold item details, and payment history.
     * Verifies that the loan actually belongs to this customer before returning data.
     */
    @GetMapping("/api/customer/loans/{loanId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getLoanDetail(
            @PathVariable Long loanId,
            @RequestHeader("Authorization") String authHeader) {

        Long userId = extractUserId(authHeader);
        CustomerResponseDTO customer = customerService.getCustomerByUserId(userId);

        // Get the loan details
        GoldLoanResponseDTO loan = loanService.getLoanById(loanId);

        // Verify this loan belongs to this customer (security check)
        if (!loan.getCustomerId().equals(customer.getId())) {
            throw new ResourceNotFoundException("Loan not found");
        }

        // Get payment history for this loan
        List<PaymentResponseDTO> payments = paymentService.getPaymentsByLoanId(loanId);

        Map<String, Object> response = new HashMap<>();
        response.put("loan", loan);
        response.put("payments", payments);

        return ResponseEntity.ok(response);
    }

    // ==================== PAYMENT HISTORY API ====================

    /**
     * Gets all payments for this customer across all their loans (paginated).
     *
     * Shows date, loan ID, amount, payment mode, and receipt number.
     * Used for the customer's payment history page.
     */
    @GetMapping("/api/customer/payments")
    @PreAuthorize("hasRole('CUSTOMER')")
    @ResponseBody
    public ResponseEntity<Page<PaymentResponseDTO>> getPaymentHistory(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Long userId = extractUserId(authHeader);
        CustomerResponseDTO customer = customerService.getCustomerByUserId(userId);

        Pageable pageable = PageRequest.of(page, size, Sort.by("paymentDate").descending());
        return ResponseEntity.ok(paymentService.getPaymentsByCustomerId(customer.getId(), pageable));
    }

    // ==================== PDF RECEIPT DOWNLOAD ====================

    /**
     * Downloads a PDF receipt for a specific payment.
     *
     * Returns a PDF file as a downloadable attachment. The customer can only
     * download receipts for their own payments (verified via JWT).
     */
    @GetMapping("/api/customer/payments/{paymentId}/receipt")
    @PreAuthorize("hasRole('CUSTOMER')")
    @ResponseBody
    public ResponseEntity<byte[]> downloadReceipt(
            @PathVariable Long paymentId,
            @RequestHeader("Authorization") String authHeader) {

        Long userId = extractUserId(authHeader);
        CustomerResponseDTO customer = customerService.getCustomerByUserId(userId);

        // Find the payment and verify it belongs to this customer
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));

        if (!payment.getGoldLoan().getCustomer().getId().equals(customer.getId())) {
            throw new ResourceNotFoundException("Payment not found");
        }

        byte[] pdfBytes = pdfReceiptService.generatePaymentReceipt(payment);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=receipt-" + payment.getReceiptNumber() + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    // ==================== PROFILE API ====================

    /**
     * Gets the customer's own profile information.
     *
     * Customers can view their details (name, phone, email, address, shop info)
     * but CANNOT edit name/address/Aadhaar — only the shop owner can edit those.
     */
    @GetMapping("/api/customer/profile")
    @PreAuthorize("hasRole('CUSTOMER')")
    @ResponseBody
    public ResponseEntity<CustomerResponseDTO> getProfile(
            @RequestHeader("Authorization") String authHeader) {

        Long userId = extractUserId(authHeader);
        return ResponseEntity.ok(customerService.getCustomerByUserId(userId));
    }

    // ==================== HELPER METHODS ====================

    /** Extracts the user ID from the JWT token */
    private Long extractUserId(String authHeader) {
        String token = authHeader.substring(7);
        return jwtUtil.extractUserId(token);
    }

    /** Extracts the shop ID from the JWT token */
    private Long extractShopId(String authHeader) {
        String token = authHeader.substring(7);
        return jwtUtil.extractShopId(token);
    }
}
