package com.trustledgersaas.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * PageController — Serves ALL HTML template pages for the frontend.
 *
 * IMPORTANT: This is the GLUE between the backend and frontend.
 * Without this controller, none of the HTML templates will be served.
 *
 * How it works:
 * - The frontend is a set of HTML templates in src/main/resources/templates/
 * - Each method maps a URL path to a template file
 * - Spring Boot's Thymeleaf (or default template resolver) finds the matching .html file
 * - The actual data is loaded via JavaScript (AJAX calls to /api/** endpoints)
 *
 * This is NOT a REST controller (@RestController). It uses @Controller,
 * which means the return value is a VIEW NAME, not JSON data.
 */
@Controller
public class PageController {

    // ==================== PUBLIC PAGES ====================

    /**
     * Homepage — the landing page with login modal and features.
     * URL: GET /
     * Template: templates/index.html
     */
    @GetMapping("/")
    public String homePage() {
        return "index";
    }

    /**
     * Customer login page — standalone page for customer login.
     * URL: GET /customer/login
     * Template: templates/customer/login.html
     */
    @GetMapping("/customer/login")
    public String customerLoginPage() {
        return "customer/login";
    }

    /**
     * Shop registration page — full-page form for new shop owners.
     * URL: GET /register
     * Template: templates/auth/register-shop.html
     */
    @GetMapping("/register")
    public String registerShopPage() {
        return "auth/register-shop";
    }

    /**
     * Shop registration status tracker — shown after register and when login is blocked pending review.
     * URL: GET /application-status
     */
    @GetMapping("/application-status")
    public String applicationStatusPage() {
        return "auth/application-status";
    }

    @GetMapping("/forgot-password")
    public String forgotPasswordPage() {
        return "auth/forgot-password";
    }

    @GetMapping("/reset-password")
    public String resetPasswordPage() {
        return "auth/reset-password";
    }

    /**
     * Hidden Super Admin login page — NOT linked from anywhere on the site.
     * Only accessible if you know the exact URL.
     * URL: GET /admin/login
     * Template: templates/admin/login.html
     */
    @GetMapping("/admin/login")
    public String adminLoginPage() {
        return "admin/login";
    }

    // ==================== SUPER ADMIN PAGES ====================

    /**
     * Super Admin dashboard — platform-wide analytics.
     * URL: GET /admin/dashboard
     * Template: templates/admin/dashboard.html
     */
    @GetMapping("/admin/dashboard")
    public String adminDashboardPage() {
        return "admin/dashboard";
    }

    /**
     * Pending shop approvals page.
     * URL: GET /admin/shop-approvals
     * Template: templates/admin/shop-approvals.html
     */
    @GetMapping("/admin/shop-approvals")
    public String adminShopApprovalsPage() {
        return "admin/shop-approvals";
    }

    /**
     * All shops list page.
     * URL: GET /admin/shops
     * Template: templates/admin/shop-approvals.html (reused — uses different JS call)
     */
    @GetMapping("/admin/shops")
    public String adminAllShopsPage() {
        return "admin/shop-approvals";
    }

    /**
     * Shop detail page — view full details of a specific shop.
     * URL: GET /admin/shops/{shopId}
     * Template: templates/admin/shop-detail.html
     */
    @GetMapping("/admin/shops/{shopId}")
    public String adminShopDetailPage(@PathVariable Long shopId) {
        return "admin/shop-detail";
    }

    /**
     * Subscription revenue log page.
     * URL: GET /admin/subscriptions
     * Template: templates/admin/subscriptions.html
     */
    @GetMapping("/admin/subscriptions")
    public String adminSubscriptionsPage() {
        return "admin/subscriptions";
    }

    // ==================== SHOP OWNER PAGES ====================

    /**
     * Shop owner dashboard — overview of shop metrics.
     * URL: GET /shop/dashboard
     * Template: templates/shop/dashboard.html
     */
    @GetMapping("/shop/dashboard")
    public String shopDashboardPage() {
        return "shop/dashboard";
    }

    /**
     * Customers list page with live search.
     * URL: GET /shop/customers
     * Template: templates/shop/customers.html
     */
    @GetMapping("/shop/customers")
    public String shopCustomersPage() {
        return "shop/customers";
    }

    /**
     * Add new customer form.
     * URL: GET /shop/add-customer
     * Template: templates/shop/add-customer.html
     */
    @GetMapping("/shop/add-customer")
    public String shopAddCustomerPage() {
        return "shop/add-customer";
    }

    /**
     * Customer detail page — profile and loan list.
     * URL: GET /shop/customers/{customerId}
     * Template: templates/shop/customer-detail.html
     */
    @GetMapping("/shop/customers/{customerId}")
    public String shopCustomerDetailPage(@PathVariable Long customerId) {
        return "shop/customer-detail";
    }

    /**
     * All loans list page.
     * URL: GET /shop/loans
     * Template: templates/shop/loans.html
     */
    @GetMapping("/shop/loans")
    public String shopLoansPage() {
        return "shop/loans";
    }

    /**
     * Create new loan form.
     * URL: GET /shop/create-loan
     * Template: templates/shop/create-loan.html
     */
    @GetMapping("/shop/create-loan")
    public String shopCreateLoanPage() {
        return "shop/create-loan";
    }

    /**
     * Loan detail page — full loan info with payment history.
     * URL: GET /shop/loans/{loanId}
     * Template: templates/shop/loan-detail.html
     */
    @GetMapping("/shop/loans/{loanId}")
    public String shopLoanDetailPage(@PathVariable Long loanId) {
        return "shop/loan-detail";
    }

    /**
     * Overdue loans page — only shows OVERDUE loans.
     * URL: GET /shop/overdue-loans
     * Template: templates/shop/overdue-loans.html
     */
    @GetMapping("/shop/overdue-loans")
    public String shopOverdueLoansPage() {
        return "shop/overdue-loans";
    }

    /**
     * Shop settings page — subscription, profile, bank details, password change.
     * URL: GET /shop/settings
     * Template: templates/shop/settings.html
     */
    @GetMapping("/shop/settings")
    public String shopSettingsPage() {
        return "shop/settings";
    }

    // ==================== CUSTOMER PORTAL PAGES ====================

    /**
     * Customer dashboard — lists all their loans.
     * URL: GET /customer/dashboard
     * Template: templates/customer/dashboard.html
     */
    @GetMapping("/customer/dashboard")
    public String customerDashboardPage() {
        return "customer/dashboard";
    }

    /**
     * Customer loan detail page.
     * URL: GET /customer/loans/{loanId}
     * Template: templates/customer/loan-detail.html
     */
    @GetMapping("/customer/loans/{loanId}")
    public String customerLoanDetailPage(@PathVariable Long loanId) {
        return "customer/loan-detail";
    }

    /**
     * Customer payment page — Razorpay checkout.
     * URL: GET /customer/pay/{loanId}
     * Template: templates/customer/pay.html
     */
    @GetMapping("/customer/pay/{loanId}")
    public String customerPayPage(@PathVariable Long loanId) {
        return "customer/pay";
    }

    /**
     * Customer payment history page.
     * URL: GET /customer/payment-history
     * Template: templates/customer/payment-history.html
     */
    @GetMapping("/customer/payment-history")
    public String customerPaymentHistoryPage() {
        return "customer/payment-history";
    }

    /**
     * Customer profile page.
     * URL: GET /customer/profile
     * Template: templates/customer/profile.html
     */
    @GetMapping("/customer/profile")
    public String customerProfilePage() {
        return "customer/profile";
    }

    /**
     * Customer change password page (shown on first login).
     * URL: GET /customer/change-password
     * Template: templates/customer/profile.html (reused — same page with password form)
     */
    @GetMapping("/customer/change-password")
    public String customerChangePasswordPage() {
        return "customer/profile";
    }
}
