# GuardPay — System Architecture

> Current implementation, not the original scaffold plan. The guarded payment
> flow, authentication, device signals, alerts, dashboard, verification, and
> PayPal Sandbox integration are implemented. `docs/GuardPay_PRD.md` and
> `docs/GuardPay_Project_Specification.md` retain the longer-term product plan.

## 1. What GuardPay is

GuardPay is an AI-assisted payment-safety layer for PayPal Sandbox. It detects
when a user may be manipulated into making a fraudulent payment — fake PayPal
support calls, urgent payment requests, suspicious invoices, suspicious URLs,
account-takeover context, and unusual transaction behavior — then explains the
risk and returns a server-authoritative **ALLOW / VERIFY / STOP** decision.

Core loop: `DETECT → UNDERSTAND → CORRELATE → EXPLAIN → WARN → VERIFY/STOP → PROTECT`.

## 2. System architecture

```text
Web browser / Android WebView (React + Vite + TypeScript)
    |
    |  REST + cookie session + CSRF + SSE alerts
    v
Spring Boot (Java 17) — trusted business layer
    |
    ├── PostgreSQL (Flyway schema, JPA/Hibernate)
    ├── PayPal Sandbox REST APIs + signature-verified webhooks
    ├── Authentication, owner-scoped data, device credentials, TOTP
    ├── Automatic signal collection + deterministic risk engine
    ├── Guarded payment orchestration + controlled verification
    ├── Alerts, notification outbox, dashboard aggregates, SSE
    |
    |  internal REST, optional shared token
    v
Python FastAPI AI service
    |
    ├── Local structural URL analysis (always)
    ├── Social-engineering LLM agent (optional)
    └── Content/invoice scam LLM agent (optional)
    |
    v
Structured findings (evidence only)
    |
    v
Spring Boot deterministic risk engine
    |
    ├── ALLOW
    ├── VERIFY
    └── STOP
```

There are no extra infrastructure pieces (no Kafka, Redis, MongoDB, vector DB,
Kubernetes, or extra microservices); a single PostgreSQL database and a single
Spring Boot process are deliberate hackathon scope decisions.

**Security principle:** the LLM never directly decides whether a payment is
allowed. AI findings are untrusted evidence that Spring Boot validates and
folds into existing engine signals. The deterministic engine remains the sole
decision authority.

## 3. Repository layout

```text
guardpay/
├── frontend/     # React + Vite + TypeScript checkout, history, dashboard
├── backend/      # Spring Boot business backend, risk engine, PayPal, migrations
├── ai-service/   # Python FastAPI evidence service (no CrewAI)
├── android/      # Kotlin WebView shell + foreground call-state reporting
├── contracts/    # Shared API/data contract assets
├── docs/         # Architecture, API, risk engine, demo, benchmark, product docs
├── scripts/      # Demo and Android build scripts
└── docker-compose.yml  # local PostgreSQL
```

## 4. Responsibility of each service

### 4.1 React + Vite (`frontend/`)

- Sign-in/registration gate with cookie-backed sessions; sign-out.
- Guarded checkout at `/payment`: amount, recipient, recipient PayPal email,
  merchant, message, URL, optional device context. No risk inputs.
- Decision display: score, severity, evidence, explanation, controlled VERIFY
  challenge, PayPal approval link, capture, and receipt.
- Transaction history at `/transactions` (AG Grid, paging, detail panel).
- Security dashboard at `/dashboard`: decision mix, blocked value, latency
  percentiles, AI availability, alert delivery, TOTP enrollment, and optional
  dev AI outage simulation.
- Live alert banner fed by `/api/alerts/stream` (SSE).
- Device integration: after sign-in, the Android shell registers a device via
  `/api/devices`; the React hook reports call state with the per-device
  credential.
- Talks to Spring Boot through REST (`VITE_API_BASE_URL`, native bridge URL, or
  same-host port 8080) and SSE. It owns no payment secrets, no risk scoring, and
  makes no PayPal calls.

### 4.2 Android shell (`android/`)

- Kotlin WebView shell around the same React checkout.
- Reads only binary telephony call state through `TelephonyManager` /
  `TelephonyCallback` with `READ_PHONE_STATE`; no audio, contacts, SMS, or
  location.
- Reports `activeCall` and `observedAt` to `/api/device-signals/call-state`
  using a per-device credential issued by authenticated registration.
- Shows a blocking call-risk dialog when a payment is started during an active
  call.
- Foreground-only observation, debug-signed demo APK, not production attestation.

### 4.3 Spring Boot (`backend/`) — the trusted business layer

- Multi-user accounts: BCrypt password hashes, opaque `HttpOnly` session
  cookies, server-side token hashes, idle/absolute expiry, session listing,
  rotation, revocation, login rate limiting, and CSRF tokens for unsafe
  cookie-authenticated requests.
- Owner-scoped transactions, recipients, devices, alerts, blocklist entries,
  idempotency keys, captures, and SSE streams.
- PayPal Sandbox integration: order creation through the guarded flow only,
  server-side capture, signature-verified idempotent webhooks.
- Automatic signal collection from trusted history, device, content, and
  network context; it never accepts client-supplied risk booleans.
- Optional AI call, response validation, and evidence mapping.
- Deterministic risk engine and final decision.
- Controlled VERIFY challenges (simulated out-of-band channel or enrolled TOTP).
- Alerts in the same transaction as the decision, notification outbox, and SSE
  broadcast after commit.
- Persisted explanations, transaction history, and dashboard aggregates.
- REST APIs and SSE for the browser/WebView.

Package map (`com.guardpay`): `ai`, `alert`, `auth`, `behavior`, `common`,
`dashboard`, `device`, `evaluation`, `feedback`, `merchant`, `metrics`, `outcome`,
`payment`, `paypal`, `recipient`, `risk`, `security`, `session`, `transaction`,
`user`, `webhook`.

### 4.5 Java Behavioral Profiling (`backend/`, `com.guardpay.behavior`)

- Per-user learned behavioral baseline computed from COMPLETED transaction
  history only (never cancelled/failed/reversed).
- Three rolling windows: 7-day (short-term), 30-day (medium-term), 90-day
  (long-term).
- Behavioral dimensions: amount (median, MAD, percentiles), time (hour-of-day,
  weekend/night ratio), recipient (unique count, established ratio), device,
  geographic location, and frequency (payments per day/week).
- Cold-start states: `INSUFFICIENT_DATA` (<5 txns), `INITIALIZING` (5–19),
  `ESTABLISHED` (20+). New users are never artificially flagged as high-risk.
- `AdaptiveBehavioralAnalyzer` compares incoming payments against the learned
  profile and produces anomaly findings (evidence only — never a payment
  decision).
- Findings fold into the existing deterministic risk engine as complementary
  `PaymentSignal`s with `SignalSource.BEHAVIORAL_PROFILE`. The engine remains
  the sole ALLOW/VERIFY/STOP authority.
- Fails safely: profiling service failure degrades to `UNAVAILABLE` and the
  deterministic engine continues alone.


### 4.4 Python FastAPI (`ai-service/`) — the evidence layer

- FastAPI service on port 8001; internal endpoint `POST /api/v1/analyze`.
- No CrewAI. A fixed deterministic pipeline runs:
  - local structural URL rules (always, even without an LLM key),
  - a social-engineering LLM agent (optional),
  - a content/invoice-scam LLM agent (optional).
- Returns structured findings only: `analysis_id`, `findings[]`, `summary`.
  Findings use a controlled code vocabulary, severity, confidence, and evidence.
- Owns no money movement, payment authorization/capture/cancel, database of
  record, or final decision.
- Fails safely: missing/unavailable LLM degrades to local URL findings or
  `UNAVAILABLE` on the Spring Boot side; never fakes an AI result.

## 5. Communication

```text
React + Vite ──REST + SSE──▶ Spring Boot ──REST (server-side)──▶ PayPal APIs
      ▲                          │  ▲
      │                          │  │ structured findings
      │                      PostgreSQL  │ POST /api/v1/analyze
      └── decision + live alerts ─┘      ▼
                                   Python FastAPI → optional LLM

PayPal ──webhook events──▶ Spring Boot (/api/paypal/webhooks)
       ──▶ PostgreSQL ──▶ SSE ──▶ React + Vite
```

- All PayPal, LLM, and AI-hop secrets stay server-side. The frontend receives
  only the API base URL.
- Spring Boot treats AI output as untrusted input: unknown codes, low
  confidence, blank evidence, and AI attempts to assert deterministic facts
  (for example `ACTIVE_CALL`) are rejected.
- Graceful degradation: if the AI service fails, times out, is unconfigured, or
  is circuit-open, deterministic signals and local content/URL analysis still
  decide. AI can only add evidence; it cannot remove or override risk.
- The device path is separate from the user session: call-state intake uses a
  per-device credential header (`X-GuardPay-Device-Token`), and device
  observations older than five minutes are stale and never count as active.

## 6. Why Spring Boot handles business logic

- Money and identity need a typed, transactional platform: JPA/Hibernate,
  validation, idempotent webhook handling, and auditable risk decisions.
- PayPal integration, PostgreSQL ownership, and the risk engine live in one
  accountable place so the final decision is reproducible without an LLM.
- Java 17 + Spring Boot 3.x is the chosen business stack; Python is deliberately
  kept out of the system of record.

## 7. Why Python handles AI

- LLM SDKs and prompt/content tooling are most productive in Python.
- Isolating AI in its own service keeps prompt/agent experimentation from
  destabilizing payments, lets it scale or fail independently, and makes the
  trust boundary explicit: Python advises, Spring Boot decides.
- The three-part deterministic pipeline needs no multi-agent framework, so
  CrewAI is intentionally not used.

## 8. Why the LLM does NOT make the final payment decision

1. **Non-determinism:** the same payment context could yield different LLM
   wording run to run. Payment intervention must be reproducible: same signals
   → same score → same decision.
2. **Hallucination / prompt-injection risk:** invoice/message/URL text is
   attacker-controlled. An LLM reading it must never authorize, capture, or
   cancel money. Structured findings are evidence, never instructions.
3. **Auditability:** users and reviewers need "which signals fired, with what
   weights" (see `docs/risk-engine.md`), not a black-box verdict.
4. **Availability:** the AI service can fail or be slow. New-recipient, amount,
   velocity, device, blocklist, network, and content rules must still work, so
   the engine runs in Spring Boot with or without AI findings.
5. **Safety UX:** the system recommends and enforces server-side; the user
   still decides whether to continue through an allowed or verified payment.
   Scores are GuardPay Risk Scores (0–100 heuristics), never "probability of
   fraud".

Enforced flow:

```text
AI evidence ─┐
Device ──────┤
History ─────┼─▶ validated signals ─▶ Spring Boot deterministic engine
Content ─────┤                          ─▶ ALLOW / VERIFY / STOP
Network ─────┘                              ─▶ PayPal order only on ALLOW
```

## 9. Architectural limitations

- External webhook delivery needs a public tunnel/deployment; localhost is not
  reachable by PayPal.
- The verification channel is simulated unless the user enrolls TOTP; there is
  no production identity provider.
- Call detection is foreground-only and the APK is debug-signed.
- Session/CSRF and owner scoping are implemented, but password reset, email
  verification, OAuth, and MFA-at-login are not.
- Recipient intelligence is per-recipient evidence, not a fraud-ring graph or
  population-level model. There is no provider chargeback webhook or external
  URL-reputation provider; feedback is explicit user/admin labels only.
