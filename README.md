# Core Banking API

A production-grade RESTful banking system built with Spring Boot 3, PostgreSQL, and Docker.
The system handles customer lifecycle management, bank account operations, and financial transactions
including deposits, withdrawals, and peer-to-peer transfers — with full concurrency safety.

---

## Table of Contents

- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Data Model](#data-model)
- [API Reference](#api-reference)
- [Request & Response Schemas](#request--response-schemas)
- [Concurrency & Transaction Safety](#concurrency--transaction-safety)
- [Exception Handling](#exception-handling)
- [Getting Started](#getting-started)
- [Example Usage](#example-usage)
- [Design Decisions](#design-decisions)
- [Potential Improvements](#potential-improvements)

---

## Tech Stack

| Technology | Version | Purpose |
|---|---|---|
| Java | 21 | LTS release; records used for immutable DTOs |
| Spring Boot | 4.0.3 | Core framework — dependency injection, embedded Tomcat, auto-configuration |
| Spring Boot Starter Web MVC | managed by parent | REST controllers, `DispatcherServlet`, Jackson serialisation |
| Spring Data JPA | managed by parent | Repository abstraction over Hibernate ORM; pessimistic locking via `@Lock` |
| Spring Boot Starter Validation | managed by parent | Bean Validation 3.0 constraints on all request DTOs |
| PostgreSQL JDBC Driver | managed by parent | Runtime JDBC driver for PostgreSQL |
| Lombok | 1.18.30 | Reduces entity boilerplate (`@Getter`, `@Setter`, `@NoArgsConstructor`, etc.) |
| Spring Boot DevTools | managed by parent | Automatic restart and live reload during development |
| Docker / Compose | — | Single-command local environment bootstrap |
| Hibernate `@SQLRestriction` | managed by Spring Data JPA | Transparent soft-delete filtering on `Customer` and `Account` entities |
---

## Project Structure

```
com.azizsattarov.core-banking
├── customer/
│   ├── Customer.java
│   ├── CustomerStatus.java             ACTIVE, CLOSED
│   ├── CustomerRepository.java
│   ├── CustomerService.java
│   ├── CustomerServiceImpl.java
│   ├── CustomerController.java
│   └── dto/
│       ├── CreateCustomerRequest.java
│       ├── UpdateCustomerRequest.java
│       └── CustomerResponse.java
├── account/
│   ├── Account.java
│   ├── AccountStatus.java              ACTIVE, FROZEN, CLOSED
│   ├── AccountRepository.java          Includes findByIdForUpdate (PESSIMISTIC_WRITE)
│   ├── AccountService.java
│   ├── AccountServiceImpl.java
│   ├── AccountController.java
│   └── dto/
│       ├── CreateAccountRequest.java
│       ├── UpdateAccountRequest.java
│       └── AccountResponse.java
├── transaction/
│   ├── Transaction.java
│   ├── TransactionType.java            DEPOSIT, WITHDRAW, TRANSFER_IN, TRANSFER_OUT
│   ├── TransactionStatus.java          APPROVED, DECLINED
│   ├── TransactionRepository.java      Includes findByAccountId JPQL query
│   ├── TransactionService.java
│   ├── TransactionServiceImpl.java
│   ├── TransactionController.java
│   └── dto/
│       ├── DepositRequest.java
│       ├── WithdrawRequest.java
│       ├── TransferRequest.java
│       ├── TransactionResponse.java
│       └── TransferResponse.java
└── exception/
    ├── BadRequestException.java        Maps to HTTP 400
    ├── NotFoundException.java          Maps to HTTP 404
    ├── GlobalExceptionHandler.java     @RestControllerAdvice
    └── dto/
        └── ErrorResponse.java
```

---

## Data Model

### Entity Relationships

```
Customer  1 ──── * Account
Account   1 ──── * Transaction
```

Relationships are managed bidirectionally. Helper methods (`addAccount`, `addTransaction`) keep both sides of each association in sync and ensure cascade operations behave correctly.

### Soft Delete

Both `Customer` and `Account` implement soft deletion. Rather than issuing a `DELETE` statement, a `deleted_at` timestamp is written to the record and the status is set to `CLOSED`. Hibernate's `@SQLRestriction("deleted_at IS NULL")` annotation filters all queries transparently — deleted records remain in the database for audit purposes but are invisible to the application layer.

### Key Fields

| Entity | Field | Notes |
|---|---|---|
| `Account` | `accountNumber` | `ACC` prefix followed by 12 random uppercase hex characters |
| `Account` | `balance` | `BigDecimal(19,4)` — avoids floating-point rounding errors on monetary values |
| `Transaction` | `referenceId` | 12-character UUID fragment; the same value is written to both sides of a transfer |
| `Transaction` | `balanceAfter` | Snapshot of the account balance at the time of the transaction |
| `Transaction` | `counterpartyAccountNumber` | Populated on `TRANSFER_IN` and `TRANSFER_OUT` for reconciliation |

---

## API Reference

### Customers

Base path: `/customers`

| Method | Endpoint | Description | Response |
|---|---|---|---|
| `POST` | `/customers` | Create a new customer | `201 CustomerResponse` |
| `GET` | `/customers` | List all active customers | `200 List<CustomerResponse>` |
| `GET` | `/customers/{id}/accounts` | List all accounts belonging to a customer | `200 List<AccountResponse>` |
| `PUT` | `/customers/{id}` | Update customer contact information | `200 CustomerResponse` |
| `DELETE` | `/customers/{id}` | Soft-delete a customer | `204 No Content` |

### Accounts

Base path: `/customers/{customerId}/accounts`

| Method | Endpoint | Description | Response |
|---|---|---|---|
| `POST` | `/` | Open a new bank account | `201 AccountResponse` |
| `PATCH` | `/{accountId}/status` | Change account status (`ACTIVE`, `FROZEN`, `CLOSED`) | `200 AccountResponse` |
| `DELETE` | `/{accountId}` | Soft-delete an account | `204 No Content` |

### Transactions

Base path: `/accounts/{accountId}`

| Method | Endpoint | Description | Response |
|---|---|---|---|
| `POST` | `/{accountId}/deposit` | Deposit funds into an account | `201 TransactionResponse` |
| `POST` | `/{accountId}/withdraw` | Withdraw funds from an account | `201 TransactionResponse` |
| `POST` | `/{fromAccountId}/transfers` | Transfer funds to another account | `201 TransferResponse` |
| `GET` | `/{accountId}/transactions` | Retrieve transaction history, ordered newest first | `200 List<TransactionResponse>` |

---

## Request & Response Schemas

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

### CreateAccountRequest

```json
{
  "initialBalance": 1000.00
}
```

### DepositRequest

```json
{
  "amountDeposit": 500.00
}
```

### WithdrawRequest

```json
{
  "amountWithdraw": 200.00
}
```

### TransferRequest

```json
{
  "toAccountId": 2,
  "amount": 300.00
}
```

### ErrorResponse

Returned on all error paths — no plain-text error strings anywhere in the API.

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

All balance-mutating operations (deposit, withdraw, transfer) acquire a `SELECT ... FOR UPDATE` lock via `AccountRepository.findByIdForUpdate()`. This prevents lost updates when two concurrent requests attempt to modify the same account balance simultaneously.

### Deadlock Prevention in Transfers

A transfer acquires locks on two accounts. To eliminate circular-wait deadlocks, locks are always acquired in ascending account ID order — regardless of which account is the sender or receiver:

```java
Long first  = Math.min(fromAccountId, toAccountId);
Long second = Math.max(fromAccountId, toAccountId);
```

This enforces a globally consistent lock-acquisition order across all concurrent threads.

### Transactional Boundaries

- All write operations are annotated with `@Transactional`. Both sides of a transfer either commit together or neither does.
- Read-only operations use `@Transactional(readOnly = true)` to enable Hibernate performance optimisations.

---

## Exception Handling

All exceptions are centralised in `GlobalExceptionHandler` (`@RestControllerAdvice`). Every error path returns a structured `ErrorResponse` with a timestamp, HTTP status code, reason phrase, descriptive message, and the request path.

| Exception | HTTP Status | Trigger |
|---|---|---|
| `NotFoundException` | `404 Not Found` | Entity lookup by ID returns no result |
| `BadRequestException` | `400 Bad Request` | Business rule violation (insufficient funds, inactive account, self-transfer, negative amount) |
| `MethodArgumentNotValidException` | `400 Bad Request` | Bean Validation constraint failure on a request DTO field |
| `Exception` (catch-all) | `500 Internal Server Error` | Unexpected runtime error; message is sanitised to `"Unexpected error"` |

---

## Getting Started

### Prerequisites

- Java 17+
- Maven 3.8+ (or use the project's Maven Wrapper: `./mvnw`)
- Docker and Docker Compose

### 1. Start the Database

```bash
docker compose up -d
```

The compose file provisions a PostgreSQL container with the following configuration:

| Parameter | Value |
|---|---|
| Container name | `postgres-spring-boot` |
| Host port | `5332` (mapped from container port `5432`) |
| Database | `dbv1` |
| Username | `dbv1` |
| Password | `pass123` |
| Data volume | `db` (persisted across restarts) |

### 2. Configure application.properties

```properties
spring.datasource.url=jdbc:postgresql://localhost:5332/dbv1
spring.datasource.username=dbv1
spring.datasource.password=pass123

spring.jpa.hibernate.ddl-auto=create-drop
spring.jpa.show-sql=true
```

> [!CAUTION]
> `ddl-auto=create-drop` drops and recreates the entire schema on every application restart.
> This is intentional for local development. Change to `validate` or `none` before deploying
> to any persistent environment or you will lose all data on restart.


### 3. Build and Run

```bash
git clone https://github.com/azizsattarov/core-banking.git
cd core-banking
docker compose up -d
./mvnw spring-boot:run
```

The API will be available at `http://localhost:8080`.

---

## Example Usage

**Create a customer**

```bash
curl -X POST http://localhost:8080/customers \
  -H 'Content-Type: application/json' \
  -d '{
    "firstName":   "Aziz",
    "lastName":    "Sattarov",
    "nationalId":  "12345678901",
    "email":       "aziz@bank.com",
    "phoneNumber": "+998901234567",
    "dateOfBirth": "1995-04-15"
  }'
```

**Open a bank account**

```bash
curl -X POST http://localhost:8080/customers/1/accounts \
  -H 'Content-Type: application/json' \
  -d '{ "initialBalance": 1000.00 }'
```

**Deposit funds**

```bash
curl -X POST http://localhost:8080/accounts/1/deposit \
  -H 'Content-Type: application/json' \
  -d '{ "amountDeposit": 500.00 }'
```

**Transfer between accounts**

```bash
curl -X POST http://localhost:8080/accounts/1/transfers \
  -H 'Content-Type: application/json' \
  -d '{ "toAccountId": 2, "amount": 250.00 }'
```

**Get transaction history**

```bash
curl http://localhost:8080/accounts/1/transactions
```

---

## Design Decisions

**Pessimistic over optimistic locking**

Optimistic locking relies on version-field conflicts and requires retry logic in the caller, which is difficult to handle correctly in a stateless REST API. For a banking system where balance correctness is non-negotiable, pessimistic locking provides a simpler and stronger guarantee. At typical banking transaction volumes, the throughput tradeoff is justified.

**Java records for DTOs**

Records are immutable by default and provide built-in `equals`, `hashCode`, and `toString` with no boilerplate. Since DTOs are pure data carriers with no behaviour, records are the appropriate type.

**Soft deletes**

Banking regulations typically require that customer data and transaction history be retained for audit and compliance purposes even after an account is closed. Soft deletes ensure a complete historical record is preserved in the database while the application layer operates exclusively on active entities.

---

## Potential Improvements

- Replace `ddl-auto` with Flyway or Liquibase for version-controlled schema migrations
- Add Spring Security with JWT authentication and role-based access control
- Add multi-currency support with FX conversion on transfers
- Paginate `/transactions` and `/customers` responses for large datasets
- Write integration tests using Testcontainers against a real PostgreSQL instance
- Expose interactive API documentation via `springdoc-openapi`
- Add an immutable audit log table capturing every state change with actor and timestamp
- Integrate Micrometer metrics and structured logging for production observability

---

## License

MIT