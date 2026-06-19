# 🏆 Trust Ledger — Multi-Tenant Gold Loan SaaS Platform

> *"Protect Gold. Preserve Trust."*

Trust Ledger is a multi-tenant SaaS platform that digitizes gold loan management for local gold loan shops in India. It provides shop owners with a complete digital dashboard, automated customer reminders, online payment collection, and a customer self-service portal.

---

## ✨ Features

### 🏪 Shop Owner Portal
- **Digital Loan Records** — Replace handwritten registers with structured digital records
- **Customer Management** — Add customers with KYC (Aadhaar, PAN, photos)
- **Loan Lifecycle** — Create, track, close, extend, and seize gold loans
- **Cash Payment Recording** — Record in-person payments with receipts
- **Dashboard Analytics** — Active loans, overdue loans, total volume, recent payments
- **Automated Reminders** — Email/SMS alerts at Day 1, 7, 15, and 30 overdue

### 👤 Customer Portal (PRO Plan)
- **View Loans** — Customers can see all their active/past loans
- **Online Payments** — Pay interest via Razorpay (UPI, Cards, Net Banking)
- **Payment History** — View all past payments with PDF receipt downloads
- **Gold Seizure Warning** — Visual warning banner for 30+ day overdue loans

### 👑 Super Admin Panel
- **Platform Analytics** — Total shops, customers, loans, revenue
- **Shop Approval/Rejection** — Review and approve new shop registrations
- **Subscription Revenue Log** — Track all subscription payments
- **Shop Suspension** — Suspend shops that violate terms

---

## 🛠️ Tech Stack

| Layer | Technology |
|-------|-----------|
| **Backend** | Java 17, Spring Boot 3.2.5 |
| **Security** | Spring Security + JWT (Stateless) |
| **Database** | MySQL 8 with JPA/Hibernate |
| **Payments** | Razorpay (Checkout + Route for PRO) |
| **PDF** | iText 7 (Payment receipts) |
| **Email** | Spring Mail (SMTP) |
| **Frontend** | HTML5 + CSS3 + Vanilla JavaScript |
| **Deployment** | Docker + Docker Compose |

---

## 🚀 Quick Start

### Prerequisites
- Java 17+
- MySQL 8.0+
- Maven 3.8+ (or use the included `mvnw` wrapper)

### 1. Clone the repository
```bash
git clone https://github.com/your-username/TrustLedger-SaaS.git
cd TrustLedger-SaaS
```

### 2. Configure the database
Create a MySQL database:
```sql
CREATE DATABASE trustledger_db;
```

### 3. Update application.properties
Edit `src/main/resources/application.properties`:
```properties
spring.datasource.url=jdbc:mysql://localhost:3306/trustledger_db
spring.datasource.username=root
spring.datasource.password=your_password

jwt.secret=your-very-long-secret-key-at-least-64-characters

razorpay.key.id=rzp_test_xxxxxxxxxxxx
razorpay.key.secret=xxxxxxxxxxxxxxxxxxxxxxxxxx
```

### 4. Run the application
```bash
./mvnw spring-boot:run
```
Or if Maven is in your PATH:
```bash
mvn spring-boot:run
```

### 5. Access the application
- **Homepage:** http://localhost:8080/
- **Admin Login:** http://localhost:8080/admin/login
- **Default Admin:** `admin@trustledger.com` / `Admin@123`

---

## 🐳 Docker Deployment

```bash
# Build and start everything
docker-compose up -d --build

# View logs
docker-compose logs -f app

# Stop
docker-compose down
```

---

## 📂 Project Structure

```
TrustLedger-SaaS/
├── src/main/java/com/trustledgersaas/
│   ├── TrustLedgerSaasApplication.java    # Main entry point
│   ├── config/
│   │   ├── SecurityConfig.java            # Spring Security + JWT config
│   │   └── DataInitializer.java           # Seeds Super Admin on first run
│   ├── controller/
│   │   ├── PageController.java            # Serves HTML templates
│   │   ├── AuthController.java            # Login/Register APIs
│   │   ├── ShopController.java            # Shop owner APIs
│   │   ├── SuperAdminController.java      # Admin APIs
│   │   ├── CustomerController.java        # Customer portal APIs
│   │   ├── LoanController.java            # Loan CRUD APIs
│   │   └── PaymentController.java         # Payment APIs
│   ├── entity/                            # JPA entities
│   ├── repository/                        # Spring Data JPA repos
│   ├── service/                           # Business logic
│   ├── dto/                               # Request/Response DTOs
│   ├── mapper/                            # Entity ↔ DTO mappers
│   ├── security/                          # JWT filter, util, user details
│   ├── exception/                         # Custom exceptions + handler
│   └── util/                              # Interest calculator
├── src/main/resources/
│   ├── application.properties
│   ├── templates/                         # HTML templates
│   │   ├── index.html                     # Public homepage
│   │   ├── auth/                          # Registration, password, status pages
│   │   ├── admin/                         # Super Admin pages
│   │   ├── shop/                          # Shop Owner pages
│   │   └── customer/                      # Customer portal pages
│   └── static/                            # CSS, JS, images
│       ├── css/
│       ├── js/
│       └── images/                        # Logo, favicon, and UI images
├── Dockerfile
├── docker-compose.yml
└── pom.xml
```

---

## 💰 Subscription Plans

| Feature | Basic (₹299/mo) | Pro (₹699/mo) |
|---------|:---:|:---:|
| Dashboard & Loan Management | ✅ | ✅ |
| Customer Limit | 100 | Unlimited |
| Cash Payment Recording | ✅ | ✅ |
| Email/SMS Reminders | ✅ | ✅ |
| Customer Login Portal | ❌ | ✅ |
| Online Payment Collection | ❌ | ✅ |
| PDF Receipt Downloads | ❌ | ✅ |

---

## 🔐 Roles & Access

| Role | Access |
|------|--------|
| **Super Admin** | `/admin/**` — Platform management, shop approvals, revenue |
| **Shop Owner** | `/shop/**` — Dashboard, customers, loans, settings |
| **Customer** | `/customer/**` — View loans, make payments, profile |

---

## 🎨 Design System

- **Background:** Cream (#FAF3E0)
- **Accent:** Gold (#D4A017)
- **Text:** Dark Navy (#1A1A2E)
- **Font:** Inter (Google Fonts)
- **Mobile:** Responsive with bottom navigation

---

## 📝 License

This project is proprietary. All rights reserved.

---

*Built with ❤️ for the gold loan shops of India.*
