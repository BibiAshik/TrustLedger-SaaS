/**
 * DASHBOARD.JS — Dashboard page logic for Shop Owner, Customer, and Super Admin.
 *
 * This file handles:
 * - Loading and rendering dashboard stats
 * - Customer list with live search
 * - Loan list and detail views
 * - Payment recording
 * - Loan lifecycle actions (close, seize, extend)
 * - Super Admin approval/rejection
 */

// ==================== SHOP OWNER DASHBOARD ====================

/**
 * Loads the Shop Owner dashboard data.
 */
async function loadShopDashboard() {
    showLoading('recentPayments');
    showLoading('loansDueThisWeek');
    try {
        const data = await apiCall('/api/shop/dashboard', 'GET');
        renderShopDashboard(data);
    } catch (error) {
        showToast('Failed to load dashboard.', 'danger');
        showEmptyState('recentPayments', 'Could not load recent payments. Please refresh after logging in again.', '!');
        showEmptyState('loansDueThisWeek', 'Could not load loans due this week. Please refresh after logging in again.', '!');
    }
}

function renderShopDashboard(data) {
    const shop = data.shop;
    const recentPayments = data.recentPayments || [];
    const loansDueThisWeek = data.loansDueThisWeek || [];
    const activeLoans = shop.activeLoanCount || shop.activeLoans || 0;
    const overdueLoans = shop.overdueLoanCount || shop.overdueLoans || 0;
    const totalCustomers = shop.customerCount || shop.totalCustomers || 0;
    const totalLoanVolume = data.totalLoanVolume || shop.totalLoanVolume || 0;

    // Update shop name in navbar
    const shopNameEl = document.getElementById('shopName');
    if (shopNameEl) shopNameEl.textContent = shop.shopName;

    // Render stats
    const statsEl = document.getElementById('statsGrid');
    if (statsEl) {
        statsEl.innerHTML =
            '<div class="stat-card gold"><div class="stat-value">' + activeLoans + '</div><div class="stat-label">Active Loans</div></div>' +
            '<div class="stat-card red"><div class="stat-value">' + overdueLoans + '</div><div class="stat-label">Overdue Loans</div></div>' +
            '<div class="stat-card blue"><div class="stat-value">' + totalCustomers + '</div><div class="stat-label">Total Customers</div></div>' +
            '<div class="stat-card green"><div class="stat-value">' + formatCurrency(totalLoanVolume) + '</div><div class="stat-label">Total Loan Volume</div></div>';
    }

    // Render recent payments
    const paymentsEl = document.getElementById('recentPayments');
    if (paymentsEl) {
        if (recentPayments.length === 0) {
            paymentsEl.innerHTML = '<p class="text-muted text-center p-4">No recent payments</p>';
        } else {
            let html = '';
            recentPayments.forEach(function(p) {
                html += '<div class="list-item">' +
                    '<div class="item-info"><div class="item-avatar">' + getInitials(p.customerName) + '</div>' +
                    '<div><div class="item-name">' + p.customerName + '</div>' +
                    '<div class="item-sub">' + p.loanDisplayId + ' · ' + p.paymentMode + '</div></div></div>' +
                    '<div class="item-right"><div class="item-amount">' + formatCurrency(p.amount) + '</div>' +
                    '<div class="item-date">' + formatDate(p.paymentDate) + '</div></div></div>';
            });
            paymentsEl.innerHTML = html;
        }
    }

    // Render loans due this week
    const dueEl = document.getElementById('loansDueThisWeek');
    if (dueEl) {
        if (loansDueThisWeek.length === 0) {
            dueEl.innerHTML = '<p class="text-muted text-center p-4">No loans due this week</p>';
        } else {
            let html = '';
            loansDueThisWeek.forEach(function(loan) {
                html += '<div class="list-item" onclick="window.location.href=\'/shop/loans/' + loan.id + '\'">' +
                    '<div class="item-info"><div><div class="item-name">' + loan.loanId + '</div>' +
                    '<div class="item-sub">' + loan.customerName + ' · ' + loan.goldItemType + '</div></div></div>' +
                    '<div class="item-right"><div class="item-amount">' + formatCurrency(loan.balanceDue) + '</div>' +
                    '<div class="item-date">Due: ' + formatDate(loan.dueDate) + '</div></div></div>';
            });
            dueEl.innerHTML = html;
        }
    }
}

// ==================== CUSTOMER LIST WITH SEARCH ====================

let searchTimeout;

/**
 * Loads the customer list (with optional search and pagination).
 */
async function loadCustomers(page, search) {
    page = page || 0;
    search = search || '';

    showLoading('customerListContent');

    try {
        let url = '/api/shop/customers?page=' + page + '&size=10';
        if (search) url += '&search=' + encodeURIComponent(search);

        const data = await apiCall(url, 'GET');
        renderCustomerList(data);
    } catch (error) {
        showToast('Failed to load customers.', 'danger');
        showEmptyState('customerListContent', 'Could not load customers. Please refresh after logging in again.', '!');
    }
}

function renderCustomerList(pageData) {
    const container = document.getElementById('customerListContent');
    if (!container) return;

    if (!pageData.content || pageData.content.length === 0) {
        showEmptyState('customerListContent', 'No customers found. Add your first customer!', '👤');
        return;
    }

    let html = '';
    pageData.content.forEach(function(c) {
        html += '<div class="customer-list-item" onclick="window.location.href=\'/shop/customers/' + c.id + '\'">' +
            '<div class="customer-details"><div class="customer-name">' + c.fullName + '</div>' +
            '<div class="customer-phone">' + c.phone + '</div></div>' +
            '<div class="customer-stats">' +
            '<div class="customer-stat"><div class="stat-value">' + (c.activeLoanCount || 0) + '</div><div class="stat-label">Active Loans</div></div>' +
            '<div class="customer-stat"><div class="stat-value">' + (c.totalOutstanding || '₹0') + '</div><div class="stat-label">Outstanding</div></div>' +
            '</div></div>';
    });

    html += buildPagination(pageData, 'loadCustomers');
    container.innerHTML = html;
}

/**
 * Live search handler — triggers search as the user types.
 */
function handleCustomerSearch(input) {
    clearTimeout(searchTimeout);
    searchTimeout = setTimeout(function() {
        loadCustomers(0, input.value.trim());
    }, 300);
}

// ==================== LOAN MANAGEMENT ====================

/**
 * Loads the loan list (with optional status filter).
 */
async function loadLoans(page, status) {
    page = page || 0;

    showLoading('loanListContent');

    try {
        let url = '/api/shop/loans?page=' + page + '&size=10';
        if (status) url += '&status=' + status;

        const data = await apiCall(url, 'GET');
        renderLoanList(data);
    } catch (error) {
        showToast('Failed to load loans.', 'danger');
        showEmptyState('loanListContent', 'Could not load loans. Please refresh after logging in again.', '!');
    }
}

function renderLoanList(pageData) {
    const container = document.getElementById('loanListContent');
    if (!container) return;

    if (!pageData.content || pageData.content.length === 0) {
        showEmptyState('loanListContent', 'No loans found.', '📋');
        return;
    }

    let html = '<table class="data-table data-table-responsive"><thead><tr>' +
        '<th>Loan ID</th><th>Customer</th><th>Amount</th><th>Due Date</th><th>Status</th><th>Balance Due</th><th>Actions</th>' +
        '</tr></thead><tbody>';

    pageData.content.forEach(function(loan) {
        html += '<tr class="clickable" onclick="window.location.href=\'/shop/loans/' + loan.id + '\'">' +
            '<td data-label="Loan ID"><strong>' + loan.loanId + '</strong></td>' +
            '<td data-label="Customer">' + loan.customerName + '</td>' +
            '<td data-label="Amount">' + formatCurrency(loan.loanAmount) + '</td>' +
            '<td data-label="Due Date">' + formatDate(loan.dueDate) + '</td>' +
            '<td data-label="Status">' + getStatusBadge(loan.status) + '</td>' +
            '<td data-label="Balance Due" class="td-amount">' + formatCurrency(loan.balanceDue) + '</td>' +
            '<td><a href="/shop/loans/' + loan.id + '" class="btn btn-sm btn-outline">View</a></td>' +
            '</tr>';
    });

    html += '</tbody></table>';
    html += buildPagination(pageData, 'loadLoans');
    container.innerHTML = html;
}

// ==================== LOAN DETAIL ====================

async function loadLoanDetail(loanId) {
    showLoading('loanDetailContent');
    try {
        const loan = await apiCall('/api/shop/loans/' + loanId, 'GET');
        const payments = await apiCall('/api/shop/loans/' + loanId + '/payments', 'GET');
        renderLoanDetail(loan, payments);
    } catch (error) {
        showToast('Failed to load loan details.', 'danger');
    }
}

function renderLoanDetail(loan, payments) {
    const container = document.getElementById('loanDetailContent');
    if (!container) return;

    let html = '';

    // Seizure warning banner
    if (loan.seizureWarningShown) {
        html += '<div class="seizure-warning"><div class="warning-icon">⚠️</div>' +
            '<div class="warning-text"><h4>Seizure Warning</h4>' +
            '<p>This loan is 30+ days overdue. The pledged gold item may be seized if payment is not received.</p></div></div>';
    }

    // Loan details grid
    html += '<div class="card mb-6"><div class="card-header"><h3>Loan Details — ' + loan.loanId + '</h3>' + getStatusBadge(loan.status) + '</div>';
    html += '<div class="detail-grid">' +
        '<div class="detail-group"><div class="detail-label">Customer</div><div class="detail-value">' + loan.customerName + '</div></div>' +
        '<div class="detail-group"><div class="detail-label">Loan Amount</div><div class="detail-value large">' + formatCurrency(loan.loanAmount) + '</div></div>' +
        '<div class="detail-group"><div class="detail-label">Interest Rate</div><div class="detail-value">' + loan.interestRate + '% / month</div></div>' +
        '<div class="detail-group"><div class="detail-label">Daily Interest</div><div class="detail-value">' + formatCurrency(loan.dailyInterestAmount) + '</div></div>' +
        '<div class="detail-group"><div class="detail-label">Loan Date</div><div class="detail-value">' + formatDate(loan.loanDate) + '</div></div>' +
        '<div class="detail-group"><div class="detail-label">Due Date</div><div class="detail-value">' + formatDate(loan.dueDate) + '</div></div>' +
        '<div class="detail-group"><div class="detail-label">Total Interest Accrued</div><div class="detail-value text-warning">' + formatCurrency(loan.totalInterestAccrued) + '</div></div>' +
        '<div class="detail-group"><div class="detail-label">Total Paid</div><div class="detail-value text-success">' + formatCurrency(loan.totalPaid) + '</div></div>' +
        '<div class="detail-group"><div class="detail-label">Balance Due</div><div class="detail-value large text-danger">' + formatCurrency(loan.balanceDue) + '</div></div>' +
        '</div>';

    // Gold item details
    html += '<h4 class="mt-6 mb-4">Gold Item</h4><div class="detail-grid">' +
        '<div class="detail-group"><div class="detail-label">Item Type</div><div class="detail-value">' + loan.goldItemType + '</div></div>' +
        '<div class="detail-group"><div class="detail-label">Weight</div><div class="detail-value">' + loan.goldWeight + ' grams</div></div>' +
        '<div class="detail-group"><div class="detail-label">Purity</div><div class="detail-value">' + loan.goldPurity + '</div></div>' +
        '<div class="detail-group"><div class="detail-label">Estimated Value</div><div class="detail-value">' + formatCurrency(loan.estimatedValue) + '</div></div>' +
        '</div></div>';

    // Action buttons (only for non-closed/seized loans)
    if (loan.status === 'ACTIVE' || loan.status === 'OVERDUE') {
        html += '<div class="flex gap-3 mb-6">' +
            '<button class="btn btn-gold" onclick="showRecordPayment(' + loan.id + ')">💰 Record Cash Payment</button>' +
            '<button class="btn btn-success" onclick="closeLoan(' + loan.id + ')">✅ Close Loan</button>' +
            '<button class="btn btn-blue" onclick="showExtendDueDate(' + loan.id + ')">📅 Extend Due Date</button>';
        if (loan.status === 'OVERDUE') {
            html += '<button class="btn btn-danger" onclick="seizeLoan(' + loan.id + ')">🔒 Mark as Seized</button>';
        }
        html += '</div>';
    }

    if (loan.status === 'CLOSED') {
        html += '<div class="mb-6"><button type="button" onclick="authenticatedDownload(\'/api/shop/loans/' + loan.id + '/closure-receipt\', \'closure-receipt-' + loan.loanId + '.pdf\')" class="btn btn-gold">📄 Download Closure Receipt</button></div>';
    }

    // Payment history
    html += '<div class="section-card"><div class="section-header"><h3>Payment History</h3></div><div class="section-body">';
    if (!payments || payments.length === 0) {
        html += '<p class="text-muted text-center p-4">No payments recorded yet.</p>';
    } else {
        html += '<table class="data-table"><thead><tr><th>Date</th><th>Receipt #</th><th>Mode</th><th>Amount</th><th>Note</th></tr></thead><tbody>';
        payments.forEach(function(p) {
            html += '<tr><td>' + formatDate(p.paymentDate) + '</td>' +
                '<td>' + (p.receiptNumber || '—') + '</td>' +
                '<td><span class="payment-mode ' + (p.paymentMode === 'CASH' ? 'cash' : 'online') + '">' + p.paymentMode + '</span></td>' +
                '<td class="td-amount">' + formatCurrency(p.amount) + '</td>' +
                '<td>' + (p.note || '—') + '</td></tr>';
        });
        html += '</tbody></table>';
    }
    html += '</div></div>';

    // Record payment modal (hidden initially)
    html += '<div id="recordPaymentModal" class="modal-overlay">' +
        '<div class="modal"><button class="modal-close" onclick="closeRecordPayment()">✕</button>' +
        '<h2>Record Cash Payment</h2>' +
        '<form onsubmit="submitCashPayment(event, ' + loan.id + ')">' +
        '<div class="form-group"><label>Amount (₹)<span class="required">*</span></label>' +
        '<input type="number" id="paymentAmount" class="form-input" step="0.01" min="1" required></div>' +
        '<div class="form-group"><label>Payment Date<span class="required">*</span></label>' +
        '<input type="date" id="paymentDate" class="form-input" required></div>' +
        '<div class="form-group"><label>Note <span class="optional">(Optional)</span></label>' +
        '<textarea id="paymentNote" class="form-textarea" rows="2" maxlength="500"></textarea></div>' +
        '<button type="submit" class="btn btn-gold btn-block">Record Payment</button></form></div></div>';

    // Extend due date modal
    html += '<div id="extendDueDateModal" class="modal-overlay">' +
        '<div class="modal"><button class="modal-close" onclick="closeExtendDueDate()">✕</button>' +
        '<h2>Extend Due Date</h2>' +
        '<form onsubmit="submitExtendDueDate(event, ' + loan.id + ')">' +
        '<div class="form-group"><label>New Due Date<span class="required">*</span></label>' +
        '<input type="date" id="newDueDate" class="form-input" required></div>' +
        '<button type="submit" class="btn btn-blue btn-block">Extend Due Date</button></form></div></div>';

    container.innerHTML = html;

    // Set default payment date to today
    const dateInput = document.getElementById('paymentDate');
    if (dateInput) dateInput.value = new Date().toISOString().split('T')[0];
}

// ==================== PAYMENT RECORDING ====================

function showRecordPayment(loanId) {
    document.getElementById('recordPaymentModal').classList.add('active');
}

function closeRecordPayment() {
    document.getElementById('recordPaymentModal').classList.remove('active');
}

async function submitCashPayment(event, loanId) {
    event.preventDefault();
    const btn = event.target.querySelector('button[type="submit"]');
    btn.disabled = true;
    btn.innerHTML = '<span class="spinner"></span> Recording...';

    try {
        await apiCall('/api/shop/loans/' + loanId + '/payments', 'POST', {
            loanId: loanId,
            amount: parseFloat(document.getElementById('paymentAmount').value),
            paymentDate: document.getElementById('paymentDate').value,
            note: document.getElementById('paymentNote').value
        });
        showToast('Payment recorded successfully!', 'success');
        closeRecordPayment();
        loadLoanDetail(loanId);
    } catch (error) {
        showToast('Failed to record payment.', 'danger');
    } finally {
        btn.disabled = false;
        btn.innerHTML = 'Record Payment';
    }
}

// ==================== LOAN LIFECYCLE ACTIONS ====================

async function closeLoan(loanId) {
    if (!confirm('Are you sure you want to close this loan? This means the customer has fully paid and the gold has been returned.')) return;

    try {
        await apiCall('/api/shop/loans/' + loanId + '/close', 'POST');
        showToast('Loan closed successfully!', 'success');
        loadLoanDetail(loanId);
    } catch (error) {
        showToast('Failed to close loan.', 'danger');
    }
}

async function seizeLoan(loanId) {
    if (!confirm('⚠️ Are you sure you want to mark this loan as SEIZED? This means the gold item will NOT be returned to the customer. This action cannot be undone.')) return;

    try {
        await apiCall('/api/shop/loans/' + loanId + '/seize', 'POST');
        showToast('Loan marked as seized.', 'warning');
        loadLoanDetail(loanId);
    } catch (error) {
        showToast('Failed to seize loan.', 'danger');
    }
}

function showExtendDueDate(loanId) {
    document.getElementById('extendDueDateModal').classList.add('active');
}

function closeExtendDueDate() {
    document.getElementById('extendDueDateModal').classList.remove('active');
}

async function submitExtendDueDate(event, loanId) {
    event.preventDefault();
    try {
        await apiCall('/api/shop/loans/' + loanId + '/extend', 'POST', {
            newDueDate: document.getElementById('newDueDate').value
        });
        showToast('Due date extended successfully!', 'success');
        closeExtendDueDate();
        loadLoanDetail(loanId);
    } catch (error) {
        showToast('Failed to extend due date.', 'danger');
    }
}

// ==================== SUPER ADMIN DASHBOARD ====================

async function loadAdminDashboard() {
    showLoading('dashboardContent');
    try {
        const data = await apiCall('/api/admin/analytics', 'GET');
        renderAdminDashboard(data);
    } catch (error) {
        showToast('Failed to load dashboard.', 'danger');
        showEmptyState('dashboardContent', 'Could not load admin dashboard. Please refresh after logging in again.', '!');
    }
}

function renderAdminDashboard(data) {
    const statsEl = document.getElementById('statsGrid');
    if (statsEl) {
        statsEl.innerHTML =
            '<div class="stat-card gold"><div class="stat-value">' + (data.totalShops || 0) + '</div><div class="stat-label">Total Shops</div></div>' +
            '<div class="stat-card green"><div class="stat-value">' + (data.activeShops || 0) + '</div><div class="stat-label">Active Shops</div></div>' +
            '<div class="stat-card warning"><div class="stat-value">' + (data.pendingApprovals || 0) + '</div><div class="stat-label">Pending Approvals</div></div>' +
            '<div class="stat-card blue"><div class="stat-value">' + (data.totalCustomers || 0) + '</div><div class="stat-label">Total Customers</div></div>' +
            '<div class="stat-card gold"><div class="stat-value">' + (data.totalActiveLoans || 0) + '</div><div class="stat-label">Active Loans</div></div>' +
            '<div class="stat-card red"><div class="stat-value">' + (data.expiredSubscriptions || 0) + '</div><div class="stat-label">Expired Subs</div></div>' +
            '<div class="stat-card green"><div class="stat-value">' + formatCurrency(data.totalLoanVolume) + '</div><div class="stat-label">Loan Volume</div></div>' +
            '<div class="stat-card blue"><div class="stat-value">' + formatCurrency(data.monthlyRevenue) + '</div><div class="stat-label">This Month Revenue</div></div>';
    }

    const contentEl = document.getElementById('dashboardContent');
    if (contentEl) {
        contentEl.innerHTML = '';
    }
}

// ==================== ADMIN SHOP APPROVALS ====================

async function loadPendingShops(page) {
    page = page || 0;
    showLoading('shopListContent');
    try {
        const data = await apiCall('/api/admin/shops/pending?page=' + page + '&size=10', 'GET');
        renderShopList(data, true);
    } catch (error) {
        showToast('Failed to load pending shops.', 'danger');
        showEmptyState('shopListContent', 'Could not load pending shops. Please refresh after logging in again.', '!');
    }
}

async function loadAllShops(page) {
    page = page || 0;
    showLoading('shopListContent');
    try {
        const data = await apiCall('/api/admin/shops?page=' + page + '&size=10', 'GET');
        renderShopList(data, false);
    } catch (error) {
        showToast('Failed to load shops.', 'danger');
        showEmptyState('shopListContent', 'Could not load shops. Please refresh after logging in again.', '!');
    }
}

function renderShopList(pageData, isPending) {
    const container = document.getElementById('shopListContent');
    if (!container) return;

    if (!pageData.content || pageData.content.length === 0) {
        showEmptyState('shopListContent', isPending ? 'No pending shop registrations.' : 'No shops registered yet.', '🏪');
        return;
    }

    let html = '<table class="data-table data-table-responsive"><thead><tr>' +
        '<th>Shop Name</th><th>Owner</th><th>City</th><th>Plan</th><th>Status</th><th>Registered</th><th>Actions</th>' +
        '</tr></thead><tbody>';

    pageData.content.forEach(function(shop) {
        html += '<tr><td data-label="Shop">' + shop.shopName + '</td>' +
            '<td data-label="Owner">' + shop.ownerFullName + '</td>' +
            '<td data-label="City">' + shop.city + '</td>' +
            '<td data-label="Plan"><span class="badge badge-' + (shop.plan || 'basic').toLowerCase() + '">' + (shop.plan || 'BASIC') + '</span></td>' +
            '<td data-label="Status">' + getStatusBadge(shop.status) + '</td>' +
            '<td data-label="Registered">' + formatDate(shop.createdAt) + '</td>' +
            '<td><button class="btn btn-sm btn-success" onclick="approveShop(' + shop.id + ', \'' + shop.shopName.replace(/'/g, "\\'") + '\')">Approve</button> ' +
            '<button class="btn btn-sm btn-danger" onclick="rejectShop(' + shop.id + ', \'' + shop.shopName.replace(/'/g, "\\'") + '\')">Reject</button> ' +
            '<a href="/admin/shops/' + shop.id + '" class="btn btn-sm btn-outline">Review</a></td></tr>';
    });

    html += '</tbody></table>';
    html += buildPagination(pageData, isPending ? 'loadPendingShops' : 'loadAllShops');
    container.innerHTML = html;
}

let approveShopTargetId = null;

function openApproveShopModal(shopId, shopName) {
    ensureApproveShopModal();
    approveShopTargetId = shopId;
    const nameEl = document.getElementById('approveShopName');
    if (nameEl) nameEl.textContent = shopName || 'this shop';
    document.getElementById('approveShopModal').classList.add('active');
}

function closeApproveShopModal(event) {
    if (event && event.target !== event.currentTarget) return;
    const modal = document.getElementById('approveShopModal');
    if (modal) modal.classList.remove('active');
    approveShopTargetId = null;
}

function ensureApproveShopModal() {
    if (document.getElementById('approveShopModal')) return;

    const modalHtml =
        '<div id="approveShopModal" class="modal-overlay" onclick="closeApproveShopModal(event)">' +
            '<div class="modal" onclick="event.stopPropagation()" style="padding:32px;">' +
                '<button type="button" class="modal-close" onclick="closeApproveShopModal()">✕</button>' +
                '<h2 style="margin-bottom:16px;">Approve Shop</h2>' +
                '<p class="text-muted" style="margin-bottom:24px; line-height:1.5;">' +
                    'Are you sure you want to approve <strong id="approveShopName" style="color:var(--white);">this shop</strong>? ' +
                    'They will be notified and can proceed to select a subscription plan.' +
                '</p>' +
                '<div style="border-top:1px solid var(--gray-200); margin-bottom:24px;"></div>' +
                '<div class="form-actions" style="display:flex; gap:16px; justify-content:flex-end;">' +
                    '<button type="button" class="btn" style="border:1px solid var(--gray-400); color:var(--gray-300); background:transparent;" onclick="closeApproveShopModal()">Cancel</button>' +
                    '<button type="button" class="btn btn-gold" onclick="submitApproveShop()">Confirm Approve</button>' +
                '</div>' +
            '</div>' +
        '</div>';

    document.body.insertAdjacentHTML('beforeend', modalHtml);
}

async function approveShop(shopId, shopName) {
    openApproveShopModal(shopId, shopName);
}

async function submitApproveShop() {
    if (!approveShopTargetId) return;
    try {
        await apiCall('/api/admin/shops/' + approveShopTargetId + '/approve', 'POST');
        showToast('Shop approved successfully!', 'success');
        closeApproveShopModal();
        setTimeout(function() { window.location.reload(); }, 1000);
    } catch (error) {
        showToast('Failed to approve shop.', 'danger');
    }
}

async function rejectShop(shopId, shopName) {
    openRejectShopModal(shopId, shopName);
}

function ensureRejectShopModal() {
    if (document.getElementById('rejectShopModal')) return;

    const modalHtml =
        '<div id="rejectShopModal" class="modal-overlay" onclick="closeRejectShopModal(event)">' +
            '<div class="modal reject-modal" onclick="event.stopPropagation()" style="padding:32px;">' +
                '<button type="button" class="modal-close" onclick="closeRejectShopModal()">✕</button>' +
                '<h2 style="margin-bottom:16px;">Reject Shop: <span id="rejectShopName" style="color:var(--gold);"></span></h2>' +
                '<p class="text-sm text-muted mb-4">Please select a reason. The shop owner will receive this by email.</p>' +
                '<div class="form-group reject-reasons" style="display:flex; flex-direction:column; gap:12px; margin-bottom:24px;">' +
                    '<label style="display:flex; align-items:center; gap:8px; cursor:pointer;"><input type="radio" name="rejectReasonPreset" value="Aadhaar document is unclear or unreadable" onchange="handleRejectReasonPresetChange()"> Aadhaar document is unclear or unreadable</label>' +
                    '<label style="display:flex; align-items:center; gap:8px; cursor:pointer;"><input type="radio" name="rejectReasonPreset" value="PAN card does not match the name provided" onchange="handleRejectReasonPresetChange()"> PAN card does not match the name provided</label>' +
                    '<label style="display:flex; align-items:center; gap:8px; cursor:pointer;"><input type="radio" name="rejectReasonPreset" value="Bank details are incomplete or incorrect" onchange="handleRejectReasonPresetChange()"> Bank details are incomplete or incorrect</label>' +
                    '<label style="display:flex; align-items:center; gap:8px; cursor:pointer;"><input type="radio" name="rejectReasonPreset" value="Owner photo is unclear or missing" onchange="handleRejectReasonPresetChange()"> Owner photo is unclear or missing</label>' +
                    '<label style="display:flex; align-items:center; gap:8px; cursor:pointer;"><input type="radio" name="rejectReasonPreset" value="custom" onchange="handleRejectReasonPresetChange()"> Other</label>' +
                '</div>' +
                '<div class="form-group" id="rejectCustomReasonGroup" style="display:none; margin-bottom:24px;">' +
                    '<label>Custom Reason</label>' +
                    '<textarea id="rejectCustomReason" class="form-textarea" rows="3" placeholder="Enter rejection reason" oninput="handleRejectReasonPresetChange()"></textarea>' +
                '</div>' +
                '<div style="border-top:1px solid var(--gray-200); margin-bottom:24px;"></div>' +
                '<div class="form-actions" style="display:flex; gap:16px; justify-content:flex-end;">' +
                    '<button type="button" class="btn" style="border:1px solid var(--gray-400); color:var(--gray-300); background:transparent;" onclick="closeRejectShopModal()">Cancel</button>' +
                    '<button type="button" id="btnConfirmReject" class="btn btn-gold" onclick="submitRejectShop()" disabled>Confirm Reject</button>' +
                '</div>' +
            '</div>' +
        '</div>';

    document.body.insertAdjacentHTML('beforeend', modalHtml);
}

let rejectShopTargetId = null;

function openRejectShopModal(shopId, shopName) {
    ensureRejectShopModal();
    rejectShopTargetId = shopId;
    const nameEl = document.getElementById('rejectShopName');
    if (nameEl) nameEl.textContent = shopName || 'this shop';
    const radios = document.querySelectorAll('input[name="rejectReasonPreset"]');
    radios.forEach(r => r.checked = false);
    document.getElementById('rejectCustomReason').value = '';
    document.getElementById('rejectCustomReasonGroup').style.display = 'none';
    document.getElementById('btnConfirmReject').disabled = true;
    document.getElementById('rejectShopModal').classList.add('active');
}

function closeRejectShopModal(event) {
    if (event && event.target !== event.currentTarget) return;
    const modal = document.getElementById('rejectShopModal');
    if (modal) modal.classList.remove('active');
    rejectShopTargetId = null;
}

function handleRejectReasonPresetChange() {
    const selectedRadio = document.querySelector('input[name="rejectReasonPreset"]:checked');
    const preset = selectedRadio ? selectedRadio.value : '';
    const customGroup = document.getElementById('rejectCustomReasonGroup');
    const customReason = document.getElementById('rejectCustomReason').value.trim();
    const btnConfirm = document.getElementById('btnConfirmReject');
    
    customGroup.style.display = preset === 'custom' ? 'block' : 'none';
    
    if (preset && preset !== 'custom') {
        btnConfirm.disabled = false;
    } else if (preset === 'custom' && customReason.length > 0) {
        btnConfirm.disabled = false;
    } else {
        btnConfirm.disabled = true;
    }
}

async function submitRejectShop() {
    const selectedRadio = document.querySelector('input[name="rejectReasonPreset"]:checked');
    const preset = selectedRadio ? selectedRadio.value : '';
    let reason = preset === 'custom'
        ? document.getElementById('rejectCustomReason').value.trim()
        : preset;

    if (!reason) {
        showToast('Please select or enter a rejection reason.', 'warning');
        return;
    }

    try {
        await apiCall('/api/admin/shops/' + rejectShopTargetId + '/reject', 'POST', { reason: reason });
        closeRejectShopModal();
        showToast('Shop rejected.', 'warning');
        setTimeout(function() { window.location.reload(); }, 1000);
    } catch (error) {
        showToast('Failed to reject shop.', 'danger');
    }
}

async function suspendShop(shopId) {
    if (!confirm('⚠️ Suspend this shop? Their login and all customer logins will be blocked.')) return;
    try {
        await apiCall('/api/admin/shops/' + shopId + '/suspend', 'POST');
        showToast('Shop suspended.', 'warning');
        setTimeout(function() { window.location.reload(); }, 1000);
    } catch (error) {
        showToast('Failed to suspend shop.', 'danger');
    }
}

// ==================== CUSTOMER DASHBOARD ====================

async function loadCustomerDashboard() {
    showLoading('dashboardContent');
    try {
        const data = await apiCall('/api/customer/dashboard', 'GET');
        renderCustomerDashboard(data);
    } catch (error) {
        showToast('Failed to load dashboard.', 'danger');
    }
}

function renderCustomerDashboard(data) {
    const customer = data.customer;
    const loans = data.loans || [];

    const nameEl = document.getElementById('customerName');
    if (nameEl) nameEl.textContent = customer.fullName;

    const contentEl = document.getElementById('dashboardContent');
    if (!contentEl) return;

    let html = '<div class="loan-list">';

    if (loans.length === 0) {
        html += '<div class="empty-state"><div class="empty-icon">📋</div><h3>No Active Loans</h3>' +
            '<p>You don\'t have any active loans at the moment.</p></div>';
    } else {
        loans.forEach(function(loan) {
            html += '<div class="loan-item' + (loan.seizureWarningShown ? ' seizure-warning-item' : '') + '">';

            if (loan.seizureWarningShown) {
                html += '<div class="seizure-warning mb-4"><div class="warning-icon">⚠️</div>' +
                    '<div class="warning-text"><h4>Gold Seizure Warning</h4>' +
                    '<p>This loan is 30+ days overdue. Your ' + loan.goldItemType + ' will be seized if payment is not received. Contact the shop immediately.</p></div></div>';
            }

            html += '<div class="loan-header"><span class="loan-id">' + loan.loanId + '</span>' + getStatusBadge(loan.status) + '</div>';
            html += '<div class="loan-details-grid">' +
                '<div class="loan-detail"><div class="detail-label">Gold Item</div><div class="detail-value">' + loan.goldItemType + '</div></div>' +
                '<div class="loan-detail"><div class="detail-label">Loan Amount</div><div class="detail-value">' + formatCurrency(loan.loanAmount) + '</div></div>' +
                '<div class="loan-detail"><div class="detail-label">Due Date</div><div class="detail-value">' + formatDate(loan.dueDate) + '</div></div>' +
                '</div>';
            html += '<div class="loan-details-grid">' +
                '<div class="loan-detail"><div class="detail-label">Interest Due</div><div class="detail-value text-warning">' + formatCurrency(loan.totalInterestAccrued) + '</div></div>' +
                '<div class="loan-detail"><div class="detail-label">Total Paid</div><div class="detail-value text-success">' + formatCurrency(loan.totalPaid) + '</div></div>' +
                '<div class="loan-detail"><div class="detail-label">Balance Due</div><div class="detail-value text-danger font-bold">' + formatCurrency(loan.balanceDue) + '</div></div>' +
                '</div>';
            html += '<div class="loan-actions">' +
                '<a href="/customer/loans/' + loan.id + '" class="btn btn-sm btn-outline">View Details</a>';
            if (loan.status === 'ACTIVE' || loan.status === 'OVERDUE') {
                html += '<a href="/customer/pay/' + loan.id + '" class="btn btn-sm btn-gold">💰 Pay Now</a>';
            }
            html += '</div></div>';
        });
    }

    html += '</div>';
    contentEl.innerHTML = html;
}
