# Review policy — order-service

## Passes

Run three passes and tag every finding with its pass.

1. **Bugs** — logic errors, broken edge cases, subtle regressions.
2. **Security** — injection risk, authentication gaps, PII in logs, unvalidated input.
3. **Compliance** — does the change match spec.md, plan.md, and the money-handling and
   secure-api-review skills.

## Severity

Reserve **Important** for findings that would break behaviour, leak data, or breach a policy.
Style and naming are **Nits**.

## Nit cap

Report at most five nits. Summarise the rest as a count.

## Do not report

- Anything under target/
- Anything spotless or the build already enforces