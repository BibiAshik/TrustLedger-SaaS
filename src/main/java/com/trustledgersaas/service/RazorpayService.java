package com.trustledgersaas.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Map;

/**
 * RazorpayService handles the payment-facing data used by the frontend.
 *
 * This version avoids the external Razorpay Java SDK so the project can compile
 * reliably, but it still creates real Razorpay Test Mode orders using Java's
 * built-in HttpClient. Signature verification is done locally with HMAC-SHA256.
 */
@Service
@Slf4j
public class RazorpayService {

    @Value("${razorpay.key.id}")
    private String razorpayKeyId;

    @Value("${razorpay.key.secret}")
    private String razorpayKeySecret;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    /**
     * Purpose: Create a real Razorpay Test Mode order for Razorpay Checkout.
     * Input: Amount in rupees, currency code, and receipt reference.
     * Output: Map containing orderId, amount in paise, currency, keyId, and receipt.
     */
    public Map<String, Object> createOrder(BigDecimal amountInRupees, String currency, String receipt) {
        try {
            int amountInPaise = amountInRupees.multiply(new BigDecimal("100")).intValue();
            String requestBody = "{"
                    + "\"amount\":" + amountInPaise + ","
                    + "\"currency\":\"" + currency + "\","
                    + "\"receipt\":\"" + escapeJson(receipt) + "\""
                    + "}";

            String authValue = razorpayKeyId + ":" + razorpayKeySecret;
            String basicAuth = Base64.getEncoder()
                    .encodeToString(authValue.getBytes(StandardCharsets.UTF_8));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.razorpay.com/v1/orders"))
                    .header("Authorization", "Basic " + basicAuth)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> httpResponse =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (httpResponse.statusCode() < 200 || httpResponse.statusCode() >= 300) {
                log.error("Razorpay order creation failed. Status: {}, Body: {}",
                        httpResponse.statusCode(), httpResponse.body());
                throw new RuntimeException("Payment order creation failed. Please check Razorpay keys.");
            }

            String orderId = extractJsonString(httpResponse.body(), "id");
            if (orderId == null || orderId.isBlank()) {
                throw new RuntimeException("Razorpay did not return an order ID.");
            }

            Map<String, Object> response = new HashMap<>();
            response.put("orderId", orderId);
            response.put("amount", amountInPaise);
            response.put("currency", currency);
            response.put("keyId", razorpayKeyId);
            response.put("receipt", receipt);

            log.info("Razorpay test order created: {} for {}", orderId, receipt);
            return response;
        } catch (Exception e) {
            log.error("Failed to create Razorpay order: {}", e.getMessage());
            throw new RuntimeException("Payment order creation failed. Please try again.");
        }
    }

    /**
     * Purpose: Verify the Razorpay callback signature.
     * Input: Razorpay order ID, payment ID, and signature from Checkout.
     * Output: true when the signature matches the configured key secret.
     */
    public boolean verifyPaymentSignature(String orderId, String paymentId, String signature) {
        try {
            String data = orderId + "|" + paymentId;
            String generatedSignature = generateHmacSha256(data, razorpayKeySecret);
            return generatedSignature.equals(signature);
        } catch (Exception e) {
            log.error("Payment signature verification failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Creates a Razorpay Linked Account for a shop owner.
     *
     * Purpose: When a shop is approved, call the Razorpay Accounts API to create
     *          a Route Linked Account using the shop owner's bank details + PAN.
     *          In LIVE mode this creates a real verified account.
     *          In TEST mode Razorpay returns a test linked account ID.
     *
     * Razorpay API: POST https://api.razorpay.com/v2/accounts
     *
     * Input: All KYC and bank details collected during shop registration.
     * Output: The Razorpay Linked Account ID (e.g. "acc_AbCdEfGhIj1234") to
     *         be stored on the Shop record.
     */
    public String createLinkedAccount(String shopName, String email, String phone,
                                      String accountNumber, String ifscCode,
                                      String accountHolderName, String panNumber,
                                      String businessType) {
        try {
            // Razorpay expects phone without country code prefix for linked accounts
            String cleanPhone = phone.startsWith("+91") ? phone.substring(3) : phone;

            String requestBody = "{"
                    + "\"email\":\"" + escapeJson(email) + "\","
                    + "\"phone\":\"" + escapeJson(cleanPhone) + "\","
                    + "\"reference_id\":\"shop_" + System.currentTimeMillis() + "\","
                    + "\"profile\":{"
                    +   "\"category\":\"financial_services\","
                    +   "\"subcategory\":\"lending\","
                    +   "\"addresses\":{"
                    +     "\"registered\":{"
                    +       "\"street1\":\"NA\","
                    +       "\"city\":\"NA\","
                    +       "\"state\":\"NA\","
                    +       "\"postal_code\":100000,"
                    +       "\"country\":\"IN\""
                    +     "}"
                    +   "}"
                    + "},"
                    + "\"type\":\"route\","
                    + "\"legal_business_name\":\"" + escapeJson(shopName) + "\","
                    + "\"business_type\":\"" + escapeJson(businessType) + "\","
                    + "\"legal_info\":{"
                    +   "\"pan\":\"" + escapeJson(panNumber) + "\""
                    + "},"
                    + "\"contact_name\":\"" + escapeJson(accountHolderName) + "\""
                    + "}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.razorpay.com/v2/accounts"))
                    .header("Authorization", basicAuthHeader())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> httpResponse =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            log.info("Razorpay Linked Account API response [{}]: {}",
                    httpResponse.statusCode(), httpResponse.body());

            if (httpResponse.statusCode() < 200 || httpResponse.statusCode() >= 300) {
                log.error("Failed to create Razorpay Linked Account for shop: {} — Status: {} Body: {}",
                        shopName, httpResponse.statusCode(), httpResponse.body());
                // Return a placeholder so the approval still succeeds — can retry later
                return "acc_pending_" + System.currentTimeMillis();
            }

            String linkedAccountId = extractJsonString(httpResponse.body(), "id");
            if (linkedAccountId == null || linkedAccountId.isBlank()) {
                log.error("Razorpay did not return a linked account ID for shop: {}", shopName);
                return "acc_pending_" + System.currentTimeMillis();
            }

            log.info("Razorpay Linked Account created for shop [{}]: {}", shopName, linkedAccountId);
            return linkedAccountId;

        } catch (Exception e) {
            log.error("Exception creating Razorpay Linked Account for shop [{}]: {}", shopName, e.getMessage());
            // Don't block approval — store a pending marker and retry later
            return "acc_pending_" + System.currentTimeMillis();
        }
    }

    /**
     * Adds a bank account to an existing Razorpay Linked Account.
     *
     * Purpose: After creating the linked account, attach the shop owner's bank
     *          details so Razorpay can settle funds to them.
     *
     * Razorpay API: POST https://api.razorpay.com/v2/accounts/{accountId}/stakeholders
     *               POST https://api.razorpay.com/v2/accounts/{accountId}/products
     *
     * NOTE: In Razorpay's flow, after account creation you must also request the
     * "route" product and add a stakeholder. This method handles that step.
     *
     * Input: The linked account ID and the shop's bank details.
     * Output: true if successful, false if failed (non-blocking).
     */
    public boolean addBankAccountToLinkedAccount(String linkedAccountId,
                                                  String accountNumber, String ifscCode,
                                                  String accountHolderName) {
        try {
            // Step 1: Add stakeholder (bank account holder)
            String stakeholderBody = "{"
                    + "\"name\":\"" + escapeJson(accountHolderName) + "\""
                    + "}";

            HttpRequest stakeholderRequest = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.razorpay.com/v2/accounts/" + linkedAccountId + "/stakeholders"))
                    .header("Authorization", basicAuthHeader())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(stakeholderBody))
                    .build();

            HttpResponse<String> stakeholderResponse =
                    httpClient.send(stakeholderRequest, HttpResponse.BodyHandlers.ofString());
            log.info("Stakeholder API [{}]: {}", stakeholderResponse.statusCode(), stakeholderResponse.body());

            // Step 2: Request Route product for the linked account
            String productBody = "{"
                    + "\"product_name\":\"route\","
                    + "\"tnc_accepted\":true"
                    + "}";

            HttpRequest productRequest = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.razorpay.com/v2/accounts/" + linkedAccountId + "/products"))
                    .header("Authorization", basicAuthHeader())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(productBody))
                    .build();

            HttpResponse<String> productResponse =
                    httpClient.send(productRequest, HttpResponse.BodyHandlers.ofString());
            log.info("Route product API [{}]: {}", productResponse.statusCode(), productResponse.body());

            if (productResponse.statusCode() < 200 || productResponse.statusCode() >= 300) {
                return false;
            }

            String productId = extractJsonString(productResponse.body(), "id");
            if (productId == null || productId.isBlank()) {
                log.warn("Route product created but no product ID returned for linked account: {}", linkedAccountId);
                return false;
            }

            // Step 3: Attach settlement bank details to the Route product.
            String settlementBody = "{"
                    + "\"settlements\":{"
                    +   "\"account_number\":\"" + escapeJson(accountNumber) + "\","
                    +   "\"ifsc_code\":\"" + escapeJson(ifscCode) + "\","
                    +   "\"beneficiary_name\":\"" + escapeJson(accountHolderName) + "\""
                    + "},"
                    + "\"tnc_accepted\":true"
                    + "}";

            HttpRequest settlementRequest = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.razorpay.com/v2/accounts/" + linkedAccountId
                            + "/products/" + productId))
                    .header("Authorization", basicAuthHeader())
                    .header("Content-Type", "application/json")
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(settlementBody))
                    .build();

            HttpResponse<String> settlementResponse =
                    httpClient.send(settlementRequest, HttpResponse.BodyHandlers.ofString());
            log.info("Route settlement API [{}]: {}",
                    settlementResponse.statusCode(), settlementResponse.body());

            return settlementResponse.statusCode() >= 200 && settlementResponse.statusCode() < 300;
        } catch (Exception e) {
            log.error("Failed to add bank account to linked account [{}]: {}", linkedAccountId, e.getMessage());
            return false;
        }
    }

    /**
     * Routes (transfers) a customer's payment to the shop's Razorpay Linked Account.
     *
     * Purpose: After verifying a customer's online loan payment, immediately route
     *          the full amount to the shop owner's linked bank account via Razorpay Route.
     *          In LIVE mode money lands in the shop's bank. In TEST mode it's simulated.
     *
     * Razorpay API: POST https://api.razorpay.com/v1/payments/{paymentId}/transfers
     *
     * Input: The Razorpay paymentId (from Checkout callback), the shop's linked
     *        account ID, and the amount to transfer.
     * Output: The Razorpay Transfer ID (logged and returned for audit purposes).
     */
    public String transferToLinkedAccount(String paymentId, String linkedAccountId,
                                          BigDecimal amountInRupees) {
        // Skip transfer for placeholder/pending accounts (test mode or setup incomplete)
        if (linkedAccountId == null || linkedAccountId.startsWith("acc_pending_") ||
                linkedAccountId.startsWith("acc_demo_")) {
            log.warn("Skipping route transfer — linked account not ready: {}", linkedAccountId);
            return null;
        }

        try {
            int amountInPaise = amountInRupees.multiply(new BigDecimal("100")).intValue();

            // Razorpay Route Transfer body
            String requestBody = "{"
                    + "\"transfers\":[{"
                    +   "\"account\":\"" + escapeJson(linkedAccountId) + "\","
                    +   "\"amount\":" + amountInPaise + ","
                    +   "\"currency\":\"INR\""
                    + "}]"
                    + "}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.razorpay.com/v1/payments/" + paymentId + "/transfers"))
                    .header("Authorization", basicAuthHeader())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> httpResponse =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            log.info("Route transfer API [{}]: {}", httpResponse.statusCode(), httpResponse.body());

            if (httpResponse.statusCode() < 200 || httpResponse.statusCode() >= 300) {
                log.error("Route transfer failed — Payment: {}, Shop account: {}, Status: {}",
                        paymentId, linkedAccountId, httpResponse.statusCode());
                return null;
            }

            String transferId = extractJsonString(httpResponse.body(), "id");
            log.info("Route transfer successful — Transfer ID: {}, Payment: {}, Amount: ₹{}",
                    transferId, paymentId, amountInRupees);
            return transferId;

        } catch (Exception e) {
            log.error("Exception during route transfer for payment [{}]: {}", paymentId, e.getMessage());
            return null;
        }
    }

    /**
     * Purpose: Generate the HMAC-SHA256 hash used by Razorpay signatures.
     * Input: The message to sign and the Razorpay key secret.
     * Output: Hex-encoded HMAC string.
     */
    private String generateHmacSha256(String data, String secret) throws Exception {
        SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes("UTF-8"), "HmacSHA256");
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(secretKeySpec);

        byte[] hashBytes = mac.doFinal(data.getBytes("UTF-8"));
        StringBuilder hexString = new StringBuilder();
        Formatter formatter = new Formatter(hexString);
        for (byte b : hashBytes) {
            formatter.format("%02x", b);
        }
        formatter.close();

        return hexString.toString();
    }

    private String basicAuthHeader() {
        String authValue = razorpayKeyId + ":" + razorpayKeySecret;
        String basicAuth = Base64.getEncoder()
                .encodeToString(authValue.getBytes(StandardCharsets.UTF_8));
        return "Basic " + basicAuth;
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String extractJsonString(String json, String fieldName) {
        String pattern = "\"" + fieldName + "\":\"";
        int start = json.indexOf(pattern);
        if (start == -1) {
            return null;
        }
        start = start + pattern.length();
        int end = json.indexOf("\"", start);
        if (end == -1) {
            return null;
        }
        return json.substring(start, end);
    }
}
