# Spec: Order Cancellation Before Capture

**Source intent:** `intent/2026-08-30-cancel-before-capture.md`
**Date:** 2026-08-30
**Status:** Draft — pending resolution of open questions in §8

---

## 1. Context

The order-service is a Spring Boot 3.3.4 / Java 23 application using Spring Data JPA,
Flyway, and an H2 in-memory database. It currently exposes two endpoints:

| Method | Path          | Purpose           |
|--------|---------------|-------------------|
| POST   | `/orders`     | Create an order   |
| GET    | `/orders/{id}`| Retrieve an order |

There is no authentication, authorisation, or security infrastructure in the codebase
today. The domain has a single `OrderStatus` value (`CREATED`) and a single
`PaymentStatus` value (`PENDING`). See §7 for the consequences.

---

## 2. New Endpoint

```
POST /orders/{id}/cancel
```

`DELETE` is not used because cancellation is a business state transition, not a resource
deletion. A POST to a sub-resource action URL is the correct REST idiom here.

### 2.1 Request

**Path parameter**

| Parameter | Type | Required | Description     |
|-----------|------|----------|-----------------|
| `id`      | Long | Yes      | Order identifier |

**Headers**

| Header          | Required | Description                              |
|-----------------|----------|------------------------------------------|
| `Authorization` | Yes      | `Bearer <gateway-JWT>` — see §3          |

**Body** — `application/json`

```json
{
  "reasonCode": "CUSTOMER_REQUEST"
}
```

| Field        | Type   | Required | Validation                              |
|--------------|--------|----------|-----------------------------------------|
| `reasonCode` | String | Yes      | Must match a value in `CancellationReasonCode` enum (§2.4). Unknown fields are rejected (HTTP 400). |

`agentId` is **not** accepted in the request body. It is extracted from the JWT `sub`
claim server-side, so it cannot be spoofed by the caller.
*(secure-api-review rule 1 — actor identity from authenticated token, not caller input)*

### 2.2 Responses

| Status | Condition                                              |
|--------|--------------------------------------------------------|
| 200    | Order successfully cancelled; body is `OrderResponse` (see §2.3) |
| 400    | Missing or invalid `reasonCode`; unknown request fields |
| 401    | Missing or invalid JWT                                 |
| 403    | Authenticated principal lacks `senior-ops` or `supervisor` role |
| 404    | Order not found                                        |
| 409    | Order is already `CANCELLED`, or payment is `CAPTURED` |

Error responses use the following envelope. `customerId` is **never** included — it is
PII and must not appear in error messages or logs.
*(secure-api-review rule 4 — PII data classification)*

```json
{
  "status": 409,
  "code": "ORDER_ALREADY_CANCELLED",
  "message": "Order 42 is already cancelled."
}
```

Defined error codes:

| `code`                    | HTTP | Meaning                               |
|---------------------------|------|---------------------------------------|
| `INVALID_REASON_CODE`     | 400  | `reasonCode` not in allowed set       |
| `UNKNOWN_FIELDS`          | 400  | Request body contains unknown fields  |
| `ORDER_NOT_FOUND`         | 404  | No order with that id                 |
| `ORDER_NOT_CANCELLABLE`   | 409  | Order is already `CANCELLED`          |
| `PAYMENT_ALREADY_CAPTURED`| 409  | Payment captured; use refund flow     |

### 2.3 Success Response Body

Reuses the existing `OrderResponse` record, extended with the new cancellation fields:

```json
{
  "id": 42,
  "customerId": "CUST-001",
  "amount": "250.0000",
  "status": "CANCELLED",
  "cancelledAt": "2026-08-30T09:14:00Z",
  "cancelledBy": "agent-id-from-jwt",
  "cancellationReason": "CUSTOMER_REQUEST"
}
```

Note: `amount` serialises as a **JSON string**, not a number.
*(money-handling rule 5 — serialise as string)*

### 2.4 Reason Code Enum

The following codes are proposed. **This list is an open question** (see §8.2) and must
be confirmed with ops before implementation.

| Code               | Meaning                               |
|--------------------|---------------------------------------|
| `CUSTOMER_REQUEST` | Customer called to cancel             |
| `DUPLICATE_ORDER`  | Order was placed more than once       |
| `FRAUD_SUSPECTED`  | Agent flagged potential fraud         |
| `DATA_ERROR`       | Order contains incorrect data         |
| `OTHER`            | None of the above; ops to document    |

---

## 3. Authentication and Authorisation

*(secure-api-review rule 1)*

The codebase currently has **no security infrastructure**. Implementing this endpoint
requires the following additions to `pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>
```

A `SecurityConfig` class must be added:

- All routes except `GET /actuator/health` require a valid gateway JWT.
- The `/orders/{id}/cancel` endpoint additionally requires the authenticated principal
  to hold the role `senior-ops` **or** `supervisor`.
- Role enforcement via `@PreAuthorize("hasAnyRole('senior-ops','supervisor')")` on the
  controller method, with `@EnableMethodSecurity` in config.
- Agent ID is extracted from `token.getName()` (the `sub` claim).

**Open question §8.1** — where roles are carried in the JWT must be confirmed before
this can be wired up.

---

## 4. Domain Changes

### 4.1 `OrderStatus`

Add `CANCELLED` as a terminal state. No transition out of `CANCELLED` is permitted.

```java
public enum OrderStatus {
    CREATED,
    CANCELLED
}
```

### 4.2 `PaymentStatus`

Add `CAPTURED`. This is needed so the service can check whether payment has been taken
before allowing cancellation.

```java
public enum PaymentStatus {
    PENDING,
    CAPTURED
}
```

> **Concern (§7.3):** `PaymentStatus` is set by the payment service. If the
> order-service's `payments` table is a local stub and not kept in sync with the real
> payment service's state, this check will not be reliable. See §7.3.

### 4.3 `Order` entity

Add three nullable columns (nullable because existing rows have no cancellation data):

| Field                | Java type | DB column              | Nullable |
|----------------------|-----------|------------------------|----------|
| `cancelledAt`        | `Instant` | `cancelled_at TIMESTAMP` | Yes    |
| `cancelledBy`        | `String`  | `cancelled_by VARCHAR(255)` | Yes |
| `cancellationReason` | `String`  | `cancellation_reason VARCHAR(100)` | Yes |

Add a domain method that enforces the state machine:

```java
public void cancel(String agentId, String reasonCode) {
    if (this.status == OrderStatus.CANCELLED) {
        throw new OrderNotCancellableException(this.id);
    }
    this.status = OrderStatus.CANCELLED;
    this.cancelledAt = Instant.now();
    this.cancelledBy = agentId;
    this.cancellationReason = reasonCode;
}
```

`cancelledAt` is always set to `Instant.now()` inside the domain — callers cannot supply
the timestamp.

### 4.4 `CancellationReasonCode` enum

New enum in `com.globalbank.orderservice.core.domain`:

```java
public enum CancellationReasonCode {
    CUSTOMER_REQUEST,
    DUPLICATE_ORDER,
    FRAUD_SUSPECTED,
    DATA_ERROR,
    OTHER
}
```

---

## 5. Service Layer

### 5.1 `OrderService.cancel`

```java
@Transactional
public Order cancel(Long orderId, String agentId, CancellationReasonCode reasonCode) {
    Order order = orderRepository.findById(orderId)
        .orElseThrow(() -> new OrderNotFoundException(orderId));

    boolean captured = paymentRepository.existsByOrderIdAndStatus(orderId, PaymentStatus.CAPTURED);
    if (captured) {
        throw new PaymentAlreadyCapturedException(orderId);
    }

    order.cancel(agentId, reasonCode.name());
    Order saved = orderRepository.save(order);

    auditEventPublisher.publish(AuditEvent.of(
        agentId,
        "ORDER_CANCELLED",
        "order:" + orderId,
        Instant.now()
    ));

    return saved;
}
```

*(secure-api-review rule 3 — audit event carries actor, action, entity, timestamp)*

### 5.2 New repository method

```java
// PaymentRepository
boolean existsByOrderIdAndStatus(Long orderId, PaymentStatus status);
```

---

## 6. New and Changed Files

| File | Change |
|------|--------|
| `pom.xml` | Add `spring-boot-starter-security`, `spring-boot-starter-oauth2-resource-server` |
| `SecurityConfig.java` *(new)* | JWT resource server config, role-based access |
| `OrderStatus.java` | Add `CANCELLED` |
| `PaymentStatus.java` | Add `CAPTURED` |
| `Order.java` | Add cancellation fields and `cancel()` method |
| `CancellationReasonCode.java` *(new)* | Reason code enum |
| `CancelOrderRequest.java` *(new)* | Request DTO — `reasonCode` only |
| `OrderResponse.java` | Add `cancelledAt`, `cancelledBy`, `cancellationReason`; fix amount serialisation to string |
| `OrderController.java` | Add `POST /{id}/cancel` handler |
| `OrderService.java` | Add `cancel()` method |
| `PaymentRepository.java` | Add `existsByOrderIdAndStatus()` |
| `AuditEventPublisher.java` *(new)* | Publishes audit events (interface + implementation TBD) |
| `OrderNotFoundException.java` *(new)* | 404 exception |
| `OrderNotCancellableException.java` *(new)* | 409 exception |
| `PaymentAlreadyCapturedException.java` *(new)* | 409 exception |
| `V2__add_cancellation_fields.sql` *(new)* | Schema migration — see below |

### 6.1 `V2__add_cancellation_fields.sql`

```sql
ALTER TABLE orders ADD COLUMN cancelled_at        TIMESTAMP;
ALTER TABLE orders ADD COLUMN cancelled_by        VARCHAR(255);
ALTER TABLE orders ADD COLUMN cancellation_reason VARCHAR(100);
```

---

## 7. Areas of Concern — Policy Conflicts and Risks

### 7.1 No security infrastructure (BLOCKING)

**Conflict:** The secure-api-review policy requires JWT authentication on every
state-changing endpoint. The codebase has no Spring Security dependency, no
`SecurityFilterChain`, no JWT decoder, and no role extraction.

**Impact:** This feature cannot be shipped without adding Spring Security and the OAuth2
resource server stack. This is a significant foundational change that affects all existing
endpoints (they will all become auth-required). Engineering must plan for this before
writing any cancellation code; the existing integration test (`OrderControllerIT`) will
fail once security is enabled unless a test security config is added.

**Resolution required:** Add security infrastructure as a prerequisite ticket.

---

### 7.2 Role source unknown (BLOCKING)

**Conflict:** The intent flags this as an open question. The authorisation model requires
roles `senior-ops` and `supervisor`, but it is not known whether these are JWT claims or
must be looked up from an external system.

**Impact:** If roles are not in the JWT, every cancellation request requires a network
call to an identity or authorisation service — adding latency and a new failure mode.
If roles are in the JWT but under a non-standard claim name (e.g. `groups`, `realm_access`,
or a custom claim), the JWT decoder configuration changes.

**Recommendation:** Roles should be carried as JWT claims to avoid a runtime dependency.
Confirm with the identity platform team.

**Resolution required before implementation.**

---

### 7.3 Payment capture check is unreliable (HIGH RISK)

**Conflict:** The intent states that the order-service must consult the payment service to
confirm capture status. The codebase has a local `payments` table, but there is no
mechanism to keep it in sync with an external payment service. The `PaymentStatus` enum
has only `PENDING`; `CAPTURED` does not exist.

**Impact:** If the local `payments` table is a stub and the real capture event is not
written here, the guard `existsByOrderIdAndStatus(orderId, CAPTURED)` will always return
`false`, making it ineffective. An ops agent could cancel an order whose payment has
already been captured, creating an inconsistent state.

**Options — pick one:**
1. The payment service publishes a `PaymentCaptured` domain event; the order-service
   subscribes and updates its local `PaymentStatus` to `CAPTURED`. (Eventual consistency;
   small race window.)
2. The order-service makes a synchronous HTTP call to the payment service at cancellation
   time. (Stronger guarantee; adds network dependency and latency.)
3. The payment service calls the order-service to mark capture before the order-service
   processes any further transitions. (Push model; payment service drives state.)

**Resolution required:** The payment integration architecture must be decided before the
cancellation guard can be trusted. This spec assumes option 1 or 2 — engineering must
confirm.

---

### 7.4 Money-handling schema conflicts (MEDIUM — pre-existing)

The following violations of the money-handling policy exist in the current codebase and
are not introduced by this feature, but must be tracked:

| Violation | Rule | Location | Risk |
|-----------|------|----------|------|
| `DECIMAL(19, 2)` in DB | Rule 1 requires `NUMERIC(19,4)` | `V1__init.sql` | Rounding loss at 3–4 decimal places |
| No `currency` column | Rule 4 — currency travels with amount | `orders`, `payments` tables | Unsafe when multi-currency is introduced |
| `amount` serialised as JSON number | Rule 5 — must be string | `OrderResponse.java` | Floating-point parsing by consumers |
| `Order.amount` scale 2 in JPA | Rule 1 — use scale 4 | `Order.java` | Inconsistency with any future 4dp fix |

**The cancellation endpoint does not add any monetary amounts**, so these violations do
not worsen. However, the `OrderResponse` serialisation fix (amount → string) is a
**breaking API change** for existing consumers of `POST /orders` and `GET /orders/{id}`.
Engineering should coordinate with consumers before making that change.

Fixing the schema precision (`DECIMAL` → `NUMERIC(19,4)`) requires a Flyway migration
on the existing `orders` and `payments` tables — safe if no production data exists, but
a migration-with-conversion if it does.

---

### 7.5 PII logging (MEDIUM — pre-existing, worsened by this feature)

The secure-api-review policy requires that fields tagged PII never appear in logs or
error messages. `customerId` on `Order` is PII. The current codebase has `show-sql: true`
in `application.yml`, which will log full SQL including `customer_id` values. This must
be disabled in any non-development environment. Additionally, any structured logging added
for the cancellation flow must explicitly exclude `customerId`.

---

### 7.6 Idempotency

Cancelling an already-cancelled order returns `409 ORDER_NOT_CANCELLABLE`. This is
intentional and prevents double-processing if ops clicks cancel twice. The response body
should still return the current order state so the console can render it correctly.

---

## 8. Open Questions (must be resolved before implementation)

| # | Question | Who owns it | Impact if unresolved |
|---|----------|-------------|----------------------|
| 8.1 | Are `senior-ops`/`supervisor` claims in the JWT, or must they be looked up? | Identity platform team | Blocks security wiring |
| 8.2 | What is the agreed list of valid reason codes? | Ops team | Blocks `CancellationReasonCode` enum |
| 8.3 | Is cancellation permanently terminal, or can a supervisor reverse it? | Operations / product | Changes state machine and audit requirements |
| 8.4 | How does the payment service signal capture to the order-service? | Payment service team | Blocks reliable capture guard (§7.3) |

---

## 9. Skill Compliance Summary

| Skill | Rule | Where applied |
|-------|------|---------------|
| secure-api-review | Rule 1 — JWT required | §3 — all routes require gateway JWT; `agentId` from `sub` claim, not request body |
| secure-api-review | Rule 2 — input validation | §2.1 — `reasonCode` validated against enum; unknown fields rejected (HTTP 400) |
| secure-api-review | Rule 3 — audit event | §5.1 — `AuditEventPublisher` emits actor, action, entity, timestamp on every cancellation |
| secure-api-review | Rule 4 — PII classification | §2.2, §7.5 — `customerId` excluded from error bodies and logs; `show-sql` flagged |
| money-handling | Rule 1 — BigDecimal / NUMERIC | §4.3 — no new monetary fields introduced; existing violations flagged in §7.4 |
| money-handling | Rule 2 — compareTo | Not applicable — no monetary comparison in cancellation flow |
| money-handling | Rule 3 — scale + HALF_EVEN | Not applicable — no monetary arithmetic in cancellation flow |
| money-handling | Rule 4 — currency travels with amount | §7.4 — existing violation flagged; not worsened by this feature |
| money-handling | Rule 5 — serialise as string | §2.3 — `OrderResponse.amount` must be serialised as string; noted as breaking change in §7.4 |
