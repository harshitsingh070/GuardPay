# GuardPay Fraud Benchmark

## Purpose

The fraud benchmark measures how the existing GuardPay risk pipeline behaves
against a labeled, synthetic dataset. It is an evaluation harness, not a new
decision path:

- It does not change signal weights, thresholds, scoring, or actions.
- It does not create PayPal orders, captures, payments, or verification records.
- It does not use a database, Spring context, device, or external network.
- It runs the real `AutomaticRiskEvaluationService`, `PaymentSignalCollectorService`,
  `DeterministicContentAnalyzer`, `AutomaticSignalAdapter`, and
  `DeterministicRiskEngine` with mocked repositories.

The checked-in dataset contains 49 scenarios: 25 labeled fraud and 24 labeled
legitimate payments, including the canonical fraud and legitimate regressions.

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
    |-- optional simulated AI findings through mocked AiAnalysisClient
    `-- DeterministicRiskEngine
    |
    v
BenchmarkResult -> BenchmarkMetrics -> report.json + report.md
```

The harness never calls `PaymentSafetyOrchestrator`, PayPal, the webhook
pipeline, or any controller. AI evidence is simulated by returning synthetic,
validated `AiFindingDto` records from a mocked `AiAnalysisClient`; no LLM is
contacted.

## Running

From `backend/`:

```powershell
mvn -q "-Dtest=FraudBenchmarkTest" test
```

From the repository root:

```powershell
mvn -f backend -q "-Dtest=FraudBenchmarkTest" test
```

The test runs every scenario in both modes, asserts the dataset expectations,
and writes:

- `backend/target/fraud-benchmark/report.json`
- `backend/target/fraud-benchmark/report.md`

Generated reports contain timestamps and must not be committed. The source of
truth is the dataset plus the test.

Dataset and metrics tests can be run together:

```powershell
mvn -q "-Dtest=FraudBenchmarkTest,BenchmarkDatasetLoaderTest,BenchmarkMetricsTest" test
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

File:
`backend/src/test/resources/fraud-benchmark/dataset/scenarios.jsonl`

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
| `input.historyAmounts` | Completed historical amounts used for the average. Use `[]` when none. |
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

## Categories

The checked-in dataset uses categories such as:

- support, invoice, investment, romance, tech-support, and family-emergency scams
- phishing, impersonation, payment-instruction, velocity, and account-takeover cases
- missing-context and boundary cases
- known subscriptions, legitimate new payees, legitimate high-value payments,
  legitimate urgent language, shorteners, active calls, and AI false positives
- canonical fraud and legitimate regressions

Categories are descriptive rather than a fixed schema. A new category is valid
as long as it is non-blank and used consistently.

## Classification rules

For binary detection metrics:

| Label | Actual action | Outcome |
|---|---|---|
| `FRAUD` | `VERIFY` or `STOP` | True positive / detected |
| `FRAUD` | `ALLOW` | False negative / missed |
| `LEGITIMATE` | `ALLOW` | True negative / correct allow |
| `LEGITIMATE` | `VERIFY` or `STOP` | False positive |

`VERIFY` and `STOP` are also counted separately. A legitimate `STOP` is a
blocked payment and is reported separately from legitimate friction (`VERIFY`).
A fraud `ALLOW` is reported separately from other false negatives.

## Metrics

The report includes:

- TP, FN, FP, TN
- precision, recall, F1, false-positive rate, classification accuracy
- exact score, level, and action accuracy
- signal TP/FP/FN, signal precision/recall/F1
- `ALLOW`/`VERIFY`/`STOP` action counts
- fraud detected/missed and legitimate allowed/verified/stopped
- average, P50, and P95 evaluation latency in milliseconds
- per-scenario expected versus actual results and failure isolation

Formulas:

```text
precision = TP / (TP + FP)
recall    = TP / (TP + FN)
F1        = 2 * precision * recall / (precision + recall)
FPR       = FP / (FP + TN)
accuracy  = (TP + TN) / evaluated scenarios
```

Signal metrics compare the set of triggered engine signal codes with
`expectedSignals`/`expectedAi.signals` per scenario. They measure whether the
same evidence fired, not whether the evidence was correct for the real world.

A ratio with a zero denominator is `null` in JSON and `n/a` in Markdown. It is
never `NaN` or infinity. Latency percentiles use nearest-rank on the observed
scenario evaluations. Latency is measured around `service.evaluate()`, not
around mock setup.

## Current baseline

Latest checked-in run (49 scenarios):

| Mode | TP | FN | FP | TN | Precision | Recall | F1 | FPR | Accuracy |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| `DETERMINISTIC_ONLY` | 19 | 6 | 8 | 16 | 70.37% | 76.00% | 73.08% | 33.33% | 71.43% |
| `VALIDATED_AI` | 21 | 4 | 9 | 15 | 70.00% | 84.00% | 76.36% | 37.50% | 73.47% |

Exact score/level/action and signal agreement are 100% because the dataset is
constructed to characterize the current engine. That is a regression property,
not proof of real-world accuracy.

Deterministic misses in this dataset:

- `F08-paypal-lookalike-known` — 30 `ALLOW`; AI raises it to 50 `VERIFY`.
- `F09-urgent-giftcard-known` — 30 `ALLOW`; social pattern alone is below the verify threshold.
- `F15-account-takeover-known` — 30 `ALLOW`; AI raises it to 50 `VERIFY`.
- `F16-known-payee-no-context` — 0 `ALLOW`; intentionally a boundary case with no signals.
- `F20-payment-instruction-url-known` — 30 `ALLOW`.
- `F23-just-below-missing-threshold` — 30 `ALLOW`.

Validated AI still misses `F09`, `F16`, `F20`, and `F23`, and adds one
legitimate false positive (`L22-ai-false-positive-medium`). These results are
for the synthetic dataset only.

## Filtering

`FraudBenchmarkRunner` accepts an optional `BenchmarkFilter`. A filter matches
scenario IDs, categories, or labels, case-insensitively:

```java
BenchmarkFilter filter = BenchmarkFilter.parse("ai_false_positive");
BenchmarkReport report =
    new FraudBenchmarkRunner().run(datasetPath, outputDirectory, filter);
```

`FraudBenchmarkTest.filterSelectsMatchingScenarios` demonstrates selecting the
canonical fraud regression. The main benchmark test runs the full 49-scenario
dataset.

## Adding scenarios

1. Append one JSON object per line to `scenarios.jsonl`.
2. Use a unique ID and an existing or intentionally new category.
3. Set `expectedScore`/`expectedRiskLevel`/`expectedAction` from the documented
   engine thresholds, and `expectedSignals` from actual engine output.
4. If AI findings are supplied, add `expectedAi` and keep `aiFindings` to
   controlled `PaymentSignalType` codes other than `ACTIVE_CALL`.
5. Run `mvn -q "-Dtest=FraudBenchmarkTest" test`.
6. Review any mismatch. Update the scenario only after deciding whether the
   input or the engine behavior is wrong. Do not weaken the test to hide a
   regression.
7. Keep data synthetic. Never add real names, emails, account IDs, payment
   credentials, or unsupported PII.

## Limitations

- The dataset is small, curated, and synthetic; it does not represent live fraud
  prevalence or attacker adaptation.
- A single run is not a statistical confidence interval.
- Latency is in-process and mock-backed, not end-to-end payment latency.
- AI mode uses fixed synthetic findings, not an LLM quality evaluation.
- The benchmark intentionally excludes PayPal, webhooks, persistence, and
  verification workflow behavior.
- Exact expectations describe the current engine; they can become stale after
  an intentional risk-engine change and must be reviewed then.
