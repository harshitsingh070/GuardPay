# GuardPay — Product Requirements Document (PRD)

**Version:** 1.0  
**Status:** Hackathon MVP  
**Product:** GuardPay  
**Category:** AI-powered payment safety / fraud prevention  
**Primary Platform:** Web application with PayPal sandbox integration  
**Target Users:** PayPal consumers and online payment users

> **Implementation status (current prototype):** This PRD describes the broader
> product plan. The implemented contract is in `docs/api-contract.md`, the
> architecture in `docs/architecture.md`, and the engine in
> `docs/risk-engine.md`; those documents and the code take precedence where this
> product plan differs.
>
> Implemented now: BCrypt registration/login with opaque `HttpOnly` session
> cookies, CSRF, rate limiting, session rotation/revocation, owner-scoped data,
> guarded payment (`POST /api/payments/guarded`), recipient-bound PayPal Sandbox
> orders, server-side capture, signature-verified webhooks, controlled
> TOTP/simulated verification, transaction history, persisted dashboard/SSE
> alerts, device registration/call-state, owner-scoped blocklist, and a
> deterministic signal engine. AI evidence uses FastAPI with two focused LLM
> agents plus local URL rules, not CrewAI; AI cannot decide or score.
>
> Not implemented: JWT/OAuth/Spring Security, password reset, email
> verification, MFA-at-login, Google Safe Browsing or live URL reputation,
> subscriptions/invoicing management, scam-report APIs, cross-user intelligence,
> and the old endpoint list in Section 26. TOTP is a payment-verification
> channel, not login MFA.

---

# 1. Executive Summary

GuardPay is an AI-powered payment safety agent designed to protect users from social-engineering and transaction-based payment scams.

The product focuses on a critical weakness in modern payment fraud: a scammer can manipulate a legitimate user into authorizing a legitimate payment.

Traditional fraud systems primarily analyze the transaction itself.

GuardPay analyzes the **transaction + user behavior + payment content + merchant/recipient context + social-engineering context**.

Before a payment is completed, GuardPay produces a risk assessment and explains why the transaction may be dangerous.

The system can recommend:

- **ALLOW** — low risk
- **VERIFY** — suspicious; verify before proceeding
- **STOP** — high-risk payment; strongly intervene

The primary hackathon scenario is a fake PayPal support/payment-request scam where a victim is on a phone call while being pressured to send money to a new recipient.

---

# 2. Product Vision

### Vision

Make online payments safer by protecting users from the human manipulation that happens around payments.

### Product principle

> **Scammers don't need to hack the payment system if they can convince the user to authorize the payment.**

GuardPay exists to identify that manipulation before money is lost.

---

# 3. Problem Statement

Users can receive:

- Fake PayPal invoices
- Fake support calls
- Phishing links
- Fake subscription renewal requests
- Fraudulent payment requests
- Merchant impersonation
- Government/bank impersonation
- Urgent payment instructions
- Requests to "move money to protect it"

A transaction can appear technically valid because the victim is the person authorizing it.

The problem is therefore not only:

> "Is this transaction fraudulent?"

It is also:

> "Why is the user making this payment right now?"

GuardPay addresses this contextual problem.

---

# 4. Goals

## Primary Goals

1. Detect high-risk payment scenarios before payment completion.
2. Detect social-engineering patterns surrounding payments.
3. Combine transaction and contextual signals.
4. Explain risk in language a normal user can understand.
5. Provide a clear STOP / VERIFY / CONTINUE decision.
6. Demonstrate real PayPal integration using sandbox APIs.
7. Provide a real-time risk dashboard.
8. Demonstrate AI agents working on different parts of the fraud analysis.

## Secondary Goals

1. Detect suspicious recurring/subscription payments.
2. Detect suspicious invoice/payment-request content.
3. Detect suspicious URLs.
4. Provide post-payment scam/dispute guidance.
5. Demonstrate real-time PayPal event updates.

---

# 5. Non-Goals

The MVP will not attempt to:

- Build a production banking-grade fraud system.
- Guarantee that a transaction is fraudulent.
- Replace PayPal's fraud infrastructure.
- Record phone calls.
- Analyze private call audio.
- Automatically recover lost money.
- Guarantee successful refunds.
- Build a complete iOS call-monitoring solution.
- Train a proprietary large machine-learning model.
- Build a full accounting platform.
- Implement every possible type of scam.

The product is a hackathon prototype demonstrating contextual payment-risk detection.

---

# 6. Target Users

## Persona 1 — Everyday Consumer

A user who makes online payments and may not recognize sophisticated scams.

### Needs

- Simple warnings
- Clear explanations
- Minimal friction
- Safe verification guidance

---

## Persona 2 — Less Technical User

A user who may be vulnerable to phone-based impersonation scams.

### Needs

- Strong warnings
- Easy-to-understand language
- Protection from urgency and pressure
- Clear "stop and verify" actions

---

## Persona 3 — Frequent Online Shopper

A user making many online transactions.

### Needs

- Merchant reputation/context
- New-recipient warnings
- Unusual-payment detection
- Subscription monitoring

---

# 7. Core User Journey

The primary GuardPay journey is:

```text
User starts payment
        ↓
GuardPay collects context
        ↓
Transaction history analyzed
        ↓
Invoice/message/URL analyzed
        ↓
Call context checked
        ↓
AI agents analyze context
        ↓
Risk engine calculates score
        ↓
GuardPay explains risk
        ↓
ALLOW / VERIFY / STOP
```

---

# 8. Primary User Story

### Fake Support Scam

**Given:**

- User receives an unexpected PayPal invoice.
- Invoice contains urgent language.
- Invoice contains a phone number.
- User calls the number.
- Caller claims to be PayPal support.
- Caller instructs user to send $2,499.
- Recipient is new.
- User normally makes payments around $87.

**When:**

The user attempts the payment.

**Then:**

GuardPay detects multiple risk signals.

**And:**

GuardPay displays a high-risk warning.

**And:**

The warning explains the evidence.

**And:**

The user can stop or verify the payment.

---

# 9. Core Features

## Feature 1 — Payment Risk Analysis

### Description

Analyze every payment before completion.

### Inputs

- Amount
- Currency
- Recipient
- Merchant
- User history
- Invoice
- Message
- URL
- Call state

### Output

- Risk score
- Risk level
- Detected patterns
- Evidence
- Recommendation

### Acceptance Criteria

- System produces a risk score between 0 and 100.
- System produces a severity level.
- System lists the signals that contributed to the score.
- System recommends ALLOW, VERIFY, or STOP.
- Risk calculation works without the LLM.

---

# 10. Feature 2 — Transaction Behavior Analysis

GuardPay maintains a behavioral profile for the user.

### Profile metrics

- Average payment
- Median payment
- Maximum payment
- Typical payment range
- Known recipients
- New recipients
- Frequent merchants
- Transaction frequency
- Recurring payment behavior

### Example

```text
Average payment: $87
Maximum payment: $420
Known recipients: 12
Current payment: $2,499
Current recipient: New
```

### Acceptance Criteria

- Historical transactions can be retrieved.
- User profile can be calculated.
- Current payment can be compared against historical behavior.
- Significant anomalies are returned as structured signals.

---

# 11. Feature 3 — Social Engineering Detection

This is the core GuardPay differentiator.

### Detect

- Impersonation
- Fake support
- Urgency
- Threats
- Account-compromise claims
- "Move money to protect it"
- "Don't hang up"
- Instructions to call a number
- Pressure to pay immediately
- Suspicious payment instructions

### Acceptance Criteria

- AI identifies relevant social-engineering patterns.
- Agent returns structured findings.
- Agent provides evidence.
- Agent does not directly execute payment actions.

---

# 12. Feature 4 — Call Context

GuardPay uses call-state context without recording call audio.

### Signal

```text
ACTIVE_CALL = TRUE/FALSE
```

### MVP implementation

- Android call-state integration where practical.
- Web/demo toggle for reliable hackathon demonstration.

### Privacy requirement

No call audio is recorded, stored, or sent to the LLM.

### Acceptance Criteria

- Active-call state can be represented.
- Risk engine can consume the signal.
- UI clearly shows when call context contributed to the risk score.

---

# 13. Feature 5 — Invoice and Payment Request Analysis

GuardPay analyzes suspicious payment requests.

### Detect

- Unexpected invoice
- Alarmist language
- Fake support instructions
- Phone numbers
- Urgent payment requests
- Suspicious payment instructions
- Account-compromise claims

### Example

> "Your account has been compromised. Call support immediately."

GuardPay should identify:

- Urgency
- Impersonation
- Support-related claim
- Phone-number instruction

---

# 14. Feature 6 — URL Safety

GuardPay analyzes URLs contained in:

- Invoices
- Payment requests
- Messages

### Technology

No Google Safe Browsing or live reputation service is implemented. Backend and
AI-service URL analysis is structural only (scheme, host shape, punycode, brand
lookalikes, shorteners, TLD/port, path lure words) and never fetches the URL.

### Result

- No structural concern
- Suspicious
- Unavailable (not supplied)

### Acceptance Criteria

- URL is extracted.
- URL is scanned.
- Result is stored as a risk signal.
- URL result contributes to the final risk assessment.

---

# 15. Feature 7 — AI Agent System

The current implementation uses **two focused LLM agents plus deterministic
local URL rules**, not CrewAI and not a transaction agent:

## Social Engineering Agent

Analyzes manipulation patterns in untrusted message text: impersonation,
pressure to pay, secrecy, suspicious payment instructions, and scam-pattern
combinations.

## Content Safety Agent

Analyzes invoice/message/merchant/recipient context for invoice scams and
scam-like payment requests.

## Local URL Analyzer

Runs even when no LLM key is configured. It performs structural checks only and
never fetches the URL.

Transaction behavior is computed by Spring Boot's deterministic collector, not
by an LLM. Every AI agent returns structured findings with a controlled code,
severity, confidence, and evidence; it must not authorize, capture, cancel, or
score a payment.

---

# 16. Feature 8 — Deterministic Risk Engine

The risk engine combines signals from:

- Transaction/recipient/merchant history
- Velocity and split-payment patterns
- Social-engineering and content analysis
- URL structure
- Blocklist, device, network, and call context

### Implemented signals

```text
Active call                    +15
New recipient                  +15
Large amount                   +20
Recipient amount anomaly       +20
Repeated attempts (10 min)     +15
Velocity hourly                +15
Velocity daily                 +20
Blocklisted recipient          +25 (forces score 100 / STOP)
Unknown merchant               +10
Unexpected invoice             +15
Urgent language                +10
Suspicious URL                 +15
Social engineering             +20
Known-recipient scam           +50
Split payment                  +40
Missing context                +20
New device                     +20
New geo                        +15
Impossible travel              +25
VPN / proxy                    +10
```

Exact trigger rules, sharing/collapsing rules, and unavailable reasons are in
`docs/risk-engine.md`.

The final score is normalized/capped at 100.

### Decision policy

```text
0–39     ALLOW
40–69    VERIFY
70–84    HIGH RISK / STRONG VERIFY
85–100   STOP
```

These values are prototype heuristics.

They are not statistical probabilities.

---

# 17. Feature 9 — Risk Explanation

The user should never see only a score.

GuardPay must explain:

- What was detected
- Why it matters
- Which signals triggered
- What the user should do

### Example

> **94/100 — Critical Risk**
>
> We detected several signals associated with impersonation and payment-request scams.
>
> - You are currently on a phone call.
> - This recipient has never received money from you.
> - $2,499 is significantly higher than your typical payment.
> - The invoice contains urgent language.
> - The invoice includes a phone number.
> - The payment URL has been flagged.
>
> We recommend stopping and independently verifying the request.

---

# 18. Feature 10 — Payment Intervention

Depending on risk:

### Low

Allow payment.

### Medium

Require additional verification.

### High

Show strong warning.

### Critical

Strongly recommend stopping and provide a STOP action.

For the hackathon prototype, payment blocking can be demonstrated through the GuardPay payment flow/sandbox rather than claiming universal interception of the native PayPal application.

---

# 19. Feature 11 — Subscription Protection

Secondary feature.

GuardPay detects:

- New recurring payments
- Price increases
- Unusual recurring payments
- Duplicate subscriptions
- Unfamiliar merchants

Example:

> "Your recurring payment increased from $49.99 to $69.99."

---

# 20. Feature 12 — Security Dashboard

The dashboard provides:

- Total transactions
- Risk distribution
- High-risk transactions
- Blocked payments
- Verified payments
- Amount protected
- Scam patterns
- Subscription alerts

---

# 21. Feature 13 — Transaction Table

Use AG Grid.

### Columns

- Transaction ID
- Date
- Merchant
- Recipient
- Amount
- Risk Score
- Risk Level
- Scam Pattern
- Status
- Action

### Features

- Sorting
- Filtering
- Search
- Pagination
- Risk-level filtering
- Amount filtering
- Date filtering
- Transaction detail view

---

# 22. Feature 14 — Real-Time Updates

PayPal webhook events update the backend.

Flow:

```text
PayPal
  ↓
Webhook
  ↓
Spring Boot
  ↓
PostgreSQL
  ↓
WebSocket/SSE
  ↓
React + Vite
```

The dashboard updates without requiring a refresh.

---

# 23. Feature 15 — Scam Report

The user can report a suspicious payment.

The report contains:

- Transaction
- Scam type
- Explanation
- Evidence
- Timestamp

This can later support dispute/report workflows.

---

# 24. Feature 16 — Post-Payment Assistance

Optional feature.

If a user believes they were scammed, GuardPay can:

- Show transaction details
- Show why it was flagged
- Help organize evidence
- Guide the user toward PayPal dispute/reporting processes

GuardPay must not guarantee refunds.

---

# 25. Functional Requirements

## Authentication

- Users can register/login.
- Protected APIs require authentication.
- The prototype uses opaque session cookies today; JWT was the original plan and is not implemented.
- User-specific transactions cannot be accessed by another user.

## Payments

- Backend can interact with PayPal sandbox.
- Backend can create/retrieve relevant payment/order information.
- Payment state is persisted.
- Webhook events are processed.

## Risk

- Every analyzed transaction receives a risk score.
- Risk score is deterministic after signals are produced.
- Risk reasons are stored.
- Risk assessment can be retrieved later.

## AI

- AI service accepts structured payment context.
- Two LLM agents and local URL rules analyze separate concerns.
- Agent outputs use structured, validated schemas.
- AI failure does not disable deterministic protection.

## Dashboard

- Transactions are displayed.
- Risk levels are visually distinguishable.
- Filtering/sorting works.
- Risk details can be opened.

---

# 26. Non-Functional Requirements

## Performance

Target:

- Normal API response under 500ms excluding external AI calls.
- AI risk assessment ideally under 2–3 seconds.
- Dashboard updates should feel real-time.

## Reliability

- AI service failure should fall back to deterministic analysis.
- PayPal errors should be handled gracefully.
- Webhook processing should be idempotent.

## Security

- Secrets server-side only.
- HTTPS in deployed environments.
- Session-cookie authentication with CSRF (JWT remains a possible future option).
- Validate all external input.
- Never trust LLM output as executable instructions.

## Privacy

- No call recording.
- No call audio sent to AI.
- Store minimum required user/payment context.

---

# 27. System Architecture

> **Plan vs implementation:** the diagram below is the original target
> architecture. The current service uses two focused LLM agents plus local URL
> rules instead of CrewAI, and Spring Boot remains the sole decision authority.
> See `docs/architecture.md` for the implemented view.

```text
                         USER
                           |
                           v
                    React + Vite Frontend
                           |
                           v
                  Spring Boot Backend
                           |
             ┌─────────────┼─────────────┐
             |             |             |
             v             v             v
          PayPal       PostgreSQL    Risk Engine
             |                           |
         Webhooks                        |
             |                           |
             └─────────────┬─────────────┘
                           |
                           v
                    Python AI Service
                         FastAPI
                           |
                    (no CrewAI)
                           |
             ┌─────────────┼─────────────┐
             v             v             v
       Spring Boot     Social         Content
       transaction   Engineering       Agent
        signals        Agent        + URL rules
             |             |             |
             └─────────────┼─────────────┘
                           v
                          LLM
                           |
                           v
                  Structured Findings
                           |
                           v
                 Spring Boot Risk Engine
                           |
             ┌─────────────┼─────────────┐
             v             v             v
           ALLOW         VERIFY         STOP
             |             |             |
             └─────────────┼─────────────┘
                           v
                       React + Vite UI
```

---

# 28. Technology Requirements

## Frontend

- React + Vite
- TypeScript
- Tailwind CSS
- shadcn/ui
- AG Grid

## Main Backend

- Java 17
- Spring Boot 3.x
- Spring Data JPA
- Hibernate
- Custom authentication filter (Spring Security is not used)

## AI Backend

- Python
- FastAPI
- OpenAI-compatible LLM API
- Deterministic local URL rules (CrewAI is not used)

## Database

- PostgreSQL

## PayPal

- PayPal REST APIs
- Orders
- Payments
- Transaction Search
- Invoicing
- Subscriptions
- Webhooks
- Agent Toolkit/MCP where useful

## Security

- HTTPS
- Session cookie + CSRF
- Optional internal AI-service token
- No Google Safe Browsing or live URL reputation in the current prototype
- JWT was planned but is not implemented

## Real-Time

- WebSocket or Server-Sent Events

## Deployment

- Render

## Notifications

- Zapier Webhooks, optional

---

# 29. API Requirements

The authoritative endpoint contract is `docs/api-contract.md`. Implemented
groups:

## Authentication and sessions

- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/logout`
- `GET /api/auth/me`, `GET /api/auth/csrf`
- Session list/rotate/revoke and password change under `/api/auth/*`
- TOTP enrollment/status/delete under `/api/mfa/totp`

## Payments and risk

- `POST /api/payments/guarded`
- `POST /api/payments/{paymentId}/verification/confirm`
- `POST /api/risk/evaluate-automatic`
- `POST /api/risk/evaluate` (legacy regression/tooling only)

## Transactions, dashboard, alerts, blocklist

- `GET /api/transactions`, `GET /api/transactions/{id}`
- `GET /api/dashboard/summary`
- `GET /api/alerts`, `GET /api/alerts/stream` (SSE)
- `GET/POST/DELETE /api/blocklist/recipients/*`

## Devices and PayPal

- `GET/POST /api/devices`, rotate/revoke device credentials
- `POST /api/device-signals/call-state`
- `GET /api/paypal/health`
- `POST /api/paypal/orders/{orderId}/capture`
- `POST /api/paypal/webhooks`

The older planned routes (`/api/payments/analyze`, `/api/payments/create`,
`/api/payments/{id}/verify`, `/api/payments/{id}/stop`, `/api/transactions/profile`,
`/api/subscriptions`, `/api/scam-reports`, `/api/risk/{transactionId}`,
`/api/risk/analyze`, and `/api/webhooks/paypal`) are not implemented.

---

# 30. Python AI API

Spring Boot communicates with Python using internal APIs.

Implemented endpoint:

`POST /api/v1/analyze` (optional `X-GuardPay-Internal-Token`)

Input: amount/currency, merchant/recipient names, novelty/invoice flags,
untrusted message text, URL string, and active-call boolean. No user profile,
audio, contacts, phone numbers, or email destinations are sent.

Output: `analysis_id`, a controlled-vocabulary `findings` list (code, title,
description, severity, confidence, evidence), and a neutral summary. The Python
service does not accept or emit `ALLOW`/`VERIFY`/`STOP`, a score, or a risk
level. Local URL rules run even without an LLM key. See `docs/api-contract.md`.

---

# 31. Data Requirements

The MVP should contain approximately 50 seeded historical transactions for the demo user.

Example profile:

```text
Average payment: $87
Maximum payment: $420
Known recipients: 12

Frequent merchants:
Amazon
Spotify
Adobe
Uber
```

Historical transactions should contain realistic:

- Dates
- Amounts
- Merchants
- Recipients
- Currencies
- Payment statuses

---

# 32. Demo Scenarios

## Scenario A — Safe

```text
Amount: $45
Merchant: Known
Recipient: Known
Call: No
Invoice: No
URL: None
```

Expected:

> 18/100 — LOW RISK — ALLOW

---

## Scenario B — Suspicious

```text
Amount: $800
Recipient: New
Invoice: Unexpected
URL: Suspicious
Call: No
```

Expected:

> 61/100 — VERIFY

---

## Scenario C — Social Engineering Scam

```text
Amount: $2,499
Recipient: New
Merchant: Unknown
Call: Yes
Invoice: Unexpected
Urgency: Yes
URL: Suspicious
```

Expected:

> 94/100 — CRITICAL — STOP

Detected pattern:

> **Impersonation / Payment-Request Scam**

---

# 33. Hero Demo Experience

The judge should see:

```text
🚨 HIGH-RISK PAYMENT

94/100

Someone may be manipulating you into making this payment.

🔴 Active phone call
🔴 New recipient
🔴 $2,499 vs $87 average
🔴 Unexpected invoice
🔴 Urgent language
🔴 Suspicious URL

Detected pattern:
Impersonation / payment-request scam

[ STOP PAYMENT ]

[ VERIFY RECIPIENT ]

[ CONTINUE ANYWAY ]
```

This is the central product moment.

---

# 34. Acceptance Criteria for MVP

The MVP is complete when:

1. A user can log in.
2. A user can enter a payment scenario.
3. Spring Boot receives the payment context.
4. User transaction history is available.
5. Transaction anomaly signals are generated.
6. Call-state context can be provided.
7. Invoice/message content can be analyzed.
8. URL can be checked.
9. Python AI service analyzes the context.
10. Two LLM agents and local URL rules return structured findings.
11. Spring Boot risk engine produces a score.
12. GuardPay displays the explanation.
13. User can choose STOP/VERIFY/CONTINUE.
14. Transaction appears in AG Grid.
15. PayPal sandbox integration works.
16. PayPal webhook events can update transaction state.
17. AI failure does not completely break basic risk analysis.
18. No call audio is recorded.

---

# 35. Hackathon Scope Priority

## P0 — Must Have

- PayPal sandbox
- Spring Boot backend
- PostgreSQL
- React + Vite
- Risk engine
- Transaction history
- Social-engineering detection
- Python/FastAPI AI evidence (two LLM agents + local URL rules)
- Risk warning
- AG Grid dashboard
- Hero demo scenario

## P1 — Should Have

- Structural URL analysis
- PayPal webhooks
- Real-time dashboard/alerts
- Authentication
- WebSocket/SSE

## P2 — Nice to Have

- Zapier notifications
- Scam reports
- Dispute assistance
- Mobile call-state integration
- Advanced analytics

---

# 36. Success Metrics

For the hackathon prototype:

### Product metrics

- Number of transactions analyzed
- Number of high-risk transactions detected
- Number of payments stopped
- Amount protected
- Number of scam patterns detected
- Number of suspicious invoices detected

### Technical metrics

- Risk analysis latency
- AI service success rate
- Webhook processing success rate
- API error rate
- Dashboard update latency

Do not claim real-world fraud detection accuracy unless validated with an appropriate dataset.

---

# 37. Risks and Mitigations

## Risk: False positives

### Mitigation

Use multiple signals and allow VERIFY instead of automatically stopping every unusual payment.

## Risk: LLM hallucination

### Mitigation

LLM produces structured evidence; deterministic engine controls the final action.

## Risk: AI service failure

### Mitigation

Fall back to deterministic transaction-risk analysis.

## Risk: Call detection limitations

### Mitigation

Use call-state only and provide a controlled demo toggle.

## Risk: PayPal API limitations

### Mitigation

Use PayPal sandbox and controlled demo data.

## Risk: External API failure

### Mitigation

Graceful fallback and cached/seeded demo scenarios.

---

# 38. Privacy and Safety Principles

GuardPay must follow these principles:

1. Never record calls.
2. Never send call audio to an LLM.
3. Never expose payment credentials to the frontend.
4. Never allow an LLM to directly execute arbitrary payment operations.
5. Clearly communicate when risk is uncertain.
6. Never claim that a risk score is a probability.
7. Let users verify before continuing.
8. Store only required information.

---

# 39. Product Language

Preferred wording:

> "Potential scam"

> "High-risk payment"

> "Possible impersonation"

> "We detected several suspicious signals"

Avoid absolute claims:

> "This is definitely fraud."

> "You will lose your money."

> "This transaction is 94% fraudulent."

The system is a decision-support and prevention layer.

---

# 40. Hackathon Positioning

## Best Use of PayPal + AI

GuardPay combines real PayPal payment context with AI-powered social-engineering analysis.

## Best Use of Agentic Commerce

Specialized agents analyze payment behavior, social-engineering context, and payment content before a commerce action.

## Best Use of AG Grid

AG Grid powers the central security/transaction investigation dashboard.

## Best Use of Render

The complete working system is deployed to Render.

---

# 41. Final Product Definition

GuardPay is not simply:

- A fraud dashboard
- A transaction classifier
- A phishing detector
- A subscription tracker
- A chatbot

It is an **AI payment safety agent** that combines these capabilities around a single goal:

> **Prevent users from being manipulated into making dangerous payments.**

The core loop is:

```text
DETECT
   ↓
UNDERSTAND
   ↓
CORRELATE
   ↓
EXPLAIN
   ↓
WARN
   ↓
VERIFY / STOP
   ↓
PROTECT
```

---

# 42. Final Architecture Decision

The project uses a hybrid backend.

### Spring Boot owns:

- PayPal integration
- Payment/business logic
- Authentication
- PostgreSQL
- Transaction history
- Webhooks
- Deterministic risk engine
- Application APIs
- Real-time communication

### Python owns:

- AI evidence service (FastAPI)
- Two focused LLM agents (social engineering, content-scam patterns)
- Deterministic local URL structure rules
- LLM interaction and finding validation

### React + Vite owns:

- User interface
- Guarded checkout
- Risk warning and controlled verification
- Dashboard, transaction history, device/TOTP setup
- SSE alert visualization

Current architecture:

```text
React + Vite / Android WebView
          |
          v
     Spring Boot -------- PostgreSQL
          |  \----------- PayPal + webhooks
          |  \----------- Deterministic risk engine
          |                  |
          |                  +-- ALLOW
          |                  +-- VERIFY
          |                  +-- STOP
          |
          v
   Python FastAPI (optional)
          |
     two LLM agents + local URL rules
          |
          v
   Structured evidence only
```

---

# 43. MVP Definition in One Sentence

> **GuardPay must be able to take a PayPal payment context, combine transaction behavior with social-engineering and content signals, generate an explainable risk score, and warn the user before a high-risk payment proceeds.**
