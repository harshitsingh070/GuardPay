# GuardPay — Product Requirements Document (PRD)

**Version:** 1.0  
**Status:** Hackathon MVP  
**Product:** GuardPay  
**Category:** AI-powered payment safety / fraud prevention  
**Primary Platform:** Web application with PayPal sandbox integration  
**Target Users:** PayPal consumers and online payment users

> **Implementation note (current prototype):** Account registration/login and JWT/session authentication are not implemented. The demo runs as a single fixed server-side workspace (`guardpay-demo`); API clients do not supply identity. Authentication sections below describe the original product plan and are superseded for this prototype.

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

Google Safe Browsing or another reputable URL reputation service.

### Result

- Safe
- Suspicious
- Unsafe
- Unknown

### Acceptance Criteria

- URL is extracted.
- URL is scanned.
- Result is stored as a risk signal.
- URL result contributes to the final risk assessment.

---

# 15. Feature 7 — AI Agent System

GuardPay uses three agents.

## Transaction Agent

Analyzes transaction behavior.

## Social Engineering Agent

Analyzes manipulation patterns.

## Content Safety Agent

Analyzes invoices, messages, and URLs.

### Agent output requirement

Every agent must return structured data.

Example:

```json
{
  "finding": "unusual_amount",
  "detected": true,
  "severity": "high",
  "evidence": "Payment is 28.7x higher than user average"
}
```

Agents should not directly authorize, capture, or cancel payments.

---

# 16. Feature 8 — Deterministic Risk Engine

The risk engine combines signals from:

- Transaction analysis
- Social-engineering analysis
- Content analysis
- URL reputation
- Call context

### Example signals

```text
Active call              +15
New recipient            +20
Unusual amount           +20
Unknown merchant         +10
Unexpected invoice       +15
Urgent language          +10
Suspicious URL           +10
Known scam pattern       +20
```

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
- JWT is used for the prototype.
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
- Three agents analyze separate concerns.
- Agent outputs use structured schemas.
- AI failure does not completely disable basic deterministic protection.

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
- JWT authentication.
- Validate all external input.
- Never trust LLM output as executable instructions.

## Privacy

- No call recording.
- No call audio sent to AI.
- Store minimum required user/payment context.

---

# 27. System Architecture

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
                         CrewAI
                           |
             ┌─────────────┼─────────────┐
             v             v             v
        Transaction     Social         Content
           Agent      Engineering       Agent
                          Agent
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
- Spring Security
- Spring Data JPA
- Hibernate

## AI Backend

- Python
- FastAPI
- CrewAI
- OpenAI-compatible LLM API

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

- Google Safe Browsing
- JWT
- HTTPS

## Real-Time

- WebSocket or Server-Sent Events

## Deployment

- Render

## Notifications

- Zapier Webhooks, optional

---

# 29. API Requirements

## Authentication

- `POST /api/auth/register`
- `POST /api/auth/login`

## Payments

- `POST /api/payments/analyze`
- `POST /api/payments/create`
- `GET /api/payments/{id}`
- `POST /api/payments/{id}/verify`
- `POST /api/payments/{id}/stop`

## Transactions

- `GET /api/transactions`
- `GET /api/transactions/{id}`
- `GET /api/transactions/profile`

## Risk

- `GET /api/risk/{transactionId}`
- `POST /api/risk/analyze`

## Subscriptions

- `GET /api/subscriptions`
- `GET /api/subscriptions/{id}`

## Reports

- `POST /api/scam-reports`
- `GET /api/scam-reports`

## Webhooks

- `POST /api/webhooks/paypal`

---

# 30. Python AI API

Spring Boot communicates with Python using internal APIs.

Conceptual endpoint:

`POST /ai/analyze-payment`

Input:

- Transaction data
- User profile
- Recipient information
- Merchant information
- Invoice
- Message
- URL
- Call state

Output:

```json
{
  "transaction_findings": [],
  "social_engineering_findings": [],
  "content_findings": [],
  "scam_pattern": "impersonation_payment_request",
  "explanation": "...",
  "recommended_risk": "high"
}
```

The Python service does not make the final payment decision.

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
10. Three agents return structured findings.
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
- Python/CrewAI
- Three agents
- Risk warning
- AG Grid dashboard
- Hero demo scenario

## P1 — Should Have

- URL scanning
- PayPal webhooks
- Real-time dashboard
- Subscription detection
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

- CrewAI
- AI agents
- LLM interaction
- NLP
- Social-engineering analysis
- Content analysis
- URL intelligence

### React + Vite owns:

- User interface
- Payment analysis experience
- Risk warning
- Security dashboard
- AG Grid
- Real-time visualization

Final architecture:

```text
React + Vite
   |
   v
Spring Boot
   |
   ├── PayPal
   ├── PostgreSQL
   ├── Webhooks
   └── Risk Engine
          |
          v
     Python FastAPI
          |
        CrewAI
          |
    ┌─────┼─────┐
    v     v     v
Transaction  Social  Content
   Agent    Agent    Agent
    └─────┼─────┘
          v
         LLM
          |
          v
   Structured Findings
          |
          v
   Spring Boot Risk Engine
          |
    ┌─────┼─────┐
    v     v     v
  ALLOW VERIFY STOP
          |
          v
       React + Vite
```

---

# 43. MVP Definition in One Sentence

> **GuardPay must be able to take a PayPal payment context, combine transaction behavior with social-engineering and content signals, generate an explainable risk score, and warn the user before a high-risk payment proceeds.**
