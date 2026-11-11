# GuardPay

**AI-powered real-time payment safety system.**

GuardPay detects when a user may be getting manipulated into making a fraudulent PayPal payment — especially through social-engineering scams such as fake PayPal support calls, urgent payment requests, suspicious invoices, suspicious URLs, and unusual transaction behavior.

> **Status:** the product flow is implemented end to end — guarded payment,
> automatic signals + validated AI evidence → deterministic risk engine →
> `ALLOW`/`VERIFY`/`STOP`, controlled TOTP/simulated verification,
> recipient-bound PayPal Sandbox orders + server-side capture,
> signature-verified webhooks, transaction history, live dashboard, SSE alerts,
> owner-scoped blocklist, and paired-device call state. Accounts use BCrypt
> password hashes and an opaque `HttpOnly` session cookie; transactions,
> recipients, devices, alerts, blocklist entries, idempotency keys, and the SSE
> feed are owner-scoped. GuardPay also has owner-scoped recipient intelligence,
> an admin-reviewed fraud-feedback lifecycle, and an admin continuous-learning
> console where a deployed rule calibration can only move the deterministic
> `ALLOW`/`VERIFY`/`STOP` thresholds. See `docs/demo.md` to run the demo and
> `docs/` for the current design.

## Architecture overview

```text
React + Vite web app / Android WebView shell
    |
    |  HTTPS + HttpOnly session cookie + CSRF
    v
Spring Boot
    |-- PostgreSQL + Flyway
    |-- PayPal Sandbox REST APIs + signature-verified webhooks
    |-- Automatic signal collection
    |     |-- history + recipient/merchant + velocity
    |     |-- content/URL structure
    |     |-- blocklist, device, network
    |     `-- validated AI evidence
    |-- Recipient intelligence + explicit feedback + learning
    |     |-- owner-scoped reports/reviews and evidence-only reputation
    |     |-- admin-verified labels -> immutable datasets
    |     `-- optional calibration thresholds (no auto-deploy)
    `-- Deterministic Risk Engine (sole decision authority)
          |-- ALLOW
          |-- VERIFY -> controlled TOTP/simulated challenge
          `-- STOP
    |
    `-- optional internal HTTP -> Python FastAPI evidence service -> LLM
```

**Security principle:** the LLM never directly decides whether a payment is
allowed.

```text
AI / device / history evidence → Spring Boot validation → deterministic score → ALLOW / VERIFY / STOP
```

- `docs/architecture.md` — current components, boundaries, and limitations.
- `docs/api-contract.md` — implemented REST/SSE/internal API contract.
- `docs/risk-engine.md` — 20-signal deterministic engine, weights, and policy.
- `docs/fraud-benchmark.md` — synthetic benchmark methodology and current baseline.
- `docs/demo.md` — runnable demo walkthrough.

## Technology stack

**Frontend and shell:**

- React + Vite, TypeScript, Tailwind CSS, shadcn/ui, AG Grid
- Kotlin Android WebView debug shell

**Backend:**

- Java 17, Spring Boot 3.x, Spring Data JPA / Hibernate, Maven, PostgreSQL, Flyway

**AI service:**

- Python 3.12+, FastAPI, OpenAI-compatible LLM client, deterministic local URL rules
- No CrewAI and no multi-agent framework

**External:**

- PayPal Sandbox REST APIs and webhooks
- Optional email/SMS notification delivery

## Repository layout

```text
guardpay/
├── frontend/     # React + Vite checkout, dashboard, transactions, alerts
├── backend/      # Spring Boot API, risk engine, persistence, PayPal integration
├── ai-service/   # FastAPI evidence service (no decisions, no CrewAI)
├── android/      # Kotlin WebView shell + foreground call-state reporting
├── docs/         # current architecture, API, engine, demo, benchmark, product docs
├── scripts/      # demo, APK build, and developer helpers
├── docker-compose.yml
└── README.md
```

The product docs `docs/GuardPay_PRD.md` and
`docs/GuardPay_Project_Specification.md` describe the broader product plan; the
implementation notes at their top and the current contract/architecture docs
take precedence for what is built.

## Local development prerequisites

- Git
- Node.js 20+ + npm (frontend)
- Java 17 + Maven 3.9+ (backend)
- Python 3.12+ (ai-service)
- PostgreSQL 16+ (or Docker + Docker Compose)
- PayPal Developer Sandbox account for live order/capture testing
- Optional LLM API key; without one the AI service still answers with local
  URL findings only, and the deterministic path continues

Local services:

| Service    | URL                   |
| ---------- | --------------------- |
| Frontend   | http://localhost:3000 |
| Backend    | http://localhost:8080 |
| AI service | http://localhost:8001 |
| PostgreSQL | localhost:5432        |

## Services

| Directory    | Description |
| ------------ | ----------- |
| `frontend/`  | Registration/login, guarded checkout, risk warning, TOTP enrollment, dashboard, transaction detail, SSE alert feed, feedback review, and learning admin console. |
| `backend/`   | Accounts/sessions/devices, transactions, recipients, merchants, PayPal integration + webhooks, PostgreSQL/Flyway, automatic signals, deterministic risk engine + final decision, recipient intelligence, feedback review, learning datasets/calibration, dashboard/alerts/blocklist APIs. |
| `ai-service/`| FastAPI structural/LLM evidence service. Returns validated structured findings only; never scores or decides. |
| `android/`   | Debug WebView shell with foreground telephony call-state reporting. |

## Quickstart

Requires **Java 17** + Maven 3.9+. The build targets release 17
(`maven-compiler-plugin` pins `<release>17</release>`).

```powershell
# 1. Start PostgreSQL (defaults match backend/.env.example)
docker compose up -d db

# 2. Configure environment (DB_HOST, DB_PORT, DB_NAME, DB_USERNAME, DB_PASSWORD)
#    For local runs without Docker env files, the application.yml defaults
#    (localhost:5432/guardpay) already work.

# 3. Run tests
mvn -f backend clean test

# 4. Start the backend
mvn -f backend spring-boot:run

# 5. Verify (health and /api/auth/* are public)
curl http://localhost:8080/api/health
# -> {"status":"UP","service":"guardpay-backend"}

# 6. Start the frontend (separate terminal)
npm --prefix frontend install
npm --prefix frontend run dev            # http://localhost:3000

# 7. Start the AI service (optional, separate terminal; run from ai-service/)
cd ai-service
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe -m uvicorn app.main:app --port 8001   # :8001/health
cd ..
```

Open the frontend, create an account, and sign in. The browser stores only the
opaque session cookie; API calls and the alert stream send it automatically.

Layering rule: `Controller → Service → Repository → Database`.
Controllers return DTOs (`common/dto/ApiResponse`) — never JPA entities.
CORS default-allows the Vite dev server (`http://localhost:3000`,
override via `CORS_ALLOWED_ORIGINS`) with credentials enabled. Authentication
uses BCrypt password hashes and an opaque `HttpOnly`, `SameSite=Lax` session
cookie; only the SHA-256 token hash is stored server-side. Sessions expire
after 24h idle or 7 days absolute, and can be listed, rotated, or revoked.
Only registration, login, CSRF bootstrap, `me`, health, signed PayPal
webhooks, device-signal intake, and the loopback verification outbox are
public. Cookie-authenticated unsafe requests must send `X-CSRF-Token` from
`GET /api/auth/csrf`. Set `GUARDPAY_AUTH_COOKIE_SECURE=true` behind HTTPS,
and `GUARDPAY_AUTH_ENABLED=false` only for fixture/unit-test tooling.

Database: Flyway owns the schema (`backend/src/main/resources/db/migration/V1–V35`,
applied automatically at startup); Hibernate stays on `ddl-auto=validate`.
Tables include `users, auth_sessions, devices, merchants, recipients,
transactions, payment_context, risk_assessments, paypal_webhook_events,
device_call_states, guarded_payment_idempotency, payment_verifications,
recipient_blocklist, alerts, notification_outbox`, reciprocal-intelligence
report/review tables, the feedback lifecycle tables, and
`learning_datasets, learning_dataset_items, learning_dataset_exclusions,
learning_models, learning_events`. V21 restores auth and makes idempotency keys
unique per owner, V22 adds per-device ownership and hashed credentials, V23
hardens session expiry and revocation, V32 adds the feedback/review/outcome
lifecycle, V33–V34 add learning datasets/models/exclusions/rollback, and V35
persists the calibration version on each assessment.
Note: if a local PostgreSQL already occupies host port 5432, either stop it
or point the backend at the compose DB via `DB_PORT`.

## PayPal Sandbox setup

Required environment variables (Sandbox):

```powershell
$env:PAYPAL_BASE_URL = "https://api-m.sandbox.paypal.com"
$env:PAYPAL_CLIENT_ID = "<sandbox-client-id>"
$env:PAYPAL_CLIENT_SECRET = "<sandbox-client-secret>"
```

Then start the backend (`docker compose up -d db`, then `mvn -f backend spring-boot:run`) and verify:

```powershell
curl http://localhost:8080/api/paypal/health
# with credentials → {"status":"UP","provider":"PAYPAL","environment":"SANDBOX"}
# without credentials → {"status":"DOWN","provider":"PAYPAL","environment":"SANDBOX"}
```

WARNING: credentials must NEVER be committed. They live only in environment
variables (see `backend/.env.example` for the documented keys). The backend
never logs tokens/secrets and never returns them from any API.

## Guarded PayPal flow (the only order-creation path)

Flow: `POST /api/payments/guarded` accepts business/payment context only and
runs in the authenticated user's workspace. The backend derives risk, stores the
transaction and decision once, and creates the PayPal order **only for ALLOW**
(status `PAYPAL_AUTHORIZATION_REQUIRED` + Sandbox approval URL). STOP creates
nothing; VERIFY creates nothing until the controlled challenge is confirmed.
The legacy public `POST /api/paypal/orders` route no longer exists.

Sandbox testing (buyer = PayPal Sandbox Personal test account). The recipient
email must belong to a Sandbox account you can use as the payee.

```powershell
# 1. Start an ALLOW payment (recipientEmail must be a Sandbox payee)
$payment = Invoke-RestMethod -Method POST http://localhost:8080/api/payments/guarded `
  -ContentType "application/json" `
  -Body '{"amount":45.00,"currency":"USD","recipientName":"Known Co","recipientEmail":"sb-merchant@business.example.com"}'
# -> risk + paypalOrder.approvalUrl for ALLOW

# 2. Open approvalUrl, approve with the Sandbox Personal account, then capture.
Invoke-RestMethod -Method POST "http://localhost:8080/api/paypal/orders/$($payment.paypalOrder.orderId)/capture"
```

Risk gate: STOP creates no order and capture is impossible; VERIFY creates no
order until the server-verified challenge succeeds; unknown orders return 404.
The frontend can never force ALLOW. Capture checks the stored decision.
Transaction status mapping: PENDING
(order created / verify-required), CANCELLED (stopped), COMPLETED/FAILED
(capture outcome). No real money moves outside explicit Sandbox approval +
allowed capture.

### M14 — validation, hardening, idempotency (no new AI model work)

`POST /api/payments/guarded` accepts an optional `Idempotency-Key` header:
repeats return the stored response verbatim (same `paymentId` *and* PayPal
order — no second order), a reused key with different content is rejected
(409), and the UNIQUE constraint arbitrates races. Capture replays return the
stored result without re-calling PayPal (row-locked check-then-capture), so
double-clicks cannot double-charge. The primary `/payment` UI is toggle-free
(amount, recipient, recipient PayPal email, merchant, message, URL) and runs in
the signed-in user's workspace. Idempotency keys are unique per owner, and
capture uniqueness is enforced in the database. Webhook, engine, and threshold
behavior are unchanged.

AI is optional evidence, not a prerequisite for blocking: the deterministic
engine can reach `STOP` without AI or device data. Without an
`OPENAI_API_KEY` the AI service still answers with local URL findings only;
if the service is down, errors, or is circuit-open, AI analysis reports
`UNAVAILABLE` and the deterministic path continues. AI-unavailable fallback,
forged-field rejection, and validated merge
behavior are covered by tests.

### M15 — end-to-end validation

Architecture: User → Guarded Payment → Automatic Signals
(transaction/recipient/merchant history, device, content, network → optional
AI) → validated findings → deterministic risk engine → ALLOW/VERIFY/STOP →
PayPal (order, authorization, server capture, webhook). **GuardPay does not
bypass PayPal authorization. It automatically evaluates payment risk and
controls whether an eligible payment can proceed.**

The guarded flow has been exercised end to end with PayPal Sandbox: ALLOW with a
real order, idempotent replay, VERIFY with no order until the challenge passes,
forged-field rejection, AI-unavailable fallback, and leak-free logs.
`/payment` shows a collapsible Developer details block (payment ID, AI status,
signal codes, order ID, payment status) with no secrets or raw JSON dumps.
Human Sandbox approval/capture and external webhook delivery still require a
human/tunnel; localhost webhooks are not reachable by PayPal.

### M16 — lifecycle status, controlled VERIFY, transaction history

- **Full lifecycle status.** `TransactionStatus` gained `APPROVED`
  (`V11__add_approved_transaction_status.sql`); `GuardedPaymentStatus` now
  covers `BLOCKED | VERIFICATION_REQUIRED | PAYPAL_AUTHORIZATION_REQUIRED |
  PENDING | APPROVED | COMPLETED | CANCELLED | FAILED`, and every guarded/capture
  response carries the `paymentStatus`. Webhooks apply monotonic transitions:
  `COMPLETED`/`CANCELLED` are terminal, `FAILED` can only recover to
  `COMPLETED`, and `APPROVED` never regresses to `PENDING`.
- **Repeated-attempt velocity signal.** `REPEATED_ATTEMPTS` (+15) fires when a
  user has made `>= 3` attempts (any status, same currency) within 10 minutes;
  the browser cannot supply it (server-derived only).
- **Controlled VERIFY (order-free).** A `VERIFY` decision creates **no** PayPal
  order. The backend issues a challenge (`V12__create_payment_verifications.sql`,
  hashed answer, TTL, capped attempts), the server records the outcome, and the
  order is created only after a server-verified success
  (`POST /api/payments/{paymentId}/verification/confirm`). Wrong codes never
  verify; expiry and max-attempts end the challenge. The channel is **TOTP**
  when an authenticator is enrolled on `/dashboard`, otherwise **simulated**
  (`simulated: true`). The code is delivered out of band and is never returned
  by the payment API or written to logs; in development the simulated code sits
  in an in-memory outbox readable only from loopback via
  `GET /api/dev/verifications/{paymentId}/code` (disable
  `GUARDPAY_DEV_VERIFICATION_OUTBOX` in production).
- **Receipt + transaction history.** Findings are persisted on each assessment
  (`V13__add_risk_assessment_findings.sql`) so decisions stay explainable.
  `GET /api/transactions` (paged) and `GET /api/transactions/{id}` return the
  stored decision, findings, PayPal references and verification outcome for the
  signed-in account only. The `/transactions` UI lists history in AG Grid with
  a detail panel; the checkout receipt closes the loop.
- **Dashboard, alerts, blocklist.** `/api/dashboard/summary` aggregates live
  decision, latency, AI, notification, and verification metrics. High-risk
  alerts persist to `alerts`/`notification_outbox` and stream over SSE at
  `/api/alerts/stream`. `/api/blocklist/recipients` is owner-scoped and a
  blocklisted recipient forces `CRITICAL / STOP`.
- **Run it:** `pwsh -File scripts/demo-guarded-flow.ps1 -RecipientEmail <sandbox-payee>` (checks the safety
  invariants against a running backend) — see `docs/demo.md`.

## PayPal webhooks (Milestone 9 — state sync, no tunnels yet)

Architecture: PayPal event → `POST /api/paypal/webhooks` (no user auth by
design; PayPal signature verification is the trust mechanism) → parse →
idempotency check (`paypal_webhook_events`, UNIQUE `paypal_event_id`; repeats
ack `{received: true, duplicate: true}` without reprocessing) → verify via
`POST /v1/notifications/verify-webhook-signature` (needs `PAYPAL_WEBHOOK_ID`
from the PayPal Developer Dashboard → Sandbox app → Webhooks; never commit or
log it) → route → sync `TransactionStatus`.

Supported: `CHECKOUT.ORDER.CREATED/APPROVED` → PENDING,
`PAYMENT.CAPTURE.PENDING` → PENDING, `PAYMENT.CAPTURE.COMPLETED` → COMPLETED,
`PAYMENT.CAPTURE.DENIED` → FAILED; `REFUNDED` and unknown types ack without
effect; unknown orders/captures ack without creating transactions. Responses:
200 `{received: true}`, 401 invalid signature, 400 malformed/unconfigured.

Core separation: **PayPal payment state ≠ GuardPay risk decision.** Webhooks
only record what PayPal did; they cannot change ALLOW/VERIFY/STOP (the
webhook layer has no reference to risk classes — enforced by test).
Transaction lookup: `paypal_order_id`, then `paypal_capture_id`, then
`supplementary_data.related_ids.order_id`.

Local limitation: PayPal cannot reach `localhost`, so live external delivery
needs a public tunnel or deployment (not configured; no tunnel credentials
committed). Unit/integration coverage stands in until then — do NOT fake a
live webhook.

## Automatic signal collection (derived, never client-supplied)

`POST /api/risk/evaluate-automatic` accepts payment context only (`amount`,
optional `recipientId`/`merchantId`/`transactionId`, device identity, message,
URL, server-observed network context). The workspace is resolved server-side
from the authenticated session and there are no risk booleans on this path.

The guarded pipeline collects twenty signals: amount/recipient/merchant history,
recipient anomaly, three velocity tiers, split payments, blocklist, content and
URL structure, social engineering, known-recipient compromise, missing context,
device novelty, geo novelty, impossible travel, VPN/proxy, and active call where
a fresh paired-device observation exists. Missing evidence is returned with an
unavailable reason instead of being guessed. The legacy
`POST /api/risk/evaluate` endpoint takes explicit booleans for
regression/tooling only and never feeds guarded decisions. See
`docs/risk-engine.md` for exact weights.

## Recipient intelligence, feedback, and continuous learning

These loops add evidence and evaluation data; they never bypass the engine.

- **Global recipient intelligence (RR-1).** `/api/intelligence/**` lets an
  owner report a recipient they paid, view an owner-scoped evidence summary,
  and lets admins review reports. Identifiers are hashed before storage and are
  never returned. Reputation is bounded evidence (up to +40 points) included in
  the existing score, with a matching `GLOBAL_RECIPIENT_*` finding. Only an
  explicit admin review can set `CONFIRMED_FRAUD`; payment outcomes and
  captures never become fraud labels.
- **Explicit feedback (FL-1).** `/api/fraud-feedback/**` stores a label snapshot
  for a transaction (`FRAUD`/`LEGITIMATE`, structured type/reason, optional
  finding code). The canonical submit path is
  `POST /api/fraud-feedback/transactions/{transactionId}`. Labels start as
  `SUBMITTED` and move through admin review
  (`/api/admin/fraud-feedback/**`) to `VERIFIED_FRAUD`,
  `VERIFIED_LEGITIMATE`, or `REJECTED`; every transition is append-only and
  audited. A corrected label returns to `SUBMITTED` with an `UPDATED` event,
  and correcting a verified label invalidates affected learning artifacts. Only
  verified labels feed learning.
- **Continuous learning (FL-2).** `/api/admin/learning/**` builds an immutable,
  checksum-addressed dataset from admin-verified feedback (features from the
  persisted decision-time `RiskAssessment`, labels stored separately), trains a
  rule-calibration candidate, and requires explicit admin approval, deployment,
  and rollback. Minimums are `20` records with at least `5` fraud and `5`
  legitimate labels; otherwise the dataset is `INSUFFICIENT_DATA` and training
  is refused with the exact unmet requirement. Training never auto-deploys, and
  a deployed calibration can only change the `verify`/`stop` thresholds — the
  deterministic score, signal weights, AI validation, and hard block are
  unchanged. Invalidation after a verified-label correction retires affected
  models and falls back to defaults.
- **Provenance.** `risk_assessments.calibration_version` records which deployed
  calibration produced a persisted decision (`deterministic-default` when none).
  The automatic risk API returns the same version.
- **Benchmark.** `docs/fraud-benchmark.md` evaluates threshold policies on real
  engine scores. The same policy comparison applies to calibration candidates;
  it is an offline evaluation, not an automatic production threshold change.

### AI risk intelligence (evidence, never decisions)

**AI findings are evidence, not payment decisions.** The Python service
(`ai-service/`, FastAPI on :8001) analyzes message/invoice text and URL
*structure* through two focused LLM agents plus deterministic local URL rules
(no URL fetching, no reputation DB) and returns closed-vocabulary findings with
confidence: `URGENT_LANGUAGE`, `IMPERSONATION`, `PRESSURE_TO_PAY`,
`SECRECY_REQUEST`, `SUSPICIOUS_PAYMENT_INSTRUCTION`, `SCAM_PATTERN`,
`SUSPICIOUS_URL_STRUCTURE`, `UNEXPECTED_INVOICE_CONTEXT`. No CrewAI — the
evidence pipeline does not need a multi-agent framework. The model is configured
via `OPENAI_API_KEY`/`OPENAI_MODEL` (never committed, never logged); without a
key the service still boots and `/api/v1/analyze` returns local structural URL
findings only — no LLM evidence, never a faked result. Set the same optional
`AI_SERVICE_INTERNAL_TOKEN` on Spring Boot and
FastAPI to require the `X-GuardPay-Internal-Token` header on the internal hop.

Spring Boot calls it only when content is present, validates findings (unknown
codes and confidence < 0.5 skipped), and folds AI evidence onto *existing* engine
signals: five social codes → one +20 `SOCIAL_ENGINEERING`; urgent language → +10;
suspicious URL structure → `SUSPICIOUS_URL`; unexpected-invoice context →
`UNEXPECTED_INVOICE`. AI cannot add weights, subtract risk, or set an action. It
reports `aiStatus: NOT_REQUESTED | AVAILABLE | UNAVAILABLE`; any failure degrades
to deterministic-only evaluation. Privacy: minimum context only (amount, names,
flags, text, URL) — no audio, phones, contacts, or emails. Logs mask message/user
content, URLs, names, and email destinations.

### M13 — Guarded PayPal payment flow (primary path)

One automatic flow: `POST /api/payments/guarded` (amount, USD, optional
description/user/recipient+merchant IDs *or* names/device/content — **no risk
fields exist**) → `PaymentSafetyOrchestrator` resolves identities (unknown
payee names are learned as new, so history semantics stay real) → full
automatic pipeline (history, device, AI, engine) → persists
Transaction + PaymentContext + RiskAssessment once → ALLOW creates the PayPal
order (`PAYPAL_AUTHORIZATION_REQUIRED` + approval URL), VERIFY
(`VERIFICATION_REQUIRED`) and STOP (`BLOCKED`) create nothing.

Approve in Sandbox, return, then capture — server-side only, re-checking the
*stored* ALLOW decision (frontend verdicts impossible: they are never
accepted). Replays return the stored capture without re-calling PayPal
(row-locked check-then-capture); webhooks stay authoritative for final state
(COMPLETED/FAILED). Transaction rows reuse `PENDING`/`CANCELLED`/
`COMPLETED`/`FAILED`. The primary UI (`/payment`) is toggle-free (amount,
recipient, recipient PayPal email, merchant, message, URL -> Pay securely) and
requires sign-in; the public manual-risk UI was removed.

### Android call-state integration (Milestone 11 — device signal only)

Architecture: Android (`TelephonyManager`/`TelephonyCallback`, `READ_PHONE_STATE`
only) maps `OFFHOOK → activeCall=true`, `IDLE`/`RINGING → false` → `POST
/api/device-signals/call-state` (per-device `X-GuardPay-Device-Token` +
`{deviceId, activeCall, observedAt}`) → backend validates the hashed
credential and upserts one owner-scoped `device_call_states` row →
`POST /api/risk/evaluate-automatic` accepts optional `deviceId`, resolves the
latest trusted state (observations older than **5 minutes are STALE, never
active**), and the collector emits `ACTIVE_CALL` (+15, `source: DEVICE`) into
the unchanged engine. Unknown/absent devices → unavailable. The automatic
request has no `activeCall` field, so clients cannot forge it.

Pipeline: payment context → GuardPay orchestration → automatic signal collection
(history, content/URL, device, network, validated AI evidence) → deterministic
risk engine → ALLOW/VERIFY/STOP → PayPal authorization and capture.

Android app (`android/`, Kotlin, `com.guardpay.device`): a thin WebView shell
around the exact same React checkout, so the phone UI matches the web UI. The
shell reads call state with `TelephonyManager`/`TelephonyCallback` (a random
UUID in `SharedPreferences`, never IMEI). After sign-in the page registers the
device through the authenticated `/api/devices` API; the shell persists the
returned per-device credential and posts call state with the same device id
the page uses. It shows **Automatic** call detection and, when the user taps
**Pay** during an
active call, a blocking fraud warning: "You are on a phone call" with
**Cancel payment** / **Continue anyway** choices. Cancel keeps the form so
the user can hang up and retry; Continue runs the normal risk check. It opens
directly on `/payment`; the rest of the app is reachable from the header.
External links (PayPal approval) open in the device browser.
Only `READ_PHONE_STATE` + `INTERNET` are requested — no
RECORD_AUDIO/CONTACTS/SMS/location.

Build the installable debug APK:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/build-android-apk.ps1
# -> GuardPay-debug.apk (also android/app/build/outputs/apk/debug/app-debug.apk)
```

The script builds `frontend/dist` and Gradle bundles it into
`android/app/src/main/assets`. The backend URL comes from
`android/local.properties` (`guardpayApiBaseUrl`) or a `-P` flag; device
credentials are issued at runtime, never baked into the APK. A physical phone
must reach the backend over the LAN (not `localhost`), and the backend must
allow the WebView origin `https://appassets.androidplatform.net`
(`CORS_ALLOWED_ORIGINS`; included in the default). For an emulator use
`guardpayApiBaseUrl=http://10.0.2.2:8080`.

Call detection is foreground-only: Android pauses telephony observation when
the app is backgrounded. The demo APK is debug-signed, not a Play Store
release, and PayPal Sandbox approval still happens in the browser.

Device credentials are now issued by authenticated registration and stored
only as hashes server-side; rotation and revocation are explicit endpoints.
Production should add short-lived credentials and platform/device attestation.

Limitations (honest): observation is foreground-only (no foreground service
by design); Android lifecycle/background restrictions can pause monitoring;
no emulator/device was available here for on-device runs, so device behavior
is covered by JVM unit tests + live backend flows, not a physical call.
Production PayPal could obtain equivalent trusted device context inside its
own authorized mobile/payment environment.

## Judge Demo (2–3 minutes)

Full instructions: `docs/demo.md`. Automated safety-invariant suite:
`pwsh -File scripts/demo-guarded-flow.ps1 -RecipientEmail <sandbox-payee>`.

1. Register or sign in, then open `/payment`; the workspace is private to the account.
2. Select **Safe Payment** (preset fills $45, known names, normal message).
3. Show automatic risk analysis → **LOW / ALLOW** and the contributing signals.
4. Show the real PayPal Sandbox order + approval link.
5. Select **Suspicious Payment** (preset fills $2,499, new names, scam text + URL).
6. Show real AI evidence (impersonation, urgency, pressure, secrecy, URL) and the
   deterministic calculation → **VERIFY** or **STOP**.
7. If **VERIFY**: the controlled challenge appears — **no PayPal order exists**.
   If TOTP is enrolled, enter the authenticator code; otherwise read the
   simulated code from the loopback dev outbox. A wrong code decrements attempts.
   The order is created only after the server verifies.
8. If **STOP**: show **PayPal Order ID: None** — no order was created.
9. Open `/transactions`: every run is stored with its decision; select a row to
   show amount, PayPal references, verification outcome, and **why** (findings).
10. Open `/dashboard`: show decision/blocked-value/latency/AI/verification
    aggregates, TOTP enrollment, the AI-outage simulator, and recent SSE alerts.
11. Explain: "GuardPay doesn't ask the user to identify the scam. It
    automatically analyzes the payment context, validates AI evidence, and lets
    a deterministic risk engine decide whether the payment can reach PayPal —
    and it never creates a PayPal order unless the decision allows it."

## Architecture

```text
PayPal
   ↑
Payment Authorization (user approves in Sandbox; server captures)
   ↑
GuardPay Integration Layer (orchestrator, gates, idempotency, webhooks)
   ↑
Deterministic Risk Engine (sole ALLOW / VERIFY / STOP authority)
   ↑
AI Evidence + Automatic Signals (history, device, content → validated findings)
   ↑
Transaction Context (amount, payees, device, message, URL)
```

"GuardPay is a reference integration layer demonstrating how AI-powered
payment safety could integrate with PayPal payment authorization flows."
It is a hackathon prototype — not production fraud infrastructure, not part
of the official PayPal app, and not endorsed by PayPal.

## Known limitations

- PayPal Sandbox approval/capture needs a human with a Sandbox Personal account.
- External webhook delivery needs a public tunnel (not configured; never faked).
- Device call-state is foreground-only with Android lifecycle limits; the APK is
  debug-signed.
- AI quality varies by model/provider; strict schema + confidence gates and
  deterministic fallback absorb failures.
- Sessions are server-side cookie sessions (no OAuth, password reset, email
  verification, or MFA on login yet). TOTP is a payment-verification channel.
  The simulated verification outbox is temporary and loopback-only.
- No live Google Safe Browsing or URL-reputation integration; URL analysis is
  structural and local, so a clean-looking phishing domain can pass.
- Learning is only as useful as the verified feedback volume. Below the fixed
  minimums there is no training; deployment is a manual admin decision and the
  calibration only moves `ALLOW`/`VERIFY`/`STOP` thresholds, not the score.
- Admin-only intelligence, feedback review, and learning consoles require a
  user promoted through `GUARDPAY_INTELLIGENCE_ADMIN_EMAILS`; clients cannot
  grant themselves admin.
- Host port 5432 may clash with a local PostgreSQL — use `DB_PORT` or stop it.

## Current step — what is / isn't done

Done:

- [x] Monorepo scaffold (`frontend/`, `backend/`, `ai-service/`, `docs/`)
- [x] Deterministic risk engine + `ALLOW`/`VERIFY`/`STOP` (sole authority)
- [x] Automatic signals (history, device) + AI evidence (never decisions)
- [x] **Adaptive behavioral profiling** — per-user learned baselines for amount, time, recipient, device, geo, and frequency (rolling 7/30/90-day windows)
- [x] Guarded flow with idempotency, server-side capture, webhooks
- [x] Full lifecycle status + controlled VERIFY (order-free until verified)
- [x] Receipt + transaction history with persisted, explainable findings
- [x] End-to-end Sandbox demo suite (`scripts/demo-guarded-flow.ps1`) + docs
- [x] Multi-user authentication with owner-scoped data, idempotency, and SSE
- [x] TOTP verification channel, persisted dashboard aggregates, SSE alerts
- [x] Device registration/rotation, call-state reporting, owner-scoped blocklist
- [x] Recipient intelligence: owner-scoped reports/summaries, hashed identifiers, admin review, evidence-only reputation
- [x] Feedback lifecycle: structured labels, append-only events/reviews, admin review/queue/metrics/export
- [x] Continuous learning: verified-label datasets, checksum/split/exclusions, candidate calibration, approval/deploy/rollback, provenance
- [x] Admin pages for feedback review and learning, plus link navigation

Explicitly NOT done (honest):

- [ ] Password reset, email verification, OAuth, and MFA-at-login
- [ ] External webhook delivery (needs a public tunnel/deploy)
- [ ] Blocklist management screen
- [ ] Production mobile release/background call monitoring
- [ ] Live URL reputation service
- [ ] Automatic model deployment or a production ML training pipeline; calibration is threshold-only and manually promoted

## Quick verify

```powershell
# unit/integration tests
mvn -f backend clean test          # backend
npm --prefix frontend run test     # frontend
python -m pytest ai-service -q     # ai-service

# end-to-end safety invariants (backend must be running)
pwsh -File scripts/demo-guarded-flow.ps1 -RecipientEmail sb-merchant@business.example.com
```
