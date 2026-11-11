# GuardPay — First-Time High-Value Payment Detection

> Implemented behavior. The engine lives in
> `com.guardpay.behavior.highvalue` and is invoked by
> `BehavioralProfileService` during the normal behavioral analysis. It produces
> evidence only; `DeterministicRiskEngine` remains the sole ALLOW / VERIFY /
> STOP authority.

## 1. Purpose

Detect payments that are unusual **relative to the user's own completed payment
history**, not relative to a fixed global amount. The engine distinguishes:

- a high absolute amount from a high amount relative to the user's baseline;
- the first payment to a recipient;
- the first high-value payment to an existing recipient;
- the first high-value payment ever observed for the account;
- a rapid amount escalation over recent activity;
- a legitimate high-value payment supported by established history.

Fixed global thresholds are not the primary detector. Amount, percentile, and
ratio thresholds are user-specific and configurable.

## 2. Non-negotiable guarantees

- Evaluation is **read-only**; it never updates the learned baseline.
- The current in-flight transaction is excluded from its own baseline.
- Currencies are strictly separated (`USD`, `INR`, `EUR` are never mixed).
- Missing history is **cold start, not fraud**.
- A zero-MAD distribution falls back to percentile and ratio tests.
- A new recipient is not suspicious by itself; the amount must also deviate.
- Scores and confidence are bounded; the engine cannot set an action.
- Evaluation is deterministic for identical inputs and timestamps.
- `enabled=false` is a kill switch: the engine reports a cold-start assessment
  with reason `DETECTION_DISABLED` and emits no finding.

## 3. Pipeline

```text
Completed, same-currency history (current transaction excluded)
    |
    v
FirstTimeHighValueDetectionEngine.evaluate(...)
    |-- baseline selection: 90d -> 30d -> all eligible history
    |-- median, MAD, p75, p90, p95, historical maximum
    |-- recipient history, novelty, recipient median/max
    |-- 7d recent window for rapid escalation only
    `-- classification + confidence
    |
    v
BehavioralProfileService
    `-- BehavioralFindingDto (classification, severity, confidence, metrics)
    |
    v
AdaptiveBehavioralAnalyzer
    `-- PaymentSignal (SignalSource.BEHAVIORAL_PROFILE, confidence gate 0.6)
    |
    v
AutomaticSignalAdapter -> DeterministicRiskEngine
    |-- specialized high-value RiskSignal weights
    |-- generic AMOUNT_DEVIATION and NEW_RECIPIENT suppressed when the
    |   new-recipient high-value signal fires
    `-- correlation layer may add bounded points
    |
    v
ALLOW / VERIFY / STOP
```

## 4. Inputs and currency separation

The engine receives the authenticated user id, current amount and currency,
optional recipient id, eligible history, the in-flight transaction id to
exclude, the evaluation timestamp, and the profile confidence status.

History is filtered to:

- status `COMPLETED`;
- a positive amount;
- the same currency (case-insensitive) when a currency is supplied;
- a timestamp strictly before the evaluation timestamp;
- a transaction id different from the in-flight transaction.

A request with no currency defaults to `USD`. Other currencies are never used to
build a USD baseline.

## 5. Baseline windows and statistics

At least **5** completed same-currency payments are required. The engine then
selects the baseline in this order:

| Priority | Window | Condition |
|---:|---|---|
| 1 | 90 days | At least 5 eligible payments inside the window |
| 2 | 30 days | At least 5 eligible payments inside the window |
| 3 | All eligible history | Fallback when both windows are sparse |

The short 7-day window is **never** used as the only baseline. It is used only to
detect rapid escalation.

The baseline reports its label (`90d`, `30d`, or `ALL`) and computes:

- median;
- MAD (median absolute deviation);
- 75th, 90th, and 95th percentiles;
- historical maximum;
- amount ratio (`current / median`);
- ratio over historical maximum;
- modified Z-score (`0.6745 * |current - median| / MAD`) when MAD is non-zero;
- percentile rank.

Percentiles use linear interpolation over the sorted baseline. A payment at or
above p75 is considered within the user's high-value scale when it does not
exceed the historical maximum and its modified Z-score is not extreme.

## 6. Classification

Classification is evaluated in this order. Only the first matching category is
returned. `detected=true` means the engine emits a finding; `NORMAL_PAYMENT`,
`LEGITIMATE_HIGH_VALUE_ESTABLISHED`, and cold start never emit one.

| Order | Classification | Detected | Condition (summary) | Severity |
|---:|---|---|---|---|
| 1 | `LEGITIMATE_HIGH_VALUE_ESTABLISHED` | No | Amount is within historical scale (≤ historical max, Z ≤ 2.5) and at or above p75 | `LOW` |
| 2 | `NORMAL_PAYMENT` | No | Amount ≤ p90, Z < 3.0, ratio ≤ 2.0, and no rapid escalation | `LOW` |
| 3 | `FIRST_PAYMENT_TO_RECIPIENT_HIGH_VALUE` | Yes | New recipient and a meaningful amount deviation | `HIGH`, or `CRITICAL` when above historical max and ratio ≥ 5.0 |
| 4 | `FIRST_HIGH_VALUE_TO_RECIPIENT` | Yes | Existing recipient; amount > 2× recipient max and ≥ 3× recipient median, plus p75 or Z ≥ 2.0 | `HIGH` when ratio over recipient max ≥ 4.0, otherwise `MEDIUM` |
| 5 | `FIRST_HIGH_VALUE_PAYMENT` | Yes | Amount > 1.25× historical max and statistically significant deviation | `CRITICAL` when ratio over max ≥ 2.0, otherwise `HIGH` |
| 6 | `RAPID_AMOUNT_ESCALATION` | Yes | Current amount ≥ 4× the 7-day maximum and above p75 | `HIGH` |
| 7 | `SIGNIFICANT_AMOUNT_DEVIATION` | Yes | Modified Z ≥ 3.0, or zero-MAD ratio ≥ 2.5 while above historical max | `CRITICAL` when Z ≥ 4.5, otherwise `HIGH` |
| 8 | `AMOUNT_EXCEEDS_HISTORICAL_MAXIMUM` | Yes | Amount > 1.25× historical max without the above conditions | `MEDIUM` |
| 9 | `NORMAL_PAYMENT` | No | Fallback | `LOW` |
| — | `COLD_START_INSUFFICIENT_DATA` | No | Fewer than 5 eligible payments, invalid amount, or engine disabled | `INFO` |

A “meaningful amount deviation” means at least one of:

- significant deviation (modified Z ≥ 3.0, or zero-MAD ratio ≥ 2.5 above max);
- amount above 1.25× historical max;
- ratio ≥ 1.5 and amount above p90.

### 6.1 Zero-MAD handling

When MAD is at or below `0.01`, the modified Z-score is unavailable. The engine
does not divide by zero and does not invent a Z-score. It falls back to
percentile and ratio tests:

- normal when the amount is ≤ p90 and ratio ≤ 2.0;
- significant when the amount is above historical max and ratio ≥ 2.5.

## 7. Recipient logic

Recipient history is restricted to the same completed, same-currency history.
The engine records the recipient payment count, median, maximum, and ratios.

- A recipient with zero completed payments is new.
- A new recipient alone is **not** suspicious. The amount must also deviate from
  the user's distribution.
- An existing recipient can trigger `FIRST_HIGH_VALUE_TO_RECIPIENT` when the
  amount is far above what that recipient normally receives.
- The recipient baseline is separate from the account-wide baseline. Both are
  reported in the assessment.

## 8. Cold start and confidence

| Completed same-currency payments | Confidence | Profile stage |
|---:|---:|---|
| 5–9 | 0.60 | Early |
| 10–19 | 0.70 | Initializing |
| 20+ | 0.90 | Established |

Cold start (< 5) returns `COLD_START_INSUFFICIENT_DATA` with confidence `0.30`,
`detected=false`, and no signal. The engine never treats missing history as
suspicious.

`AdaptiveBehavioralAnalyzer` adds a second gate: a finding becomes a detected
`PaymentSignal` only when the profile is not `INSUFFICIENT_DATA` and confidence
is at least `0.6`. This prevents low-confidence early profiles from contributing
risk points.

## 9. Risk-engine integration

Each detected classification maps to one `RiskSignal`:

| Classification | Signal | Points |
|---|---|---:|
| `FIRST_PAYMENT_TO_RECIPIENT_HIGH_VALUE` | `FIRST_PAYMENT_TO_RECIPIENT_HIGH_VALUE` | 35 |
| `FIRST_HIGH_VALUE_PAYMENT` | `FIRST_HIGH_VALUE_PAYMENT` | 20 |
| `FIRST_HIGH_VALUE_TO_RECIPIENT` | `FIRST_HIGH_VALUE_TO_RECIPIENT` | 20 |
| `SIGNIFICANT_AMOUNT_DEVIATION` | `SIGNIFICANT_AMOUNT_DEVIATION` | 20 |
| `AMOUNT_EXCEEDS_HISTORICAL_MAXIMUM` | `AMOUNT_EXCEEDS_HISTORICAL_MAXIMUM` | 15 |
| `RAPID_AMOUNT_ESCALATION` | `RAPID_AMOUNT_ESCALATION` | 15 |

`NORMAL_PAYMENT`, `LEGITIMATE_HIGH_VALUE_ESTABLISHED`, and cold start emit no
signal.

When any specialized amount signal fires, `DeterministicRiskEngine` suppresses
the generic `AMOUNT_DEVIATION` points so the same amount evidence is not counted
twice. `FIRST_PAYMENT_TO_RECIPIENT_HIGH_VALUE` also replaces the generic
`NEW_RECIPIENT` points; its weight of 35 reflects the consolidated new-recipient
and amount-deviation evidence. The finding remains visible for audit.

The engine cannot force an action. Points are combined with all other evidence,
capped at 100, and mapped through the normal `RiskLevels` policy.

### 9.1 Correlation

The correlation layer maps the new signals into existing evidence families:

- `FIRST_HIGH_VALUE_PAYMENT`, `SIGNIFICANT_AMOUNT_DEVIATION`,
  `AMOUNT_EXCEEDS_HISTORICAL_MAXIMUM`, and `RAPID_AMOUNT_ESCALATION` → `AMOUNT`;
- `FIRST_HIGH_VALUE_TO_RECIPIENT` and `FIRST_PAYMENT_TO_RECIPIENT_HIGH_VALUE` →
  `RECIPIENT`.

Version `1.0` includes these bounded combinations:

- `FIRST_TIME_HIGH_VALUE_COMBO`: new recipient + high-value amount deviation;
- `HIGH_VALUE_ACCOUNT_TAKEOVER_COMBO`: high-value + new device + new location;
- `HIGH_VALUE_SOCIAL_ENGINEERING_COMBO`: high-value + unfamiliar recipient +
  content/social evidence;
- `HIGH_VALUE_SUSPICIOUS_RECIPIENT_COMBO`: high-value + blocklist or known
  recipient scam;
- `RAPID_FINANCIAL_ESCALATION_COMBO`: rapid escalation + velocity;
- `HIGH_VALUE_BEHAVIORAL_DEVIATION_COMBO`: high-value + unusual hour + frequency
  anomaly.

Correlation adds at most 25 points, never lowers a score, and never changes the
final decision authority.

## 10. API

### GET /api/behavior/high-value/baseline

Authenticated, read-only. Optional `currency` query parameter (default `USD`).

```json
{
  "currency": "USD",
  "profileStatus": "ESTABLISHED",
  "transactionCount": 24,
  "medianAmount": 1800.00,
  "madAmount": 240.00,
  "historicalMax": 9500.00,
  "p75Amount": 3100.00,
  "p90Amount": 7200.00,
  "p95Amount": 8800.00,
  "typicalHighValueThreshold": 7200.00,
  "established": true
}
```

When the profile service is unavailable, the endpoint returns
`profileStatus="UNAVAILABLE"`, `transactionCount=0`, null metrics, and
`established=false`. It never fabricates a baseline.

## 11. Frontend

- `FirstTimeHighValueCard` renders the backend's triggered high-value findings
  with severity, ratio, observed amount, baseline amount, window, confidence, and
  score impact. It does not re-score.
- The card appears in the checkout `DecisionPanel` and the stored
  `TransactionDetailPanel`.
- High-value findings are excluded from the generic findings list to avoid
  duplicate display; the dedicated card owns that evidence.
- `classifyFraudPattern` labels the presentation-only pattern
  `FIRST_TIME_HIGH_VALUE`.
- The dashboard shows a currency-specific baseline card with median, p90, p95,
  historical maximum, and the typical high-value threshold.

## 12. Configuration

All thresholds live in `FirstTimeHighValueConfig`
(`guardpay.risk.high-value`). Defaults:

| Property | Default | Meaning |
|---|---:|---|
| `enabled` | `true` | Kill switch; false emits no finding |
| `min-transactions-for-baseline` | 5 | Minimum eligible samples |
| `cold-start-threshold` | 10 | Early-profile boundary |
| `established-threshold` | 20 | Established-profile boundary |
| `min-meaningful-deviation-ratio` | 1.50 | Smallest ratio treated as meaningful |
| `modified-z-significant-threshold` | 3.00 | Significant outlier |
| `modified-z-extreme-threshold` | 4.50 | Extreme outlier |
| `zero-mad-threshold` | 0.01 | Below this, MAD is treated as zero |
| `zero-mad-significant-ratio` | 2.50 | Zero-MAD significance ratio |
| `amount-within-scale-z-threshold` | 2.50 | “Within historical scale” Z |
| `normal-amount-ratio-ceiling` | 2.00 | Normal-payment ratio ceiling |
| `percentile-high-value` | 0.90 | High-value percentile |
| `percentile-extreme` | 0.95 | Extreme percentile |
| `historical-max-escalation-factor` | 1.25 | Exceeds-max multiplier |
| `recipient-escalation-factor` | 3.00 | Recipient median multiplier |
| `recipient-max-escalation-factor` | 2.00 | Recipient max multiplier |
| `recipient-min-modified-z` | 2.00 | Recipient branch Z floor |
| `new-recipient-critical-ratio` | 5.00 | New-recipient CRITICAL ratio |
| `recipient-critical-ratio` | 4.00 | Existing-recipient CRITICAL ratio |
| `extreme-ratio-over-max` | 2.00 | CRITICAL ratio over historical max |
| `recent-window-days` | 7 | Rapid-escalation window |
| `medium-window-days` | 30 | Medium baseline window |
| `long-window-days` | 90 | Preferred baseline window |
| `rapid-escalation-window-days` | 7 | Label and escalation window |
| `rapid-escalation-factor` | 4.00 | Jump over recent maximum |
| `max-signal-contribution` | 25 | Reserved bound |
| `confidence-cold-start` | 0.30 | Cold-start confidence |
| `confidence-discount-cold-start` | 0.60 | Early (5-9 samples) confidence |
| `confidence-initializing` | 0.70 | Initializing confidence |
| `confidence-established` | 0.90 | Established confidence |

Every property has a `GUARDPAY_HIGH_VALUE_*` environment override in
`application.yml`.

## 13. Benchmark

The dedicated benchmark is described in
[`fraud-benchmark.md`](fraud-benchmark.md#high-value-scenarios). It runs the real
engine through the evaluation harness with the engine enabled and disabled.

Current 10-scenario result:

| Configuration | TP | FN | FP | TN | Precision | Recall | F1 | Findings | Cold starts |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| `WITH_HIGH_VALUE` | 5 | 0 | 0 | 5 | 100.00% | 100.00% | 100.00% | 5 | 2 |
| `WITHOUT_HIGH_VALUE` | 0 | 5 | 0 | 5 | n/a | 0.00% | n/a | 0 | 10 |

These numbers describe the synthetic scenario set only. They are not a
real-world accuracy claim.

## 14. Limitations

- Baselines need at least five completed same-currency payments. Sparse users
  receive cold start, not a guessed baseline.
- The engine uses only the current account's own history. It does not use global
  spending norms.
- The benchmark is small and synthetic; category and signal rates can move
  sharply with one scenario.
- Correlation rules are bounded heuristics, not learned probabilities.
- The engine reports evidence; it never overrides a hard block or sets the final
  action.
- Profile confidence and the analyzer's `0.6` gate can suppress an early
  finding even when the raw statistical deviation is large.
