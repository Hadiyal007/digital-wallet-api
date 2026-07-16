# Digital Wallet API

[![CI](https://github.com/Hadiyal007/digital-wallet-api/actions/workflows/ci.yml/badge.svg)](https://github.com/Hadiyal007/digital-wallet-api/actions/workflows/ci.yml)

A production-oriented backend for a digital wallet application, built with Spring Boot 4.

**Live demo:** run locally with one command → see [Quick Start](#quick-start)  
**Demo credentials:** `admin` / `admin123` · `user1` / `user123`  
**API docs:** `http://localhost:8080/swagger-ui.html` (after starting)

---

## Architecture
┌──────────────────────────────────────────────────────────────────┐
│                React Frontend  (Vite + React 18)                  │
│        Login · Dashboard · History · Beneficiaries               │
│                      localhost:5173                               │
└─────────────────────────┬────────────────────────────────────────┘
│ HTTPS  ·  Authorization: Bearer <JWT>
▼
┌──────────────────────────────────────────────────────────────────┐
│               Spring Boot 4 API  (localhost:8080)                 │
│                                                                   │
│  ┌──────────────────────────────────────────────────────────┐    │
│  │  Security layer                                          │    │
│  │  JwtAuthFilter · SecurityConfig · CorsConfig             │    │
│  └──────────────────────────┬─────────────────────────────┘    │
│                              │                                    │
│  ┌──────────┐ ┌───────────┐ ┌─────────────────┐ ┌──────────┐   │
│  │   Auth   │ │  Users    │ │  Transactions    │ │  Admin   │   │
│  │Controller│ │Controller │ │   Controller     │ │Controller│   │
│  └────┬─────┘ └─────┬─────┘ └────────┬─────────┘ └────┬─────┘   │
│       └─────────────┴────────────────┴────────────────┘         │
│                              │                                    │
│  ┌──────────────────────────────────────────────────────────┐    │
│  │  Service layer                                           │    │
│  │  UserService · WalletService · TransactionService        │    │
│  │  BeneficiaryService · AuditLogService                    │    │
│  │                                                          │    │
│  │  ● @Transactional on all writes                          │    │
│  │  ● @Version (optimistic locking) on Wallet               │    │
│  │  ● ApplicationEventPublisher → AuditEventListener        │    │
│  └──────────────────────────┬─────────────────────────────┘    │
│                              │                                    │
│  ┌──────────────────────────────────────────────────────────┐    │
│  │  Spring Data JPA Repositories                            │    │
│  └──────────────────────────┬─────────────────────────────┘    │
└─────────────────────────────┼────────────────────────────────────┘
│ JDBC
▼
┌──────────────────────────────┐
│       MySQL 8                │
│  users · wallets             │
│  transactions                │
│  beneficiaries · audit_logs  │
└──────────────────────────────┘

**JWT Auth Flow:**

POST /api/auth/login  {username, password}
→ AuthenticationManager validates BCrypt hash
→ JwtUtil.generateToken(username, role)
→ Response: { token, tokenType, role, expiresInMs }
All subsequent requests:
Authorization: Bearer <token>
→ JwtAuthFilter extracts + validates signature
→ Sets Authentication in SecurityContext
→ Controller runs with identity available


---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.0 |
| Security | Spring Security + JWT (jjwt 0.12.6) |
| Database | MySQL 8 |
| ORM | Spring Data JPA / Hibernate 6 |
| Validation | Jakarta Bean Validation (`@Valid`) |
| Build | Maven 3 + Maven Wrapper |
| Frontend | React 18 + Vite 5 + Axios |
| Routing | React Router v6 |
| API Docs | SpringDoc OpenAPI 2.5 (Swagger UI) |
| Testing | JUnit 5 · Mockito · MockMvc · H2 (35 tests) |
| Container | Docker + docker-compose |

---

## Features

### Security & Authentication
- Stateless JWT authentication (replaces HTTP Basic Auth)
- BCrypt password hashing
- Role-based access control: `ROLE_USER` / `ROLE_ADMIN` via `@PreAuthorize`
- IDOR vulnerability fixes: every resource endpoint verifies the caller owns the resource
- Secrets externalized via environment variables — no credentials in source code
- Structured `401` / `403` responses with generic messages (prevents username enumeration)

### Wallet Operations
- **Credit** — add money to wallet
- **Debit** — withdraw money (InsufficientFundsException on overdraft)
- **Transfer** — wallet-to-wallet with atomic `@Transactional` guarantees
- **Optimistic locking** — `@Version Long version` on Wallet prevents the double-spend race condition where two concurrent transfers both pass the balance check
- Wallet freeze/unfreeze by admin

### API Design
- Consistent `ApiResponse<T>` envelope on all responses: `{ success, message, data }`
- `PagedResponse<T>` on all list endpoints with `page`, `size`, `sort` query params
- Structured `400` validation errors with field-level messages
- Global exception handler: domain exceptions → correct HTTP status codes

### Audit Logging
- Every credit/debit/transfer writes to `audit_logs` table
- Records: actor username, action, wallet number, amount, counterparty, IP address, status, failure reason
- Failed attempts are logged even when the business transaction rolls back — listener uses `REQUIRES_NEW` transaction propagation
- Four admin query endpoints: all logs, by user, by wallet, failed only

### Testing
- **Unit tests** (Mockito): TransactionService — happy paths, IDOR rejection, insufficient funds, frozen wallet, optimistic lock conflict, audit event publishing
- **Integration tests** (MockMvc + H2): login success/failure, JWT enforcement, validation errors, IDOR → 403
- H2 in-memory database for tests via `@ActiveProfiles("test")`

---

## Quick Start

### Option A — Docker (no local MySQL required)

```bash
git clone https://github.com/Hadiyal007/digital-wallet-api.git
cd digital-wallet-api
docker-compose up --build
```

- API: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- First startup takes ~3-4 minutes (MySQL + Maven build). Subsequent starts ~30s.

### Option B — Local development (requires MySQL 8)

```bash
# 1. Create the database
mysql -u root -p -e "CREATE DATABASE walletdb;"

# 2. Set your real MySQL password
export DB_PASSWORD=your_mysql_password

# 3. Run tests (H2 — no MySQL needed)
./mvnw test

# 4. Start the backend
./mvnw spring-boot:run
```

### Frontend

```bash
cd digital-wallet-frontend
npm install
npm run dev
# → http://localhost:5173
```

Make sure the Spring Boot backend is running on port 8080 first. The Vite dev server proxies `/api` requests to `localhost:8080` automatically.

---

## API Reference

Full interactive docs: `http://localhost:8080/swagger-ui.html`

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| `POST` | `/api/auth/login` | Public | Login, returns JWT |
| `GET` | `/api/auth/me` | JWT | Current user profile |
| `POST` | `/api/users/register` | Public | Create account |
| `GET` | `/api/users/{id}` | JWT (self/admin) | Get user |
| `GET` | `/api/users/{id}/wallet` | JWT (self/admin) | Get wallet |
| `PUT` | `/api/users/{id}` | JWT (self/admin) | Update profile |
| `POST` | `/api/transactions/credit/{walletId}` | JWT (owner) | Add money |
| `POST` | `/api/transactions/debit/{walletId}` | JWT (owner) | Withdraw money |
| `POST` | `/api/transactions/transfer/{walletId}` | JWT (owner) | Transfer |
| `GET` | `/api/transactions/history/{walletId}` | JWT (owner/admin) | Paginated history |
| `POST` | `/api/beneficiaries/{userId}` | JWT (self) | Add beneficiary |
| `GET` | `/api/beneficiaries/{userId}` | JWT (self) | List beneficiaries |
| `DELETE` | `/api/beneficiaries/{userId}/{id}` | JWT (self) | Delete beneficiary |
| `GET` | `/api/admin/users` | Admin | Paginated user list |
| `PUT` | `/api/admin/wallets/{id}/freeze` | Admin | Freeze wallet |
| `PUT` | `/api/admin/wallets/{id}/unfreeze` | Admin | Unfreeze wallet |
| `GET` | `/api/admin/audit-logs` | Admin | All audit logs |
| `GET` | `/api/admin/audit-logs/user/{username}` | Admin | Logs by user |
| `GET` | `/api/admin/audit-logs/failed` | Admin | Failed operations only |

---

## Database Schema
users                      wallets
─────────────────────      ──────────────────────────
id (PK, auto)              id (PK, auto)
full_name                  wallet_number (UNIQUE)
username (UNIQUE)          balance (DECIMAL 19,2)
email (UNIQUE)             status (ACTIVE | FROZEN)
password (BCrypt)          version  ← @Version optimistic lock
role (ROLE_USER |          created_at
ROLE_ADMIN)          user_id (FK → users)
transactions               beneficiaries
─────────────────────      ──────────────────────────
id (PK, auto)              id (PK, auto)
amount (DECIMAL 19,2)      beneficiary_name
type (CREDIT | DEBIT |     beneficiary_wallet_number
TRANSFER)            nickname
description                user_id (FK → users)
created_at
status (SUCCESS | FAILED)  audit_logs
sender_wallet_id (FK)      ──────────────────────────
receiver_wallet_id (FK)    id (PK, auto)
username        ← plain string (no FK)
action          ← survives user deletion
wallet_number   ← plain string (no FK)
amount
counterparty_wallet_number
status (SUCCESS | FAILED)
failure_reason
timestamp
ip_address

**Indexes:** `audit_logs.username`, `audit_logs.timestamp`

---

## Key Engineering Decisions

**Why JWT instead of HTTP Basic Auth?**  
HTTP Basic resends the raw password on every request. JWT proves identity once at login and issues a cryptographically signed token. No server-side session state. Scales horizontally without shared session storage.

**Why optimistic locking on Wallet?**  
Without it, two concurrent `transfer()` calls can both read `balance = 1000`, both pass the "sufficient funds" check, and both commit a debit — producing a wrong final balance. `@Version Long version` adds `AND version = ?` to every `UPDATE`. The second concurrent write gets 0 rows updated, triggers a rollback, and returns `409 Conflict`. The client retries.

**Why `REQUIRES_NEW` propagation on the audit log listener?**  
With default `REQUIRED` propagation, if the business transaction rolls back (e.g. insufficient funds), the audit log write rolls back too — deleting the evidence of the failed attempt. `REQUIRES_NEW` gives the listener its own independent transaction that commits regardless of what the outer transaction does.

**Why `PagedResponse<T>` instead of Spring's `Page<T>`?**  
Spring's `Page` exposes internal implementation fields (`pageable`, `sort`, `numberOfElements`, `empty`) that clients don't need and that create a coupling to Spring Data internals. `PagedResponse<T>` exposes exactly: `content`, `pageNumber`, `pageSize`, `totalElements`, `totalPages`, `first`, `last`.

**Why store `username` and `walletNumber` as plain strings in `audit_logs` instead of foreign keys?**  
Foreign keys would mean deleting a user cascades into deleting their audit trail — a compliance violation. Denormalized strings make the audit log an immutable, permanent record that survives the deletion of the records it describes.

---

## Project Structure
digital-wallet/
├── src/main/java/com/wallet/digital_wallet/
│   ├── config/
│   │   ├── DataInitializer.java     Demo user seeding (idempotent)
│   │   ├── SecurityConfig.java      JWT filter chain, RBAC rules
│   │   ├── CorsConfig.java          Explicit allowed origins
│   │   ├── WebConfig.java           Max page size cap (50)
│   │   └── OpenApiConfig.java       Swagger UI + JWT auth scheme
│   ├── controller/
│   │   ├── AuthController.java      /api/auth
│   │   ├── UserController.java      /api/users
│   │   ├── TransactionController.java  /api/transactions
│   │   ├── BeneficiaryController.java  /api/beneficiaries
│   │   ├── AdminController.java     /api/admin
│   │   └── HealthController.java    /health
│   ├── dto/                         Request/Response DTOs + PagedResponse
│   ├── entity/                      User, Wallet, Transaction, Beneficiary, AuditLog
│   ├── event/                       WalletAuditEvent
│   ├── exception/                   GlobalExceptionHandler + 7 domain exceptions
│   ├── listener/                    AuditEventListener (REQUIRES_NEW)
│   ├── repository/                  5 Spring Data JPA repositories
│   ├── security/                    JwtUtil, JwtAuthFilter, CustomUserDetailsService
│   └── service/                     5 service classes
├── src/main/resources/
│   ├── application.properties       Shared config + spring.profiles.default=dev
│   ├── application-dev.properties   MySQL, show-sql, Swagger enabled
│   └── application-prod.properties  Postgres via env vars, Swagger disabled
├── src/test/
│   ├── resources/application-test.properties  H2 in-memory
│   └── java/.../
│       ├── security/JwtUtilTest.java           6 unit tests
│       ├── service/TransactionServiceTest.java 13 unit tests
│       └── controller/
│           ├── AuthControllerIntegrationTest.java        7 tests
│           └── TransactionControllerIntegrationTest.java 8 tests
├── digital-wallet-frontend/
│   ├── src/api/axios.js             Interceptors (attach JWT, catch 401)
│   ├── src/components/ProtectedRoute.jsx
│   └── src/pages/
│       ├── Login.jsx
│       ├── Register.jsx             Field-level @Valid errors in UI
│       ├── Dashboard.jsx            Balance, credit, debit, nav
│       ├── History.jsx              Paginated transaction table
│       └── Beneficiaries.jsx        Add/list/delete + transfer modal
├── docker-compose.yml               MySQL + Spring Boot, one command
├── Dockerfile                       Multi-stage: JDK build → JRE run
├── render.yaml                      Render.com Blueprint (optional cloud)
└── .env.example                     Required env vars documented

---

## Running Tests

```bash
./mvnw test
# Uses H2 in-memory DB via @ActiveProfiles("test")
# No MySQL, no network, completes in ~8 seconds
# Expected: 35 tests, 0 failures
```

---

## Environment Variables

| Variable | Required | Description | Dev default |
|---|---|---|---|
| `DB_PASSWORD` | **Yes** | Database password | `changeme` |
| `DB_URL` | No | JDBC URL | `jdbc:mysql://localhost:3306/walletdb` |
| `DB_USERNAME` | No | DB user | `root` |
| `JWT_SECRET` | **Prod** | Signing secret (≥32 chars) | long dev default |
| `JWT_EXPIRATION_MS` | No | Token TTL in ms | `3600000` (1h) |
| `DEMO_ADMIN_PASSWORD` | No | Seeded admin password | `admin123` |
| `DEMO_USER_PASSWORD` | No | Seeded user password | `user123` |
| `SPRING_PROFILES_ACTIVE` | Prod | `dev` or `prod` | `dev` |

Generate a strong JWT secret:
```bash
openssl rand -base64 64
```