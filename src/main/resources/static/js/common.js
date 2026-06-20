/**
 * COMMON.JS — Shared JavaScript utilities used across ALL pages.
 *
 * This file handles:
 * - JWT token management (store, retrieve, auto-attach to API calls)
 * - Reusable API fetch wrapper with auth headers
 * - Toast notifications
 * - Sidebar toggle (mobile)
 * - Logout functionality
 * - Pagination helpers
 * - Aadhaar masking display
 */

// ==================== JWT TOKEN MANAGEMENT ====================

/**
 * Stores the JWT token and user info in localStorage after login.
 */
function saveAuth(token, role, userId, shopId, fullName, email, phone) {
    sessionStorage.setItem('jwt_token', token);
    sessionStorage.setItem('user_role', role);
    sessionStorage.setItem('user_id', userId);
    if (shopId) sessionStorage.setItem('shop_id', shopId);
    if (fullName) sessionStorage.setItem('user_full_name', fullName);
    if (email) sessionStorage.setItem('user_email', email);
    if (phone) sessionStorage.setItem('user_phone', phone);
}

/**
 * Retrieves the stored JWT token.
 */
function getToken() {
    return sessionStorage.getItem('jwt_token');
}

/**
 * Retrieves the stored user role.
 */
function getRole() {
    return sessionStorage.getItem('user_role');
}

/**
 * Clears all auth data and redirects to the homepage.
 */
function logout() {
    var role = getRole();
    clearAuth();
    if (role === 'ROLE_SUPER_ADMIN') {
        window.location.href = '/admin/login';
    } else {
        window.location.href = '/';
    }
}

function clearAuth() {
    sessionStorage.removeItem('jwt_token');
    sessionStorage.removeItem('user_role');
    sessionStorage.removeItem('user_id');
    sessionStorage.removeItem('shop_id');
    sessionStorage.removeItem('user_full_name');
    sessionStorage.removeItem('user_email');
    sessionStorage.removeItem('user_phone');
}

function redirectToLoginForCurrentPage() {
    const path = window.location.pathname;
    if (path.startsWith('/admin')) {
        window.location.href = '/admin/login';
    } else if (path.startsWith('/customer')) {
        window.location.href = '/customer/login';
    } else {
        window.location.href = '/';
    }
}

/**
 * Checks if the user is logged in (has a valid token).
 * If not, redirects to the login page.
 */
function requireAuth() {
    if (!getToken()) {
        window.location.href = '/';
        return false;
    }
    return true;
}

function requireRoleForCurrentPage() {
    const path = window.location.pathname;
    const role = getRole();

    if (path.startsWith('/admin') && path !== '/admin/login' && role !== 'ROLE_SUPER_ADMIN') {
        clearAuth();
        window.location.href = '/admin/login';
        return false;
    }

    if (path.startsWith('/shop') && role !== 'ROLE_SHOP_OWNER') {
        clearAuth();
        window.location.href = '/';
        return false;
    }

    if (path.startsWith('/customer') && path !== '/customer/login' && role !== 'ROLE_CUSTOMER') {
        clearAuth();
        window.location.href = '/customer/login';
        return false;
    }

    return true;
}

// ==================== API FETCH WRAPPER ====================

/**
 * Makes an authenticated API call with the JWT token.
 *
 * Purpose: Centralize all API calls so the JWT token is always
 * included in the Authorization header automatically.
 *
 * Input: URL, HTTP method, optional body data.
 * Output: Parsed JSON response.
 *
 * Usage: const data = await apiCall('/api/shop/dashboard', 'GET');
 *        const result = await apiCall('/api/shop/loans', 'POST', loanData);
 */
async function apiCall(url, method, body, _isRetry = false) {
    const token = getToken();
    const headers = {
        'Authorization': 'Bearer ' + token
    };

    // Only set Content-Type for JSON bodies (not for FormData/multipart)
    if (body && !(body instanceof FormData)) {
        headers['Content-Type'] = 'application/json';
    }

    const options = {
        method: method || 'GET',
        headers: headers
    };

    if (body) {
        if (body instanceof FormData) {
            options.body = body;
        } else {
            options.body = JSON.stringify(body);
        }
    }

    const response = await fetch(url, options);

    // Handle 401 Unauthorized — token expired or invalid
    if (response.status === 401) {
        try {
            const errData = await response.json();
            if (errData.expired === true) {
                logout();
                return null;
            }
        } catch (e) {
            // Ignore json parse errors
        }
        
        if (!_isRetry) {
            return await apiCall(url, method, body, true);
        }
        
        logout();
        return null;
    }

    // Handle 403 Forbidden — no access
    if (response.status === 403) {
        clearAuth();
        showToast('Access denied. Please log in with the correct account.', 'danger');
        setTimeout(function() {
            redirectToLoginForCurrentPage();
        }, 700);
        return null;
    }

    // Parse JSON response
    const data = await response.json();

    // If the API returned an error message, show it
    if (!response.ok) {
        const errorMsg = data.message || data.error || 'Something went wrong';
        showToast(errorMsg, 'danger');
        throw new Error(errorMsg);
    }

    return data;
}

/**
 * Makes an unauthenticated API call (for login/register endpoints).
 */
async function publicApiCall(url, method, body) {
    const headers = {
        'Content-Type': 'application/json'
    };

    const options = {
        method: method || 'POST',
        headers: headers,
        body: body ? JSON.stringify(body) : undefined
    };

    const response = await fetch(url, options);
    const data = await response.json();

    if (!response.ok) {
        const errorMsg = data.message || data.error || 'Something went wrong';
        throw new Error(errorMsg);
    }

    return data;
}

/**
 * Downloads a protected file endpoint using the stored JWT token.
 *
 * Plain <a href="/api/..."> links cannot attach the Authorization header,
 * so protected PDF downloads must use fetch() and then open the returned blob.
 */
async function authenticatedDownload(url, fallbackFilename) {
    const response = await fetch(url, {
        method: 'GET',
        headers: {
            'Authorization': 'Bearer ' + getToken()
        }
    });

    if (response.status === 401) {
        logout();
        return;
    }

    if (!response.ok) {
        showToast('Download failed. Please try again.', 'danger');
        return;
    }

    const blob = await response.blob();
    const objectUrl = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = objectUrl;

    const disposition = response.headers.get('Content-Disposition') || '';
    const match = disposition.match(/filename="?([^"]+)"?/);
    link.download = match ? match[1] : (fallbackFilename || 'download.pdf');

    document.body.appendChild(link);
    link.click();
    link.remove();
    window.URL.revokeObjectURL(objectUrl);
}

// ==================== TOAST NOTIFICATIONS ====================

/**
 * Shows a toast notification at the top-right of the screen.
 *
 * Purpose: Display success, error, warning, or info messages
 * that auto-dismiss after 4 seconds.
 *
 * Input: message text, type ('success', 'danger', 'warning', 'info')
 */
function showToast(message, type) {
    type = type || 'info';

    // Create toast container if it doesn't exist
    let container = document.getElementById('toast-container');
    if (!container) {
        container = document.createElement('div');
        container.id = 'toast-container';
        container.className = 'toast-container';
        document.body.appendChild(container);
    }

    // Create the toast element
    const toast = document.createElement('div');
    toast.className = 'toast alert-' + type;
    toast.textContent = message;

    container.appendChild(toast);

    // Auto-remove after 4 seconds
    setTimeout(function() {
        toast.style.animation = 'toastSlideOut 0.3s ease forwards';
        setTimeout(function() {
            toast.remove();
        }, 300);
    }, 4000);
}

// ==================== SIDEBAR TOGGLE (Mobile) ====================

/**
 * Toggles the sidebar open/closed on mobile devices.
 */
function toggleSidebar() {
    const sidebar = document.querySelector('.sidebar');
    const backdrop = document.querySelector('.sidebar-backdrop');
    if (sidebar) {
        sidebar.classList.toggle('open');
    }
    if (backdrop) {
        backdrop.classList.toggle('active');
    }
    document.body.classList.toggle('sidebar-open', sidebar && sidebar.classList.contains('open'));
}

/**
 * Adds the mobile sidebar toggle button to dashboard navbars.
 * The templates share the same navbar/sidebar structure, so doing this once
 * here keeps every internal page responsive without repeating HTML.
 */
function initializeMobileSidebar() {
    const sidebar = document.querySelector('.sidebar');
    const navbar = document.querySelector('.navbar');

    if (!sidebar || !navbar || document.querySelector('.sidebar-toggle')) {
        return;
    }

    const toggleButton = document.createElement('button');
    toggleButton.type = 'button';
    toggleButton.className = 'sidebar-toggle';
    toggleButton.setAttribute('aria-label', 'Open navigation menu');
    toggleButton.innerHTML = '<span></span><span></span><span></span>';
    toggleButton.addEventListener('click', function(event) {
        event.stopPropagation();
        toggleSidebar();
    });

    const backdrop = document.createElement('div');
    backdrop.className = 'sidebar-backdrop';
    backdrop.addEventListener('click', toggleSidebar);

    const brand = navbar.querySelector('.navbar-brand');
    navbar.insertBefore(toggleButton, brand || navbar.firstChild);
    document.body.appendChild(backdrop);
}

/**
 * Keeps logout in one place per role instead of repeating it on every page.
 * Shop Owner: Settings page.
 * Customer: Profile page.
 * Super Admin: Dashboard page.
 */
function initializeLogoutVisibility() {
    const currentPath = window.location.pathname;
    const logoutAllowedPaths = [
        '/shop/settings',
        '/customer/profile',
        '/admin/dashboard'
    ];

    if (logoutAllowedPaths.includes(currentPath)) {
        return;
    }

    const logoutButtons = document.querySelectorAll('button[onclick="logout()"]');
    logoutButtons.forEach(function(button) {
        button.style.display = 'none';
    });
}

// ==================== PAGINATION HELPERS ====================

/**
 * Builds pagination HTML controls.
 *
 * Purpose: Generate Previous/Next/Page buttons for paginated list views.
 * Input: pageData (Spring's Page object from the API), callback function name.
 * Output: Returns HTML string for pagination controls.
 */
function buildPagination(pageData, callbackFn) {
    if (!pageData || pageData.totalPages <= 1) return '';

    let html = '<div class="pagination">';

    // Previous button
    html += '<button onclick="' + callbackFn + '(' + (pageData.number - 1) + ')" ' +
            (pageData.first ? 'disabled' : '') + '>← Prev</button>';

    // Page numbers
    const startPage = Math.max(0, pageData.number - 2);
    const endPage = Math.min(pageData.totalPages - 1, pageData.number + 2);

    for (let i = startPage; i <= endPage; i++) {
        html += '<button onclick="' + callbackFn + '(' + i + ')" ' +
                'class="' + (i === pageData.number ? 'active' : '') + '">' +
                (i + 1) + '</button>';
    }

    // Next button
    html += '<button onclick="' + callbackFn + '(' + (pageData.number + 1) + ')" ' +
            (pageData.last ? 'disabled' : '') + '>Next →</button>';

    // Page info
    html += '<span class="page-info">Page ' + (pageData.number + 1) +
            ' of ' + pageData.totalPages + '</span>';

    html += '</div>';
    return html;
}

// ==================== UTILITY FUNCTIONS ====================

/**
 * Masks an Aadhaar number to show only last 4 digits.
 * Input: "1234 5678 9012" → Output: "XXXX XXXX 9012"
 */
function maskAadhaar(aadhaar) {
    if (!aadhaar || aadhaar.length < 4) return 'XXXX XXXX XXXX';
    const lastFour = aadhaar.replace(/\s/g, '').slice(-4);
    return 'XXXX XXXX ' + lastFour;
}

/**
 * Formats a number as Indian Rupee currency.
 * Input: 12345.67 → Output: "₹12,345.67"
 */
function formatCurrency(amount) {
    if (amount === null || amount === undefined) return '₹0.00';
    return '₹' + parseFloat(amount).toLocaleString('en-IN', {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2
    });
}

/**
 * Formats a date string to a readable format.
 * Input: "2026-03-15" → Output: "15 Mar 2026"
 */
function formatDate(dateStr) {
    if (!dateStr) return '—';
    const date = new Date(dateStr);
    return date.toLocaleDateString('en-IN', {
        day: '2-digit',
        month: 'short',
        year: 'numeric'
    });
}

/**
 * Gets the badge class for a loan/shop status.
 */
function getStatusBadge(status) {
    const statusMap = {
        'ACTIVE': 'badge-active',
        'PENDING': 'badge-pending',
        'APPROVED': 'badge-approved',
        'REJECTED': 'badge-overdue',
        'OVERDUE': 'badge-overdue',
        'CLOSED': 'badge-closed',
        'SEIZED': 'badge-seized',
        'SUSPENDED': 'badge-suspended',
        'EXPIRED': 'badge-expired'
    };
    const badgeClass = statusMap[status] || 'badge-pending';
    return '<span class="badge ' + badgeClass + '">' + status + '</span>';
}

/**
 * Shows a loading spinner inside a container element.
 */
function showLoading(containerId) {
    const el = document.getElementById(containerId);
    if (el) {
        el.innerHTML = '<div class="loading-overlay">' +
            '<div class="spinner spinner-lg"></div>' +
            '<p>Loading...</p></div>';
    }
}

/**
 * Shows an empty state message inside a container element.
 */
function showEmptyState(containerId, message, icon) {
    icon = icon || '📋';
    const el = document.getElementById(containerId);
    if (el) {
        el.innerHTML = '<div class="empty-state">' +
            '<div class="empty-icon">' + icon + '</div>' +
            '<h3>Nothing here yet</h3>' +
            '<p>' + message + '</p></div>';
    }
}

/**
 * Gets the initials from a name (for avatar display).
 * Input: "Ramesh Kumar" → Output: "RK"
 */
function getInitials(name) {
    if (!name) return '?';
    return name.split(' ')
        .map(function(word) { return word.charAt(0).toUpperCase(); })
        .slice(0, 2)
        .join('');
}

// ==================== EVENT LISTENERS ====================

// Close sidebar when clicking outside on mobile
document.addEventListener('click', function(event) {
    const sidebar = document.querySelector('.sidebar');
    const toggleBtn = document.querySelector('.sidebar-toggle');
    if (sidebar && sidebar.classList.contains('open') &&
        !sidebar.contains(event.target) &&
        (!toggleBtn || !toggleBtn.contains(event.target))) {
        sidebar.classList.remove('open');
        document.body.classList.remove('sidebar-open');
        const backdrop = document.querySelector('.sidebar-backdrop');
        if (backdrop) backdrop.classList.remove('active');
    }
});

document.addEventListener('DOMContentLoaded', initializeMobileSidebar);
document.addEventListener('DOMContentLoaded', initializeLogoutVisibility);
document.addEventListener('DOMContentLoaded', requireRoleForCurrentPage);

// Add toastSlideOut animation
const style = document.createElement('style');
style.textContent = '@keyframes toastSlideOut { to { opacity: 0; transform: translateX(100px); } }';
document.head.appendChild(style);
