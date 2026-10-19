# GuardPay — API Contract (planned boundaries)

> Planned boundaries only. Endpoints are **not implemented** in this init step.
> This doc freezes the seams so frontend / backend / AI-service can be built independently.

Conventions (planned): JSON; `Authorization: Bearer <JWT>` on user-scoped
Spring Boot APIs; server-to-server shared secret on Spring Boot → Python;
idempotency on webhook ingestion; amounts as decimal strings with explicit
currency (e.g. `"amount": "2499.00", "currency": "USD"`).

---

## A. React + Vite → Spring Boot (public app API, planned base `http://localhost:8080`)

The React + Vite app calls these endpoints through the typed REST client
(`src/services/api-client.ts`, base URL from `VITE_API_BASE_URL`).

### Auth

```text
POST /api/auth/register   — planned: { name, email, password } → { user, token }
POST /api/auth/login      — planned: { email, password } → { user, token }
```

### Payments (GuardPay flow)

```text
POST /api/payments/analyze        — submit payment context, run profile + AI + risk engine
POST /api/payments/create         — create PayPal sandbox order/payment after assessment
GET  /api/payments/{id}           — payment + latest assessment
POST /api/payments/{id}/verify    — mark user-verified (medium-risk path)
POST /api/payments/{id}/stop      — mark stopped/blocked (high/critical path)
```

Planned `POST /api/payments/analyze` request shape:

```json
{
  "amount": "2499.00",
  "currency": "USD",
  "recipient": "new-recipient@example.com",
  "merchant": "unknown",
  "invoiceText": "Your account has been compromised. Call support immediately.",
  "messageText": "optional free-text context",
  "url": "https://example-suspicious-link.test/pay",
  "activeCall": true
}
```

Planned response shape:

```json
{
  "transactionId": "txn_123",
  "score": 94,
  "severity": "CRITICAL",
  "recommendation": "STOP",
  "scamPattern": "impersonation_payment_request",
  "signals": [
    { "name": "active_call", "weight": 15, "detected": true, "evidence": "Payment started during an active phone call.", "source": "deterministic" }
  ],
  "explanation": "We detected several signals associated with impersonation and payment-request scams...",
  "aiUnavailable": false
}
```

### Transactions / profile / risk

```text
GET /api/transactions              — list (query: riskLevel, status, merchant, dateFrom/To, min/maxAmount, page, sort)
GET /api/transactions/{id}         — detail incl. assessment history
GET /api/transactions/profile      — user behavioral profile (avg/median/max, known recipients, frequent merchants)
GET /api/risk/{transactionId}      — latest risk assessment for a transaction
POST /api/risk/analyze             — planned direct risk-analysis entry (same engine as payments/analyze)
```

### Subscriptions / reports / dashboard (planned, secondary)

```text
GET /api/subscriptions            — recurring-payment overview
GET /api/subscriptions/{id}       — subscription detail
POST /api/scam-reports            — { transactionId, scamType, description } → report
GET /api/scam-reports             — list reports
GET /api/dashboard/summary        — counts: analyzed, high-risk, stopped, verified, amount protected, patterns
```

Real-time (planned): WebSocket or SSE stream pushing transaction/risk updates to
React + Vite so the dashboard updates without refresh.

---

## B. Spring Boot → Python (internal AI API, planned base `http://localhost:8000`)

Single conceptual operation: **analyze payment context**.

```text
POST /ai/analyze-payment   (internal; shared-secret auth, never exposed to browsers)
```

Planned request: transaction data + user profile snapshot + recipient/merchant
info + invoice/message text + URL (+ its reputation result if already fetched) +
`activeCall` boolean. No payment credentials, no call audio — minimum context only.

Planned response (structured findings only — **no decision**):

```json
{
  "transaction_findings": [
    { "finding": "unusual_amount", "detected": true, "severity": "high", "evidence": "Payment is 28.7x higher than user average" }
  ],
  "social_engineering_findings": [
    { "finding": "fake_support_claim", "detected": true, "severity": "high", "evidence": "Caller claims to be PayPal support" }
  ],
  "content_findings": [
    { "finding": "urgent_language", "detected": true, "severity": "medium", "evidence": "Invoice says 'contact support immediately'" }
  ],
  "url_reputation": "suspicious",
  "scam_pattern": "impersonation_payment_request",
  "explanation": "Context suggests possible impersonation and payment-request scam...",
  "recommended_risk": "high"
}
```

Contract rules (planned): strict schema validation in Spring Boot; unknown
fields ignored; invalid responses trigger the deterministic-only fallback;
`recommended_risk` is advisory evidence, never the final score; per-finding
`severity` informs evidence display, not direct weights (weights stay in the
Spring Boot engine per `docs/risk-engine.md`).

Health (scaffold only, exists now): `GET /health → { "status": "ok" }`.

---

## C. PayPal → Spring Boot webhooks (planned)

```text
POST /api/webhooks/paypal   — PayPal event delivery (sandbox in MVP)
```

Planned behavior: verify webhook signature server-side; persist raw event;
process idempotently (dedupe by PayPal event ID); update transaction/payment
state in PostgreSQL; publish WS/SSE update for React + Vite; log event + outcome.
Consumes PayPal REST (OAuth, orders, payments, transaction search, invoicing,
subscriptions) exclusively from Spring Boot — never from the browser or the AI
service. PayPal Agent Toolkit/MCP may assist later but never replaces this path.

---

## D. What is explicitly out of contract in this step

No auth tokens are issued, no payments are created, no webhooks are verified,
and no AI analysis runs — all three services are scaffolds. This contract
exists so the next milestones can implement each seam without renegotiating it.
