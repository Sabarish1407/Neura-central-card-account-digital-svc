# Debit Card Digital Banking Microservice (`Neura-central-card-account-digital-svc`)

Production-ready, single-repository digital banking microservice managing core account operations, VISA debit card lifecycle, and transaction authorization with cryptographic data protection.

Built with **Java 21 LTS** and **Spring Boot 3.4.3** (`jakarta.*` enterprise packages).

---

## 🌟 Key Capabilities

- **VISA Debit Card Issuance:** Issue genuine Luhn-valid VISA cards linked to active deposit accounts.
- **Card Security & PCI-DSS Compliance:**
  - Card numbers (PAN) are **masked** (`4111 **** **** 1111`) in all logs, APIs, and client views.
  - Card numbers at rest are encrypted via **AES-256-GCM** using an authenticated encryption key (`ENCRYPTION_MASTER_KEY`).
  - O(1) indexed lookup via deterministic **SHA-256** card number hashing.
  - External operations use non-reversible **Card Tokens** (`tok-...`).
  - Plain text card numbers and CVVs are **never** persisted in the database.
- **Card Activation & PIN Security:**
  - Cards are activated by verifying the initial CVV and configuring a 4-digit PIN.
  - PINs are salted and hashed using **BCrypt** (`BCryptPasswordEncoder`). Plain text PINs are never stored.
- **Controls & Lifecycle:**
  - Freeze and unfreeze debit cards instantly.
  - Configurable daily spending limits and channel controls (ATM, POS, eCommerce, Contactless).
- **Transaction Authorization Engine:**
  - Validates card status (rejects `INACTIVE`, `FROZEN`, `BLOCKED`, `LOST_OR_STOLEN`).
  - Verifies salted BCrypt PIN with brute-force defense (locks card after 3 consecutive failed attempts).
  - Enforces rolling 24-hour daily spend limits.
  - Verifies account funds and settles authorized amounts atomically.
  - Comprehensive transaction audit trail.

---

## 📁 Repository Structure

```text
Neura-central-card-account-digital-svc/
├── .env.example                     # Environment variable blueprint
├── .gitignore                       # Standard Java / Maven ignore rules
├── Dockerfile                       # Multi-stage Java 21 build (EXPOSE 8081, SERVER_PORT=8081)
├── mvnw / mvnw.cmd                  # Maven Wrapper (committed with +x executable bit)
├── pom.xml                          # Single root pom.xml (Spring Boot 3.4.3, Java 21)
├── docs/
│   └── ACCEPTANCE_CRITERIA.md       # Numbered acceptance criteria (AC-01 through AC-12)
├── src/
│   ├── main/
│   │   ├── java/com/nuera/banking/card/
│   │   │   ├── NeuraCentralCardAccountDigitalSvcApplication.java
│   │   │   ├── model/               # DTOs & Domain Enums
│   │   │   │   ├── dto/             # Account, Card & Transaction DTOs
│   │   │   │   └── enums/           # CardStatus, Network, TransactionStatus, DeclineReason
│   │   │   └── service/             # Controllers, Services, Entities, Repositories, Utils
│   │   │       ├── controller/      # CardController, TransactionController, AccountController
│   │   │       ├── entity/          # JPA Entities: AccountEntity, CardEntity, TransactionEntity
│   │   │       ├── exception/       # GlobalExceptionHandler, BankingException
│   │   │       ├── repository/      # Spring Data JPA Repositories
│   │   │       ├── service/         # AccountService, CardService, TransactionService, EncryptionService
│   │   │       └── util/            # CardSecurityUtils (Luhn, BCrypt, AES, Masking)
│   │   └── resources/
│   │       ├── application.yml      # Default PostgreSQL profile (${SERVER_PORT:8080})
│   │       └── application-test.yml # Test profile with in-memory H2 (console disabled)
│   └── test/
│       ├── java/com/nuera/banking/card/
│       │   ├── unit/                # Unit tests (Services, Cryptography, Masking)
│       │   ├── webslice/            # @WebMvcTest slice tests for all controllers
│       │   ├── jpa/                 # @DataJpaTest tests with in-memory H2
│       │   ├── integration/         # @SpringBootTest HTTP integration & Actuator tests
│       │   └── acceptance/          # 1:1 automated tests for AC-01 through AC-12
│       └── resources/
│           └── application-test.yml # Test profile configuration
```

---

## 🚀 API Endpoints

### 1. Actuator Health
- `GET /actuator/health` — Returns system health (`{"status":"UP"}`).

### 2. Accounts API (`/api/v1/accounts`)
- `POST /api/v1/accounts` — Open new account.
- `GET /api/v1/accounts/{accountId}` — Retrieve account details and balance.
- `POST /api/v1/accounts/{accountId}/deposit` — Deposit funds into an account.

### 3. Cards API (`/api/v1/cards`)
- `POST /api/v1/cards/issue` — Issue a new VISA debit card on an active account.
- `POST /api/v1/cards/{cardId}/activate` — Activate card with CVV and set 4-digit PIN.
- `POST /api/v1/cards/{cardId}/freeze` — Freeze card.
- `POST /api/v1/cards/{cardId}/unfreeze` — Unfreeze card.
- `GET /api/v1/cards/{cardId}` — Get card details (masked card number, status, limits).
- `GET /api/v1/cards/account/{accountId}` — List cards for an account.
- `PUT /api/v1/cards/{cardId}/controls` — Update daily limits and channel flags.

### 4. Transactions API (`/api/v1/transactions`)
- `POST /api/v1/transactions/authorize` — Authorize a purchase or withdrawal with PIN.
- `GET /api/v1/transactions/{transactionId}` — Retrieve single transaction audit record.
- `GET /api/v1/transactions/card/{cardId}` — Get transaction history for a card.
- `GET /api/v1/transactions/account/{accountId}` — Get transaction history for an account.

---

## 🧪 Running Tests

The test suite runs 100% self-contained from a clean clone without requiring Docker, external databases, or network connections. In-memory H2 is used strictly under the `test` profile.

To run all unit, web-slice, JPA, integration, and acceptance criteria tests:

```bash
./mvnw -B test
```

On Windows:
```cmd
mvnw.cmd -B test
```

### Acceptance Criteria Mapping
All numbered acceptance criteria documented in `docs/ACCEPTANCE_CRITERIA.md` are verified by `AcceptanceCriteriaTest.java`:
- **AC-01:** VISA debit card issuance with masked card number.
- **AC-02:** Card data security & plain text prohibition (AES-256-GCM + tokenization).
- **AC-03:** Card activation with CVV & salted BCrypt PIN hashing.
- **AC-04:** Card freezing & transaction rejection.
- **AC-05:** Card unfreezing & transaction resumption.
- **AC-06:** Transaction authorization with PIN verification & daily limit.
- **AC-07:** PIN verification & 3-attempt lockout defense.
- **AC-08:** Daily spending limit enforcement.
- **AC-09:** Insufficient funds decline.
- **AC-10:** Inactive or blocked card decline.
- **AC-11:** Transaction history audit retrieval.
- **AC-12:** Health endpoint `/actuator/health` and configurable port `${SERVER_PORT:8080}`.

---

## 🏃 Starting the Service

### Option 1: Local Development (Test Profile with In-Memory H2)
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=test
```
The application will listen on `http://localhost:8080`.
OpenAPI documentation available at `http://localhost:8080/swagger-ui.html`.

### Option 2: Production with PostgreSQL
1. Set up environment variables (copy `.env.example` to `.env`):
   ```bash
   export SERVER_PORT=8080
   export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/bank_db
   export SPRING_DATASOURCE_USERNAME=postgres
   export SPRING_DATASOURCE_PASSWORD=your_password
   export ENCRYPTION_MASTER_KEY=your32ByteSecretKeyForAes256Gcm!
   ```
2. Start the service:
   ```bash
   ./mvnw spring-boot:run
   ```

### Option 3: Docker Multi-Stage Container
The included `Dockerfile` builds a lightweight container listening on port `8081`:
```bash
# Build the image
docker build -t debit-card-service .

# Run container
docker run -p 8081:8081 \
  -e SPRING_PROFILES_ACTIVE=test \
  -e ENCRYPTION_MASTER_KEY=mySuperSecretMasterKey32BytesLong! \
  debit-card-service
```
Check health:
```bash
curl http://localhost:8081/actuator/health
```
