# 🏆 Trust Ledger — Multi-Tenant Gold Loan SaaS Platform

> *"Protect Gold. Preserve Trust."*

Trust Ledger is a comprehensive, production-ready SaaS application designed to digitize the operations of local gold loan and finance businesses in India. 

It provides shop owners with a powerful digital dashboard to manage customers and loans, while offering their end-customers a seamless self-service portal to view balances and make online interest payments. The platform is multi-tenant, meaning a single instance can securely host data for hundreds of independent shop owners.

---

## 📸 Screenshots

*(Add your screenshots here)*

- **Landing Page:** 
- **Shop Owner Dashboard:** 
- **Customer Payment Portal:** 
- **Super Admin Panel:** 

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

### 5. Access the Application
- **Main Website / Shop Owner Login:** `http://localhost:8080/`
- **Customer Login:** `http://localhost:8080/customer/login`
- **Super Admin Login:** `http://localhost:8080/admin/login`

*(Default Super Admin credentials are created on startup: `admin@trustledger.com` / `Admin@123`)*

---

## 🐳 Docker Deployment

The project includes a `docker-compose.yml` file for easy deployment.

```bash
# Build and start the MySQL and Spring Boot containers in the background
docker-compose up -d --build

# View logs
docker-compose logs -f
```

## ☁️ Production Deployment (Railway)
1. Link your GitHub repository to Railway.
2. Add a **MySQL** database plugin to your Railway project.
3. In your Spring Boot service variables, override the placeholders:
   - `SPRING_PROFILES_ACTIVE=prod`
   - `MYSQLHOST`, `MYSQLPORT`, `MYSQLDATABASE`, `DB_USERNAME`, `DB_PASSWORD`
   - `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`
   - `MAIL_PASSWORD`
4. Railway will automatically build the `Dockerfile` and deploy the application.
