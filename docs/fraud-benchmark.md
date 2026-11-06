# GuardPay Fraud Benchmark

## Purpose

The fraud benchmark measures how the existing GuardPay risk pipeline behaves
against labeled, synthetic datasets. It is an evaluation harness, not a new
decision path:

- It does not change signal weights, production thresholds, scoring, or actions.
- It does not create PayPal orders, captures, payments, or verification records.
- It does not use a database, Spring context, device, or external network.
- It runs the real `AutomaticRiskEvaluationService`, `PaymentSignalCollectorService`,
  `DeterministicContentAnalyzer`, `AutomaticSignalAdapter`, and
  `DeterministicRiskEngine` with mocked repositories.

There are two distinct datasets:

- **Dev** (`54` scenarios: `26` fraud, `28` legitimate) is used for diagnosis
  and threshold exploration.
- **Held-out** (`32` scenarios: `16` fraud, `16` legitimate) is PayPal-specific,
  uses different IDs and inputs, and is evaluated only with the fixed
  production policy. It must not be used to select thresholds or tune scenarios.

The dev dataset includes the canonical fraud and legitimate regressions.

## Architecture

```text
JSONL scenarios
    |
    v
BenchmarkDatasetLoader  (strict parse + validation)
    |
    v
BenchmarkHarness        (fresh mocks per scenario)
    |
    v
AutomaticRiskEvaluationService
    |-- PaymentSignalCollectorService + DeterministicContentAnalyzer
    |   `-- RecipientHistoryService + RecipientRiskEvaluator
    |-- optional simulated AI findings through mocked AiAnalysisClient
    `-- DeterministicRiskEngine
    |
    v
BenchmarkResult -> BenchmarkMetrics
                -> CategoryMetrics (primary category and signal dimensions)
                -> RecipientComparison (history on vs off, per mode)
                -> ThresholdAnalysis (benchmark-only score policy comparison)
                -> report.json + report.md
                -> optional BenchmarkComparison (dev vs held-out)
                -> comparison.json + comparison.md
```

The report is tagged as a `DEV` or `HELD_OUT` run. A `HELD_OUT` report evaluates
only the fixed `production-baseline` policy and does not compute best-by-metric
labels or optimize thresholds.

The harness never calls `PaymentSafetyOrchestrator`, PayPal, the webhook
pipeline, or any controller. AI evidence is simulated by returning synthetic,
validated `AiFindingDto` records from a mocked `AiAnalysisClient`; no LLM is
contacted.

## Running

From `backend/`:

```powershell
mvn -q "-Dtest=FraudBenchmarkTest,HeldOutBenchmarkTest" test
```

From the repository root:

```powershell
mvn -f backend -q "-Dtest=FraudBenchmarkTest,HeldOutBenchmarkTest" test
```

`FraudBenchmarkTest` runs the dev dataset in both modes and writes:

- `backend/target/fraud-benchmark/report.json`
- `backend/target/fraud-benchmark/report.md`

`HeldOutBenchmarkTest` runs the held-out dataset with the fixed production
policy, writes:

- `backend/target/fraud-benchmark/held-out/report.json`
- `backend/target/fraud-benchmark/held-out/report.md`

and then writes the dev/held-out comparison:

- `backend/target/fraud-benchmark/comparison.json`
- `backend/target/fraud-benchmark/comparison.md`

Generated reports contain timestamps and must not be committed. The source of
truth is the datasets plus the tests.

Each report stores the dataset role, path, scenario count, and SHA-256 of the
dataset file for reproducibility. It also contains overall metrics,
primary-category metrics, overlapping signal-dimension metrics, threshold
comparisons, and generated recommendations. Dev reports include best-by-metric
labels; held-out reports do not.

Dataset, metrics, category, threshold, and held-out tests can be run together:

```powershell
mvn -q "-Dtest=FraudBenchmarkTest,HeldOutBenchmarkTest,BenchmarkDatasetLoaderTest,BenchmarkMetricsTest,CategoryMetricsTest,ThresholdConfigurationTest,ThresholdAnalysisTest" test
```

## Modes

### `DETERMINISTIC_ONLY`

The mocked `AiAnalysisClient.analyze()` throws `AiUnavailableException`. The
service records `aiStatus=UNAVAILABLE` and continues with deterministic
history, content, URL, and device evidence only. This is the default safety
path when AI is disabled or unavailable.

### `VALIDATED_AI`

The mocked client returns synthetic structured findings listed in the
scenario's `input.aiFindings`. Findings are converted by the real
`AiFindingAdapter`, so only controlled-vocabulary codes with confidence at
least `0.5` count. AI can add evidence to existing engine signals; it cannot set
an action directly.

If a scenario has content but no `aiFindings`, the mock returns an empty
finding list (`aiStatus=AVAILABLE`). If a scenario has no content, the service
does not request AI (`aiStatus=NOT_REQUESTED`).

## Dataset

Files:

- Dev: `backend/src/test/resources/fraud-benchmark/dataset/scenarios.jsonl`
  (`54` scenarios, IDs such as `F01`/`L01`, including `F26`/`F27` and
  `L26`–`L28` for recipient intelligence).
- Held-out: `backend/src/test/resources/fraud-benchmark/held-out/scenarios.jsonl`
  (`32` PayPal-specific scenarios, IDs `HF01`–`HF16` and `HL01`–`HL16`).

Held-out IDs and exact input records must remain disjoint from the dev dataset.
`HeldOutBenchmarkTest` enforces both constraints. Do not inspect held-out
results and then edit held-out scenarios, thresholds, or signal weights; that
would defeat the purpose of the held-out evaluation.

Each line is one JSON object. Blank lines are ignored. IDs must be unique.
Unknown JSON fields, invalid enums, unknown signals, inconsistent
score/level/action combinations, and AI findings without content are rejected
before evaluation.

Example shape:

```json
{
  "id": "F05-fake-invoice-no-pressure",
  "category": "invoice_scam",
  "description": "Fake invoice without urgency; AI contributes unexpected-invoice context.",
  "label": "FRAUD",
  "expectedScore": 50,
  "expectedRiskLevel": "MEDIUM",
  "expectedAction": "VERIFY",
  "expectedSignals": ["NEW_RECIPIENT", "LARGE_AMOUNT", "UNKNOWN_MERCHANT"],
  "input": {
    "amount": 1200,
    "currency": "USD",
    "historyAmounts": [100, 120],
    "recipientHistoryAmounts": [],
    "recentAttempts": 0,
    "recipient": "NEW",
    "merchant": "NEW",
    "contentText": "Invoice #5821 for consulting services is attached. Thank you for your business.",
    "contentUrl": null,
    "device": "NONE",
    "aiFindings": ["UNEXPECTED_INVOICE_CONTEXT"]
  },
  "tags": ["invoice_scam", "ai_assisted"],
  "expectedAi": {
    "score": 65,
    "riskLevel": "MEDIUM",
    "action": "VERIFY",
    "signals": ["NEW_RECIPIENT", "LARGE_AMOUNT", "UNKNOWN_MERCHANT", "UNEXPECTED_INVOICE"]
  }
}
```

### Fields

| Field | Meaning |
|---|---|
| `id` | Unique scenario identifier. |
| `category` | Group used for analysis and optional filtering. |
| `description` | Human-readable intent of the scenario. |
| `label` | `FRAUD` or `LEGITIMATE`. |
| `expectedScore` | Deterministic-mode score, 0–100. |
| `expectedRiskLevel` | Must equal `RiskLevels.levelFor(expectedScore)`. |
| `expectedAction` | Must equal `RiskLevels.actionFor(expectedRiskLevel)`. |
| `expectedSignals` | Triggered engine signal codes, using `RiskSignal` names. |
| `input.amount` | Positive payment amount. |
| `input.currency` | Currency code; the harness uses `USD` scenarios today. |
| `input.historyAmounts` | Completed historical amounts used for the account-wide average. Use `[]` when none. |
| `input.recipientHistoryAmounts` | Optional completed amounts for this recipient. When omitted, `KNOWN` recipients default to `historyAmounts`; `NEW` recipients must use `[]`. |
| `input.recentAttempts` | Prior attempts inside the 10-minute velocity window; current attempt is added automatically. |
| `input.recipient` | `NEW`, `KNOWN`, or `NONE`. `KNOWN` requires at least one completed transaction. |
| `input.merchant` | `NEW`, `KNOWN`, or `NONE`. |
| `input.contentText` | Optional message text; `null` means not supplied. |
| `input.contentUrl` | Optional URL; `null` means not supplied. |
| `input.device` | `NONE`, `ACTIVE`, or `INACTIVE`. `NONE` has no trusted observation. |
| `input.aiFindings` | Controlled AI codes used only in `VALIDATED_AI`. Use `[]` when none. |
| `tags` | Non-empty analysis labels. |
| `expectedAi` | Optional AI-mode outcome. Required when `aiFindings` is non-empty. |

`aiFindings` are codes, not full DTOs. The harness creates valid synthetic
findings with title, description, evidence, severity, and confidence `0.9`.
AI cannot emit `ACTIVE_CALL`.

### Expected values

`expectedScore`, `expectedRiskLevel`, `expectedAction`, and `expectedSignals`
describe what the current engine does for that scenario. They must not be tuned
to make a failing test pass without reviewing whether the engine or the
scenario input is wrong.

`expectedAi` is optional for deterministic scenarios. If absent, the AI-mode
expectation is the same as the deterministic expectation.

Dev expectations may be refined during normal development. Held-out
expectations must be fixed from the current engine behavior before the first
held-out results are reviewed and must not be retrofitted to improve metrics.

## Categories

The checked-in dataset uses categories such as:

- support, invoice, investment, romance, tech-support, and family-emergency scams
- phishing, impersonation, payment-instruction, velocity, and account-takeover cases
- missing-context and boundary cases
- known subscriptions, legitimate new payees, legitimate high-value payments,
  legitimate urgent language, shorteners, active calls, and AI false positives
- recipient amount anomalies, recipient velocity, and legitimate known-recipient
  controls
- canonical fraud and legitimate regressions

Categories are descriptive rather than a fixed schema. A new category is valid
as long as it is non-blank and used consistently.

### Primary categories and signal dimensions

The report has two separate diagnostic views:

- **Primary category metrics** partition the selected scenarios by `category`.
  Their counts add up to the selected dataset.
- **Signal-dimension metrics** group scenarios that are expected to involve a
  given engine signal, such as `SOCIAL_ENGINEERING`, `SUSPICIOUS_URL`,
  `UNEXPECTED_INVOICE`, `NEW_RECIPIENT`, `RECIPIENT_AMOUNT_ANOMALY`,
  `LARGE_AMOUNT`, `REPEATED_ATTEMPTS`, or `UNKNOWN_MERCHANT`. A scenario can
  appear in several dimensions.

Signal dimensions are multi-label and are **not** additive partitions. They
answer questions such as "how does the engine perform on payments involving a
social-engineering signal?" without double-counting those payments in the
overall metrics. The dimension membership is derived from the expected signals
for the mode being evaluated (`expectedSignals` in deterministic mode,
`expectedAi.signals` when present in validated-AI mode).

## Classification rules

For binary detection metrics:

| Label | Actual action | Outcome |
|---|---|---|
| `FRAUD` | `VERIFY` or `STOP` | True positive / detected |
| `FRAUD` | `ALLOW` | False negative / missed |
| `LEGITIMATE` | `ALLOW` | True negative / correct allow |
| `LEGITIMATE` | `VERIFY` or `STOP` | False positive |

`VERIFY` and `STOP` are also counted separately. In this benchmark, legitimate
payment friction is any non-`ALLOW` outcome for a legitimate payment:
legitimate `VERIFY` + legitimate `STOP`. The two are still reported separately
so a blocked payment is not confused with a verification prompt. A fraud
`ALLOW` is reported separately from other false negatives.

## Metrics

The report includes:

- TP, FN, FP, TN
- precision, recall, F1, false-positive rate, classification accuracy
- exact score, level, and action accuracy
- signal TP/FP/FN, signal precision/recall/F1
- `ALLOW`/`VERIFY`/`STOP` action counts
- fraud detected/missed and legitimate allowed/verified/stopped
- average, P50, and P95 evaluation latency in milliseconds
- per-category TP/FN/FP/TN, precision, recall, F1, accuracy, FNR, FPR, and action counts
- per-signal-dimension diagnostic metrics for cross-cutting evidence groups
- threshold-policy comparisons using actual engine scores
- generated recommendations and best configuration labels
- per-scenario expected versus actual results and failure isolation

Formulas:

```text
precision = TP / (TP + FP)
recall    = TP / (TP + FN)
F1        = 2 * precision * recall / (precision + recall)
FPR       = FP / (FP + TN)
FNR       = FN / (FN + TP)
accuracy  = (TP + TN) / evaluated scenarios
```

Signal metrics compare the set of triggered engine signal codes with
`expectedSignals`/`expectedAi.signals` per scenario. They measure whether the
same evidence fired, not whether the evidence was correct for the real world.
All ratios, including category and threshold rates, use `null` for a zero
denominator in JSON and `n/a` in Markdown; they are never `NaN` or infinity.
Latency percentiles use nearest-rank on the observed scenario evaluations.
Latency is measured around `service.evaluate()`, not around mock setup.

## Current baseline

Latest checked-in dev run (54 scenarios: 26 fraud, 28 legitimate):

| Mode | TP | FN | FP | TN | Precision | Recall | F1 | FPR | Accuracy |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| `DETERMINISTIC_ONLY` | 24 | 2 | 8 | 20 | 75.00% | 92.31% | 82.76% | 28.57% | 81.48% |
| `VALIDATED_AI` | 26 | 0 | 9 | 19 | 74.29% | 100.00% | 85.25% | 32.14% | 83.33% |

Exact score/level/action and signal agreement are 100% because the dataset is
constructed to characterize the current engine. That is a regression property,
not proof of real-world accuracy.

Deterministic misses in this dataset:

- `F08-paypal-lookalike-known` — 30 `ALLOW`; AI raises it to 50 `VERIFY`.
- `F15-account-takeover-known` — 30 `ALLOW`; AI raises it to 50 `VERIFY`.

`F09-urgent-giftcard-known`, `F20-payment-instruction-url-known`, and
`F23-just-below-missing-threshold` are detected deterministically in the
current baseline. `L25-known-payee-no-context` is a labeled legitimate boundary
case: it produces no signals and is correctly allowed. It is not a fraud miss.

Validated AI detects every fraud scenario, including `F08` and `F15`, and adds
one legitimate false positive (`L22-ai-false-positive-medium`). These results are
for the synthetic dataset only.

Notable category observations from the current dataset:

- `boundary` detects 1/1 fraud scenarios in both modes; `L25` is correctly
  allowed.
- `invoice_scam` detects 5/5 fraud scenarios with no legitimate false positives.
- `legitimate_new_payee` has 5/8 legitimate payments sent to `VERIFY` in
  deterministic mode and 5/8 in validated-AI mode, the largest friction source.
- `ai_false_positive` has 1/4 deterministic false positives and 2/4 validated-AI
  false positives.
- `account_takeover` and `impersonation` are missed deterministically and
  detected with AI; each has one scenario.

Small categories make these rates volatile; use the counts alongside the
percentages.

### Recipient intelligence comparison

The dev run evaluates each mode twice: once with recipient intelligence
disabled (the previous account-wide behavior) and once with the real
`RecipientHistoryService` and `RecipientRiskEvaluator`. The disabled run mocks a
recipient context with no usable amount history, so it reproduces the old
behavior without changing production code paths.

| Configuration | TP | FN | FP | TN | Precision | Recall | F1 | Amount anomaly |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| `DETERMINISTIC_ONLY_BASELINE` | 23 | 3 | 8 | 20 | 74.19% | 88.46% | 80.70% | 0/1 |
| `DETERMINISTIC_ONLY_RECIPIENT` | 24 | 2 | 8 | 20 | 75.00% | 92.31% | 82.76% | 1/1 |
| `VALIDATED_AI_BASELINE` | 25 | 1 | 9 | 19 | 73.53% | 96.15% | 83.33% | 0/1 |
| `VALIDATED_AI_RECIPIENT` | 26 | 0 | 9 | 19 | 74.29% | 100.00% | 85.25% | 1/1 |

Recipient intelligence adds one true positive and no false positives in each
mode by detecting `F26-known-recipient-amount-anomaly`. Across all
configurations, new recipients are detected `30/30`, known recipients are never
misclassified as new (`0/24`), repeated recipient attempts are detected `4/4`,
and only `7/54` scenarios (`12.96%`) have at least three completed recipient
history samples. The remaining `47/54` (`87.04%`) are insufficient-history
cases, so they deliberately produce no amount-anomaly signal. That sparse
history coverage is expected in a synthetic dataset. Held-out data was not used
to choose the `5x` multiplier, the 20-point weight, or the comparison scenarios.

## Held-out evaluation

The held-out dataset is PayPal-specific and was not used to choose thresholds or
signal weights. It contains 16 fraud and 16 legitimate scenarios, with IDs and
exact inputs disjoint from dev. It is always evaluated with the fixed
`production-baseline` policy:

| Mode | TP | FN | FP | TN | Precision | Recall | F1 | FPR | Accuracy |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| `DETERMINISTIC_ONLY` | 16 | 0 | 2 | 14 | 88.89% | 100.00% | 94.12% | 12.50% | 93.75% |
| `VALIDATED_AI` | 16 | 0 | 2 | 14 | 88.89% | 100.00% | 94.12% | 12.50% | 93.75% |

Both modes record 2 legitimate `VERIFY` outcomes and 0 legitimate `STOP`
outcomes. Recipient-specific held-out metrics are identical with recipient
intelligence enabled or disabled:

- Recipient scenarios: `32/32`
- New recipient detection: `18/18`
- Known recipient misclassified as new: `0/14`
- Recipient amount anomaly detection: `0/0` (the held-out dataset contains no
  recipient amount-anomaly scenario, so this metric has no held-out coverage)
- Repeated recipient attempts: `2/2`
- Sufficient recipient history (at least 3 completed samples): `9/32` (`28.13%`)
- Insufficient recipient history: `23/32` (`71.88%`)

Current held-out observations:

- No fraud scenario is missed in either mode.
- `HL02-paypal-freelancer-invoice` and `HL13-paypal-shortener-new` are the two
  legitimate `VERIFY` outcomes.
- `HF07-paypal-invoice-known-ai-impersonation` scores `45 MEDIUM VERIFY`
  deterministically and `65 MEDIUM VERIFY` with AI.
- `HF16-paypal-known-urgent-payment` is detected at `60 MEDIUM VERIFY` in both
  modes.
- `HL14-paypal-ai-false-positive` stays `0 LOW ALLOW` deterministically and
  `20 LOW ALLOW` with AI.
- Official PayPal domains/subdomains such as `paypal.com` and `paypal.me` are not
  treated as lookalikes, even when brand names overlap. This URL false positive
  was found during held-out evaluation and fixed in `DeterministicContentAnalyzer`;
  regression coverage was added to `DeterministicContentAnalyzerTest`.

The comparison report shows dev and held-out metrics side by side plus deltas.
Held-out results are evidence for the fixed policy, not a new tuning signal.

## Threshold analysis

The report evaluates alternative benchmark-only score policies against the
**actual scores** produced by the engine. It never mutates the production
thresholds in `RiskLevels`.

Each policy has three cutoffs:

```text
score <= lowMax     -> ALLOW
score <= mediumMax  -> VERIFY
score <= highMax    -> VERIFY
score > highMax     -> STOP
```

The production baseline is `lowMax=39`, `mediumMax=69`, `highMax=84`. The
built-in comparisons are:

| Name | LOW max | MEDIUM max | HIGH max | Intent |
|---|---:|---:|---:|---|
| `production-baseline` | 39 | 69 | 84 | Preserve current production behavior. |
| `allow-30` | 30 | 69 | 84 | Slightly reduce allow-only zone. |
| `allow-20` | 20 | 69 | 84 | Recall-first allow boundary. |
| `allow-50` | 50 | 69 | 84 | Friction-first allow boundary. |
| `stop-80` | 39 | 69 | 79 | Earlier `STOP`. |
| `stop-90` | 39 | 69 | 89 | Later `STOP`. |
| `balanced-50-80` | 50 | 69 | 79 | Lower allow and earlier stop. |
| `recall-first-20-70` | 20 | 60 | 69 | Earlier verify and stop. |
| `friction-first-55-95` | 55 | 75 | 94 | Later verify and stop. |

Dev reports mark the best policy for precision, recall, F1, accuracy, FPR, FNR,
fraud allowed, legitimate verification, legitimate stop, and legitimate payment
friction. Held-out reports evaluate only `production-baseline` and contain no
best-by-metric labels.

Current dev threshold observations:

- Deterministic mode: the production baseline has 92.31% recall, 82.76% F1,
  and 8 legitimate false positives. `allow-20` reaches 100.00% recall with
  81.25% F1, but raises legitimate false positives to 12. `allow-50` reduces
  friction to 1 false positive but drops recall to 57.69%.
- Validated-AI mode: the production baseline is best by recall, F1, and accuracy
  at 100.00%, 85.25%, and 83.33%. `allow-20` keeps 100.00% recall but raises
  legitimate false positives to 14; `allow-50` reduces friction to 2 false
  positives with 61.54% recall.

These are trade-offs on a small synthetic dev dataset. A higher recall or F1
here does not by itself justify a production threshold change. Threshold
selection must use dev data only; held-out data is reserved for evaluating the
fixed production policy after selection.

## Filtering

`FraudBenchmarkRunner` accepts an optional `BenchmarkFilter`. A filter matches
scenario IDs, categories, or labels, case-insensitively:

```java
BenchmarkFilter filter = BenchmarkFilter.parse("ai_false_positive");
BenchmarkReport report =
    new FraudBenchmarkRunner().run(datasetPath, outputDirectory, filter);
```

`FraudBenchmarkTest.filterSelectsMatchingScenarios` demonstrates selecting the
canonical fraud regression. The main benchmark test runs the full 54-scenario
dataset.

## Adding scenarios

1. Append one JSON object per line to the chosen dataset:
   - Dev for diagnosis and new regression coverage.
   - Held-out only for genuinely unseen PayPal-specific evaluation cases, and
     never after using their results to tune the policy.
2. Use a unique ID and an existing or intentionally new category. Held-out IDs
   start with `H`.
3. Set `expectedScore`/`expectedRiskLevel`/`expectedAction` from the documented
   engine thresholds, and `expectedSignals` from actual engine output.
4. If AI findings are supplied, add `expectedAi` and keep `aiFindings` to
   controlled `PaymentSignalType` codes other than `ACTIVE_CALL`.
5. Run `mvn -q "-Dtest=FraudBenchmarkTest,HeldOutBenchmarkTest" test` for dev
   changes; run `HeldOutBenchmarkTest` for held-out changes.
6. Review any mismatch. Update the scenario only after deciding whether the
   input or the engine behavior is wrong. Do not weaken the test to hide a
   regression, and do not edit held-out expectations to improve results.
7. Keep data synthetic. Never add real names, emails, account IDs, payment
   credentials, or unsupported PII.

Held-out scenarios must not duplicate dev IDs or exact input records. If a new
case is added to held-out, keep it out of dev and do not use its outcome for
threshold selection.

## Limitations

- The datasets are small, curated, and synthetic; they do not represent live
  fraud prevalence or attacker adaptation.
- A single run is not a statistical confidence interval. Category and signal
  rates with few scenarios can move sharply from one case.
- Threshold policies are ranked on dev data used for diagnosis, so the
  best-by-metric policy may be overfit. The held-out run evaluates only the fixed
  production policy; it is not a threshold search and not a production
  recommendation by itself.
- Both datasets are small and synthetic. Held-out results reduce, but do not
  eliminate, selection bias from development.
- Signal dimensions overlap; their counts must not be added together.
- Latency is in-process and mock-backed, not end-to-end payment latency.
- AI mode uses fixed synthetic findings, not an LLM quality evaluation.
- The benchmark intentionally excludes PayPal, webhooks, persistence, and
  verification workflow behavior.
- Recipient intelligence is exercised with synthetic history only. Held-out has
  no recipient amount-anomaly scenario, so that signal has dev-only coverage;
  the held-out run confirms no regression, not independent anomaly accuracy.
- Unsuccessful (`FAILED`/`CANCELLED`) recipient attempts are tracked but not
  separately scored by the current engine.
- Exact expectations describe the current engine; they can become stale after
  an intentional risk-engine change and must be reviewed then.
