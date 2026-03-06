# Core Banking System

A RESTful backend application built with **Spring Boot** that provides foundational banking operations — customer management, account lifecycle, and financial transactions with full ACID guarantees.

---

## Tech Stack

- **Java 17+** / Spring Boot 3.x
- **Spring Data JPA** + Hibernate
- **PostgreSQL**
- **Lombok**
- **Jakarta Bean Validation**

---

## Features

- Customer management with soft-delete (`deleted_at` + `@SQLRestriction`)
- Account lifecycle: create, freeze, close, soft-delete
- Deposits, withdrawals, and peer-to-peer transfers with balance tracking
- Deadlock-safe transfers via ordered pessimistic write locks
- Full transaction history per account, ordered by date descending
- Validation on all request bodies

---

## Project Structure

```
src/main/java/com/azizsattarov/corebanking/
├── customer/
│   ├── Customer.java                  # JPA entity with soft delete
│   ├── CustomerController.java        # REST endpoints
│   ├── CustomerService.java           # Interface
│   ├── CustomerServiceImpl.java       # Business logic
│   ├── CustomerRepository.java        # Spring Data JPA
│   └── CustomerStatus.java            # ACTIVE, CLOSED
├── account/
│   ├── Account.java                   # JPA entity with balance & status
│   ├── AccountController.java         # REST endpoints
│   ├── AccountService.java            # Interface
│   ├── AccountServiceImpl.java        # Business logic
│   ├── AccountRepository.java         # Includes pessimistic lock query
│   └── AccountStatus.java             # ACTIVE, FROZEN, CLOSED
└── transaction/
    ├── Transaction.java               # JPA entity linked to Account
    ├── TransactionController.java     # REST endpoints
    ├── TransactionService.java        # Interface
    ├── TransactionServiceImpl.java    # Core financial logic
    ├── TransactionRepository.java     # Custom JPQL history query
    ├── TransactionType.java           # DEPOSIT, WITHDRAW, TRANSFER_IN, TRANSFER_OUT
    └── TransactionStatus.java         # APPROVED, DECLINED
```

---

## Getting Started

### Prerequisites

- Java 17+
- Maven 3.8+ or Gradle 8+
- PostgreSQL

### Configuration

In `src/main/resources/application.properties`:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/corebanking
spring.datasource.username=your_db_user
spring.datasource.password=your_db_password
spring.jpa.hibernate.ddl-auto=update
```

### Run

```bash
mvn spring-boot:run
```

API is available at `http://localhost:8080`

---

## API Reference

### Customers — `/customers`

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/customers` | Create a new customer |
| `GET` | `/customers` | List all active customers |
| `GET` | `/customers/{id}/accounts` | Get all accounts for a customer |
| `PUT` | `/customers/{id}` | Update customer contact info |
| `DELETE` | `/customers/{id}` | Soft-delete customer |

**Create Customer — Request:**
```json
{
  "firstName": "Jane",
  "lastName": "Doe",
  "nationalId": "12345678901",
  "email": "jane@example.com",
  "phoneNumber": "+1234567890",
  "dateOfBirth": "1990-05-15"
}
```

---

### Accounts — `/customers/{customerId}/accounts`

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/{customerId}/accounts` | Create account under a customer |
| `PATCH` | `/{customerId}/accounts/{accountId}/status` | Change account status |
| `DELETE` | `/{customerId}/accounts/{accountId}` | Soft-delete account |

**Account Status values:** `ACTIVE` · `FROZEN` · `CLOSED`

**Create Account — Request:**
```json
{ "initialBalance": 1000.00 }
```

**Response:**
```json
{
  "accountId": 1,
  "accountNumber": "ACC3F2A1B9C8D0",
  "accountStatus": "ACTIVE",
  "balance": 1000.0000,
  "createdAt": "2024-01-15T10:35:00"
}
```

---

### Transactions — `/accounts/{accountId}`

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/{accountId}/deposit` | Deposit funds |
| `POST` | `/{accountId}/withdraw` | Withdraw funds |
| `POST` | `/{fromAccountId}/transfers` | Transfer between accounts |
| `GET` | `/{accountId}/transactions` | Get transaction history |

**Deposit — Request:**
```json
{ "amountDeposit": 500.00 }
```

**Withdraw — Request:**
```json
{ "amountWithdraw": 200.00 }
```

**Transfer — Request:**
```json
{ "toAccountId": 2, "amount": 300.00 }
```

**Transfer — Response:**
```json
{
  "referenceId": "X1Y2Z3A4B5C6",
  "transactionFromId": 3,
  "transactionToId": 4,
  "fromAccountId": 1,
  "toAccountId": 2,
  "amount": 300.0000,
  "balanceFrom": 700.0000,
  "balanceTo": 1300.0000,
  "createdAtFrom": "2024-01-15T11:05:00",
  "createdAtTo": "2024-01-15T11:05:00"
}
```

**Transfer rules:**
- Source and destination accounts must be different
- Both accounts must be `ACTIVE`
- Source account must have sufficient balance
- Both sides share the same `referenceId`

---

## Concurrency & Data Integrity

### Pessimistic Locking

All balance-modifying operations use `SELECT ... FOR UPDATE` via `AccountRepository.findByIdForUpdate()`, preventing lost updates under concurrent requests.

### Deadlock Prevention

Transfer operations acquire locks on both accounts in a deterministic order (lower `accountId` first), ensuring two concurrent transfers between the same accounts always contend for locks in the same sequence.

```java
Long first  = min(accountFromId, toAccountId);
Long second = max(accountFromId, toAccountId);
// Always lock 'first' before 'second'
```

### Soft Deletes

Both `Customer` and `Account` use soft deletion — records are never physically removed. A `deleted_at` timestamp is set, and `@SQLRestriction("deleted_at IS NULL")` automatically filters them out of all queries.

---

## Error Handling

| Status | Cause |
|--------|-------|
| `201 Created` | Resource successfully created |
| `200 OK` | Resource retrieved or updated |
| `204 No Content` | Resource deleted |
| `400 Bad Request` | Negative amount, insufficient funds, same-account transfer, inactive account |
| `404 Not Found` | Customer or Account not found |

---

## Data Model

```
Customer (1) ──── (*) Account (1) ──── (*) Transaction
```

- One customer can have many accounts
- One account can have many transactions
- Transfers create one `TRANSFER_OUT` + one `TRANSFER_IN` record sharing a `referenceId`
- All monetary values stored as `DECIMAL(19,4)` — `BigDecimal` used throughout to avoid floating-point errors

---

## License

MIT
