# GuardPay

**AI-powered real-time payment safety system.**

GuardPay detects when a user may be getting manipulated into making a fraudulent PayPal payment — especially through social-engineering scams such as fake PayPal support calls, urgent payment requests, suspicious invoices, suspicious URLs, and unusual transaction behavior.

> **Init-step status:** project structure + architecture only. No business features are implemented yet (no PayPal integration, no auth, no entities, no risk engine, no AI agents). See `docs/`.

## Architecture overview

```text
React + Vite
    |
    v
Spring Boot
   |
   ├── PostgreSQL
   ├── PayPal APIs
   ├── PayPal Webhooks
   ├── Deterministic Risk Engine
   |
   v
Python FastAPI
   |
   v
CrewAI
   |
   ├── Transaction Risk Agent
   ├── Social Engineering Agent
   └── Content Safety Agent
   |
   v
LLM
   |
   v
Structured Findings
   |
   v
Spring Boot Risk Engine
   |
    ├── ALLOW
    ├── VERIFY
    └── STOP
    |
    v
React + Vite
```

**Security principle:** the LLM never directly decides whether a payment is allowed.

```text
AI agents → structured evidence/findings → Spring Boot deterministic risk engine → final score → ALLOW / VERIFY / STOP
```

- `docs/architecture.md` — system architecture + service responsibilities.
- `docs/api-contract.md` — planned API boundaries (React + Vite ↔ Spring Boot, Spring Boot ↔ Python, PayPal → Spring Boot).
- `docs/risk-engine.md` — planned design of the deterministic risk engine (not implemented).

## Technology stack (do not change)

**Frontend:**

- React + Vite, TypeScript, Tailwind CSS, shadcn/ui, AG Grid

**Backend:**

- Java 17, Spring Boot 3.x, Spring Security, Spring Data JPA / Hibernate, Maven, PostgreSQL

**AI Service:**

- Python 3.12+, FastAPI, CrewAI, LLM API

**External:**

- PayPal REST APIs, PayPal Webhooks, URL reputation/security API where appropriate

**Deployment target:** Render

## Repository layout

```text
guardpay/
├── frontend/     # React + Vite + TS + Tailwind + shadcn/ui + AG Grid (skeleton only)
├── backend/      # Spring Boot 3.x / Java 17 (package skeleton only)
├── ai-service/   # Python FastAPI + CrewAI (structure only, no agent logic)
├── docs/
│   ├── architecture.md
│   ├── api-contract.md
│   └── risk-engine.md
├── .gitignore
├── README.md
└── docker-compose.yml
```

Existing product specs in `docs/` (`GuardPay_PRD.md`, `GuardPay_Project_Specification.md`) are the source of truth for later milestones.

## Local development prerequisites

- Git
- Node.js 20+ + npm (frontend)
- Java 17 + Maven 3.9+ (backend)
- Python 3.12+ (ai-service)
- PostgreSQL 16+ (or Docker + Docker Compose)
- PayPal Developer sandbox account (later milestone — not needed for this init step)
- LLM API key (later milestone — not needed for this init step)

Planned local URLs (not all running yet):

| Service    | URL                   |
| ---------- | --------------------- |
| Frontend   | http://localhost:3000 |
| Backend    | http://localhost:8080 |
| AI service | http://localhost:8000 |
| PostgreSQL | localhost:5432        |

## Planned services

| Directory    | Description                                                                 |
| ------------ | --------------------------------------------------------------------------- |
| `frontend/`  | Payment interface, risk-warning interface, dashboard, transaction history (AG Grid), real-time risk updates. |
| `backend/`   | Auth, users, transactions, recipients, merchants, payment processing, PayPal integration + webhooks, PostgreSQL, deterministic risk engine + final ALLOW/VERIFY/STOP decision, REST APIs, real-time communication. |
| `ai-service/`| FastAPI + CrewAI agents (transaction risk, social engineering, content safety), LLM interaction, structured AI findings only. No payment decisions. |

## Backend quickstart (Milestone 2 — foundation)

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

# 5. Verify (no auth needed for the probe)
curl http://localhost:8080/api/health
# → {"status":"UP","service":"guardpay-backend"}
```

Layering rule: `Controller → Service → Repository → Database`.
Controllers return DTOs (`common/dto/ApiResponse`) — never JPA entities.
CORS default-allows the Vite dev server (`http://localhost:3000`,
override via `CORS_ALLOWED_ORIGINS`). Only `GET /api/health` is public;
all other APIs deny by default until the auth milestone.

Database (Milestone 3): Flyway owns the schema (`backend/src/main/resources/db/migration/V1–V6`,
applied automatically at startup); Hibernate stays on `ddl-auto=validate`.
Tables: `users, merchants, recipients, transactions, payment_context, risk_assessments`.
Note: if a local PostgreSQL already occupies host port 5432, either stop it
or point the backend at the compose DB via `DB_PORT`.

## PayPal Sandbox setup (Milestone 7 — foundation, no orders yet)

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

## PayPal Orders flow (Milestone 8 — Sandbox, risk-gated)

Flow: `POST /api/paypal/orders` (amount USD + risk context) → backend re-runs
the Risk Engine server-side, stores Transaction + PaymentContext +
RiskAssessment, and creates the PayPal order **only for ALLOW**
(status `CREATED` + Sandbox approval URL). STOP/VERIFY create no PayPal order.

Sandbox testing (buyer = PayPal Sandbox Personal test account):

```powershell
# 1. Create a small order (ALLOW context)
curl -X POST http://localhost:8080/api/paypal/orders `
  -H "Content-Type: application/json" `
  -d '{"amount": 5.00, "currency": "USD", "historicalAverage": 87}'
# → {"orderId": "...", "status": "CREATED", "approvalUrl": "https://www.sandbox.paypal.com/checkoutnow?...", ...}

# 2. Open approvalUrl in a browser, log in with the Sandbox Personal account,
#    approve, return to GuardPay.

# 3. Capture (allowed only by the stored server-side ALLOW decision)
curl -X POST http://localhost:8080/api/paypal/orders/{orderId}/capture
# → {"orderId": "...", "status": "COMPLETED", "captureId": "...", ...}
```

Risk gate: STOP → no order, capture impossible; VERIFY → no order, HTTP 403
gate body on capture attempts (`{"allowed": false, ...}`); unknown order →
404. The frontend can never force ALLOW — capture checks the stored decision.
Transaction status mapping: PENDING (order created / verify-required),
CANCELLED (stopped), COMPLETED/FAILED (capture outcome). No real money moves
outside explicit Sandbox approval + allowed capture.

### M14 — validation, hardening, idempotency (no new AI model work)

`POST /api/payments/guarded` accepts an optional `Idempotency-Key` header:
repeats return the stored response verbatim (same `paymentId` *and* PayPal
order — no second order), a reused key with different content is rejected
(409), and the UNIQUE constraint arbitrates races. Capture replays return the
stored result without re-calling PayPal (row-locked check-then-capture), so
double-clicks cannot double-charge. The primary `/payment` UI is toggle-free
(amount, recipient, merchant, message, URL); the manual demo survives at
`/payment/manual`. Webhook, engine, and threshold behavior are unchanged.

Real-LLM status: no `OPENAI_API_KEY` is available in this environment, so the
live hero runs deterministically (STOP via the guarded endpoint additionally
requires AI/device evidence by design — deterministic signals top out at
VERIFY). AI-unavailable fallback, forged-field rejection, and the full
mocked pipeline (including an AI-driven 100/STOP merge test) are green.

### M15 — end-to-end validation (key configured, account unfunded)

Architecture: User → Guarded Payment → Automatic Signals
(Transaction/Merchant/Recipient, Device, Content → AI) → Validated Findings
→ Deterministic Risk Engine → ALLOW/VERIFY/STOP → PayPal (order,
authorization, server capture, webhook). **GuardPay does not bypass PayPal
authorization. It automatically evaluates payment risk and controls whether
an eligible payment can proceed.**

Validated live: AI health (`ai_configured: true`), guarded ALLOW (+ real
Sandbox order, idempotent replay byte-identical with exactly one transaction
row), VERIFY 40/MEDIUM with no order, forged-field rejection, manual hero
100/CRITICAL/STOP, both healths, leak-free logs, clean database afterward.
`/payment` shows a collapsible Developer details block (payment ID, AI
status, signal codes, order ID, payment status) — no secrets, no raw JSON
dumps. Real-LLM hero: **pending — the configured key's account has no
credits** (`RateLimitError` 429 at OpenAI account level, verified); the
service fails safely (`UNAVAILABLE` fallback) and the full mocked merge path
is unit-proven. Human Sandbox approval/capture and tunneled webhooks remain
user-executed; external webhook delivery was not live-tested (localhost
unreachable).

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

## Automatic signal collection (Milestone 10 — derived, never client-supplied)

`POST /api/risk/evaluate-automatic` accepts payment context only
(`amount`, `userId`, optional `recipientId`/`merchantId`/`transactionId`) —
there are no risk booleans on this path, so forged flags are ignored.

CURRENT (backend-derived): `UNUSUAL_AMOUNT` (same ≥5× definition as the
engine, shared constant), `NEW_RECIPIENT` / `UNKNOWN_MERCHANT` (prior
COMPLETED history, current excluded), with `INSUFFICIENT_HISTORY` /
`..._IDENTITY_UNAVAILABLE` reasons instead of guesses; `UNEXPECTED_INVOICE`
and `SUSPICIOUS_URL` report `INSUFFICIENT_CONTEXT` /
`URL_REPUTATION_NOT_CONFIGURED` until their integrations exist.

FUTURE (report `NOT_AVAILABLE_YET`): `URGENT_LANGUAGE` +
`SOCIAL_ENGINEERING_DETECTED` (AI analysis). `ACTIVE_CALL` left this group in
Milestone 11 — it is now device-derived when a fresh paired-device state
exists, otherwise unavailable. The old manual toggles (`POST /api/risk/evaluate`,
Milestone 6 UI) stay unchanged for demos/regression but are NOT authoritative
— only derived signals feed the automatic decision.

### M12 AI risk intelligence (evidence, never decisions)

**AI findings are evidence, not payment decisions.** The Python service
(`ai-service/`, FastAPI on :8001) analyzes message/invoice text, payment
context, and URL *structure* through three focused agents (social
engineering, content/scam patterns, URL heuristics — no URL fetching, no
reputation DB) and returns closed-vocabulary findings with confidence
(`URGENT_LANGUAGE`, `IMPERSONATION`, `PRESSURE_TO_PAY`, `SECRECY_REQUEST`,
`SUSPICIOUS_PAYMENT_INSTRUCTION`, `SCAM_PATTERN`,
`SUSPICIOUS_URL_STRUCTURE`, `UNEXPECTED_INVOICE_CONTEXT`). No CrewAI — a
fixed deterministic pipeline needs no multi-agent framework. The model is
configured via `OPENAI_API_KEY`/`OPENAI_MODEL` (never committed, never
logged); without a key the service boots and `/api/v1/analyze` fails
clearly (503) instead of faking results.

Spring Boot calls it only when the automatic request carries content, maps
validated findings (unknown codes skipped, confidence < 0.5 skipped) onto its
*existing* engine flags (five social codes collapse onto one +20
`SOCIAL_ENGINEERING` — no double counting, no new weights, cap still 100),
and reports `aiStatus: NOT_REQUESTED | AVAILABLE | UNAVAILABLE`. Any AI
failure degrades to deterministic-only evaluation; AI can never set
ALLOW/VERIFY/STOP. Privacy: minimum context only (amount, names, flags,
text, URL, call boolean) — no audio, phones, contacts, or emails, and message
bodies are never logged.

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
recipient, merchant, message, URL → Pay securely); the M6 manual demo lives
on at `/payment/manual` for regression.

### Android call-state integration (Milestone 11 — device signal only)

Architecture: Android (`TelephonyManager`/`TelephonyCallback`, `READ_PHONE_STATE`
only) maps `OFFHOOK → activeCall=true`, `IDLE`/`RINGING → false` → `POST
/api/device-signals/call-state` (`X-GuardPay-Device-Token` + `{deviceId,
activeCall, observedAt}`) → backend upserts one `device_call_states` row →
`POST /api/risk/evaluate-automatic` accepts optional `deviceId`, resolves the
latest trusted state (observations older than **5 minutes are STALE, never
active**), and the collector emits `ACTIVE_CALL` (+15, `source: DEVICE`) into
the unchanged engine. Unknown/absent devices → unavailable. The automatic
request has no `activeCall` field, so clients cannot forge it.

Pipeline: PayPal → GuardPay Integration Layer → Automatic Signal Collection
(Transaction/Merchant history + Device `ACTIVE_CALL`) → (AI Agents: future)
→ Deterministic Risk Engine → ALLOW/VERIFY/STOP.

Android app (`android/`, Kotlin, `com.guardpay.device`): one screen — Call
Status ACTIVE/IDLE, Backend CONNECTED/DISCONNECTED, Device ID (random UUID in
`SharedPreferences`, never IMEI), privacy line "No audio is recorded or
analyzed." Run: open in Android Studio or
`gradle -p android :app:assembleDebug -PguardpayDeviceToken=...`
(`guardpayApiBaseUrl` default `http://10.0.2.2:8080` for the emulator;
physical devices need the dev machine's LAN IP — `localhost` on a phone is
the phone itself). No RECORD_AUDIO/CONTACTS/SMS/location permissions.

"This is a development/hackathon device pairing mechanism. Production
implementation should use authenticated device registration, short-lived
credentials, and platform/device attestation."

Limitations (honest): observation is foreground-only (no foreground service
by design); Android lifecycle/background restrictions can pause monitoring;
no emulator/device was available here for on-device runs, so device behavior
is covered by JVM unit tests + live backend flows, not a physical call.
Production PayPal could obtain equivalent trusted device context inside its
own authorized mobile/payment environment.

## Judge Demo (2–3 minutes)

1. Open GuardPay at `/payment`.
2. Select **Safe Payment** (preset fills $45, known names, normal message).
3. Show automatic risk analysis → **LOW / ALLOW**, score 0.
4. Show the real PayPal Sandbox order + approval link.
5. Reset Demo.
6. Select **Suspicious Payment** (preset fills $2,499, new names, scam text + URL).
7. Show transaction signals (new recipient, large amount, unknown merchant).
8. Show real AI evidence (impersonation, urgency, pressure, secrecy, URL).
9. Show deterministic calculation → **CRITICAL / STOP**.
10. Show **PayPal Order ID: None** — no order was created.
11. Reset Demo.
12. Explain: "GuardPay doesn't ask the user to identify the scam. It
automatically analyzes the payment context, uses AI for evidence, and lets
a deterministic risk engine decide whether the payment can reach PayPal."

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
- Device call-state is foreground-only with Android lifecycle limits.
- Free-tier AI models vary run to run; strict schema + confidence gates absorb it.
- Demo user ownership until authentication exists; temp-public dev endpoints.
- Host port 5432 may clash with a local PostgreSQL — use `DB_PORT` or stop it.

## Current step — what is / isn't done

Done (this step):

- [x] Monorepo scaffold (`frontend/`, `backend/`, `ai-service/`, `docs/`)
- [x] Valid placeholder configs (`pom.xml`, `package.json`, `requirements.txt`, `docker-compose.yml`)
- [x] Architecture / API-contract / risk-engine design docs

Explicitly NOT done (awaiting next instruction):

- [ ] PayPal integration, auth, DB entities, risk engine, AI agents, CrewAI logic, fake APIs

## Quick verify (this step)

```powershell
# repo structure
Get-ChildItem -Recurse -Depth 3 | Select-Object FullName

# compose file is valid YAML (requires Docker Compose v2)
docker compose config

# backend config parses (requires Maven)
mvn -f backend/pom.xml validate

# frontend config parses (requires Node)
npm --prefix frontend pkg get name version

# ai-service deps list parses (requires Python)
python -m pip install --dry-run -r ai-service/requirements.txt
```
