# GuardPay — Deterministic Risk Engine

> Implemented behavior. The engine lives in `com.guardpay.risk`; the guarded
> payment pipeline and automatic signal collector feed it validated evidence.
> The AI service never scores or decides.

## 1. Purpose

Combine transaction, recipient/merchant, velocity, content, URL, device,
network, and blocklist evidence into one reproducible **GuardPay Risk Score
(0–100)** and a final action: **ALLOW / VERIFY / STOP**.

The engine runs in Spring Boot with or without AI findings. AI and device data
are optional evidence sources; the arithmetic and thresholds are deterministic.

## 2. Inputs

- Current payment context: amount, currency, recipient, merchant, message text,
  URL, optional paired-device identity, and server-observed network context.
  The guarded request has no risk booleans.
- Owner-scoped history derived from PostgreSQL: completed transactions,
  account-wide average amount, recipient median, merchant history, recent
  attempts in multiple windows, prior device observations, and prior geolocated
  payments.
- Optional device call state: fresh observation from a registered device.
  Observations older than five minutes are stale and never active.
- Optional AI findings: controlled-vocabulary evidence with severity,
  confidence, and evidence text. Unknown codes and confidence below `0.5` are
  discarded.
- Owner-scoped blocklist entries.
- Network context such as country and VPN/proxy result, when a trusted resolver
  supplied it. Missing data is reported as unavailable, never guessed as safe.

## 3. Signal catalog and weights

The `RiskSignal` enum is the single source of truth for codes, display copy, and
weights. Signals are evaluated in this fixed order; only triggered signals
contribute points.

| # | Code | Points | Source | Fires when |
|---:|---|---:|---|---|
| 1 | `ACTIVE_CALL` | +15 | Device | A fresh paired-device observation reports an active call. |
| 2 | `NEW_RECIPIENT` | +20 | Transaction history | No prior completed transaction to this recipient (same currency, current attempt excluded). Unavailable identity never counts as new. |
| 3 | `LARGE_AMOUNT` | +20 | Transaction history | Current amount is at least **5×** the account-wide completed-history average. Missing/zero/negative history never fires. |
| 4 | `RECIPIENT_AMOUNT_ANOMALY` | +20 | Recipient history | At least 3 completed samples exist and the current amount is at least **5×** the recipient median. Suppressed when `LARGE_AMOUNT` already fires. |
| 5 | `REPEATED_ATTEMPTS` | +15 | Transaction history | At least **3 attempts including the current one** within **10 minutes**, any status, same currency. |
| 6 | `VELOCITY_HOURLY` | +15 | Transaction history | At least **5 attempts including the current one** within **1 hour**. |
| 7 | `VELOCITY_DAILY` | +20 | Transaction history | At least **10 attempts including the current one** within **24 hours**. |
| 8 | `BLOCKLISTED_RECIPIENT` | +25 | Blocklist | The recipient is on the current user's fraud blocklist. This also forces the final score to 100. |
| 9 | `UNKNOWN_MERCHANT` | +10 | Merchant history | No previous completed transaction with the merchant. |
| 10 | `UNEXPECTED_INVOICE` | +15 | Payment content / AI | Invoice, billing, overdue, refund, or payment-request wording combined with unexpected/pressure context, or validated AI `UNEXPECTED_INVOICE_CONTEXT`. |
| 11 | `URGENT_LANGUAGE` | +10 | Payment content / AI | Urgency or pressure phrasing, or validated AI `URGENT_LANGUAGE`. |
| 12 | `SUSPICIOUS_URL` | +10 | URL structure / AI | Local structural URL rules fire, or validated AI `SUSPICIOUS_URL_STRUCTURE` fires. |
| 13 | `SOCIAL_ENGINEERING` | +20 | Content + counterparty + call / AI | Multiple independent scam indicators co-occur, or any validated AI social code fires. Counted once. |
| 14 | `KNOWN_RECIPIENT_SCAM` | +50 | Recipient history + content | An established recipient sends a new high-risk payment instruction, a lookalike invoice/link, or a scam-pattern combination consistent with a compromised payee. |
| 15 | `SPLIT_PAYMENT` | +40 | Transaction history | At least 3 payments to the same recipient, each below `$1,000`, total at least `$1,000` within 24 hours (current attempt included). |
| 16 | `MISSING_CONTEXT` | +20 | Payment context | High-value payment with no message/link and no trusted device observation. Threshold is `$1,000`, or `$500` for a new recipient. |
| 17 | `NEW_DEVICE` | +20 | Transaction history | The account has prior device observations and this device fingerprint/id has not been seen. A first-ever payment reports unavailable instead of new. |
| 18 | `NEW_GEO` | +15 | Transaction history / network | The account has prior geolocated payments and this country has not been seen. Missing geo reports unavailable. |
| 19 | `IMPOSSIBLE_TRAVEL` | +25 | Transaction history / network | A different country is seen within **6 hours** of the previous geolocated payment. |
| 20 | `VPN_PROXY` | +10 | Network | Trusted network reputation reports a VPN, proxy, or hosting connection. |

Weights are fixed prototype heuristics in one enum, not learned probabilities.
A signal that cannot be derived reliably is reported with a reason code and
does not fire.

### 3.1 Hard block

`BLOCKLISTED_RECIPIENT` is a hard block: when it fires, the final score is
forced to `100` and the action is `STOP`, regardless of the other weights. The
blocklist is owner-scoped and requires a reliable recipient identity.

### 3.2 Signal sources

Collected signals carry a source such as `DEVICE`, `TRANSACTION_HISTORY`,
`MERCHANT_HISTORY`, `PAYMENT_CONTEXT`, `URL_REPUTATION`, `NETWORK`, `AI`, or
`NOT_AVAILABLE`. Unavailable signals include a reason such as
`INSUFFICIENT_HISTORY`, `RECIPIENT_IDENTITY_UNAVAILABLE`, `DEVICE_IDENTITY_UNAVAILABLE`,
`NO_PRIOR_DEVICE_HISTORY`, `GEO_UNAVAILABLE`, `NO_PRIOR_GEO_HISTORY`, or
`NETWORK_REPUTATION_UNAVAILABLE`.

## 4. Recipient and account intelligence

Recipient intelligence uses only the current GuardPay account's own transaction
history and recipient rows; it is never shared across accounts.

- Identity is the persisted recipient row scoped to the owning user. Display
  names, email text, and PayPal account strings are never used as identity, and
  there is no fuzzy or cross-account matching. An id belonging to another user is
  treated as unknown, not used or re-created.
- `RecipientHistoryService` loads at most 20 recent `COMPLETED` payments for the
  current user and recipient, optionally filtered to the payment currency. The
  query is owner-scoped and bounded to 21 rows.
- Rows at or after the current attempt, the current transaction, non-completed
  rows, future rows, invalid amounts, and other currencies are excluded.
  Duplicate transaction ids are collapsed.
- At least 3 completed samples are required before an amount anomaly is
  considered. The baseline is the median, which resists small historical
  outliers. Insufficient history is reported explicitly and produces no signal.
- `RECIPIENT_AMOUNT_ANOMALY` uses the same `5×` multiplier as `LARGE_AMOUNT`
  and is suppressed when `LARGE_AMOUNT` already covers the same amount evidence.
- `NEW_RECIPIENT` is based on completed history only. A failed, cancelled, or
  pending attempt does not make a recipient known.
- Failed and cancelled attempts are tracked as unsuccessful diagnostics but are
  not separately scored.
- The account-wide average and all velocity windows use completed/attempt
  history as appropriate, owner-scoped and currency-matched. The collector uses
  one evaluation timestamp for these comparisons.

## 5. Content, URL, and social-engineering rules

The backend `DeterministicContentAnalyzer` runs independently of AI and detects:

- urgency/pressure wording;
- invoice, billing, overdue, refund, and unexpected-request wording;
- high-risk payment instructions (gift cards, crypto, wire transfer, money
  transfer apps, remote-access tools, one-time codes);
- suspicious URL structure.

Local URL checks include non-HTTPS links, embedded credentials/user info, raw IP
hosts, punycode, excessive subdomains, anonymous shorteners, high-abuse TLDs,
nonstandard ports, brand-lookalike hosts, malformed URLs, and excessive length.
The AI service adds structural checks for `@` obfuscation, bare IP hosts,
punycode, excessive subdomains, brand names in unrelated subdomains, and lure
words in the registrable domain. No URL is fetched; there is no reputation
database or Google Safe Browsing integration.

`SOCIAL_ENGINEERING` fires when independent indicators combine:

- a scam-pattern combination in content;
- a payment instruction plus a new/unknown counterparty or an active call;
- an unexpected invoice plus a new counterparty and urgency or an active call;
- an active call plus urgency, an unexpected invoice, or a payment instruction.

A single AI social finding is not enough by itself; the five AI social codes
(`IMPERSONATION`, `PRESSURE_TO_PAY`, `SECRECY_REQUEST`,
`SUSPICIOUS_PAYMENT_INSTRUCTION`, `SCAM_PATTERN`) fold into the one
`SOCIAL_ENGINEERING` signal, so repeated LLM evidence cannot multiply points.

## 6. Scoring algorithm

1. Start at `0`.
2. Evaluate all 20 signals in fixed order.
3. Add the points of each triggered signal.
4. If `BLOCKLISTED_RECIPIENT` fired, set the score to `100`.
5. Otherwise cap at `100` (`score = min(total, 100)`).
6. Map the score to level and action.
7. Return every finding, including non-triggered ones, plus the explanation and
   automatic signal evidence.

Identical validated inputs produce identical scores and decisions.

## 7. Decision policy

| Score | Level | Action | Meaning |
|---:|---|---|---|
| 0–39 | LOW | `ALLOW` | Proceed to PayPal authorization |
| 40–69 | MEDIUM | `VERIFY` | No PayPal order; controlled challenge required |
| 70–84 | HIGH | `VERIFY` | No order; high-risk warning and challenge |
| 85–100 | CRITICAL | `STOP` | No order; payment blocked |

`RiskLevels` owns these thresholds: `LOW_MAX=39`, `MEDIUM_MAX=69`,
`HIGH_MAX=84`, `MAX_SCORE=100`. MEDIUM and HIGH share `VERIFY`; CRITICAL is
`STOP`. Deterministic signals can reach `STOP` without AI or a device signal —
for example a high-value scam with multiple content signals, or a blocklisted
recipient.

## 8. AI evidence and fallback

Spring Boot calls the AI service only when `contentText` or `contentUrl` is
present. The AI service returns structured findings; Spring Boot validates and
folds them into existing engine signals:

- `URGENT_LANGUAGE` → `URGENT_LANGUAGE` (+10)
- `SUSPICIOUS_URL_STRUCTURE` → `SUSPICIOUS_URL` (+10)
- `UNEXPECTED_INVOICE_CONTEXT` → `UNEXPECTED_INVOICE` (+15)
- `IMPERSONATION`, `PRESSURE_TO_PAY`, `SECRECY_REQUEST`,
  `SUSPICIOUS_PAYMENT_INSTRUCTION`, `SCAM_PATTERN` → one
  `SOCIAL_ENGINEERING` (+20)

AI cannot add a new weight, subtract risk, or set an action. Unknown codes,
confidence below `0.5`, blank evidence, and AI attempts to assert deterministic
facts (for example `ACTIVE_CALL`) are rejected.

Fallback behavior:

- AI timeout, connection error, non-2xx/invalid response, demo-toggled-off AI,
  or an open circuit sets
  `aiStatus=UNAVAILABLE` and continues with deterministic signals.
- No LLM key on the AI service is not an error: the service returns a valid
  response containing local URL findings only (or none), so the status is
  `AVAILABLE` and the evidence simply lacks LLM findings.
- No content sets `aiStatus=NOT_REQUESTED`.
- A successful call sets `aiStatus=AVAILABLE`.
- The decision is never hard-dependent on the LLM.

## 9. Explanation output

Every guarded assessment returns and persists:

- score, risk level, recommended action;
- human-readable explanation;
- per-signal evidence with source and reason codes;
- full engine findings, including which signals did and did not fire;
- AI status.

Language rules: "potential scam / high-risk payment / possible impersonation";
never "definitely fraud" and never "94% probability of fraud".

## 10. Persistence

Guarded payments persist the transaction, payment context, risk assessment, and
findings. `risk_assessment_findings` keeps decisions explainable after the fact.
Transaction detail serves the stored assessment and verification outcome.

The automatic evaluation endpoint (`POST /api/risk/evaluate-automatic`) is
read-only for history and does not persist an assessment. The legacy
`POST /api/risk/evaluate` path takes explicit booleans for regression/tooling
and is not used by the guarded flow.

## 11. Testing and benchmark coverage

- Unit tests cover weight math, capping, blocklist override, and boundaries at
  39/40, 69/70, and 84/85.
- Collector tests cover unavailable reasons, owner scoping, recipient median
  rules, velocity tiers, split payments, device freshness/staleness/replay,
  geo/travel rules, and missing-context thresholds.
- AI adapter tests cover the controlled vocabulary, confidence gate, social-code
  collapsing, and rejection of deterministic-assertion codes.
- The synthetic fraud benchmark (`docs/fraud-benchmark.md`) runs the real
  collector and engine with mocked repositories; it reports regression metrics,
  never real-world accuracy.
