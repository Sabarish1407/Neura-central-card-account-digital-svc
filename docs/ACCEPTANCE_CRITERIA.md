# Acceptance Criteria: Debit Card Digital Banking Service

This document defines the formal numbered acceptance criteria for the debit card digital banking service (`Neura-central-card-account-digital-svc`). Each criterion is mapped to an automated test in `src/test/java/com/nuera/banking/card/acceptance/AcceptanceCriteriaTest.java`.

---

### AC-01: VISA Debit Card Issuance
- **Description:** A customer with an active account can issue a VISA debit card.
- **Verification:**
  - Card network is `VISA` and card type is `DEBIT`.
  - Initial card status is `INACTIVE`.
  - Returned card number is masked (e.g. `4111********1111` or formatted `4111 **** **** 1111`).
  - Card is linked to the specified account ID.

---

### AC-02: Card Data Security & Plain Text Prohibition
- **Description:** Plain text Primary Account Numbers (PAN) and CVV must never be persisted in plain text.
- **Verification:**
  - Card number is stored encrypted at rest using AES-256-GCM.
  - Card token is generated for external references.
  - Card number hash (SHA-256) is stored for O(1) indexed lookup.
  - CVV is hashed and never stored in plain text.

---

### AC-03: Card Activation with CVV and Salted BCrypt PIN
- **Description:** An inactive debit card must be activated using the matching CVV and setting a 4-digit numeric PIN.
- **Verification:**
  - Incorrect CVV rejects activation with an error.
  - Successfully activated card transitions status from `INACTIVE` to `ACTIVE`.
  - The 4-digit PIN is hashed using salted BCrypt (`BCryptPasswordEncoder`) and stored in the database.
  - The plain text PIN is never retained or returned.

---

### AC-04: Card Freezing
- **Description:** A customer can temporarily freeze an active debit card.
- **Verification:**
  - Card status transitions from `ACTIVE` to `FROZEN`.
  - Any transaction authorization attempts while frozen are declined with status `DECLINED` and reason `CARD_FROZEN`.

---

### AC-05: Card Unfreezing
- **Description:** A customer can unfreeze a previously frozen debit card.
- **Verification:**
  - Card status transitions from `FROZEN` back to `ACTIVE`.
  - Transaction authorization succeeds normally after unfreezing.

---

### AC-06: Transaction Authorization with PIN & Daily Limit
- **Description:** Authorize debit card purchase transactions verifying card status, BCrypt PIN match, daily spend limit, and account balance.
- **Verification:**
  - Correct BCrypt PIN allows transaction.
  - Account available balance is deducted immediately.
  - Transaction record is logged as `APPROVED`.
  - Response contains approval code and masked card details.

---

### AC-07: PIN Verification & 3-Attempt Lockout
- **Description:** Provide defense against brute-force attacks by validating PIN with salted BCrypt and locking the card after 3 consecutive failures.
- **Verification:**
  - Invalid PIN results in `DECLINED` with reason `INVALID_PIN`.
  - Failed attempt count increments on each wrong attempt.
  - After 3 consecutive wrong attempts, card status is transitioned to `BLOCKED` with reason `PIN_TRIES_EXCEEDED`.

---

### AC-08: Daily Limit Enforcement
- **Description:** Decline any transaction that causes total spending in the rolling 24-hour window to exceed the configured daily limit.
- **Verification:**
  - Transactions within daily limit succeed.
  - A transaction exceeding the daily limit is declined with `EXCEEDS_DAILY_LIMIT`.
  - Account balance is unaffected.

---

### AC-09: Insufficient Funds Decline
- **Description:** Decline transactions when the linked account balance is lower than the authorization amount.
- **Verification:**
  - Returns `DECLINED` with reason `INSUFFICIENT_FUNDS`.
  - Account balance remains unchanged.
  - Transaction is recorded with status `DECLINED`.

---

### AC-10: Inactive or Blocked Card Decline
- **Description:** Transactions attempted on cards that are `INACTIVE`, `FROZEN`, `BLOCKED`, or `LOST_OR_STOLEN` must be rejected immediately without deducting funds.
- **Verification:**
  - Authorization returns `DECLINED`.
  - Decline reason reflects card status (`CARD_INACTIVE`, `CARD_FROZEN`, `CARD_BLOCKED`).

---

### AC-11: Transaction History Retrieval
- **Description:** Cardholders can retrieve their historical transactions filtered by card ID or account ID.
- **Verification:**
  - Endpoint returns list of transactions with type, amount, status, timestamp, merchant, and masked card number.
  - History preserves chronological order.

---

### AC-12: Health Endpoint & Configurable Port
- **Description:** The microservice exposes a standard health endpoint on `/actuator/health` and listens on `${SERVER_PORT:8080}`.
- **Verification:**
  - HTTP `GET /actuator/health` returns status `200 OK` with `{"status":"UP"}`.
  - The server port can be overridden via `SERVER_PORT` environment variable.
