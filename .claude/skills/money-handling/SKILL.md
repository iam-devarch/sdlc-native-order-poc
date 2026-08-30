---
name: money-handling
description: Use whenever monetary amounts are declared, compared, summed, persisted or serialised.
---

1. Amounts are BigDecimal in Java and NUMERIC(19,4) in the schema. Never double or float.
2. Compare with compareTo, never equals — equals is scale-sensitive.
3. Set scale explicitly with RoundingMode.HALF_EVEN before persisting or returning.
4. Currency travels with the amount. Never assume a default.
5. Serialise as a JSON string, not a number.

State in your summary where each rule was applied.