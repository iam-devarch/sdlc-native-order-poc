---
title: Ops-initiated order cancellation via API
author: Operations (hi.riyazkhan@gmail.com)
date: 2026-08-30
status: draft
---

# Intent: Cancel Order

## Problem
Operations agents cancel orders by hand in the admin console, requiring manual data entry
across multiple screens. Each cancellation takes roughly four minutes; the team handles
around forty per day, consuming approximately 2.7 hours of agent time daily on a single
mechanical task. When payment has already been captured the flow diverges into a separate
refund process, which this problem does not address.

## Proposed outcome
A senior ops agent or supervisor can cancel an eligible order (payment not yet captured)
through a single action, in under thirty seconds. The cancellation is recorded with the
agent's identity, a reason code, and a timestamp. The order moves to a terminal cancelled
state and cannot be modified further.

## Affected users and systems
- Senior ops agents and supervisors (the only roles permitted to cancel)
- Admin console (current point of entry; will call the new capability)
- Order service (owns order state transitions)
- Payment service (consulted to confirm capture status before allowing cancellation)

## Constraints
- Only orders where payment has **not** been captured may be cancelled via this flow;
  captured-payment cases must go through the refund process (out of scope).
- Only users with the senior-ops or supervisor role may invoke cancellation.
- Every cancellation must persist: reason code, agent ID, and UTC timestamp — required
  for audit and compliance.
- Customer notification is out of scope; the agent notifies the customer on the call.
- Self-serve cancellation (customer-initiated) is out of scope; design must not block it
  being added later.

## Open questions
- What is the authoritative source for role assignment — is `senior-ops`/`supervisor`
  a claim in the existing auth token, or must the order service look it up?
- Is there a defined set of valid reason codes, or does ops need to propose them?
- Should a cancellation be reversible (e.g. by a supervisor) or permanently terminal?
- Does the payment service expose a synchronous "is payment captured?" check, or must
  the order service own that state?
