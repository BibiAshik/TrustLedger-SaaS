package com.trustledgersaas.controller;

import com.trustledgersaas.dto.request.PaymentVerifyRequestDTO;
import com.trustledgersaas.dto.response.CustomerResponseDTO;
import com.trustledgersaas.dto.response.PaymentResponseDTO;
import com.trustledgersaas.entity.GoldLoan;
import com.trustledgersaas.entity.Shop;
import com.trustledgersaas.exception.InvalidRequestException;
import com.trustledgersaas.exception.ResourceNotFoundException;
import com.trustledgersaas.repository.GoldLoanRepository;
import com.trustledgersaas.repository.ShopRepository;
import com.trustledgersaas.security.JwtUtil;
import com.trustledgersaas.service.CustomerService;
import com.trustledgersaas.service.PaymentService;
import com.trustledgersaas.service.RazorpayService;
import com.trustledgersaas.service.ShopService;
import com.trustledgersaas.util.InterestCalculator;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * PaymentController — Handles online payment flow via Razorpay.
 *
 * This controller manages TWO distinct payment flows:
 *
 * 1. SUBSCRIPTION PAYMENTS (Shop Owner → Super Admin):
 *    Shop owner pays ₹299 (Basic) or ₹699 (Pro) for their monthly subscription.
 *    Uses the Super Admin's own Razorpay account. Money stays with Bibi.
 *
 * 2. LOAN INTEREST PAYMENTS (Customer → Shop Owner):
 *    Customer pays their loan interest online. Uses Razorpay Route so the
 *    money goes DIRECTLY to the shop's linked bank account, never through
 *    the Super Admin.
 *
 * Both flows follow the same 3-step pattern:
 *   Step 1: Backend creates a Razorpay Order (returns orderId)
 *   Step 2: Frontend opens Razorpay Checkout popup with that orderId
 *   Step 3: After payment, frontend sends paymentId + signature to backend for verification
 *
 * The frontend shows a "Processing payment..." spinner during Step 3.
 */
@RestController
@RequestMapping("/api/payment")
@Slf4j
public class PaymentController {

    @Autowired
    private RazorpayService razorpayService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private ShopService shopService;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private GoldLoanRepository goldLoanRepository;

    @Autowired
    private ShopRepository shopRepository;

    @Autowired
    private JwtUtil jwtUtil;

    // ==================== SUBSCRIPTION PAYMENT (Shop Owner → Super Admin) ====================

    /**
     * Creates a Razorpay Order for a subscription payment.
     *
     * Purpose: Shop owner selects a plan (BASIC ₹299 / PRO ₹699) and clicks "Pay".
     *          This endpoint creates a Razorpay Order so the frontend can open the
     *          checkout popup.
     *
     * Input: Plan type (BASIC or PRO) in the request body.
     * Output: Razorpay order ID, amount, currency, and the key ID for the frontend.
     */
    @PostMapping("/subscription/create-order")
    @PreAuthorize("hasRole('SHOP_OWNER')")
    public ResponseEntity<Map<String, Object>> createSubscriptionOrder(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> body) {

        Long shopId = extractShopId(authHeader);
        String planType = body.get("planType");

        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new ResourceNotFoundException("Shop not found"));

        // Check downgrade logic
        if ("BASIC".equalsIgnoreCase(planType) && "PRO".equalsIgnoreCase(shop.getPlan())) {
            if ("ACTIVE".equalsIgnoreCase(shop.getSubscriptionStatus())) {
                throw new InvalidRequestException("You are a PRO user. Please wait until your current subscription usage is finished to switch to BASIC.");
            }
        }

        // Determine the amount based on the plan
        BigDecimal amount;
        if ("PRO".equalsIgnoreCase(planType)) {
            amount = new BigDecimal("699.00");
        } else if ("BASIC".equalsIgnoreCase(planType)) {
            amount = new BigDecimal("299.00");
        } else {
            throw new InvalidRequestException("Invalid plan type. Choose BASIC or PRO.");
        }

        // Create the Razorpay Order
        String receipt = "sub_shop_" + shopId + "_" + System.currentTimeMillis();
        Map<String, Object> order = razorpayService.createOrder(amount, "INR", receipt);

        // Add the plan type to the response (frontend needs this for the verify step)
        order.put("planType", planType.toUpperCase());
        order.put("shopId", shopId);

        return ResponseEntity.ok(order);
    }

    /**
     * Verifies a subscription payment and activates the shop's subscription.
     *
     * Purpose: After the shop owner completes payment in the Razorpay popup,
     *          verify the signature server-side, then activate their subscription.
     *
     * Input: Razorpay orderId, paymentId, signature, and the plan type.
     * Output: Success message with subscription details.
     */
    @PostMapping("/subscription/verify")
    @PreAuthorize("hasRole('SHOP_OWNER')")
    public ResponseEntity<Map<String, Object>> verifySubscriptionPayment(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody PaymentVerifyRequestDTO dto) {

        Long shopId = extractShopId(authHeader);

        // Step 1: Verify the Razorpay signature (never trust the frontend blindly!)
        boolean isValid = razorpayService.verifyPaymentSignature(
                dto.getRazorpayOrderId(), dto.getRazorpayPaymentId(), dto.getRazorpaySignature());

        if (!isValid) {
            throw new InvalidRequestException("Payment verification failed. " +
                    "The payment signature is invalid. Please try again or contact support.");
        }

        // Step 2: Activate the subscription
        Map<String, String> activationResult = shopService.activateSubscription(
                shopId, dto.getPlanType(), dto.getRazorpayPaymentId(), dto.getRazorpayOrderId());

        // Step 3: If it's a PRO plan, ensure Razorpay Linked Account exists for direct payments
        if ("PRO".equalsIgnoreCase(dto.getPlanType())) {
            Shop shop = shopRepository.findById(shopId)
                    .orElseThrow(() -> new ResourceNotFoundException("Shop not found"));

            // Only create if not already created or if previous attempt was a placeholder
            boolean needsLinkedAccount = shop.getRazorpayLinkedAccountId() == null ||
                    shop.getRazorpayLinkedAccountId().isEmpty() ||
                    shop.getRazorpayLinkedAccountId().startsWith("acc_pending_");

            if (needsLinkedAccount) {
                String linkedAccountId = razorpayService.createLinkedAccount(
                        shop.getShopName(), shop.getEmail(), shop.getPhone(),
                        shop.getBankAccountNumber(), shop.getIfscCode(),
                        shop.getAccountHolderName(),
                        shop.getPanNumber() != null ? shop.getPanNumber() : "PENDING",
                        shop.getBusinessType() != null ? shop.getBusinessType() : "individual");

                shop.setRazorpayLinkedAccountId(linkedAccountId);
                shopRepository.save(shop);

                // Add bank account + enable Route product if account was created successfully
                if (linkedAccountId != null && !linkedAccountId.startsWith("acc_pending_")) {
                    razorpayService.addBankAccountToLinkedAccount(
                            linkedAccountId,
                            shop.getBankAccountNumber(),
                            shop.getIfscCode(),
                            shop.getAccountHolderName());
                }

                log.info("Razorpay Linked Account setup on PRO upgrade for shop: {} — ID: {}",
                        shop.getShopName(), linkedAccountId);
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("message", activationResult.get("message"));
        response.put("success", true);

        return ResponseEntity.ok(response);
    }

    // ==================== LOAN INTEREST PAYMENT (Customer → Shop Owner) ====================

    /**
     * Creates a Razorpay Order for a customer's loan interest payment.
     *
     * Purpose: Customer clicks "Pay Now" on a loan → this creates a Razorpay Order
     *          for the current interest due amount (or a custom amount).
     *
     * The amount defaults to the current balance due but the customer can adjust it
     * (partial payments are allowed — they reduce the balance but don't close the loan).
     */
    @PostMapping("/loan/create-order")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Map<String, Object>> createLoanPaymentOrder(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Object> body) {

        Long userId = extractUserId(authHeader);
        Long loanId = Long.valueOf(body.get("loanId").toString());

        // Verify the loan exists
        GoldLoan loan = goldLoanRepository.findById(loanId)
                .orElseThrow(() -> new ResourceNotFoundException("Loan not found"));

        // Get the payment amount (from request, or calculate the balance due)
        BigDecimal amount;
        if (body.containsKey("amount") && body.get("amount") != null) {
            amount = new BigDecimal(body.get("amount").toString());
        } else {
            // Calculate current balance due
            BigDecimal totalPaid = paymentService.getPaymentsByLoanId(loanId)
                    .stream()
                    .map(PaymentResponseDTO::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            amount = InterestCalculator.calculateBalanceDue(
                    loan.getLoanAmount(), loan.getInterestRate(), loan.getLoanDate(), totalPaid);
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidRequestException("No balance due on this loan.");
        }

        // Create Razorpay Order
        String receipt = "loan_" + loan.getLoanId() + "_" + System.currentTimeMillis();
        Map<String, Object> order = razorpayService.createOrder(amount, "INR", receipt);
        order.put("loanId", loanId);

        return ResponseEntity.ok(order);
    }

    /**
     * Verifies a customer's loan payment and records it.
     *
     * Purpose: After customer completes payment in Razorpay popup, verify the
     *          signature, record the payment, and route the money to the shop's
     *          linked bank account.
     *
     * Flow:
     * 1. Verify Razorpay signature (security check)
     * 2. Record the payment in our database
     * 3. Route/transfer the money to the shop's Linked Account (Razorpay Route)
     */
    @PostMapping("/loan/verify")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Map<String, Object>> verifyLoanPayment(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody PaymentVerifyRequestDTO dto) {

        // Step 1: Verify the Razorpay signature
        boolean isValid = razorpayService.verifyPaymentSignature(
                dto.getRazorpayOrderId(), dto.getRazorpayPaymentId(), dto.getRazorpaySignature());

        if (!isValid) {
            throw new InvalidRequestException("Payment verification failed. Please try again.");
        }

        // Step 2: Record the payment in our database
        PaymentResponseDTO paymentRecord = paymentService.recordOnlinePayment(
                dto.getLoanId(), dto.getAmount(),
                dto.getRazorpayPaymentId(), dto.getRazorpayOrderId(),
                "ONLINE"); // Payment mode will be generic "ONLINE" — Razorpay handles sub-types

        // Step 3: Route the money to the shop's Linked Account
        GoldLoan loan = goldLoanRepository.findById(dto.getLoanId())
                .orElseThrow(() -> new ResourceNotFoundException("Loan not found"));

        Shop shop = loan.getShop();
        if (shop.getRazorpayLinkedAccountId() != null && !shop.getRazorpayLinkedAccountId().isEmpty()) {
            try {
                razorpayService.transferToLinkedAccount(
                        dto.getRazorpayPaymentId(), shop.getRazorpayLinkedAccountId(), dto.getAmount());
            } catch (Exception e) {
                // Log but don't fail — the payment was already recorded
                // Route transfer failure can be reconciled manually
                log.error("Route transfer failed for payment {} to shop {}: {}",
                        dto.getRazorpayPaymentId(), shop.getShopName(), e.getMessage());
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Payment successful! ₹" + dto.getAmount() + " has been recorded.");
        response.put("success", true);
        response.put("receiptNumber", paymentRecord.getReceiptNumber());

        return ResponseEntity.ok(response);
    }

    // ==================== HELPER METHODS ====================

    private Long extractUserId(String authHeader) {
        String token = authHeader.substring(7);
        return jwtUtil.extractUserId(token);
    }

    private Long extractShopId(String authHeader) {
        String token = authHeader.substring(7);
        return jwtUtil.extractShopId(token);
    }
}
