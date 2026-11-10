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
| 21 | `AMOUNT_DEVIATION` | +15 | Behavioral profile | Payment amount deviates significantly from the user's learned baseline (MAD-based modified Z-score). |
| 22 | `UNUSUAL_PAYMENT_HOUR` | +10 | Behavioral profile | Payment occurs at a time outside the user's typical activity window. |
| 23 | `RECIPIENT_BEHAVIOR_DEVIATION` | +15 | Behavioral profile | Payment to a recipient that does not match the user's established counterparty relationships. |
| 24 | `TRANSACTION_FREQUENCY_ANOMALY` | +10 | Behavioral profile | Payment frequency deviates from the user's learned cadence (sudden burst). |
| 25 | `GEOGRAPHIC_BEHAVIOR_DEVIATION` | +15 | Behavioral profile | Payment from a location outside the user's established geographic pattern. |
| 26 | `DEVICE_BEHAVIOR_DEVIATION` | +15 | Behavioral profile | Payment from a device not previously used for the account. |
| 27 | `OVERALL_BEHAVIOR_DEVIATION` | +20 | Behavioral profile | Multiple behavioral dimensions deviate simultaneously. |

Behavioral signals are computed by `AdaptiveBehavioralAnalyzer` against a
per-user learned baseline stored in `user_behavior_profiles`. They are evidence
only — never a payment decision — and the deterministic engine remains the sole
ALLOW/VERIFY/STOP authority. Profiling uses rolling 7/30/90-day windows and
robust statistics (median, MAD) to resist outlier distortion.

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
4. Add the bounded correlation contribution (section 10, `0`–`25` points).
   Correlation can only raise the score; it never lowers it.
5. If `BLOCKLISTED_RECIPIENT` fired, set the score to `100`.
6. Otherwise cap at `100` (`score = min(total, 100)`).
7. Map the score to level and action.
8. Return every finding, including non-triggered ones, plus the explanation,
   automatic signal evidence, and correlation findings.

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

## 9. Behavioral profiling evidence

`AdaptiveBehavioralAnalyzer` runs after the existing signal collector and AI
evidence, comparing the incoming payment against the user's learned behavioral
baseline. It produces complementary `PaymentSignal`s with
`SignalSource.BEHAVIORAL_PROFILE`:

- `AMOUNT_DEVIATION` (+15): amount deviates from personalized median/MAD baseline
- `UNUSUAL_PAYMENT_HOUR` (+10): time outside user's typical activity window
- `RECIPIENT_BEHAVIOR_DEVIATION` (+15): counterparty not in established relationships
- `TRANSACTION_FREQUENCY_ANOMALY` (+10): sudden payment burst vs learned cadence
- `GEOGRAPHIC_BEHAVIOR_DEVIATION` (+15): location outside established pattern
- `DEVICE_BEHAVIOR_DEVIATION` (+15): previously unseen device
- `OVERALL_BEHAVIOR_DEVIATION` (+20): multiple dimensions deviate simultaneously

Behavioral signals are evidence only. They never override hard-stop rules, never
set an action directly, and never bypass `BLOCKLISTED_RECIPIENT`. If profiling is
unavailable (`INSUFFICIENT_DATA`, `UNAVAILABLE`), no behavioral findings are
generated and the deterministic engine decides alone.

Cold-start handling: users with fewer than 5 completed transactions are marked
`INSUFFICIENT_DATA` and receive no artificial risk inflation. Profiles become
`ESTABLISHED` at 20+ transactions with adequate data coverage.

## 10. Signal correlation

The correlation layer (`com.guardpay.risk.correlation`) detects suspicious
*combinations* of individually weak or moderate signals. It is a bounded,
deterministic, explainable evidence source layered on top of the existing
signals. The `DeterministicRiskEngine` remains the only decision authority:
correlation contributes points, never a level or action.

### 10.1 Pipeline

1. `AutomaticRiskEvaluationService` collects the normal `PaymentSignal`s.
2. `CorrelationSignalNormalizer` converts the available signals into
   `CorrelationSignal`s, mapping each to an `EvidenceFamily` and dropping
   duplicates by signal code.
3. `CorrelationRuleRegistry` holds the versioned rule catalogue.
4. `RiskCorrelationEngine` evaluates every rule against the normalized signals,
   resolves overlap, scores the matches, and returns a `CorrelationResult`.
5. The engine adds the contribution and caps the score at `100`.
6. The response and persistence layer carry `correlationFindings`,
   `correlationStatus`, and `correlationContribution`.

Correlation failures are isolated: any exception returns
`CorrelationResult.unavailable()` and the deterministic decision proceeds
unchanged. Missing or unavailable evidence is `UNKNOWN`/`UNAVAILABLE`, never
treated as suspicious.

### 10.2 Evidence families

Signals are grouped so that correlation rewards independent evidence rather than
repeated observations from one source: `AMOUNT`, `RECIPIENT`, `DEVICE`,
`GEOLOCATION`, `NETWORK`, `VELOCITY`, `CONTENT`, `SOCIAL_ENGINEERING`, `TIME`,
`CALL`, `BLOCKLIST`, `MERCHANT`, and `BEHAVIORAL_AGGREGATE`.

### 10.3 Enabled rule catalogue (version `1.0`)

| Code | Category | Required families | Weight |
|---|---|---|---:|
| `ACCOUNT_TAKEOVER_COMBO` | Account takeover | Device + network/geo + new recipient | 0.95 |
| `ACCOUNT_TAKEOVER_VELOCITY_COMBO` | Account takeover | Device/geo + velocity | 0.9 |
| `ACCOUNT_TAKEOVER_ATTEMPT_COMBO` | Account takeover | New device + repeated attempts | 0.9 |
| `SOCIAL_ENGINEERING_CALL_COMBO` | Social engineering | Call + recipient + content/social | 1.0 |
| `SOCIAL_ENGINEERING_RECIPIENT_COMBO` | Social engineering | Recipient + amount + social | 1.0 |
| `PHISHING_CONTENT_COMBO` | Social engineering | Content + content + social | 0.95 |
| `VELOCITY_AMOUNT_COMBO` | Velocity/structuring | Velocity + amount | 0.85 |
| `SPLIT_PAYMENT_RECIPIENT_COMBO` | Velocity/structuring | Split payment + recipient anomaly | 0.9 |
| `NEW_RECIPIENT_AMOUNT_COMBO` | Counterparty | New recipient + amount anomaly | 0.8 |
| `VPN_DEVICE_GEO_COMBO` | Network | VPN/proxy + new device/geo | 0.85 |
| `AMOUNT_TIME_FREQUENCY_COMBO` | Behavioral | Amount + time + frequency deviation | 0.85 |
| `AMOUNT_RECIPIENT_GEO_COMBO` | Behavioral | Amount + recipient + geographic deviation | 0.85 |
| `BEHAVIORAL_MULTI_DIMENSION_COMBO` | Behavioral | Two or more behavioral deviations | 0.8 |

Each rule declares a minimum interaction strength (`0.12` by default) and a
minimum per-signal confidence (`0.5` by default). A rule fires only when every
required family is satisfied by an available signal.

### 10.4 Documented but unsupported correlations

These combinations are intentionally not implemented because the current data
sources do not provide the required evidence: `MULTIPLE_NEW_RECIPIENTS_VELOCITY`,
`REPEATED_FAILED_ATTEMPTS`, `DATACENTER_ASN_VELOCITY`,
`AMOUNT_ESCALATION_SEQUENCE`, `MULTIPLE_RECIPIENT_CHANGES`,
`SHORT_TIME_INTERVAL_CHANGES`, and `RECIPIENT_HISTORY_MULTI_ANOMALY`. They are
recorded in the registry as `UnsupportedCorrelation` entries so the gap is
explicit rather than silently scored.

### 10.5 Scoring

For each matched rule, with signal strengths `strength = 0.5*severity +
0.3*confidence + 0.2*(points/25)`:

- `Q = geometricMean(strengths) * independenceFactor`, where
  `independenceFactor = 0.5 + 0.5 * distinctFamilies / requiredSignals`;
- `C = noisyOR(max confidence per evidence family)`;
- `I_r = weight * Q * C * freshness` (freshness is `1.0` for current evidence);
- aggregate `S = 1 - product(1 - I_r)`;
- `points = round(S * maxContributionPoints)`.

The default `maxContributionPoints` is `25`. Points are allocated across the
firing rules proportionally, capped per family (`familyContributionCap=12`), and
rounded with largest-remainder so the sum of `contributionPoints` exactly equals
the reported contribution.

Overlap resolution runs first: rules are ordered by interaction strength,
duplicate participating-signal sets are dropped, and rules whose evidence
overlaps a stronger rule by at least `overlapThreshold=0.6` are multiplied by
`dependencyDiscount=0.5` and flagged `overlapDiscounted`.

### 10.6 Bounds and guarantees

- Correlation adds `0`–`25` nominal points; the final score still caps at `100`.
- It never lowers the score, never changes an action downward, and never
  overrides a hard block.
- AI cannot inject correlation rules or set correlation points; AI signals are
  only evidence inputs to the deterministic rules.
- The contribution is reported separately from the base signal total so the
  explanation can attribute every point.

## 11. Explanation output

Every guarded assessment returns and persists:

- score, risk level, recommended action;
- human-readable explanation;
- per-signal evidence with source and reason codes;
- full engine findings, including which signals did and did not fire;
- AI status;
- correlation findings, their evidence, and their point contribution, when the
  correlation layer produced a match.

Language rules: "potential scam / high-risk payment / possible impersonation";
never "definitely fraud" and never "94% probability of fraud".

## 12. Persistence

Guarded payments persist the transaction, payment context, risk assessment, and
findings. `risk_assessment_findings` keeps decisions explainable after the fact.
`risk_assessments` also stores `correlation_findings` (JSON), `correlation_status`,
and `correlation_contribution` (see migration `V28`). Transaction detail serves
the stored assessment, correlation evidence, and verification outcome.

The automatic evaluation endpoint (`POST /api/risk/evaluate-automatic`) is
read-only for history and does not persist an assessment. The legacy
`POST /api/risk/evaluate` path takes explicit booleans for regression/tooling
and is not used by the guarded flow.

## 13. Testing and benchmark coverage

- Unit tests cover weight math, capping, blocklist override, and boundaries at
  39/40, 69/70, and 84/85.
- Collector tests cover unavailable reasons, owner scoping, recipient median
  rules, velocity tiers, split payments, device freshness/staleness/replay,
  geo/travel rules, and missing-context thresholds.
- AI adapter tests cover the controlled vocabulary, confidence gate, social-code
  collapsing, and rejection of deterministic-assertion codes.
- Correlation unit tests cover normalization, family mapping, rule matching,
  overlap resolution, scoring bounds, and failure isolation.
- `CorrelationScenarioBenchmarkTest` runs a dedicated scenario set
  (`fraud-benchmark/correlation/correlation-scenarios.jsonl`) and asserts that
  correlation never lowers a score, never weakens an action, escalates the
  intended combinations, and reports an exact, bounded contribution.
- The synthetic fraud benchmark (`docs/fraud-benchmark.md`) runs the real
  collector and engine with mocked repositories; it reports regression metrics,
  never real-world accuracy.
