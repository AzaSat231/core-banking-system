# Core Banking API

A production-grade RESTful banking system built with Spring Boot 4, PostgreSQL, JWT authentication, and Docker.
The system handles customer lifecycle management, bank account operations, and financial transactions
including deposits, withdrawals, and peer-to-peer transfers — with full concurrency safety and role-based access control.

**Live API:** https://core-banking-api.onrender.com
**Swagger UI:** https://core-banking-api.onrender.com/swagger-ui.html

> **Note:** Hosted on Render free tier — first request may take ~60 seconds to wake up.

---

## Table of Contents

- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Security & Authentication](#security--authentication)
- [Data Model](#data-model)
- [API Reference](#api-reference)
- [Request & Response Schemas](#request--response-schemas)
- [Concurrency & Transaction Safety](#concurrency--transaction-safety)
- [Exception Handling](#exception-handling)
- [Getting Started](#getting-started)
- [Running Tests](#running-tests)
- [Example Usage](#example-usage)
- [Design Decisions](#design-decisions)

---

## Tech Stack

| Technology | Version | Purpose |
|---|---|---|
| Java | 21 | LTS release; records used for immutable DTOs |
| Spring Boot | 4.0.3 | Core framework — dependency injection, embedded Tomcat, auto-configuration |
| Spring Security | managed by parent | JWT-based stateless authentication and role-based authorization |
| JJWT | 0.12.6 | JWT token generation and validation |
| Spring Data JPA | managed by parent | Repository abstraction over Hibernate ORM; pessimistic locking via `@Lock` |
| Spring Boot Starter Validation | managed by parent | Bean Validation 3.0 constraints on all request DTOs |
| PostgreSQL | 15 | Primary database |
| springdoc-openapi | 2.8.6 | Interactive Swagger UI and OpenAPI 3.1 documentation |
| Testcontainers | managed by parent | Integration tests against a real PostgreSQL container |
| Lombok | 1.18.30 | Reduces entity boilerplate |
| Docker / Compose | — | Single-command local environment bootstrap |

---

## Project Structure

```
com.azizsattarov.corebanking
├── auth/
│   ├── User.java                       JPA entity for authentication
│   ├── UserRole.java                   ROLE_ADMIN, ROLE_USER
│   ├── UserRepository.java
│   ├── AppUserDetailsService.java
│   ├── JwtUtil.java                    Token generation and validation (JJWT 0.12.6)
│   ├── JwtAuthFilter.java              OncePerRequestFilter — extracts and validates Bearer token
│   ├── SecurityConfig.java             Stateless security, role-based rules
│   ├── AuthController.java             POST /auth/register, POST /auth/login
│   └── dto/
│       ├── RegisterRequest.java
│       ├── LoginRequest.java
│       └── AuthResponse.java
├── customer/
│   ├── Customer.java
│   ├── CustomerStatus.java             ACTIVE, CLOSED
│   ├── CustomerRepository.java
│   ├── CustomerService.java
│   ├── CustomerServiceImpl.java
│   ├── CustomerController.java
│   └── dto/
├── account/
│   ├── Account.java
│   ├── AccountStatus.java              ACTIVE, FROZEN, CLOSED
│   ├── AccountRepository.java          findByIdForUpdate (PESSIMISTIC_WRITE)
│   ├── AccountService.java
│   ├── AccountServiceImpl.java
│   ├── AccountController.java
│   └── dto/
├── transaction/
│   ├── Transaction.java
│   ├── TransactionType.java            DEPOSIT, WITHDRAW, TRANSFER_IN, TRANSFER_OUT
│   ├── TransactionStatus.java          APPROVED, DECLINED
│   ├── TransactionRepository.java
│   ├── TransactionService.java
│   ├── TransactionServiceImpl.java
│   ├── TransactionController.java
│   └── dto/
├── config/
│   └── SwaggerConfig.java              OpenAPI + JWT Bearer auth configuration
└── exception/
    ├── BadRequestException.java        HTTP 400
    ├── NotFoundException.java          HTTP 404
    ├── GlobalExceptionHandler.java     @RestControllerAdvice
    └── dto/
        └── ErrorResponse.java
```

---

## Security & Authentication

The API uses **stateless JWT authentication** via Spring Security.

### Roles

| Role | Permissions |
|---|---|
| `ROLE_ADMIN` | Full access — create/update/delete customers and accounts, perform transactions, view data |
| `ROLE_USER` | Read access + perform transactions (deposit, withdraw, transfer) |

### How to Authenticate

**1. Register**
```http
POST /auth/register
{
  "username": "admin1",
  "password": "admin123",
  "role": "ROLE_ADMIN"
}
```

**2. Login**
```http
POST /auth/login
{
  "username": "admin1",
  "password": "admin123"
}
```
Returns:
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "username": "admin1",
  "role": "ROLE_ADMIN"
}
```

**3. Use the token**

Add to all subsequent requests:
```
Authorization: Bearer <token>
```

In Swagger UI, click the **Authorize** button (🔒) and enter: `Bearer <your-token>`

---

## Data Model

### Entity Relationships

```
Customer  1 ──── * Account
Account   1 ──── * Transaction
```

### Soft Delete

Both `Customer` and `Account` implement soft deletion. A `deleted_at` timestamp is written and status set to `CLOSED`. Hibernate's `@SQLRestriction("deleted_at IS NULL")` filters all queries transparently — deleted records remain for audit purposes.

### Key Fields

| Entity | Field | Notes |
|---|---|---|
| `Account` | `accountNumber` | `ACC` prefix + 12 random uppercase hex characters |
| `Account` | `balance` | `BigDecimal(19,4)` — avoids floating-point rounding on monetary values |
| `Transaction` | `referenceId` | 12-character UUID fragment; same value on both sides of a transfer |
| `Transaction` | `balanceAfter` | Snapshot of balance at transaction time |
| `Transaction` | `counterpartyAccountNumber` | Populated on `TRANSFER_IN` / `TRANSFER_OUT` for reconciliation |

---

## API Reference

### Authentication

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| `POST` | `/auth/register` | Public | Register a new user |
| `POST` | `/auth/login` | Public | Login and receive JWT token |

### Customers

| Method | Endpoint | Role | Description |
|---|---|---|---|
| `POST` | `/customers` | ADMIN | Create a new customer |
| `GET` | `/customers` | ADMIN, USER | List all active customers |
| `GET` | `/customers/{id}/accounts` | ADMIN, USER | List customer accounts |
| `PUT` | `/customers/{id}` | ADMIN | Update customer information |
| `DELETE` | `/customers/{id}` | ADMIN | Soft-delete a customer |

### Accounts

| Method | Endpoint | Role | Description |
|---|---|---|---|
| `POST` | `/customers/{customerId}/accounts` | ADMIN | Open a new bank account |
| `PATCH` | `/customers/{customerId}/accounts/{accountId}/status` | ADMIN | Change account status |
| `DELETE` | `/customers/{customerId}/accounts/{accountId}` | ADMIN | Soft-delete an account |

### Transactions

| Method | Endpoint | Role | Description |
|---|---|---|---|
| `POST` | `/accounts/{accountId}/deposit` | ADMIN, USER | Deposit funds |
| `POST` | `/accounts/{accountId}/withdraw` | ADMIN, USER | Withdraw funds |
| `POST` | `/accounts/{accountId}/transfers` | ADMIN, USER | Transfer to another account |
| `GET` | `/accounts/{accountId}/transactions` | ADMIN, USER | Get transaction history |

---

## Request & Response Schemas

### RegisterRequest
```json
{
  "username": "admin1",
  "password": "admin123",
  "role": "ROLE_ADMIN"
}
```

### CreateCustomerRequest
```json
{
  "firstName":   "Aziz",
  "lastName":    "Sattarov",
  "nationalId":  "12345678901",
  "email":       "aziz@bank.com",
  "phoneNumber": "+998901234567",
  "dateOfBirth": "1995-04-15"
}
```

### DepositRequest
```json
{ "amountDeposit": 500.00 }
```

### WithdrawRequest
```json
{ "amountWithdraw": 200.00 }
```

### TransferRequest
```json
{ "toAccountId": 2, "amount": 300.00 }
```

### ErrorResponse
```json
{
  "timestamp": "2025-06-01T14:32:00",
  "status":    400,
  "error":     "Bad Request",
  "message":   "Insufficient funds",
  "path":      "/accounts/1/withdraw"
}
```

---

## Concurrency & Transaction Safety

### Pessimistic Locking

All balance-mutating operations acquire a `SELECT ... FOR UPDATE` lock via `AccountRepository.findByIdForUpdate()`. This prevents lost updates when concurrent requests modify the same account simultaneously.

### Deadlock Prevention in Transfers

Locks are always acquired in ascending account ID order — regardless of sender/receiver direction:

```java
Long first  = Math.min(fromAccountId, toAccountId);
Long second = Math.max(fromAccountId, toAccountId);
```

This enforces a globally consistent lock-acquisition order across all threads, eliminating circular-wait deadlocks.

### Transactional Boundaries

- All write operations are annotated with `@Transactional` — both sides of a transfer commit together or neither does
- Read-only operations use `@Transactional(readOnly = true)` for Hibernate performance optimisations

---

## Exception Handling

| Exception | HTTP Status | Trigger |
|---|---|---|
| `NotFoundException` | `404` | Entity not found by ID |
| `BadRequestException` | `400` | Business rule violation (insufficient funds, inactive account, negative amount) |
| `MethodArgumentNotValidException` | `400` | Bean Validation failure on request DTO |
| `Exception` (catch-all) | `500` | Unexpected runtime error |

---

## Getting Started

### Prerequisites

- Java 21+
- Maven 3.8+ (or use `./mvnw`)
- Docker and Docker Compose

### 1. Clone and Start Database

```bash
git clone https://github.com/AzaSat231/core-banking-system.git
cd core-banking-system
docker compose up -d
```

| Parameter | Value |
|---|---|
| Host port | `5332` |
| Database | `dbv1` |
| Username | `dbv1` |
| Password | `pass123` |

### 2. Run the Application

```bash
./mvnw spring-boot:run
```

API available at: `http://localhost:8080`
Swagger UI at: `http://localhost:8080/swagger-ui.html`

---

## Running Tests

Integration tests use **Testcontainers** — a real PostgreSQL container is spun up automatically. No manual setup required.

```bash
./mvnw test
```

### Test Coverage

| Test | Description |
|---|---|
| `deposit_shouldIncreaseBalance` | Verifies balance increases after deposit |
| `withdraw_shouldThrow_whenInsufficientFunds` | Verifies exception on overdraft attempt |
| `transfer_shouldMoveMoney_betweenAccounts` | Verifies atomic transfer between two accounts |

---

## Example Usage

### Register and Login

```bash
# Register admin
curl -X POST https://core-banking-api.onrender.com/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username": "admin1", "password": "admin123", "role": "ROLE_ADMIN"}'

# Login
curl -X POST https://core-banking-api.onrender.com/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username": "admin1", "password": "admin123"}'
```

### Create Customer and Account

```bash
TOKEN="your-jwt-token-here"

curl -X POST https://core-banking-api.onrender.com/customers \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"firstName": "Aziz", "lastName": "Sattarov", "nationalId": "12345678901", "email": "aziz@bank.com", "phoneNumber": "+998901234567", "dateOfBirth": "1995-04-15"}'

curl -X POST https://core-banking-api.onrender.com/customers/1/accounts \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"initialBalance": 1000.00}'
```

### Deposit and Transfer

```bash
curl -X POST https://core-banking-api.onrender.com/accounts/1/deposit \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"amountDeposit": 500.00}'

curl -X POST https://core-banking-api.onrender.com/accounts/1/transfers \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"toAccountId": 2, "amount": 250.00}'
```

---

## Design Decisions

**Pessimistic over optimistic locking** — For a banking system where balance correctness is non-negotiable, pessimistic locking provides stronger guarantees without requiring retry logic in callers.

**Java records for DTOs** — Records are immutable by default with built-in `equals`, `hashCode`, and `toString`. Since DTOs are pure data carriers, records are the appropriate type.

**Stateless JWT authentication** — No server-side session state. Each request is self-contained, making the API horizontally scalable.

**Soft deletes** — Banking regulations require data retention for audit and compliance. Soft deletes preserve complete history while keeping the application layer clean.

---

## License

MIT