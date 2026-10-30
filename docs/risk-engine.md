# GuardPay - Deterministic Risk Engine (design + implemented behavior)

> The engine is implemented in `com.guardpay.risk`; this document keeps the
> original design and notes where the built behavior matches or extends it.

## 1. Purpose

Combine transaction, recipient/merchant, social-engineering, content, URL, and
call-state signals into a single reproducible **GuardPay Risk Score (0–100)**
and a final recommendation: **ALLOW / VERIFY / STOP**.

The engine lives in Spring Boot (`com.guardpay.risk`) and runs **with or
without** AI findings. The LLM never scores or decides directly.

## 2. Inputs (planned)

- Current payment context: amount, currency, recipient, merchant, invoice/message text, URL, `activeCall` boolean.
- User behavioral profile derived from PostgreSQL history: average/median/max payment, typical range, known recipients, frequent merchants, transaction frequency, subscription baselines.
- AI structured findings (when available): `transaction_findings`, `social_engineering_findings`, `content_findings`, `scam_pattern`, `explanation`, per-agent severities. Each finding carries `finding`, `detected`, `severity`, `evidence`.
- URL-reputation result: `safe | suspicious | unsafe | unknown` (Google Safe Browsing or equivalent; `unknown` on provider failure — never default to safe).

## 3. Signal catalog + prototype weights (planned heuristics)

| # | Signal | Planned weight | Source |
|---|--------|----------------|--------|
| 1 | Active phone call during payment | +15 | call-state flag |
| 2 | New recipient (no prior history) | +20 | recipients table / profile |
| 3 | Unusually large amount vs profile | +20 | transaction agent + profile comparison |
| 4 | Unknown / unfamiliar merchant | +10 | merchants table / profile |
| 5 | Unexpected invoice / payment request | +15 | content agent + invoice metadata |
| 6 | Urgent / threatening language | +10 | social-engineering + content agents |
| 7 | Suspicious / unsafe URL | +10 | URL-reputation + content agent |
| 8 | Known scam pattern (e.g. impersonation / payment-request scam) | +20 | social-engineering agent classification |
| 9 | Amount anomaly versus this recipient's history | +20 | recipient history comparison |

Weights are prototype heuristics, **not** statistical probabilities. Tuning them
is a later step; the init rule is: weights are constants in one place, changeable
without touching agents or UI.

### 3.1 Recipient intelligence (implemented)

Recipient intelligence is derived from the current GuardPay account's own
transaction history (the existing user ownership model; a single demo account in
the current configuration); it is never shared across accounts and adds no new
storage or gateway.

- Recipient identity is the persisted `recipients` row scoped to the owning
  `user_id`. Display name, email text, and PayPal account strings are never used
  as identity, and no fuzzy matching or cross-account lookup occurs.
- `RecipientHistoryService` loads at most 20 most recent `COMPLETED` payments
  for the current user and recipient, optionally filtered to the payment
  currency. The query is owner-scoped (`user_id`) and bounded to 21 rows, so it
  never loads unbounded history. Rows at or after the current attempt, the
  current transaction, non-`COMPLETED` rows (`PENDING`, `APPROVED`, `FAILED`,
  `CANCELLED`), future rows, invalid amounts, and other currencies are excluded.
  Duplicate transaction IDs are collapsed.
- At least 3 completed samples are required before an amount is considered
  anomalous. The recipient baseline is the median of those samples, which is
  robust to a small number of historical outliers. Insufficient history is
  reported explicitly and never produces a fraud signal.
- `RECIPIENT_AMOUNT_ANOMALY` (`+20`) fires when the current amount is at least
  `5x` the recipient median. It is suppressed when the account-wide
  `LARGE_AMOUNT` signal already fires, avoiding double-counting the same amount
  evidence. The multiplier reuses the existing `LARGE_AMOUNT_MULTIPLIER`
  constant; no new weight or tuning was introduced.
- `NEW_RECIPIENT` remains based on completed history only. A recipient with no
  completed payment is new even if an earlier attempt failed or was cancelled.
  A recipient with no reliable identity (missing or owned by another account)
  is `unavailable`, not new. A first payment to a legitimate new recipient is a
  supported scenario and is not automatically stopped.
- `REPEATED_ATTEMPTS` still uses the account-wide 10-minute velocity window. Its
  evidence is strengthened to mention how many of those attempts targeted the
  same recipient, but the signal and weight do not change. Recipient attempts
  are bounded to 50 rows, exclude the current transaction and future rows, and
  collapse duplicate transaction IDs; no separate recipient-velocity signal is
  created.
- `RecipientRiskContext` also tracks `FAILED` and `CANCELLED` attempts as
  unsuccessful attempts for diagnostics and future work. `PENDING`, `APPROVED`,
  and unknown outcomes are never classified as confirmed failures. The current
  engine does not score those diagnostics directly.

History queries use the existing `transactions` table and indexes
(`idx_transactions_user_id`, `idx_transactions_recipient_id`,
`idx_transactions_timestamp`). No schema migration was needed for this feature.

#### PayPal data boundary

Recipient intelligence uses only GuardPay's own `transactions` and `recipients`
rows. It does not call PayPal, does not assume access to PayPal internal risk
scores, recipient reputation, private recipient identity data, or a complete
PayPal transaction history, and does not create or capture payments. The
existing PayPal Orders / webhook / capture lifecycle is unchanged; recipient
evidence is consumed only by the server-side signal collector and deterministic
engine.

## 4. Scoring algorithm (planned)

1. Start at `0`.
2. For each signal whose `detected == true` (after Spring Boot validation of AI output), add its weight.
3. Cap/normalize to `0–100` (`score = min(100, total)`).
4. Map to severity + recommendation (see §5).
5. Attach the full fired-signal list (name, weight, evidence, source: `deterministic` vs `ai-assisted`) so the UI can explain every point.

Determinism requirement: identical validated inputs ⇒ identical score and decision, regardless of LLM phrasing.

## 5. Decision policy (planned)

| Score | Severity | Recommendation | UX intent |
|-------|----------|----------------|-----------|
| 0–39 | Low | **ALLOW** | Continue payment; show low-risk confirmation |
| 40–69 | Medium | **VERIFY** | Require verification; show what to check |
| 70–84 | High | **STRONG VERIFY** | Strong warning; independent verification before proceeding |
| 85–100 | Critical | **STOP** | Strongly recommend stopping; offer STOP action |

Example: `$2,499` to a new recipient on an active call with an unexpected urgent
invoice and flagged URL ⇒ ~94/100 ⇒ CRITICAL ⇒ STOP (hero demo scenario).

The implemented pipeline also evaluates recipient history before scoring: a
known recipient is not automatically safe, because a payment far outside that
recipient's completed history adds `RECIPIENT_AMOUNT_ANOMALY`. This is
account-scoped and uses only completed payments.

## 6. AI-failure fallback (planned)

- If `POST /ai/analyze-payment` times out, errors, or returns invalid schema: log the failure, proceed with deterministic-only signals (account-wide amount anomaly, recipient-specific amount anomaly, new recipient, unknown merchant, call state, deterministic invoice/URL checks), and mark the assessment `aiUnavailable: true` so the UI shows "AI analysis unavailable — basic protection active".
- AI findings can only **add documented evidence**; they cannot bypass the engine, execute payments, or change the decision policy.

## 7. Explanation output (planned)

Every assessment stores and returns: score, severity, recommendation, detected
scam pattern (if any), per-signal evidence strings, and a human-readable
explanation. The frontend must show *why* (signal list), never just the number.
Language rules: "potential scam / high-risk payment / possible impersonation";
never "definitely fraud" and never "94% probability of fraud".

## 8. Persistence (planned)

- `risk_assessments`: score, severity, recommendation, scam pattern, explanation, timestamp.
- `risk_signals`: one row per evaluated signal (name, weight, detected, evidence, source).
- Re-retrievable via `GET /api/risk/{transactionId}` for dashboard/detail views.

## 9. Testing hooks (planned, not now)

Unit: weight math, capping, threshold boundaries (39/40, 69/70, 84/85), fallback
with AI unavailable. Demo: Scenario A safe (~18 ALLOW), B suspicious (~61
VERIFY), C scam (~94 STOP).
