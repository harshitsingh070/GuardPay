# GuardPay

**Stop social-engineering payments before they reach PayPal.**

GuardPay is a PayPal-native payment-safety layer for fake support calls, urgent
payment requests, suspicious invoices, and phishing links. It combines
automatic signals with AI evidence, then lets a server-side deterministic engine
decide `ALLOW` / `VERIFY` / `STOP` before any PayPal order can be created.

> **The difference:** AI explains the risk. The deterministic engine makes the
> decision. The LLM can never approve a payment.

## Demo

> **Watch the 3-minute demo:** <https://youtu.be/-U2VJCCrRs4>
>
> **Run it:** `docker compose up --build` → <http://localhost:3000>

**What judges will see:**

1. **Suspicious payment** → AI evidence + deterministic signals → `VERIFY` or
   `STOP`; **no PayPal order is created**.
2. **Safe payment** → `ALLOW` → real PayPal Sandbox approval and capture.
3. **Proof** → `/transactions` shows the exact findings; `/dashboard` shows
   blocked value, latency, AI availability, and verification outcomes.

No PayPal or Groq credentials are required for the deterministic flow. Full
script: [`docs/demo.md`](docs/demo.md).

**PayPal AI Hackathon** · **License:** MIT (see [`LICENSE`](LICENSE)).

## Table of Contents

- [Demo](#demo)
- [What is GuardPay?](#what-is-guardpay)
- [Features](#features)
- [How it works](#how-it-works)
- [Architecture](#architecture)
- [Tech stack](#tech-stack)
- [Prerequisites](#prerequisites)
- [Quickstart (Docker)](#quickstart-docker)
- [Manual setup](#manual-setup)
- [Configuration](#configuration)
- [PayPal Sandbox setup](#paypal-sandbox-setup)
- [Groq setup](#groq-setup)
- [Demo walkthrough](#demo-walkthrough)
- [Key API endpoints](#key-api-endpoints)
- [Testing](#testing)
- [Android shell](#android-shell)
- [Troubleshooting](#troubleshooting)
- [Security and privacy](#security-and-privacy)
- [What is real vs simulated](#what-is-real-vs-simulated)
- [Limitations](#limitations)
- [Project structure](#project-structure)
- [Documentation](#documentation)
- [License](#license)

## What is GuardPay?

Social-engineering scams work because the victim approves the payment. The
attacker does not need to break PayPal; they convince the account holder to send
money. GuardPay adds a guardrail before PayPal authorization:

1. The user submits payment context — amount, recipient, merchant, message, URL.
2. GuardPay automatically collects behavioral, history, device, network,
   content/URL, and optional AI evidence.
3. A server-side deterministic engine produces `ALLOW`, `VERIFY`, or `STOP`.
4. Only `ALLOW` can create a PayPal order. `VERIFY` creates nothing until a
   server-verified challenge succeeds. `STOP` creates nothing.

**AI is evidence, not the decision.** AI explains what it sees; the
deterministic engine remains the sole decision authority.

## Features

### Payment safety

- **Guarded PayPal checkout** — one automatic flow for amount, recipient,
  merchant, message, and URL.
- **Automatic signal collection** — per-user behavioral baselines (amount, time,
  recipient, device, geo, frequency), transaction/recipient/merchant history,
  velocity tiers, split payments, blocklist, content/URL structure, device
  novelty, geo novelty, impossible travel, VPN/proxy, and active-call state.
- **Deterministic risk engine** — weighted, explainable signals produce
  `ALLOW`, `VERIFY`, or `STOP`; clients cannot submit risk verdicts.
- **Explainable findings** — every decision is stored with the signals and
  findings that produced it.
- **Idempotent checkout and capture** — retries return the stored result;
  double-clicks cannot double-charge.

### AI evidence

- **Groq-backed analysis** — default model `openai/gpt-oss-20b` through the
  OpenAI-compatible API.
- **Two focused agents** — social engineering and content scams, plus
  deterministic local URL rules. No CrewAI or multi-agent framework.
- **Closed vocabulary** — `URGENT_LANGUAGE`, `IMPERSONATION`,
  `PRESSURE_TO_PAY`, `SECRECY_REQUEST`, `SUSPICIOUS_PAYMENT_INSTRUCTION`,
  `SCAM_PATTERN`, `SUSPICIOUS_URL_STRUCTURE`, `UNEXPECTED_INVOICE_CONTEXT`.
- **Validated merge** — Spring Boot validates findings and maps them onto
  existing engine signals. AI cannot add weights, subtract risk, or set an
  action.
- **Graceful fallback** — without a key, or when a model call fails, the service
  still returns local URL findings and the deterministic engine decides.

### Controlled verification

- **Order-free VERIFY** — a `VERIFY` decision creates no PayPal order.
- **TOTP or simulated challenge** — TOTP when an authenticator is enrolled;
  otherwise a simulated code in the loopback-only development outbox.
- **Server-side confirmation** — hashed answer, TTL, capped attempts, and no
  order until the backend confirms success.
- **No code leakage** — the code is never returned in the payment response or
  written to logs.

### PayPal integration

- **PayPal Sandbox Orders v2** — recipient-bound order creation, human approval,
  and server-side capture.
- **Signature-verified webhooks** — PayPal state sync without changing the
  GuardPay risk decision.
- **Authorization is never bypassed** — the user still approves in PayPal; the
  frontend cannot force an `ALLOW`.
- **Owner-scoped references** — orders, captures, transactions, and idempotency
  keys belong to the signed-in account.

### History and operations

- **Transaction history** — AG Grid list with a detail panel showing amount,
  PayPal references, verification outcome, and the exact decision findings.
- **Dashboard** — decision counts, blocked value, p50/p95 latency, AI
  availability, alert delivery, and verification success.
- **SSE alerts** — persisted high-risk alerts stream to the in-app banner and
  dashboard.
- **Blocklist API** — owner-scoped recipient blocklist; a blocked recipient
  forces `CRITICAL / STOP`.
- **Notification outbox** — critical alerts enqueue email/SMS delivery without
  changing the payment decision.

### Recipient intelligence and learning

- **Recipient intelligence** — owner-scoped reports, hashed identifiers,
  admin-reviewed evidence, and bounded reputation that feeds the existing score.
- **Feedback lifecycle** — structured `FRAUD`/`LEGITIMATE` labels, admin review,
  append-only events, and audited transitions.
- **Continuous learning** — admin-verified labels become immutable datasets; a
  calibration candidate can only move `VERIFY`/`STOP` thresholds and requires
  explicit approval, deployment, and rollback.
- **Provenance** — each assessment records the calibration version that produced
  it.

### Android call-state

- **Kotlin WebView shell** — the same React checkout inside a thin Android app.
- **Foreground call detection** — `TelephonyManager`/`TelephonyCallback` reports
  `OFFHOOK`, `IDLE`, and `RINGING`.
- **Active-call warning** — tapping Pay during a call shows a blocking fraud
  warning with cancel/continue choices.
- **Minimal permissions** — `READ_PHONE_STATE` and `INTERNET` only; no audio,
  contacts, SMS, or location.
- **Runtime credentials** — device credentials are issued by the authenticated
  `/api/devices` API, never baked into the APK.

## How it works

```text
Payment context → automatic signals + optional AI evidence
→ Spring Boot validation → deterministic risk engine
→ ALLOW / VERIFY / STOP → PayPal order only when allowed
```

1. The signed-in user submits payment context (no risk booleans, no client
   verdict).
2. The backend resolves identities and derives behavioral, history, recipient,
   device, network, content/URL, and optional AI signals.
3. The deterministic engine produces the final decision and explainable
   findings.
4. Only `ALLOW` creates a PayPal order; `VERIFY` waits for a server-verified
   challenge; `STOP` creates nothing.

### Decision semantics

| Decision | Meaning | PayPal order created? |
| --- | --- | --- |
| `ALLOW` | Risk is acceptable under the current policy. | Yes, after the backend persists the decision. |
| `VERIFY` | The payment needs a controlled challenge. | No, until the challenge is confirmed server-side. |
| `STOP` | The payment is blocked. | No, and capture is impossible. |

### AI finding vocabulary

`URGENT_LANGUAGE`, `IMPERSONATION`, `PRESSURE_TO_PAY`, `SECRECY_REQUEST`,
`SUSPICIOUS_PAYMENT_INSTRUCTION`, `SCAM_PATTERN`,
`SUSPICIOUS_URL_STRUCTURE`, `UNEXPECTED_INVOICE_CONTEXT`.

Confidence is observation certainty, not a risk score. Findings with unknown
codes or confidence below `0.5` are skipped by Spring Boot.

### Signal sources

Behavioral baselines, transaction/recipient/merchant history, amount and
velocity tiers, split payments, blocklist, content and URL structure, social
engineering, known-recipient compromise, missing context, device novelty, geo
novelty, impossible travel, VPN/proxy, active call, and validated AI evidence.

## Architecture

```text
React + Vite web app / Android WebView shell
        |
        |  HTTPS + HttpOnly session cookie + CSRF
        v
Spring Boot
   |-- PostgreSQL + Flyway
   |-- PayPal Sandbox orders, capture, and signature-verified webhooks
   |-- automatic signals: behavioral, history, recipient/merchant, velocity,
   |   content/URL, network, blocklist, device
   |-- optional validated AI evidence (FastAPI + Groq)
   `-- deterministic risk engine (sole decision authority)
          |-- ALLOW
          |-- VERIFY -> controlled TOTP/simulated challenge
          `-- STOP
```

### Components

| Component | Responsibility |
| --- | --- |
| `frontend/` | React + Vite checkout, dashboard, transactions, alerts, feedback/learning admin pages. |
| `backend/` | Spring Boot API, automatic signals, deterministic risk engine, PayPal integration, PostgreSQL/Flyway, auth and owner scoping. |
| `ai-service/` | FastAPI evidence service: local URL rules plus focused Groq agents. Returns findings only; never decides. |
| `android/` | Kotlin WebView shell with foreground call-state reporting. |
| PostgreSQL | Transaction, risk, feedback, learning, alert, and idempotency persistence. |
| PayPal Sandbox | Orders v2, approval, capture, and webhook state sync. |
| Groq | OpenAI-compatible LLM API used for evidence extraction. |

**Security principle:** the LLM never directly decides whether a payment is
allowed.

## Tech stack

| Layer | Technology |
| --- | --- |
| Frontend | React, Vite, TypeScript, Tailwind CSS, shadcn/ui, AG Grid |
| Backend | Java 17, Spring Boot 3.x, Spring Data JPA/Hibernate, Maven, PostgreSQL 16, Flyway |
| AI service | Python 3.12+, FastAPI, OpenAI SDK, Groq `openai/gpt-oss-20b` |
| Mobile | Kotlin Android WebView shell, `TelephonyManager`/`TelephonyCallback` |
| Packaging | Docker Compose |
| Integrations | PayPal Sandbox Orders v2, signature-verified webhooks, Groq API |
| Sponsor tool | AG Grid transaction history grid |

## Prerequisites

**Fast path (Docker):**

- Docker Desktop with Docker Compose.
- Optional: PayPal Sandbox credentials and a Groq API key.

**Manual path:**

- Java 17 + Maven 3.9+.
- Node.js 20+ and npm.
- Python 3.12+.
- PostgreSQL 16+, or Docker for the database.
- Optional: PayPal Sandbox app, Sandbox Personal account, and Groq key.

**PayPal Sandbox requirements:**

- A Sandbox app client id and secret.
- A Sandbox Personal account for the human approval step.
- A Sandbox payee email that exists in your PayPal Developer account. The
  preset recipient is a placeholder and PayPal may reject it.

**Shell note:** the examples use PowerShell. On macOS/Linux, use `export` for
environment variables and the equivalent Python/npm/Maven commands.

## Quickstart (Docker)

### 1. Clone the repository

```powershell
git clone <your-repo-url>
cd GuardPay
```

### 2. Configure optional credentials

```powershell
# Optional; the stack boots without them.
$env:PAYPAL_BASE_URL = "https://api-m.sandbox.paypal.com"
$env:PAYPAL_CLIENT_ID = "<sandbox-client-id>"
$env:PAYPAL_CLIENT_SECRET = "<sandbox-client-secret>"
$env:PAYPAL_WEBHOOK_ID = "<sandbox-webhook-id>"   # optional for local webhooks
$env:OPENAI_API_KEY = "<groq-key>"                # or GROQ_API_KEY
$env:GUARDPAY_INTELLIGENCE_ADMIN_EMAILS = "<your-email>"  # optional admin console
```

You can put the same values in a gitignored **repo-root `.env`** file instead:

```env
PAYPAL_BASE_URL=https://api-m.sandbox.paypal.com
PAYPAL_CLIENT_ID=
PAYPAL_CLIENT_SECRET=
PAYPAL_WEBHOOK_ID=
OPENAI_API_KEY=
GUARDPAY_INTELLIGENCE_ADMIN_EMAILS=
```

Never commit real credentials.

### 3. Start the stack

```powershell
docker compose up --build
```

### 4. Open the services

| Service | URL |
| --- | --- |
| Frontend | http://localhost:3000 |
| Backend health | http://localhost:8080/api/health |
| PayPal health | http://localhost:8080/api/paypal/health |
| AI health | http://localhost:8001/health |
| PostgreSQL | localhost:5432 |

### 5. Register and try the demo

Open `http://localhost:3000`, register an account, and go to `/payment`. Use the
**Safe Payment** and **Suspicious Payment** presets. See the
[Demo walkthrough](#demo-walkthrough) for the full flow.

Without PayPal credentials, registration, risk decisions, verification, history,
and the dashboard still work; only live order creation/capture is unavailable.
Without a Groq key, the AI service returns local URL findings only and the
deterministic engine decides alone.

### Stop, reset, and logs

```powershell
docker compose down          # stop containers
docker compose down -v       # stop and delete the PostgreSQL volume
docker compose logs -f       # follow logs
docker compose logs -f backend
docker compose logs -f ai-service
```

If port `5432` is already used by a local PostgreSQL, stop it or set `DB_PORT`.
If ports `3000`, `8080`, or `8001` are occupied, free them or change the
published ports in `docker-compose.yml`.

## Manual setup

### 1. Start PostgreSQL

```powershell
docker compose up -d db
```

Or use a local PostgreSQL 16 instance with the defaults from
`backend/.env.example`:

```text
DB_HOST=localhost
DB_PORT=5432
DB_NAME=guardpay
DB_USERNAME=guardpay
DB_PASSWORD=guardpay
```

Flyway applies the schema automatically at backend startup.

### 2. Start the backend

```powershell
mvn -f backend clean test
mvn -f backend spring-boot:run
```

The backend loads `backend/.env` automatically. Copy `backend/.env.example` to
`backend/.env` and set the optional PayPal, AI, and admin values. Explicit OS
environment variables take precedence.

Verify:

```powershell
curl http://localhost:8080/api/health
# {"status":"UP","service":"guardpay-backend"}

curl http://localhost:8080/api/paypal/health
# with credentials:    {"status":"UP","provider":"PAYPAL","environment":"SANDBOX"}
# without credentials: {"status":"DOWN","provider":"PAYPAL","environment":"SANDBOX"}
```

### 3. Start the frontend

```powershell
npm --prefix frontend install
npm --prefix frontend run dev
```

The frontend runs at `http://localhost:3000`. Configure the API URL in
`frontend/.env`:

```env
VITE_API_BASE_URL=http://localhost:8080
```

For a phone on the same network, use the dev machine's LAN IP instead of
`localhost`.

### 4. Start the AI service

```powershell
cd ai-service
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
copy .env.example .env
# Edit .env and set OPENAI_API_KEY or GROQ_API_KEY.
.\.venv\Scripts\python.exe -m uvicorn app.main:app --port 8001
cd ..
```

Verify:

```powershell
curl http://localhost:8001/health
# {"status":"ok","service":"guardpay-ai-service","ai_configured":true}
```

### 5. Open the app

Open `http://localhost:3000`, register, and use `/payment`. The backend and AI
service logs show startup and request activity; secrets are never logged.

## Configuration

### Backend (`backend/.env`)

| Variable | Default | Purpose |
| --- | --- | --- |
| `DB_HOST` | `localhost` | PostgreSQL host. |
| `DB_PORT` | `5432` | PostgreSQL port. |
| `DB_NAME` | `guardpay` | Database name. |
| `DB_USERNAME` | `guardpay` | Database user. |
| `DB_PASSWORD` | `guardpay` | Database password. |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000,https://appassets.androidplatform.net` | Browser/WebView origins. |
| `PAYPAL_BASE_URL` | `https://api-m.sandbox.paypal.com` | PayPal Sandbox API base URL. |
| `PAYPAL_CLIENT_ID` | empty | Sandbox app client id. |
| `PAYPAL_CLIENT_SECRET` | empty | Sandbox app secret. |
| `PAYPAL_WEBHOOK_ID` | empty | Webhook id for signature verification. |
| `AI_SERVICE_BASE_URL` | `http://localhost:8001` | Internal AI service URL. |
| `AI_SERVICE_INTERNAL_TOKEN` | empty | Shared `X-GuardPay-Internal-Token`; set the same value in the AI service. |
| `AI_SERVICE_READ_TIMEOUT_MS` | `8000` (Compose sets `15000`) | Timeout for the AI hop. |
| `GUARDPAY_AUTH_COOKIE_SECURE` | `false` | Set `true` behind HTTPS. |
| `GUARDPAY_DEV_VERIFICATION_OUTBOX` | `true` | Loopback-only simulated verification code outbox. |
| `GUARDPAY_VERIFICATION_LOG_CODE` | `true` | Print the simulated code to the backend terminal (local dev only). |
| `GUARDPAY_INTELLIGENCE_ADMIN_EMAILS` | empty | Comma-separated emails promoted to admin. |

`backend/.env.example` also documents auth, CSRF, session, login-rate-limit, and
behavioral-tuning settings.

### AI service (`ai-service/.env`)

| Variable | Default | Purpose |
| --- | --- | --- |
| `OPENAI_API_KEY` or `GROQ_API_KEY` | empty | Groq API key (`gsk_...`). Without it, local URL findings only. |
| `OPENAI_MODEL` | `openai/gpt-oss-20b` | Groq model. |
| `OPENAI_BASE_URL` | `https://api.groq.com/openai/v1` | OpenAI-compatible API endpoint. |
| `AI_SERVICE_INTERNAL_TOKEN` | empty | Must match the backend token when set. |

To use OpenAI instead, set `OPENAI_BASE_URL=https://api.openai.com/v1` and
`OPENAI_MODEL=gpt-4o-mini`. Any OpenAI-compatible gateway works the same way.

### Frontend (`frontend/.env`)

| Variable | Default | Purpose |
| --- | --- | --- |
| `VITE_API_BASE_URL` | `http://localhost:8080` | Backend URL used by the browser. |
| `VITE_CHECKOUT_MOCK` | unset | Set `true` only for the explicit mock checkout; do not use for the Sandbox demo. |

### Docker Compose

`docker compose` reads a repo-root `.env` automatically and also accepts OS
environment variables. The AI service receives `OPENAI_API_KEY` or
`GROQ_API_KEY`, `OPENAI_MODEL`, and `OPENAI_BASE_URL`; the backend receives the
PayPal, database, CORS, AI, and admin variables shown in `docker-compose.yml`.

## PayPal Sandbox setup

1. Open the **PayPal Developer Dashboard** and create a **Sandbox** app under
   **Apps & Credentials**.
2. Copy the Sandbox **client id** and **secret**.
3. Create or confirm a **Sandbox Personal** account for the buyer approval step.
4. Use a **Sandbox payee email** that exists in your developer account. The
   preset recipient is a placeholder and PayPal may reject it.
5. Optional: create a Sandbox webhook and copy its id into
   `PAYPAL_WEBHOOK_ID`. PayPal cannot reach `localhost`; external delivery needs
   a public tunnel or deployment.
6. Set the variables in your shell or gitignored `.env`:

```env
PAYPAL_BASE_URL=https://api-m.sandbox.paypal.com
PAYPAL_CLIENT_ID=
PAYPAL_CLIENT_SECRET=
PAYPAL_WEBHOOK_ID=
```

7. Restart the backend or `docker compose up --build`.
8. Verify:

```powershell
curl http://localhost:8080/api/paypal/health
# {"status":"UP","provider":"PAYPAL","environment":"SANDBOX"}
```

**Notes:** PayPal order creation is the only money-moving path and is reachable
only after `ALLOW` (or after a controlled `VERIFY` succeeds). No real money moves
outside explicit Sandbox approval and allowed capture. PayPal authorization is
never bypassed.

## Groq setup

1. Create a key at <https://console.groq.com/keys>. Keys start with `gsk_`.
2. For manual runs, put it in `ai-service/.env`:

```env
OPENAI_API_KEY=gsk_your_key_here
OPENAI_MODEL=openai/gpt-oss-20b
OPENAI_BASE_URL=https://api.groq.com/openai/v1
```

`GROQ_API_KEY` works instead of `OPENAI_API_KEY`. You can also export the
variables in your shell.

3. For Docker, put the key in the repo-root `.env` or export it before starting:

```powershell
$env:OPENAI_API_KEY = "gsk_your_key_here"
docker compose up --build
```

4. Verify the AI service:

```powershell
curl http://localhost:8001/health
# ai_configured should be true
```

5. Without a key, the service still starts and `/api/v1/analyze` returns local
   URL findings only. It never fabricates an LLM result. Never commit the key.

## Demo walkthrough

Full instructions: [`docs/demo.md`](docs/demo.md).

### Safe Payment

1. Register or sign in, then open `/payment`.
2. Select **Safe Payment**. The preset fills a modest amount, known names, and a
   normal message.
3. The backend evaluates the payment and returns `ALLOW` with a real PayPal
   Sandbox order and approval link.
4. If PayPal rejects the placeholder recipient, enter a Sandbox payee email that
   exists in your Developer account and retry.
5. Open the approval link, approve with the Sandbox Personal account, return, and
   capture. The server re-checks the stored `ALLOW` decision before capture.

### Suspicious Payment

1. Select **Suspicious Payment**. The preset fills a large amount, new names,
   scam text, and a suspicious URL.
2. The deterministic signals and optional AI evidence produce `VERIFY` or
   `STOP`. No PayPal order is created either way.
3. If the decision is `STOP`, show **PayPal Order ID: None**.
4. If the decision is `VERIFY`, the controlled challenge appears.

### VERIFY and TOTP

- If TOTP is enrolled on `/dashboard`, enter the authenticator code.
- Otherwise, the simulated code is always `666666`. It is also printed in the
  backend terminal (`[DEV ONLY] Simulated verification code ...`), and can be
  read from the loopback-only dev outbox:

```powershell
Invoke-RestMethod "http://localhost:8080/api/dev/verifications/<paymentId>/code"
```

- A wrong code decrements attempts; expiry and max attempts end the challenge.
- The order is created only after the server verifies the correct code.
- The code is never returned by the payment API. Terminal logging is for local
  development only; set `GUARDPAY_VERIFICATION_LOG_CODE=false` to disable it.

### Transactions and dashboard

- Open `/transactions` for AG Grid history. Select a row to see amount, PayPal
  references, verification outcome, AI status, and the exact decision findings.
- Open `/dashboard` for decision counts, blocked value, latency, AI
  availability, alert delivery, verification success, and TOTP enrollment.
- The dashboard can simulate an AI outage to show deterministic fallback.

### Alerts and blocklist

- High-risk alerts appear in the in-app banner over SSE at
  `/api/alerts/stream` and in the dashboard's recent-alerts list.
- Alerts persist and enqueue notification delivery; delivery failure does not
  change the decision.
- The owner-scoped blocklist API is `/api/blocklist/recipients`. A blocked
  recipient forces `CRITICAL / STOP`. There is no dedicated blocklist
  management screen yet.

### Automated safety-invariant suite

```powershell
pwsh -File scripts/demo-guarded-flow.ps1 -RecipientEmail "<sandbox-payee>"
```

The script drives the real backend over HTTP and checks that:

- a non-`ALLOW` decision never creates a PayPal order;
- a `VERIFY` decision creates a challenge and no order;
- a wrong code never verifies and never creates an order;
- the correct code verifies and only then creates the order;
- transaction history reflects the runs.

Exit code `0` means every checked invariant held. The script deliberately does
not assert specific scores because AI availability varies. It never fakes a
capture; human Sandbox approval stays manual.

### 3-minute video outline

1. State the problem: victims approve scam payments; AI should not be the final
   decision-maker.
2. Show the guarded checkout and the **Suspicious Payment** preset.
3. Show the AI evidence and deterministic calculation producing `VERIFY`/`STOP`
   with no PayPal order.
4. Show **Safe Payment** creating a real Sandbox order only on `ALLOW`.
5. Approve and capture in PayPal Sandbox.
6. Show `/transactions` and `/dashboard` for explainability, alerts, and metrics.
7. Close with the architecture: AI evidence → Spring Boot validation →
   deterministic engine → PayPal.

## Key API endpoints

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| `GET` | `/api/health` | Public | Backend liveness. |
| `POST` | `/api/auth/register`, `/api/auth/login` | Public | Create/sign in an account. |
| `GET` | `/api/auth/csrf`, `/api/auth/me` | Session | CSRF bootstrap and current user. |
| `POST` | `/api/payments/guarded` | Session | Run the guarded payment flow. |
| `POST` | `/api/payments/{paymentId}/verification/confirm` | Session | Confirm a `VERIFY` challenge. |
| `POST` | `/api/paypal/orders/{orderId}/capture` | Session | Capture an approved order. |
| `POST` | `/api/paypal/webhooks` | PayPal signature | Webhook state sync. |
| `GET` | `/api/transactions`, `/api/transactions/{id}` | Session | Owner-scoped history/detail. |
| `GET` | `/api/dashboard/summary` | Session | Dashboard aggregates. |
| `GET` | `/api/alerts/stream` | Session | SSE alert stream. |
| `GET`/`POST`/`DELETE` | `/api/blocklist/recipients` | Session | Owner-scoped blocklist. |
| `POST` | `/api/risk/evaluate-automatic` | Session | Automatic signal evaluation. |
| `POST` | `/api/device-signals/call-state` | Device token | Trusted call-state observation. |
| `POST` | `/api/devices` | Session | Register/rotate/revoke a device. |
| `GET` | `/api/paypal/health` | Public | PayPal configuration health. |
| `GET` | `/api/dev/verifications/{paymentId}/code` | Loopback only | Simulated verification code in development. |
| `*` | `/api/intelligence/**`, `/api/fraud-feedback/**`, `/api/admin/**` | Session/admin | Recipient intelligence, feedback review, and learning. |

The full REST/SSE/internal contract is in
[`docs/archive/api-contract.md`](docs/archive/api-contract.md).

## Testing

```powershell
# Backend: 480 tests, 1 skipped at the time of writing
mvn -f backend clean test

# Frontend: 55 tests
npm --prefix frontend run test

# AI service: 32 tests; no network and no API key required
python -m pytest ai-service -q
```

The AI service setup is in [`ai-service/README.md`](ai-service/README.md).

End-to-end safety invariants (backend must be running):

```powershell
pwsh -File scripts/demo-guarded-flow.ps1 -RecipientEmail "<sandbox-payee>"
```

Coverage includes the deterministic engine, guarded flow, idempotency, webhook
state sync, auth/owner scoping, AI validation/fallback, and the frontend
checkout/verification flows.

## Android shell

Build the debug APK:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/build-android-apk.ps1
# -> GuardPay-debug.apk
```

The script builds the frontend and bundles it into the Android WebView shell.
Configure the backend URL in `android/local.properties` (gitignored):

```properties
guardpayApiBaseUrl=http://10.0.2.2:8080
```

Use `http://10.0.2.2:8080` for the emulator or your machine's LAN IP for a
physical phone. The APK is debug-signed, opens on `/payment`, and requests only
`READ_PHONE_STATE` and `INTERNET`. Call detection is foreground-only; Android
lifecycle rules can pause monitoring when the app is backgrounded. Device
credentials are issued at runtime by the authenticated `/api/devices` API and
are never baked into the APK.

## Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| `docker compose` cannot connect | Docker Desktop is not running. | Start Docker Desktop and retry. |
| Database port conflict | Local PostgreSQL already uses `5432`. | Stop it or set `DB_PORT` (and update the compose port). |
| Frontend cannot reach backend | Wrong `VITE_API_BASE_URL`. | Set it to `http://localhost:8080` and rebuild/restart the frontend. |
| PayPal order rejected | Recipient is not a real Sandbox payee. | Use a payee email that exists in your PayPal Developer account. |
| PayPal health is `DOWN` | Missing client id/secret or wrong base URL. | Check `PAYPAL_CLIENT_ID`, `PAYPAL_CLIENT_SECRET`, and `PAYPAL_BASE_URL`. |
| AI shows `UNAVAILABLE` | AI service down, key missing, or timeout. | Check `http://localhost:8001/health`, the key, and `AI_SERVICE_READ_TIMEOUT_MS`. |
| No AI findings | No Groq key or model call failed. | This is expected fallback; local URL findings still run. Add a key for AI evidence. |
| Verification code missing | TOTP not enrolled and outbox disabled. | Use TOTP on `/dashboard` or keep `GUARDPAY_DEV_VERIFICATION_OUTBOX=true` locally. |
| Webhooks not delivered | PayPal cannot reach `localhost`. | Use a public tunnel or deployment; do not fake a live webhook. |
| Android WebView CORS error | Origin not allowed. | Add `https://appassets.androidplatform.net` to `CORS_ALLOWED_ORIGINS`. |
| Tests fail on database connection | PostgreSQL is not running. | Start `docker compose up -d db` or your local PostgreSQL. |
| Need a clean database | Existing demo data. | `docker compose down -v` then `docker compose up --build`. |
| Need logs | Debugging a service. | `docker compose logs -f backend` or `docker compose logs -f ai-service`. |

## Security and privacy

- **Deterministic authority.** The LLM never decides a payment; the deterministic
  engine is the only source of `ALLOW` / `VERIFY` / `STOP`.
- **Owner-scoped data.** Transactions, recipients, devices, alerts, blocklist
  entries, idempotency keys, and SSE feeds belong to the signed-in account.
- **Authentication.** BCrypt password hashes, opaque `HttpOnly` session cookies,
  CSRF protection, idle/absolute expiry, and session rotation/revocation.
- **Secrets.** PayPal and Groq credentials live in environment variables or a
  gitignored `.env` file. They are never committed or logged.
- **AI privacy.** The AI service receives minimum context only (amount, names,
  flags, text, URL); no audio, phone numbers, contacts, or emails. Full message
  bodies are never logged.
- **Sandbox safety.** PayPal orders and captures use Sandbox REST calls; no real
  money moves outside explicit Sandbox approval and allowed capture.
- **No affiliation.** GuardPay is a hackathon prototype, not official PayPal
  infrastructure, and is not endorsed by PayPal.

## What is real vs simulated

| Concern | Status |
| --- | --- |
| Risk decision | Real deterministic engine, server-side only |
| AI evidence | Real Groq call when configured; local URL rules if unconfigured or the call fails; optional and never decisive |
| PayPal order/capture | Real PayPal Sandbox REST calls |
| Webhooks | Real handler; external delivery needs a public tunnel |
| Verification | Simulated by default, or TOTP when enrolled |
| Device call-state | Real when a paired Android device reports it; foreground-only |
| Feedback/learning | Real admin workflow; calibration only moves thresholds |
| Alerts/notifications | Real persisted alerts and SSE; notification delivery is asynchronous |
| Blocklist | Real owner-scoped API and hard-block rule; no dedicated management UI yet |

## Limitations

- PayPal Sandbox approval/capture needs a human with a Sandbox Personal account.
- External webhook delivery needs a public tunnel (not configured; never faked).
- Verification is simulated by default or TOTP when enrolled; it is not a
  production identity channel.
- Device call-state is foreground-only; the APK is debug-signed.
- No password reset, email verification, OAuth, or MFA at login yet. TOTP is a
  payment-verification channel.
- No live URL reputation service; URL analysis is structural and local.
- AI quality varies by model/provider; strict schema/confidence gates and the
  deterministic fallback absorb failures.
- Admin-only intelligence, feedback review, and learning consoles require a user
  promoted through `GUARDPAY_INTELLIGENCE_ADMIN_EMAILS`.
- Learning calibration is threshold-only and requires manual admin approval; it
  never auto-deploys.
- There is no dedicated blocklist management screen yet.
- This is a hackathon prototype, not production fraud infrastructure, and is not
  affiliated with or endorsed by PayPal.

## Project structure

```text
GuardPay/
├── frontend/          React + Vite checkout, dashboard, transactions, alerts
├── backend/           Spring Boot API, risk engine, PayPal integration
├── ai-service/        FastAPI evidence service (Groq-backed, never decides)
├── android/           Kotlin WebView shell + foreground call-state reporting
├── docs/              architecture, risk engine, demo, benchmark, archived docs
├── scripts/           demo and APK build helpers
├── docker-compose.yml
├── LICENSE
└── README.md
```

## Documentation

- [`docs/demo.md`](docs/demo.md) — runnable demo and judge walkthrough.
- [`docs/architecture.md`](docs/architecture.md) — components, boundaries, and limitations.
- [`docs/risk-engine.md`](docs/risk-engine.md) — signal catalog, weights, and policy.
- [`docs/fraud-benchmark.md`](docs/fraud-benchmark.md) — synthetic benchmark methodology.
- [`docs/archive/api-contract.md`](docs/archive/api-contract.md) — implemented REST/SSE/internal API contract.
- [`ai-service/README.md`](ai-service/README.md) — AI service setup and provider configuration.

## License

MIT. See [`LICENSE`](LICENSE).
