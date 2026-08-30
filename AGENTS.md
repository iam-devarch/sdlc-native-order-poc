# order-service

## Commands

- `mvn -q verify` — build. Healthy output ends with BUILD SUCCESS.
- `mvn -q test` — unit and integration tests. All green, no skips.
- `mvn -q spotless:check` — lint. Zero violations.

## Conventions

- Java 23, Spring Boot 3.3. No new Lombok.
- Money is always BigDecimal. Never double, never float.
- Every endpoint has an integration test under src/test/java/.../api.
- Package layout: api (REST), core (domain), adapters (external).

## Architecture

- Flyway owns the schema. Never edit an applied migration; add a new one.
- Generated classes under target/ are never edited.

## Verifying your work

Run all three commands before reporting any task complete, and paste the output.
If a test fails, fix the code, not the test.

## Things the agent gets wrong

- Do not bump dependency versions. The platform team owns them.
- Do not widen an endpoint's response to carry fields the spec did not ask for.