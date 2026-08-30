# Implementation Plan: Order Cancellation Before Capture

## Context

Ops agents cancel ~40 orders/day by hand in the admin console (~4 min each). This plan
delivers `POST /orders/{id}/cancel` so a senior-ops agent or supervisor can cancel a
pre-capture order in a single call, with a full audit trail. The spec is at `spec.md`;
the intent is at `intent/2026-08-30-cancel-before-capture.md`.

**Two open questions must be resolved before Step 5 (security) can be coded:**
- **Q1 (blocking):** Are `senior-ops`/`supervisor` JWT claims or external lookups?
  → Confirm with identity platform team.
- **Q2 (blocking):** What is the agreed reason-code list?
  → Confirm with ops. Proposed default: CUSTOMER_REQUEST, DUPLICATE_ORDER,
  FRAUD_SUSPECTED, DATA_ERROR, OTHER.
- **Q3:** Is cancellation permanently terminal or reversible by a supervisor?
- **Q4:** How does the payment service signal capture to the order-service?
  (event vs. sync call vs. push) — affects reliability of the capture guard.

---

## Step 1 — Schema Migration

**Why first:** Flyway runs on startup; all subsequent test boots require it.

| File | Change |
|------|--------|
| `src/main/resources/db/migration/V2__add_cancellation_fields.sql` | **new** |

```sql
ALTER TABLE orders ADD COLUMN cancelled_at        TIMESTAMP;
ALTER TABLE orders ADD COLUMN cancelled_by        VARCHAR(255);
ALTER TABLE orders ADD COLUMN cancellation_reason VARCHAR(100);
```

**Test:** Any test that boots the Spring context (e.g. existing `OrderControllerIT`)
confirms Flyway applies V2 cleanly and `validate` passes.

---

## Step 2 — Domain Model

No infrastructure dependencies; fully unit-testable.

| File | Change |
|------|--------|
| `src/main/java/com/globalbank/orderservice/core/domain/OrderStatus.java` | Add `CANCELLED` |
| `src/main/java/com/globalbank/orderservice/core/domain/PaymentStatus.java` | Add `CAPTURED` |
| `src/main/java/com/globalbank/orderservice/core/domain/CancellationReasonCode.java` | **new enum** — CUSTOMER_REQUEST, DUPLICATE_ORDER, FRAUD_SUSPECTED, DATA_ERROR, OTHER |
| `src/main/java/com/globalbank/orderservice/core/domain/Order.java` | Add `cancelledAt` (Instant), `cancelledBy` (String), `cancellationReason` (String); add `cancel(agentId, reasonCode)` method |

`Order.cancel()` enforces the state machine inline — throws `OrderNotCancellableException`
if already `CANCELLED`, then sets all three fields and transitions status. `cancelledAt`
is always `Instant.now()` inside the domain; callers cannot supply it.

**Test — new file:**
`src/test/java/com/globalbank/orderservice/core/domain/OrderTest.java`

| Test case | Asserts |
|-----------|---------|
| `cancel_transitionsCreatedToCancelled` | status = CANCELLED, all three fields populated |
| `cancel_setsTimestampInternally` | `cancelledAt` is non-null; caller-supplied value is not accepted |
| `cancel_onAlreadyCancelledOrder_throws` | `OrderNotCancellableException` thrown |

---

## Step 3 — Exceptions, Audit Interface, Repository

Small, no tests of their own — exercised through service tests in Step 4.

| File | Change |
|------|--------|
| `src/main/java/com/globalbank/orderservice/core/domain/OrderNotFoundException.java` | **new** — RuntimeException, carries order id |
| `src/main/java/com/globalbank/orderservice/core/domain/OrderNotCancellableException.java` | **new** — RuntimeException, carries order id |
| `src/main/java/com/globalbank/orderservice/core/domain/PaymentAlreadyCapturedException.java` | **new** — RuntimeException, carries order id |
| `src/main/java/com/globalbank/orderservice/core/domain/AuditEvent.java` | **new** — record: actor, action, entity, timestamp |
| `src/main/java/com/globalbank/orderservice/core/service/AuditEventPublisher.java` | **new** — single-method interface: `publish(AuditEvent)` |
| `src/main/java/com/globalbank/orderservice/adapters/audit/LoggingAuditEventPublisher.java` | **new** — structured-log implementation of the interface; default bean |
| `src/main/java/com/globalbank/orderservice/adapters/persistence/PaymentRepository.java` | Add `boolean existsByOrderIdAndStatus(Long orderId, PaymentStatus status)` |

---

## Step 4 — Service Layer

| File | Change |
|------|--------|
| `src/main/java/com/globalbank/orderservice/core/service/OrderService.java` | Add `cancel(Long orderId, String agentId, CancellationReasonCode reasonCode)` |

Logic: load order (→ 404), check captured payment (→ 409), call `order.cancel()`, save,
publish `AuditEvent` (actor=agentId, action="ORDER_CANCELLED", entity="order:{id}",
timestamp=Instant.now()).

**Test — new file:**
`src/test/java/com/globalbank/orderservice/core/service/OrderServiceTest.java`

All dependencies mocked (Mockito). No Spring context.

| Test case | Asserts |
|-----------|---------|
| `cancel_happyPath` | Order saved as CANCELLED; audit event published with correct fields |
| `cancel_orderNotFound_throws` | `OrderNotFoundException` |
| `cancel_paymentCaptured_throws` | `PaymentAlreadyCapturedException`; order NOT saved; no audit event |
| `cancel_alreadyCancelled_throws` | `OrderNotCancellableException` propagated from domain |
| `cancel_auditEvent_hasCorrectFields` | actor = agentId, action = "ORDER_CANCELLED", entity = "order:42" |

---

## Step 5 — Security Infrastructure

**Resolve Q1 before this step.** Adding Spring Security makes all existing endpoints
auth-required — the existing `OrderControllerIT` will fail until the test harness is
updated here.

| File | Change |
|------|--------|
| `pom.xml` | Add `spring-boot-starter-security`, `spring-boot-starter-oauth2-resource-server` |
| `src/main/java/com/globalbank/orderservice/config/SecurityConfig.java` | **new** — `SecurityFilterChain`: all routes require JWT except `GET /actuator/health`; enable `@EnableMethodSecurity` |
| `src/main/resources/application.yml` | Add `spring.security.oauth2.resourceserver.jwt.issuer-uri` (value from identity team) |
| `src/test/java/com/globalbank/orderservice/config/TestSecurityConfig.java` | **new** — test-only bean that disables auth or provides a mock JWT decoder so existing tests continue to compile and pass |
| `src/test/java/com/globalbank/orderservice/api/OrderControllerIT.java` | **modify** — add `Authorization: Bearer <test-jwt>` to existing `postThenGetRoundTrips` request |

Role enforcement is on the controller method via
`@PreAuthorize("hasAnyRole('senior-ops','supervisor')")`, not in `SecurityFilterChain`,
so other endpoints are unaffected by role rules.

---

## Step 6 — API Layer

| File | Change |
|------|--------|
| `src/main/java/com/globalbank/orderservice/api/CancelOrderRequest.java` | **new** — record: `CancellationReasonCode reasonCode`; Jackson configured to reject unknown fields |
| `src/main/java/com/globalbank/orderservice/api/OrderResponse.java` | Add `cancelledAt`, `cancelledBy`, `cancellationReason` fields; annotate `amount` with `@JsonSerialize(using = ToStringSerializer.class)` |
| `src/main/java/com/globalbank/orderservice/api/OrderController.java` | Add `POST /{id}/cancel` — extracts `agentId` from `JwtAuthenticationToken.getName()`; delegates to `OrderService.cancel()` |
| `src/main/java/com/globalbank/orderservice/api/GlobalExceptionHandler.java` | **new** — `@RestControllerAdvice`; maps each domain exception to its HTTP status and error-code envelope; `customerId` never included in any error body |

**Test — new file:**
`src/test/java/com/globalbank/orderservice/api/CancelOrderControllerIT.java`

Full Spring Boot integration test (`@SpringBootTest`, `TestRestTemplate`).

| Test case | HTTP | Asserts |
|-----------|------|---------|
| `cancel_happyPath` | 200 | Body has status=CANCELLED, cancelledBy=agent-from-jwt, cancellationReason set, amount is a JSON string |
| `cancel_noJwt` | 401 | No order mutation |
| `cancel_wrongRole` | 403 | No order mutation |
| `cancel_invalidReasonCode` | 400 | Error code INVALID_REASON_CODE |
| `cancel_unknownField` | 400 | Error code UNKNOWN_FIELDS |
| `cancel_orderNotFound` | 404 | Error code ORDER_NOT_FOUND; no customerId in body |
| `cancel_paymentCaptured` | 409 | Error code PAYMENT_ALREADY_CAPTURED |
| `cancel_alreadyCancelled` | 409 | Error code ORDER_NOT_CANCELLABLE |
| `cancel_doubleCancelReturnsCurrentState` | 409 | Response body contains CANCELLED order for console rendering |
| `agentId_comesFromJwtNotBody` | 200 | `cancelledBy` matches JWT sub, not any value in request |

---

## Execution Order Summary

```
Step 1  Schema migration           — unblocked, do immediately
Step 2  Domain model + unit tests  — unblocked, do immediately
Step 3  Exceptions, audit, repo    — unblocked, do immediately
Step 4  Service + unit tests       — after Steps 2 & 3
Step 5  Security + fix existing IT — BLOCKED on Q1; do after Q1 resolved
Step 6  API layer + IT             — after Steps 4 & 5
```

Steps 1–4 can be built and unit-tested before Q1 is resolved. The app cannot be
integration-tested end-to-end until Step 5 is complete.

---

## Files Changed — Full Inventory

| File | new / modify |
|------|-------------|
| `src/main/resources/db/migration/V2__add_cancellation_fields.sql` | new |
| `src/main/java/.../core/domain/OrderStatus.java` | modify |
| `src/main/java/.../core/domain/PaymentStatus.java` | modify |
| `src/main/java/.../core/domain/CancellationReasonCode.java` | new |
| `src/main/java/.../core/domain/Order.java` | modify |
| `src/main/java/.../core/domain/OrderNotFoundException.java` | new |
| `src/main/java/.../core/domain/OrderNotCancellableException.java` | new |
| `src/main/java/.../core/domain/PaymentAlreadyCapturedException.java` | new |
| `src/main/java/.../core/domain/AuditEvent.java` | new |
| `src/main/java/.../core/service/AuditEventPublisher.java` | new |
| `src/main/java/.../adapters/audit/LoggingAuditEventPublisher.java` | new |
| `src/main/java/.../adapters/persistence/PaymentRepository.java` | modify |
| `src/main/java/.../core/service/OrderService.java` | modify |
| `pom.xml` | modify |
| `src/main/java/.../config/SecurityConfig.java` | new |
| `src/main/resources/application.yml` | modify |
| `src/main/java/.../api/CancelOrderRequest.java` | new |
| `src/main/java/.../api/OrderResponse.java` | modify |
| `src/main/java/.../api/OrderController.java` | modify |
| `src/main/java/.../api/GlobalExceptionHandler.java` | new |
| `src/test/java/.../config/TestSecurityConfig.java` | new |
| `src/test/java/.../core/domain/OrderTest.java` | new |
| `src/test/java/.../core/service/OrderServiceTest.java` | new |
| `src/test/java/.../api/OrderControllerIT.java` | modify |
| `src/test/java/.../api/CancelOrderControllerIT.java` | new |

(Package prefix: `com/globalbank/orderservice`)

---

## Verification

1. `mvn test` — all unit tests green (Steps 2–4 independently verifiable before security)
2. `mvn verify` — all integration tests green end-to-end (requires Step 5 complete)
3. Manual smoke: `POST /orders/{id}/cancel` with a valid senior-ops JWT returns 200;
   `GET /orders/{id}` returns status=CANCELLED with audit fields populated.
4. Negative: same request with no JWT → 401; wrong role → 403; captured payment → 409.
