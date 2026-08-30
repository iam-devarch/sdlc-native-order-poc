---
name: secure-api-review
description: Use when creating or modifying an external-facing endpoint, reviewing API code, or generating an OpenAPI spec.
---

When you create or change an endpoint:

1. Authentication — every endpoint requires the gateway JWT. No anonymous routes except
   /actuator/health.
2. Input validation — validate request bodies against the OpenAPI schema. Reject unknown fields.
3. Audit — every state-changing endpoint emits an audit event carrying actor, action, entity
   and timestamp.
4. Data classification — fields tagged PII in the schema never appear in logs or error messages.

State in your summary which of these four you applied and where.