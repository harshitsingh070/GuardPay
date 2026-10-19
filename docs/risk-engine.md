# GuardPay — Deterministic Risk Engine (planned design)

> Design only. **Do NOT implement yet.** This doc defines the intended behavior
> for a later milestone. No risk-engine code exists in `backend/` in this step.

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

Weights are prototype heuristics, **not** statistical probabilities. Tuning them
is a later step; the init rule is: weights are constants in one place, changeable
without touching agents or UI.

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

## 6. AI-failure fallback (planned)

- If `POST /ai/analyze-payment` times out, errors, or returns invalid schema: log the failure, proceed with deterministic-only signals (amount anomaly, new recipient, unknown merchant, call state, deterministic invoice/URL checks), and mark the assessment `aiUnavailable: true` so the UI shows "AI analysis unavailable — basic protection active".
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
