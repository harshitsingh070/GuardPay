# GuardPay — Complete Project Specification

## 1. Project Overview

> **Implementation note (current prototype):** Account registration/login is implemented with BCrypt password hashes and opaque `HttpOnly` session cookies (no OAuth/JWT). Identity always comes from the session, never from client-supplied fields, and account data is owner-scoped. The authentication sections below describe the original product plan; password reset, email verification, OAuth, and MFA-at-login remain future work.

**GuardPay** is an AI-powered, real-time payment safety platform designed around PayPal payments.

The core problem is not simply that fraudulent transactions exist. A major class of payment fraud happens because scammers manipulate users into **authorizing the payment themselves** through social engineering.

GuardPay therefore focuses on the context surrounding a payment.

It combines:

- PayPal transaction information
- User transaction history
- Recipient and merchant behavior
- Active-call context
- Invoice/payment-request content
- URL reputation
- Social-engineering indicators
- Recurring-payment/subscription signals
- AI-generated scam-pattern analysis

GuardPay produces a deterministic risk score, explains the reasons behind the score, and recommends one of three actions:

- **ALLOW** — low risk
- **VERIFY** — suspicious; user should verify
- **STOP** — high-risk payment; strong intervention

### Core product statement

> GuardPay is an AI-powered real-time payment safety agent that detects when a user may be manipulated into making a fraudulent PayPal payment, explains why the transaction is risky, and helps the user stop or safely verify it before money is sent.

### Core differentiator

Traditional fraud detection asks:

> "Is this transaction fraudulent?"

GuardPay asks:

> "Is someone manipulating this person into making this transaction?"

---

# 2. Primary Problem

Scammers frequently use social engineering rather than directly attacking payment infrastructure.

A typical attack can look like:

1. A scammer contacts the victim by phone, email, SMS, or messaging app.
2. The scammer impersonates PayPal, a bank, technical support, a merchant, a government organization, or another trusted entity.
3. The scammer creates urgency or fear.
4. The victim is instructed to open PayPal.
5. The victim is instructed to make a payment, approve an invoice, or transfer money.
6. The payment is technically authorized by the legitimate user.
7. Traditional transaction-only fraud detection may not understand the surrounding social-engineering context.

GuardPay addresses this gap by correlating multiple independent signals.

---

# 3. Target Users

## Primary users

- PayPal consumers
- Users making online purchases
- Users receiving PayPal invoices/payment requests
- Users vulnerable to phone-based social engineering
- Users managing recurring subscriptions

## Secondary users

- Small businesses
- Freelancers
- Online merchants
- Families helping less technically experienced users
- Security-conscious PayPal users

---

# 4. Main Use Case

## Fake PayPal Support Scam

Example:

A user receives an unexpected PayPal invoice for `$2,499`.

The invoice contains an urgent message:

> "Your account has been compromised. Contact support immediately."

The user calls the number.

The scammer claims to be PayPal support and tells the user:

> "To secure your account, send the money to this safe account."

The user opens PayPal and starts the payment.

GuardPay detects:

- Active phone call
- New recipient
- Unusually high amount
- Unexpected invoice
- Urgent language
- Impersonation pattern
- Suspicious URL or phone number
- Payment instructions inconsistent with normal behavior

GuardPay calculates a high risk score and presents a warning before the payment continues.

---

# 5. Product Features

## 5.1 Real-Time Payment Risk Analysis

Every payment can be analyzed before completion.

GuardPay evaluates:

- Amount
- Currency
- Recipient
- Merchant
- User transaction history
- Recipient history
- Transaction frequency
- Invoice/payment request
- URL
- Active call state
- Social-engineering indicators

### Output

- Risk score: `0–100`
- Severity
- Detected scam pattern
- Evidence
- Recommended action

---

# 6. Risk Levels

## Low Risk — 0–39

Action:

**ALLOW**

Example:

> $45 payment to a frequently used merchant with normal transaction characteristics.

UI:

> 🟢 Low Risk — Continue Payment

---

## Medium Risk — 40–69

Action:

**VERIFY**

Example:

> New merchant and unusually high payment, but no strong social-engineering evidence.

UI:

> 🟡 Review Payment

---

## High Risk — 70–84

Action:

**STRONG VERIFY**

The user receives a strong warning and should independently verify the payment.

---

## Critical Risk — 85–100

Action:

**STOP**

The system strongly recommends stopping the payment.

UI:

> 🔴 Potential Payment Scam

---

# 7. Risk Signals

The initial prototype should use the following signals.

| Signal | Initial Weight |
|---|---:|
| Active phone call | +15 |
| New recipient | +20 |
| Unusually large amount | +20 |
| Unknown merchant | +10 |
| Unexpected invoice | +15 |
| Urgent language | +10 |
| Suspicious URL | +10 |
| Known scam pattern | +20 |

The score should be normalized/capped to 100.

These weights are prototype heuristics, not statistically validated probabilities.

Never describe `94/100` as a `94% probability of fraud`.

Call it a **GuardPay Risk Score**.

---

# 8. AI Agent Architecture

GuardPay uses three specialized AI agents.

## 8.1 Transaction Risk Agent

### Responsibility

Analyze payment behavior and transaction history.

### Input

- Current amount
- Currency
- Recipient
- Merchant
- Historical transactions
- User average payment
- User maximum payment
- Known recipients
- Merchant frequency
- Payment frequency

### Example output

```json
{
  "unusual_amount": true,
  "new_recipient": true,
  "amount_vs_average": "28.7x",
  "evidence": [
    "Current payment is $2499",
    "User average payment is $87",
    "Recipient has no previous transaction history"
  ]
}
```

---

## 8.2 Social Engineering Agent

### Responsibility

Detect manipulation patterns.

### Detects

- Impersonation
- Fake support
- Fake PayPal representative
- Urgency
- Threats
- Account-compromise claims
- "Move your money to protect it"
- "Don't hang up"
- Instructions to call a specific number
- Pressure to make immediate payment
- Payment instructions from a caller

### Example output

```json
{
  "pattern": "impersonation_payment_request_scam",
  "confidence": "high",
  "signals": [
    "active_call",
    "urgency",
    "fake_support_claim",
    "payment_instruction"
  ]
}
```

---

## 8.3 Content Safety Agent

### Responsibility

Analyze content surrounding the payment.

### Input

- Invoice text
- Payment request
- Message
- URL
- Phone number
- Merchant information

### Detects

- Suspicious URLs
- Phishing
- Urgency
- Fake support messages
- Credential requests
- Payment instructions
- Suspicious phone numbers
- Scam language

URL reputation should use Google Safe Browsing or another reputable URL-security provider.

---

# 9. AI Decision Architecture

The LLM should **not** directly control the payment decision.

Correct architecture:

```text
Payment Data
      |
      v
Signal Extraction
      |
      v
CrewAI Agents
      |
      v
Structured AI Findings
      |
      v
Deterministic Risk Engine
      |
      v
Risk Score
      |
      +----> ALLOW
      +----> VERIFY
      +----> STOP
```

The LLM is responsible for:

- Understanding context
- Identifying scam patterns
- Explaining evidence
- Producing structured findings

The deterministic risk engine is responsible for:

- Combining signals
- Calculating the final risk score
- Selecting the final application action

This makes the system more predictable and defensible.

---

# 10. Technology Stack

## Frontend

### React + Vite

Purpose:

- Web application
- Payment experience
- Risk-warning interface
- Dashboard
- Authentication UI
- Real-time updates

### TypeScript

Used throughout the frontend.

### Tailwind CSS

Used for styling.

### shadcn/ui

Used for reusable interface components.

### AG Grid

Used for the transaction and risk dashboard.

AG Grid is a deliberate sponsor technology and should be visibly useful rather than added artificially.

---

# 11. Main Backend

## Spring Boot

Spring Boot is the main business/backend platform.

Use:

- Java 17
- Spring Boot 3.x
- Spring Web
- Spring Security
- Spring Data JPA
- Hibernate
- PostgreSQL driver
- WebSocket/SSE support

### Spring Boot responsibilities

- REST APIs
- Authentication
- User management
- PayPal API integration
- PayPal webhooks
- Transaction management
- Database operations
- User transaction profiles
- Deterministic risk engine
- Risk assessment storage
- Subscription data
- Alert management
- Frontend communication

Spring Boot is the trusted application/business layer.

---

# 12. Python AI Service

## FastAPI

Python runs as a separate specialized AI service.

### Python responsibilities

- CrewAI
- AI agent orchestration
- LLM calls
- NLP
- Social-engineering analysis
- Invoice analysis
- URL/content analysis
- Structured AI output

### Why Python is separate

The main payment/business layer remains in Java.

Python is used because the AI-agent ecosystem, CrewAI, NLP tooling, and LLM integrations are particularly convenient in Python.

---

# 13. Database

## PostgreSQL

PostgreSQL stores:

- Users
- Transactions
- Recipients
- Merchants
- Risk assessments
- Risk signals
- Alerts
- Subscriptions
- Scam reports
- Agent findings

---

# 14. Proposed Database Entities

## users

Fields:

- id
- name
- email
- created_at
- updated_at

## transactions

Fields:

- id
- paypal_transaction_id
- user_id
- amount
- currency
- merchant
- recipient
- status
- risk_score
- risk_level
- created_at

## recipients

Fields:

- id
- user_id
- recipient_identifier
- first_seen_at
- transaction_count
- total_amount

## merchants

Fields:

- id
- merchant_identifier
- name
- first_seen_at
- transaction_count
- risk_status

## risk_assessments

Fields:

- id
- transaction_id
- score
- severity
- recommendation
- scam_pattern
- explanation
- created_at

## risk_signals

Fields:

- id
- risk_assessment_id
- signal_name
- weight
- detected
- evidence
- source

## subscriptions

Fields:

- id
- user_id
- merchant
- amount
- currency
- billing_period
- next_payment
- previous_amount
- current_amount
- status

## alerts

Fields:

- id
- user_id
- transaction_id
- severity
- message
- status
- created_at

## scam_reports

Fields:

- id
- user_id
- transaction_id
- scam_type
- description
- created_at

---

# 15. PayPal Integration

Use PayPal's developer sandbox for the prototype.

Relevant PayPal capabilities:

- Orders
- Payments
- Transaction Search
- Invoicing
- Subscriptions
- Webhooks
- Disputes
- PayPal AI/agent tooling where useful

### Orders

Used for payment/order workflows.

### Payments

Used for payment state and payment information.

### Transaction Search

Used to understand historical user transaction behavior.

### Invoicing

Used for invoice/payment-request scenarios.

### Subscriptions

Used for recurring payment intelligence.

### Webhooks

Used for real-time PayPal events.

### Disputes

Used for post-payment assistance.

Disputes should not be part of the critical real-time fraud path.

---

# 16. PayPal Agent Toolkit / MCP

Use PayPal's agent-oriented tooling where it provides real value.

The agent layer should not replace the main Spring Boot payment backend.

Recommended relationship:

```text
Spring Boot
    |
    +---- PayPal REST APIs
    |
    +---- PayPal Agent Toolkit / MCP where useful
```

The system should remain understandable even if the AI service is temporarily unavailable.

---

# 17. Call Detection

The product only needs a **call-state signal**.

It does not need to record the call.

Required signal:

```text
ACTIVE_CALL = TRUE/FALSE
```

### Android prototype

Use Android phone/call-state capabilities where permitted.

### Web prototype

Provide a controlled demo toggle:

> 📞 Active Call: ON

This is useful because the hackathon demo should not depend entirely on OS-specific behavior.

### Privacy principle

GuardPay should not record or store phone-call audio.

---

# 18. URL Security

Use Google Safe Browsing or another reputable URL reputation service.

Input:

- URL from invoice
- URL from payment request
- URL pasted by user

Output:

- Safe
- Suspicious
- Malicious/unsafe
- Unknown

The Content Safety Agent combines this technical URL result with the surrounding text context.

---

# 19. Real-Time Architecture

Use:

**PayPal Webhooks → Spring Boot → PostgreSQL → WebSocket/SSE → React + Vite**

Example:

```text
PayPal
   |
   | payment event
   v
Spring Boot Webhook
   |
   v
Risk/Transaction Update
   |
   v
PostgreSQL
   |
   v
WebSocket/SSE
   |
   v
React + Vite Dashboard
```

This makes the dashboard update without page refresh.

---

# 20. Frontend Screens

The MVP should have three primary screens.

## Screen 1 — Payment Analysis

Fields:

- Amount
- Currency
- Recipient
- Merchant
- Invoice/message
- URL
- Call status

Button:

> ANALYZE PAYMENT

---

## Screen 2 — Risk Warning

Main information:

- Risk score
- Severity
- Detected scam pattern
- Evidence
- AI explanation
- Recommended action

Buttons:

> STOP PAYMENT

> VERIFY RECIPIENT

> CONTINUE ANYWAY

---

## Screen 3 — Security Dashboard

Use AG Grid.

Columns:

- Transaction ID
- Date
- Merchant
- Recipient
- Amount
- Risk score
- Risk level
- Scam pattern
- Status
- Action

Filters:

- Risk level
- Status
- Merchant
- Date
- Amount

Sort:

- Highest risk
- Highest amount
- Newest transaction

---

# 21. Example Risk Warning

```text
🚨 HIGH-RISK PAYMENT

Risk Score: 94/100

Potential impersonation/payment-request scam

Why GuardPay flagged this:

🔴 Active phone call
🔴 New recipient
🔴 $2,499 vs $87 user average
🔴 Unexpected invoice
🔴 Urgent language detected
🔴 Suspicious payment URL

The surrounding context suggests that someone
may be manipulating you into making this payment.

[ STOP PAYMENT ]

[ VERIFY RECIPIENT ]

[ CONTINUE ANYWAY ]
```

---

# 22. Subscription Protection

This is a secondary feature.

GuardPay analyzes recurring payments.

It can detect:

- New subscriptions
- Price increases
- Unusual recurring payments
- Duplicate subscriptions
- Forgotten subscriptions
- Unfamiliar recurring merchants

Example:

> "Your recurring payment increased from $49.99 to $69.99."

Another example:

> "This recurring payment is from a merchant you have not used for six months. Review before continuing."

Do not let subscription functionality distract from the primary scam-detection experience.

---

# 23. Post-Payment Protection

Optional secondary feature.

If the user reports a suspicious payment, GuardPay can:

- Display transaction information
- Explain why it was flagged
- Collect relevant evidence
- Guide the user through the dispute/report process

Do not promise successful refunds.

---

# 24. Notifications

Use Zapier only as an optional notification layer.

Example:

```text
High-risk payment detected
       |
       v
Spring Boot
       |
       v
Zapier Webhook
       |
       +----> Email
       |
       +----> SMS
```

Notification example:

> GuardPay blocked a high-risk $2,499 payment to an unknown recipient.

Zapier must never be part of the core fraud-prevention path.

---

# 25. Authentication

Use Spring Security with JWT for the prototype.

Flow:

```text
Login
  |
  v
Spring Security
  |
  v
JWT
  |
  v
Protected APIs
```

Never expose:

- PayPal client secrets
- LLM API keys
- Safe Browsing keys
- database credentials

to the frontend.

Store secrets as server-side environment variables.

---

# 26. Service Architecture

The system consists of three major applications.

## Application 1 — Frontend

**React + Vite**

Responsible for:

- User interface
- Dashboard
- Payment analysis form
- Risk warning
- Authentication UI
- Real-time display

## Application 2 — Main Backend

**Spring Boot**

Responsible for:

- Authentication
- PayPal
- Database
- Transactions
- Webhooks
- Risk engine
- API
- Real-time communication

## Application 3 — AI Service

**Python + FastAPI + CrewAI**

Responsible for:

- Agent orchestration
- LLM
- NLP
- Scam-pattern classification
- Content analysis

---

# 27. Complete Architecture

```text
                         USER
                           |
                           v
                    React + Vite Frontend
                           |
                           | REST / WebSocket
                           v
                  ┌───────────────────┐
                  │    Spring Boot    │
                  │     Java 17       │
                  └─────────┬─────────┘
                            |
             ┌──────────────┼───────────────┐
             |              |               |
             v              v               v
          PayPal        PostgreSQL       Risk Engine
           APIs             |               |
             |              |               |
          Webhooks           |               |
             |              |               |
             └──────────────┼───────────────┘
                            |
                            v
                    Python AI Service
                         FastAPI
                            |
                          CrewAI
                            |
           ┌────────────────┼────────────────┐
           |                |                |
           v                v                v
      Transaction      Social Engineering   Content
         Agent               Agent           Agent
           |                |                |
           └────────────────┼────────────────┘
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
                 ┌──────────┼──────────┐
                 |          |          |
                 v          v          v
               ALLOW      VERIFY      STOP
                 |          |          |
                 └──────────┼──────────┘
                            v
                       React + Vite UI
```

---

# 28. API Communication

## Frontend → Spring Boot

Examples:

- POST `/api/auth/login`
- GET `/api/transactions`
- POST `/api/payments/analyze`
- POST `/api/payments/create`
- GET `/api/risk/{transactionId}`
- GET `/api/subscriptions`
- POST `/api/scam-reports`
- GET `/api/dashboard/summary`

## Spring Boot → Python

Example conceptual operation:

> Analyze payment context

Input:

- transaction
- user profile
- recipient
- invoice
- URL
- call state

Output:

- transaction findings
- social-engineering findings
- content findings
- scam pattern
- explanation

## Spring Boot → PayPal

Used for:

- OAuth
- Orders
- Payments
- Transaction Search
- Invoicing
- Subscriptions
- Webhooks

---

# 29. Security Requirements

## Secrets

All credentials must be environment variables.

Never commit:

- PayPal secrets
- LLM keys
- database passwords
- Safe Browsing keys
- JWT secrets

## API Security

- HTTPS
- JWT authentication
- Input validation
- Rate limiting where practical
- Server-side PayPal calls
- Server-side AI calls

## AI Security

Do not let an LLM directly execute arbitrary payment actions.

The LLM produces structured findings.

Spring Boot validates those findings.

The deterministic risk engine makes the final application decision.

---

# 30. Privacy Requirements

GuardPay should follow a privacy-first design.

Do not:

- Record calls
- Store call audio
- Send call audio to the LLM
- Store unnecessary personal data
- Expose payment secrets to AI agents

Use only the minimum context needed.

The call detector should provide:

> Active call = true

rather than:

> Full call recording.

---

# 31. Error Handling

Every external integration must have a fallback.

### PayPal unavailable

Show:

> Payment service temporarily unavailable.

### AI service unavailable

Use deterministic risk signals and show:

> AI analysis unavailable. Basic risk protection remains active.

This is important.

GuardPay should not become completely useless because the LLM API fails.

### Safe Browsing unavailable

Mark:

> URL reputation unavailable

rather than automatically calling the URL safe.

---

# 32. AI Service Failure Strategy

If Python/CrewAI fails:

```text
Payment
   |
   v
Spring Boot
   |
   v
Deterministic signals
   |
   v
Basic risk score
   |
   v
Continue with reduced analysis
```

This gives the system graceful degradation.

---

# 33. Logging

Spring Boot should log:

- API errors
- PayPal errors
- webhook events
- risk decisions
- AI-service failures

Python should log:

- agent execution
- LLM failures
- malformed outputs
- content-analysis failures

Do not log secrets or sensitive payment credentials.

---

# 34. Testing Strategy

## Unit tests

Spring Boot:

- Risk calculation
- Transaction profile calculations
- Authentication
- PayPal service logic
- Webhook processing

Python:

- Agent output validation
- Scam-pattern detection
- Structured response parsing

## Integration tests

Test:

- Spring Boot → PostgreSQL
- Spring Boot → PayPal sandbox
- Spring Boot → Python AI service
- PayPal webhook → Spring Boot

## Demo tests

Three mandatory scenarios:

### Scenario A — Safe

$45, known merchant, no call.

Expected:

> Low risk / Continue

### Scenario B — Suspicious

$800, new recipient, unexpected invoice, suspicious URL.

Expected:

> Medium risk / Verify

### Scenario C — Scam

$2,499, active call, new recipient, urgent invoice, suspicious URL.

Expected:

> Critical risk / Stop

---

# 35. Demo Data

Create a deterministic demo user.

Example:

```text
User: Alex
Average payment: $87
Maximum payment: $420
Known recipients: 12
Common merchants:
- Amazon
- Spotify
- Adobe
- Uber
```

Seed approximately 50 historical transactions.

Make the data realistic:

- Different amounts
- Different merchants
- Different dates
- Different recipients
- Normal recurring payments
- Occasional larger purchases

Then create the three demo scenarios.

---

# 36. Demo Scenario C — Main Showstopper

Transaction:

```text
Amount: $2,499
Recipient: New Recipient
Merchant: Unknown
Invoice: Unexpected
Active Call: Yes
URL: Suspicious
Urgency: High
```

GuardPay detects:

```text
Active call
New recipient
Abnormal amount
Unexpected invoice
Urgency
Suspicious URL
Impersonation pattern
```

Result:

> **94/100 — CRITICAL**

Pattern:

> **Impersonation / Payment-Request Scam**

Recommendation:

> **STOP**

---

# 37. Development Order

## Phase 1 — Project Setup

Set up:

- GitHub repository
- React + Vite
- Spring Boot
- Python/FastAPI
- PostgreSQL
- Environment configuration
- Local development setup

Create separate directories/repositories only if team workflow requires it.

Recommended monorepo:

```text
guardpay/
├── frontend/
├── backend/
├── ai-service/
├── docs/
└── README.md
```

---

## Phase 2 — Spring Boot Core

Build:

- User model
- Transaction model
- Recipient model
- Risk model
- PostgreSQL connection
- REST APIs
- JWT authentication

---

## Phase 3 — PayPal Integration

Implement:

- OAuth
- Sandbox configuration
- Order creation
- Payment information
- Transaction history
- Webhook handling

---

## Phase 4 — Demo Data

Seed:

- 50 transactions
- 12 known recipients
- Common merchants
- Normal payment history

Build user profile calculations.

---

## Phase 5 — Deterministic Risk Engine

Implement:

- Signal detection
- Risk weights
- Score normalization
- Risk severity
- ALLOW/VERIFY/STOP decision

This must work without AI.

---

## Phase 6 — Frontend MVP

Build:

- Payment form
- Call toggle
- Risk warning
- Transaction dashboard
- AG Grid

At this point you should already have a working non-AI prototype.

---

## Phase 7 — Python AI Service

Set up:

- FastAPI
- CrewAI
- LLM provider
- Structured outputs
- Authentication between Spring and Python

---

## Phase 8 — Three AI Agents

Implement:

1. Transaction Agent
2. Social Engineering Agent
3. Content Safety Agent

Make every agent return structured evidence.

---

## Phase 9 — URL and Content Analysis

Add:

- Safe Browsing
- Invoice analysis
- Payment-request analysis
- Scam-language detection

---

## Phase 10 — Integration

Connect:

```text
Spring Boot
      |
      v
Python AI
      |
      v
CrewAI
      |
      v
LLM
      |
      v
Structured findings
      |
      v
Spring Risk Engine
```

---

## Phase 11 — Real-Time Experience

Add:

- PayPal webhooks
- WebSocket/SSE
- Live dashboard updates
- Live risk status

---

## Phase 12 — Secondary Features

Only after the core works:

- Subscription monitoring
- Price-change detection
- Post-payment assistance
- Scam reports
- Notifications
- Zapier integration

---

# 38. MVP Definition

The project is considered MVP-complete when this works end-to-end:

```text
User starts payment
       |
       v
Spring Boot receives payment context
       |
       v
User profile analyzed
       |
       v
Python AI analyzes context
       |
       v
Three agents return findings
       |
       v
Spring risk engine calculates score
       |
       v
GuardPay displays warning
       |
       v
User chooses STOP / VERIFY / CONTINUE
```

If this works reliably, the core hackathon project is complete.

Everything else is enhancement.

---

# 39. What Not to Build

Do not waste hackathon time on:

- Custom machine-learning fraud model
- Call recording
- Full iOS implementation
- Complex microservices
- Kubernetes
- Kafka
- Redis unless required
- Custom vector database
- 10+ AI agents
- Full accounting system
- Full dispute automation
- Production-grade banking infrastructure

The project needs to demonstrate the concept convincingly, not become a production payment processor.

---

# 40. Sponsor Prize Alignment

GuardPay directly targets these categories:

## Best Use of PayPal + AI

Core functionality:

- PayPal transactions
- PayPal APIs
- AI agents
- AI-powered fraud prevention

## Best Use of Agentic Commerce

The agents analyze context and assist the user around the payment lifecycle.

The agent architecture should be visible in the demo.

## Best Use of AG Grid

AG Grid should be the actual transaction/risk-management interface.

Use:

- Filtering
- Sorting
- Risk-level grouping
- Status
- Transaction details
- Risk score

## Best Use of Render

Deploy the actual working application on Render.

Avoid claiming the prize simply because the application is hosted there. The deployment should be meaningful and functional.

---

# 41. Optional Sponsor Integrations

Potential additional sponsor integrations can be considered later:

- APIMatic
- Bryntum
- Channel3

Only integrate these if they provide a genuine capability.

Do not add a sponsor technology simply to claim a prize if it makes the architecture worse.

---

# 42. Core Project Metrics

For the demo dashboard, show:

- Transactions analyzed
- High-risk transactions
- Payments stopped
- Payments verified
- Total amount protected
- Active subscriptions
- Suspicious payment requests
- Average risk score

Example:

```text
Transactions Analyzed       128
High-Risk Payments            7
Payments Stopped              5
Payments Verified             2
Amount Protected          $8,742
Scam Patterns Detected        11
```

These are demo metrics and should be clearly treated as such.

---

# 43. Main Demo Story

The presentation should not start with architecture.

Start with the problem.

> "Scammers don't need to hack PayPal. They can convince the victim to authorize the payment."

Then show the fake invoice.

Then show the phone call.

Then show the user opening the payment.

Then GuardPay detects the combination of signals.

Then show:

> **94/100 — CRITICAL**

Then explain why.

Then stop the payment.

Then reveal the architecture.

The story should be:

**Scam → Context → Detection → Explanation → Intervention → Protection**

---

# 44. One-Sentence Pitch

> **GuardPay is an AI payment safety agent that detects social-engineering attacks by correlating transaction behavior, payment content, merchant risk, and call context before a user sends money.**

---

# 45. Final Technology Summary

### Frontend

- React + Vite
- TypeScript
- Tailwind CSS
- shadcn/ui
- AG Grid

### Main Backend

- Java 17
- Spring Boot
- Spring Security
- Spring Data JPA
- Hibernate

### AI Backend

- Python
- FastAPI
- CrewAI
- GPT/current available OpenAI model

### Data

- PostgreSQL

### Payments

- PayPal REST APIs
- PayPal Agent Toolkit/MCP where useful
- PayPal Webhooks

### Security

- Google Safe Browsing
- JWT
- HTTPS
- Server-side secrets

### Real-time

- WebSocket/SSE

### Notifications

- Zapier

### Deployment

- Render

### Version Control

- Git
- GitHub

---

# 46. Final Architecture Decision

**Do not change this unless implementation problems force a change.**

```text
                    REACT + VITE
                       |
                       v
                SPRING BOOT
                 JAVA 17
                       |
       ┌───────────────┼────────────────┐
       |               |                |
    PAYPAL         POSTGRESQL       RISK ENGINE
       |                                |
   WEBHOOKS                             |
       |                                |
       └───────────────┬────────────────┘
                       |
                       v
                PYTHON FASTAPI
                       |
                    CREWAI
                       |
        ┌──────────────┼──────────────┐
        |              |              |
   Transaction     Social          Content
      Agent       Engineering       Agent
                     Agent
        |              |              |
        └──────────────┼──────────────┘
                       |
                       v
                      LLM
                       |
                       v
                STRUCTURED FINDINGS
                       |
                       v
                SPRING BOOT RISK
                    ENGINE
                       |
              ┌────────┼────────┐
              |        |        |
            ALLOW    VERIFY    STOP
              |        |        |
              └────────┼────────┘
                       |
                       v
                  REACT + VITE UI
```

**The core principle is simple: Spring Boot owns the money and business logic. Python owns the intelligence. The LLM provides analysis, but never gets unrestricted control over payments.**
