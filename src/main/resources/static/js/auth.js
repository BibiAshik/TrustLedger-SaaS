/**
 * AUTH.JS — Handles login, registration, and customer login functionality.
 *
 * This file manages:
 * - Shop Owner login (modal popup on homepage)
 * - Shop Owner registration (full-page form)
 * - Customer login (with multi-shop disambiguation)
 * - Super Admin login (hidden page)
 * - Forgot password flow
 */

// ==================== SHOP OWNER LOGIN ====================

/**
 * Opens the Shop Owner login modal on the homepage.
 */
function openLoginModal() {
    const modal = document.getElementById('loginModal');
    if (modal) modal.classList.add('active');
}

/**
 * Closes the Shop Owner login modal.
 */
function closeLoginModal() {
    const modal = document.getElementById('loginModal');
    if (modal) modal.classList.remove('active');
    clearLoginErrors();
}

/**
 * Handles Shop Owner / Super Admin login form submission.
 *
 * Flow: Submit email + password → API verifies credentials →
 * On success: save JWT, redirect to dashboard based on role.
 * On failure: show error message.
 */
async function handleLogin(event) {
    event.preventDefault();
    clearLoginErrors();

    const email = document.getElementById('loginEmail').value.trim();
    const password = document.getElementById('loginPassword').value;
    const submitBtn = event.target.querySelector('button[type="submit"]');

    if (!email || !password) {
        showLoginError('Please enter both email and password.');
        return;
    }

    // Show loading state
    submitBtn.disabled = true;
    submitBtn.innerHTML = '<span class="spinner"></span> Logging in...';

    try {
        const data = await publicApiCall('/api/auth/login', 'POST', {
            identifier: email,
            password: password
        });

        if (data.token) {
            saveAuth(data.token, data.role, data.userId, data.shopId, data.fullName, data.email, data.phone);

            // Redirect based on role
            if (data.role === 'ROLE_SUPER_ADMIN') {
                window.location.href = '/admin/dashboard';
            } else if (data.role === 'ROLE_SHOP_OWNER') {
                window.location.href = '/shop/dashboard';
            }
        } else if (data.applicationStatus) {
            window.location.href = data.redirectUrl ||
                ('/application-status?email=' + encodeURIComponent(data.email));
        } else if (data.error || data.message) {
            showLoginError(data.message || data.error);
        }
    } catch (error) {
        showLoginError(error.message || 'Login failed. Please try again.');
    } finally {
        submitBtn.disabled = false;
        submitBtn.innerHTML = 'Login';
    }
}

/**
 * Shows an error message on the login form.
 */
function showLoginError(message) {
    const errorEl = document.getElementById('loginError');
    if (errorEl) {
        errorEl.textContent = message;
        errorEl.style.display = 'block';
    }
}

function clearLoginErrors() {
    const errorEl = document.getElementById('loginError');
    if (errorEl) {
        errorEl.textContent = '';
        errorEl.style.display = 'none';
    }
}

// ==================== CUSTOMER LOGIN ====================

/**
 * Handles customer login form submission.
 *
 * Special logic: If the same phone/email exists at multiple shops,
 * the API returns a list of shops. The frontend then shows a
 * shop-selection screen (per section 6.6 of the spec).
 */
async function handleCustomerLogin(event) {
    event.preventDefault();

    const identifier = document.getElementById('customerIdentifier').value.trim();
    const password = document.getElementById('customerPassword').value;
    const errorEl = document.getElementById('customerLoginError');
    const submitBtn = event.target.querySelector('button[type="submit"]');

    if (errorEl) errorEl.style.display = 'none';

    if (!identifier || !password) {
        if (errorEl) {
            errorEl.textContent = 'Please enter your phone/email and password.';
            errorEl.style.display = 'block';
        }
        return;
    }

    // Validate: if contains @, treat as email; if 10 digits, treat as phone
    const isEmail = identifier.includes('@');
    const isPhone = /^\d{10}$/.test(identifier);

    if (!isEmail && !isPhone) {
        if (errorEl) {
            errorEl.textContent = 'Enter a valid phone number (10 digits) or email address.';
            errorEl.style.display = 'block';
        }
        return;
    }

    submitBtn.disabled = true;
    submitBtn.innerHTML = '<span class="spinner"></span> Logging in...';

    try {
        const data = await publicApiCall('/api/auth/customer/login', 'POST', {
            identifier: identifier,
            password: password
        });

        if (data.token) {
            // Single match — log in directly
            saveAuth(data.token, data.role, data.userId, data.shopId, data.fullName, data.email, data.phone);

            // Check if first login (force password change)
            if (data.isFirstLogin) {
                window.location.href = '/customer/change-password';
            } else {
                window.location.href = '/customer/dashboard';
            }
        } else if (data.shops && data.shops.length > 0) {
            // Multiple matches — show shop selection
            showShopSelection(data.shops, identifier, password);
        } else if (data.error || data.message) {
            if (errorEl) {
                errorEl.textContent = data.message || data.error;
                errorEl.style.display = 'block';
            }
        }
    } catch (error) {
        if (errorEl) {
            errorEl.textContent = error.message || 'Login failed. Please try again.';
            errorEl.style.display = 'block';
        }
    } finally {
        submitBtn.disabled = false;
        submitBtn.innerHTML = 'Login';
    }
}

/**
 * Shows the shop selection screen when a customer has accounts
 * at multiple shops (multi-shop disambiguation per section 6.6).
 */
function showShopSelection(shops, identifier, password) {
    const loginForm = document.getElementById('customerLoginForm');
    const selectionDiv = document.getElementById('shopSelection');

    if (loginForm) loginForm.style.display = 'none';
    if (selectionDiv) {
        let html = '<div class="shop-selection">' +
            '<h3>Select Your Shop</h3>' +
            '<p class="text-sm text-muted text-center mb-4">' +
            'We found multiple accounts linked to this number. Please select your shop:</p>';

        shops.forEach(function(shop, index) {
            html += '<label class="shop-option">' +
                '<input type="radio" name="selectedShop" value="' + shop.shopId + '"' +
                (index === 0 ? ' checked' : '') + '>' +
                '<span class="shop-name">' + shop.shopName + '</span>' +
                '</label>';
        });

        html += '<button class="btn btn-gold btn-block mt-4" ' +
            'onclick="selectShopAndLogin(\'' + identifier + '\', \'' + password + '\')">' +
            'Continue</button>';
        html += '<button class="btn btn-outline btn-block mt-2" ' +
            'onclick="backToCustomerLogin()">← Back</button>';
        html += '</div>';

        selectionDiv.innerHTML = html;
        selectionDiv.style.display = 'block';
    }
}

/**
 * Completes login after the customer selects a specific shop.
 */
async function selectShopAndLogin(identifier, password) {
    const selectedShop = document.querySelector('input[name="selectedShop"]:checked');
    if (!selectedShop) {
        showToast('Please select a shop.', 'warning');
        return;
    }

    try {
        const data = await publicApiCall('/api/auth/customer/login', 'POST', {
            identifier: identifier,
            password: password,
            selectedShopId: parseInt(selectedShop.value)
        });

        if (data.token) {
            saveAuth(data.token, data.role, data.userId, data.shopId, data.fullName, data.email, data.phone);

            if (data.isFirstLogin) {
                window.location.href = '/customer/change-password';
            } else {
                window.location.href = '/customer/dashboard';
            }
        }
    } catch (error) {
        showToast(error.message || 'Login failed.', 'danger');
    }
}

/**
 * Goes back to the customer login form from the shop selection screen.
 */
function backToCustomerLogin() {
    const loginForm = document.getElementById('customerLoginForm');
    const selectionDiv = document.getElementById('shopSelection');

    if (loginForm) loginForm.style.display = 'block';
    if (selectionDiv) selectionDiv.style.display = 'none';
}

// ==================== SHOP REGISTRATION ====================

/**
 * Handles the shop owner registration form submission.
 *
 * Uses FormData because the form includes file uploads (multipart).
 * Regular JSON won't work for file uploads.
 */
async function handleShopRegistration(event) {
    event.preventDefault();

    const form = event.target;
    const formData = new FormData(form);

    // Razorpay expects uppercase PAN
    const panInput = form.querySelector('input[name="panNumber"]');
    if (panInput && panInput.value) {
        formData.set('panNumber', panInput.value.trim().toUpperCase());
    }

    const submitBtn = form.querySelector('button[type="submit"]');

    submitBtn.disabled = true;
    submitBtn.innerHTML = '<span class="spinner"></span> Registering...';

    try {
        const response = await fetch('/api/auth/shop/register', {
            method: 'POST',
            body: formData
        });

        const data = await response.json();

        if (response.ok) {
            const email = form.querySelector('[name="email"]').value.trim();
            window.location.href = data.redirectUrl ||
                ('/application-status?email=' + encodeURIComponent(email));
        } else {
            showToast(data.message || data.error || 'Registration failed.', 'danger');
        }
    } catch (error) {
        showToast('Registration failed. Please try again.', 'danger');
    } finally {
        submitBtn.disabled = false;
        submitBtn.innerHTML = 'Register My Shop';
    }
}

// ==================== FORGOT PASSWORD ====================

/**
 * Handles the forgot password form submission.
 */
async function handleForgotPassword(event) {
    event.preventDefault();

    const email = document.getElementById('forgotEmail').value.trim();
    const submitBtn = event.target.querySelector('button[type="submit"]');

    if (!email) {
        showToast('Please enter your email address.', 'warning');
        return;
    }

    submitBtn.disabled = true;
    submitBtn.innerHTML = '<span class="spinner"></span> Sending...';

    try {
        await publicApiCall('/api/auth/forgot-password', 'POST', { email: email });
        showToast('Password reset link sent to your email.', 'success');
    } catch (error) {
        showToast(error.message || 'Failed to send reset link.', 'danger');
    } finally {
        submitBtn.disabled = false;
        submitBtn.innerHTML = 'Send Reset Link';
    }
}

// ==================== FILE UPLOAD PREVIEW ====================

/**
 * Handles file input change to show the selected filename.
 */
function handleFileSelect(input) {
    const fileName = input.files[0] ? input.files[0].name : '';
    const area = input.closest('.file-upload-area');

    if (area) {
        const nameEl = area.querySelector('.file-name');
        if (fileName) {
            area.classList.add('has-file');
            if (nameEl) nameEl.textContent = '✓ ' + fileName;
        } else {
            area.classList.remove('has-file');
            if (nameEl) nameEl.textContent = '';
        }
    }
}
