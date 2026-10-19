# GuardPay — System Architecture

> Init-step design doc. No business logic is implemented yet.

## 1. What GuardPay is

GuardPay is an AI-powered real-time payment safety system. It detects when a user
may be manipulated into making a fraudulent PayPal payment — e.g. fake PayPal
support calls, urgent payment requests, suspicious invoices, suspicious URLs,
and unusual transaction behavior — then explains the risk and lets the user
choose **STOP / VERIFY / CONTINUE**.

Core loop: `DETECT → UNDERSTAND → CORRELATE → EXPLAIN → WARN → VERIFY/STOP → PROTECT`.

## 2. System architecture

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

Monorepo layout:

```text
guardpay/
├── frontend/     # React + Vite UI
├── backend/      # Spring Boot business backend
├── ai-service/   # Python FastAPI + CrewAI intelligence service
├── docs/
└── docker-compose.yml  # local Postgres + planned service slots
```

There are no extra infrastructure pieces (no Kafka, Redis, MongoDB, vector DB,
Kubernetes, or extra microservices) — a deliberate hackathon scope decision.

## 3. Responsibility of each service

### 3.1 React + Vite (frontend/)

- Payment interface (amount, currency, recipient, merchant, invoice/message, URL, call-state toggle).
- Risk-warning interface (score, severity, evidence, explanation, STOP / VERIFY / CONTINUE).
- Client-side routing with React Router (`/` home, `/payment`, `/risk`, `/dashboard`, `/transactions`).
- Security dashboard + transaction history via AG Grid (sorting, filtering, search, pagination, detail view).
- Real-time risk updates (consumes Spring Boot WebSocket/SSE events).
- Talks to Spring Boot exclusively through REST APIs (`VITE_API_BASE_URL`) plus the real-time channel.
- Owns **no** payment secrets, no risk-score calculation, no PayPal calls.

### 3.2 Spring Boot (backend/) — the trusted business layer

- Authentication (planned Spring Security + JWT) and user isolation.
- Users, transactions, recipients, merchants, subscriptions, alerts, scam reports.
- Payment processing + PayPal integration (sandbox REST: orders, payments, transaction search, invoicing, subscriptions, disputes where useful).
- PayPal webhooks receiver (idempotent processing → PostgreSQL → real-time push to UI).
- Transaction history + user behavioral profile (averages, known recipients, merchant frequency).
- Calls the Python AI service, **validates** its output, then runs the **deterministic risk engine**.
- Persists risk assessments + signals; makes the **final ALLOW / VERIFY / STOP decision**.
- Exposes REST APIs and real-time channels to React + Vite.

Package map (`com.guardpay`): `auth`, `user`, `transaction`, `recipient`,
`merchant`, `payment`, `paypal`, `webhook`, `risk`, `common`.

### 3.3 Python FastAPI (ai-service/) — the intelligence layer

- FastAPI + CrewAI + LLM API.
- Three agents, each returning **structured findings only**:
  - Transaction Risk Agent — behavior anomalies (unusual amount, new recipient, frequency).
  - Social Engineering Agent — manipulation patterns (impersonation, fake support, urgency, threats, "move money to protect it", "don't hang up").
  - Content Safety Agent — invoice/message/URL analysis incl. URL-reputation result.
- Owns **no** money movement, **no** payment authorization/capture/cancel,
  **no** database of record, **no** final decision.

## 4. Communication between services

```text
React + Vite ──REST + WebSocket/SSE──▶ Spring Boot ──REST (server-side)──▶ PayPal APIs
      ▲                                │  ▲
      │                                │  │ structured findings
      │                          Postgres  │ POST /ai/analyze-payment
      └──── risk result + live updates ─┘  ▼
                                    Python FastAPI → CrewAI → LLM

PayPal ──webhook events──▶ Spring Boot (/api/webhooks/paypal) ──▶ Postgres ──▶ WS/SSE ──▶ React + Vite
```

- All PayPal, LLM, and URL-reputation secrets stay server-side (Spring Boot / AI service env vars). The frontend only gets `VITE_API_BASE_URL`.
- Spring Boot → Python uses a single internal operation (conceptually `POST /ai/analyze-payment`; see `docs/api-contract.md`). Spring Boot authenticates to it (planned shared secret) and treats its response as **untrusted input**: schema-validate, then feed into the risk engine.
- Graceful degradation: if Python/LLM fails or times out, Spring Boot falls back to deterministic transaction-risk signals and labels the response "AI analysis unavailable — basic protection active". The payment path never hard-depends on the LLM.

## 5. Why Spring Boot handles business logic

- Money and identity need a mature, typed, transactional platform: JPA/Hibernate, Spring Security, validation, idempotent webhook handling, auditable risk decisions.
- PayPal integration, PostgreSQL ownership, and the risk engine must live in one accountable place so the final decision is reproducible and testable without an LLM.
- Java 17 + Spring Boot 3.x is the team's chosen business stack; Python is deliberately kept out of this role to avoid splitting the system of record.

## 6. Why Python handles AI

- CrewAI, LLM SDKs, and NLP/content-analysis tooling are most productive in Python.
- Isolating AI in its own service keeps prompt/agent experimentation from destabilizing payments, lets it scale/fail independently, and makes the trust boundary explicit: Python advises, Spring Boot decides.

## 7. Why the LLM does NOT make the final payment decision

1. **Non-determinism:** the same payment context could yield different LLM wording/scores run to run. Payment intervention must be reproducible: same signals → same score → same ALLOW/VERIFY/STOP.
2. **Hallucination / prompt-injection risk:** invoice/message/URL text is attacker-controlled. An LLM reading it must never be able to directly authorize, capture, or cancel money. Structured findings are treated as evidence, never as instructions.
3. **Auditability:** judges and users need "which signals fired, with what weights" (see `docs/risk-engine.md`), not a black-box verdict. Deterministic weights give that.
4. **Availability:** the LLM/CrewAI service can fail or be slow. Basic protection (new recipient, unusual amount, active call, suspicious URL) must still work — so the engine runs in Spring Boot with or without AI findings.
5. **Safety UX:** the system recommends; the human decides (STOP / VERIFY / CONTINUE). Scores are GuardPay Risk Scores (0–100 heuristics), never "probability of fraud".

Enforced flow:

```text
AI agents → structured evidence/findings → Spring Boot deterministic risk engine → final score → ALLOW / VERIFY / STOP → React + Vite
```
