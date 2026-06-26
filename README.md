# 🏆 Trust Ledger — Multi-Tenant Gold Loan SaaS Platform

> *"Protect Gold. Preserve Trust."*

Trust Ledger is a comprehensive, production-ready SaaS application designed to digitize the operations of local gold loan and finance businesses in India. 

It provides shop owners with a powerful digital dashboard to manage customers and loans, while offering their end-customers a seamless self-service portal to view balances and make online interest payments. The platform is multi-tenant, meaning a single instance can securely host data for hundreds of independent shop owners.

---

## 📸 Screenshots

*(Add your screenshots here)*

- **Landing Page:** <br>
<img width="800" alt="Landing Page 1" src="https://github.com/user-attachments/assets/dd0b550f-670a-4691-9731-b0b94e2114ec" /><br><br>
<img width="800" alt="Landing Page 2" src="https://github.com/user-attachments/assets/5bffd727-323d-44ae-b881-64110833950f" /><br><br>
<img width="800" alt="Landing Page 3" src="https://github.com/user-attachments/assets/2bbb9276-4e6e-43c6-a577-500df28b58ec" /><br><br>

- **Shop Owner Dashboard:** <br>
<img width="800" alt="Shop Dashboard 1" src="https://github.com/user-attachments/assets/a59c9b16-ae92-4a19-a897-d0a829f0dc93" /><br><br>
<img width="800" alt="Shop Dashboard 2" src="https://github.com/user-attachments/assets/03bf0a84-c519-4bdd-b381-fd85b24c61bf" /><br><br>
<img width="800" alt="Shop Dashboard 3" src="https://github.com/user-attachments/assets/40d74965-08df-4635-bb69-b3cd3322fca4" /><br><br>

- **Customer Payment Portal:** <br>
<img width="800" alt="Customer Portal 1" src="https://github.com/user-attachments/assets/3ebbaa37-451d-4557-8b2e-6826c043e67f" /><br><br>
<img width="800" alt="Customer Portal 2" src="https://github.com/user-attachments/assets/5e34b5ac-4758-4e6a-abe1-49686a761de3" /><br><br>
<img width="300" alt="Customer Mobile 1" src="https://github.com/user-attachments/assets/abcfbae8-515c-4b07-9210-6027ec9619a3" /> &nbsp;
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
<img width="300" alt="Customer Mobile 2" src="https://github.com/user-attachments/assets/7cb40bb4-8672-4042-8606-f68ffe06cf6d" /><br><br>

- **Super Admin Panel:** <br>
<img width="800" alt="Admin Panel" src="https://github.com/user-attachments/assets/dfddb96e-c05e-4391-abf4-710c1f44869a" /><br><br>

---

## ✨ Key Features

### 🏪 Shop Owner Portal (The Core App)
- **Digital Loan Records:** Replace handwritten ledgers with structured digital records.
- **Customer Management:** Onboard customers with KYC details (Aadhaar, PAN, photos).
- **Loan Lifecycle:** Create, track, close, and seize gold loans securely.
- **Payment Processing:** Record manual cash payments and automatically sync online Razorpay payments.
- **Smart Analytics Dashboard:** Monitor active loans, overdue loans, total loan volume, and recent payments at a glance.
- **Automated Reminders:** Scheduled background jobs automatically send email reminders to customers when their loans become 1, 7, 15, or 30 days overdue.

### 👤 Customer Portal (Available on PRO Plan)
- **Self-Service Dashboard:** Customers can log in to view their active and past loans.
- **Online Payments:** Securely pay accrued loan interest online via Razorpay (UPI, Cards, Net Banking). Payments are routed directly to the specific Shop Owner's bank account.
- **Payment History:** View all past transactions and download generated PDF receipts.
- **Status Alerts:** Visual warning banners for loans that are 30+ days overdue to prevent gold seizure.

### 👑 Super Admin Panel (Platform Management)
- **Platform Analytics:** Track total registered shops, active customers, total loans, and total platform revenue.
- **Shop Approvals:** Review new shop registrations and approve/reject them.
- **Subscription Management:** View all incoming subscription payments from Shop Owners (Basic / PRO plans).

---

## 🛠️ Tech Stack

| Layer | Technology |
|-------|-----------|
| **Backend** | Java 17, Spring Boot 3.2.5 (Spring MVC, Spring Data JPA) |
| **Security** | Spring Security + JWT (Stateless Authentication) |
| **Database** | MySQL 8 with Hibernate (JPA) |
| **Payments** | Razorpay (Checkout + Razorpay Route for Multi-vendor splits) |
| **Scheduled Jobs** | Spring `@Scheduled` (Daily cron jobs for status updates & emails) |
| **Email** | Spring Mail (SMTP integration) |
| **Frontend** | HTML5, Vanilla CSS (Custom Design System), Vanilla JS (Fetch API) |
| **Deployment** | Docker, Docker Compose, Railway |

---

## 🏗️ Architecture Highlights

- **Data Isolation:** Every entity (Customer, Loan, Payment) is strictly bound to a `Shop ID`. Queries use role-based access control and JWT claims to ensure Shop A can never see Shop B's data.
- **Smart Razorpay Routing:** Trust Ledger uses Razorpay Route. When a customer pays loan interest online, the money bypasses the Super Admin and routes directly to the specific Shop Owner's linked Razorpay account.
- **Stateless Authentication:** Fully stateless JWT architecture. No server-side sessions, allowing the app to scale horizontally with ease.

---

## 🚀 Local Development Setup

### Prerequisites
- Java 17+
- MySQL 8.0+
- Maven (or use the included `./mvnw` wrapper)

### 1. Clone the repository
```bash
git clone https://github.com/your-username/TrustLedger-SaaS.git
cd TrustLedger-SaaS
```

### 2. Configure the Database
Create a new MySQL database:
```sql
CREATE DATABASE trustledger_saas;
```

### 3. Setup Local Properties (Important!)
To keep your secrets safe, the main `application.properties` uses placeholder keys. For local development, create an `application-local.properties` file in `src/main/resources/` (this file is ignored by Git):

```properties
# src/main/resources/application-local.properties

# Database Credentials
spring.datasource.username=root
spring.datasource.password=YOUR_REAL_MYSQL_PASSWORD

# Email SMTP Setup (App Password)
spring.mail.password=YOUR_GMAIL_APP_PASSWORD

# Razorpay Test Keys
RAZORPAY_KEY_ID=rzp_test_xxxxxxxxxxxx
RAZORPAY_KEY_SECRET=xxxxxxxxxxxxxxxxxxxxxxxxxx
razorpay.key.id=${RAZORPAY_KEY_ID}
razorpay.key.secret=${RAZORPAY_KEY_SECRET}
```

### 4. Run the Application
Run the app using the `local` profile to automatically load your secrets:
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

---

## 🌐 Accessing the Portals

Once the application is running locally, you can access the different role-based portals using the following links:

### 1. Main Website & Shop Owner Portal
- **Link:** [http://localhost:8080/](http://localhost:8080/)
- **Description:** The landing page for the platform where new shops can register and existing Shop Owners can log in to manage their loans and customers.

### 2. Customer Portal
- **Link:** [http://localhost:8080/customer/login](http://localhost:8080/customer/login)
- **Description:** A dedicated portal for customers (borrowers) to view their active loans and pay their interest dues securely online.

### 3. Super Admin Panel
- **Link:** [http://localhost:8080/admin/login](http://localhost:8080/admin/login)
- **Description:** The hidden management dashboard for platform administrators to approve new shops and track platform metrics.
- **Default Credentials:** `admin@trustledger.com` / `Admin@123` *(Auto-generated on startup)*
