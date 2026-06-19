/**
 * PAYMENT.JS — Handles Razorpay payment integration for both
 * subscription payments (Shop Owner → Super Admin) and
 * loan interest payments (Customer → Shop Owner).
 */

// ==================== SUBSCRIPTION PAYMENT (Shop Owner) ====================

/**
 * Initiates a subscription payment flow.
 *
 * Step 1: Create a Razorpay Order on our backend
 * Step 2: Open the Razorpay Checkout popup
 * Step 3: After payment, verify the signature on our backend
 *
 * Input: planType — 'BASIC' or 'PRO'
 */
async function paySubscription(planType) {
    try {
        // Step 1: Create order on backend
        const orderData = await apiCall('/api/payment/subscription/create-order', 'POST', {
            planType: planType
        });

        // Step 2: Open Razorpay Checkout popup
        const options = {
            key: orderData.keyId,
            amount: orderData.amount,
            currency: orderData.currency,
            name: 'Trust Ledger',
            description: planType + ' Plan Subscription',
            order_id: orderData.orderId,
            handler: function(response) {
                // Step 3: Verify payment on backend
                verifySubscriptionPayment(response, planType, orderData.amount);
            },
            prefill: {
                email: localStorage.getItem('user_email') || '',
                contact: localStorage.getItem('user_phone') || ''
            },
            theme: {
                color: '#D4A017'
            },
            modal: {
                ondismiss: function() {
                    showToast('Payment cancelled.', 'warning');
                }
            }
        };

        const razorpay = new Razorpay(options);
        razorpay.open();

    } catch (error) {
        showToast('Failed to initiate payment. Please try again.', 'danger');
    }
}

/**
 * Verifies the subscription payment after Razorpay Checkout completes.
 */
async function verifySubscriptionPayment(razorpayResponse, planType, amount) {
    // Show processing state
    showToast('Processing payment...', 'info');

    try {
        const data = await apiCall('/api/payment/subscription/verify', 'POST', {
            razorpayOrderId: razorpayResponse.razorpay_order_id,
            razorpayPaymentId: razorpayResponse.razorpay_payment_id,
            razorpaySignature: razorpayResponse.razorpay_signature,
            planType: planType,
            amount: amount / 100 // Convert paise back to rupees
        });

        showToast('Payment successful! Your ' + planType + ' subscription is now active.', 'success');

        // Reload the page to reflect the new subscription status
        setTimeout(function() {
            window.location.reload();
        }, 2000);

    } catch (error) {
        showToast('Payment verification failed. Please contact support.', 'danger');
    }
}

// ==================== LOAN INTEREST PAYMENT (Customer) ====================

/**
 * Initiates an online loan interest payment flow.
 *
 * Input: loanId — the loan to pay for
 *        amount — optional custom amount (defaults to full balance due)
 */
async function payLoanInterest(loanId, amount) {
    try {
        const body = { loanId: loanId };
        if (amount) body.amount = amount;

        // Step 1: Create order on backend
        const orderData = await apiCall('/api/payment/loan/create-order', 'POST', body);

        // Step 2: Open Razorpay Checkout popup
        const options = {
            key: orderData.keyId,
            amount: orderData.amount,
            currency: orderData.currency,
            name: 'Trust Ledger',
            description: 'Loan Interest Payment',
            order_id: orderData.orderId,
            handler: function(response) {
                // Step 3: Verify payment on backend
                verifyLoanPayment(response, loanId, orderData.amount);
            },
            theme: {
                color: '#D4A017'
            },
            modal: {
                ondismiss: function() {
                    showToast('Payment cancelled.', 'warning');
                }
            }
        };

        const razorpay = new Razorpay(options);
        razorpay.open();

    } catch (error) {
        showToast('Failed to initiate payment. Please try again.', 'danger');
    }
}

/**
 * Verifies the loan payment after Razorpay Checkout completes.
 * Shows a "Processing payment..." spinner during verification.
 */
async function verifyLoanPayment(razorpayResponse, loanId, amountInPaise) {
    // Show processing overlay
    const overlay = document.getElementById('paymentProcessing');
    if (overlay) overlay.style.display = 'flex';

    try {
        const data = await apiCall('/api/payment/loan/verify', 'POST', {
            razorpayOrderId: razorpayResponse.razorpay_order_id,
            razorpayPaymentId: razorpayResponse.razorpay_payment_id,
            razorpaySignature: razorpayResponse.razorpay_signature,
            loanId: loanId,
            amount: amountInPaise / 100 // Convert paise back to rupees
        });

        // Show success
        if (overlay) {
            overlay.innerHTML = '<div style="text-align:center;padding:40px;color:#FFFFFF;">' +
                '<div style="font-size:64px;margin-bottom:16px;">✅</div>' +
                '<h2>Payment Successful!</h2>' +
                '<p class="text-muted" style="color:#A0AEC0;">' + data.message + '</p>' +
                '<p class="text-sm text-muted" style="color:#A0AEC0;">Receipt: ' + data.receiptNumber + '</p>' +
                '<a href="/customer/dashboard" class="btn btn-gold mt-4">Back to Dashboard</a></div>';
        } else {
            showToast(data.message || 'Payment successful!', 'success');
            setTimeout(function() {
                window.location.href = '/customer/dashboard';
            }, 2000);
        }

    } catch (error) {
        // Show failure
        if (overlay) {
            overlay.innerHTML = '<div style="text-align:center;padding:40px;color:#FFFFFF;">' +
                '<div style="font-size:64px;margin-bottom:16px;">❌</div>' +
                '<h2>Payment Failed</h2>' +
                '<p class="text-muted" style="color:#A0AEC0;">Please try again or contact support.</p>' +
                '<a href="/customer/dashboard" class="btn btn-outline mt-4">Back to Dashboard</a></div>';
        } else {
            showToast('Payment failed. Please try again.', 'danger');
        }
    }
}

/**
 * Initializes the pay page with loan details and payment form.
 */
async function initPayPage(loanId) {
    try {
        const data = await apiCall('/api/customer/loans/' + loanId, 'GET');
        const loan = data.loan;

        const container = document.getElementById('payContent');
        if (!container) return;

        let html = '<div class="card">' +
            '<h3>Pay for Loan: ' + loan.loanId + '</h3>' +
            '<div class="detail-grid mt-4">' +
            '<div class="detail-group"><div class="detail-label">Gold Item</div><div class="detail-value">' + loan.goldItemType + '</div></div>' +
            '<div class="detail-group"><div class="detail-label">Loan Amount</div><div class="detail-value">' + formatCurrency(loan.loanAmount) + '</div></div>' +
            '<div class="detail-group"><div class="detail-label">Balance Due</div><div class="detail-value large text-danger">' + formatCurrency(loan.balanceDue) + '</div></div>' +
            '</div>' +
            '<div class="form-group mt-6">' +
            '<label>Payment Amount (₹)</label>' +
            '<input type="number" id="payAmount" class="form-input" step="0.01" min="1" value="' + (loan.balanceDue || 0) + '">' +
            '<p class="form-help">You can pay the full balance or a partial amount.</p>' +
            '</div>' +
            '<button class="btn btn-gold btn-lg btn-block" onclick="payLoanInterest(' + loanId + ', document.getElementById(\'payAmount\').value)">' +
            '💰 Pay Now with Razorpay</button></div>';

        // Processing overlay (hidden initially)
        html += '<div id="paymentProcessing" class="loading-overlay" style="display:none;position:fixed;top:0;left:0;right:0;bottom:0;background:rgba(5,5,6,0.95);z-index:9999;color:#FFFFFF;">' +
            '<div class="spinner spinner-lg"></div><p>Processing payment...</p></div>';

        container.innerHTML = html;

    } catch (error) {
        showToast('Failed to load loan details.', 'danger');
    }
}
