# GuardPay — API Contract

> This document describes the **implemented** API. The original long-term
> endpoint plan is retained in `docs/GuardPay_PRD.md` and
> `docs/GuardPay_Project_Specification.md`; endpoints listed there are not a
> contract unless they appear here.

## 1. Global rules

- Default local base URLs: frontend `http://localhost:3000`, backend
  `http://localhost:8080`, AI service `http://localhost:8001`.
- The frontend resolves its backend URL from the Android native bridge, then
  `VITE_API_BASE_URL`, then the page's hostname on port 8080.
- Browser authentication uses an opaque `HttpOnly`, `SameSite=Lax` cookie named
  `guardpay_session`. Identity comes from that session; clients never supply an
  owner or `userId`.
- Unsafe cookie-authenticated requests (`POST`, `PUT`, `PATCH`, `DELETE`) must
  send `X-CSRF-Token` from `GET /api/auth/csrf` or the `csrfToken` returned by
  auth endpoints. The client refreshes once on `403 Invalid CSRF token`.
- Transactions, recipients, devices, alerts, blocklist entries, idempotency
  keys, captures, and SSE streams are owner-scoped.
- Risk is always server-derived. No endpoint accepts a score, level, action,
  `activeCall`, or other risk boolean from a browser.
- Amounts are decimal JSON numbers, not client-formatted strings. Currency is
  `USD` on the guarded path today.
- Validation and domain errors return a JSON body containing a `message`.
  Session expiry redirects the UI to `/login`.
- PayPal order creation exists only inside the guarded payment flow. Capture
  re-checks the stored decision.

## 2. Authentication and sessions

### Public auth bootstrap

```text
POST /api/auth/register   body: { email, password, displayName? }
POST /api/auth/login      body: { email, password }
POST /api/auth/logout     clears the session cookie
GET  /api/auth/me         { authenticated, user | null, csrfToken }
GET  /api/auth/csrf       { csrfToken }
```

- `password` is 8–72 characters and is stored as a BCrypt hash.
- Login is rate-limited and returns `429` after repeated failures.
- Sessions expire after 24 hours idle or 7 days absolute.
- `me` returns the current `UserView` (`id`, `email`, `displayName`) or
  `authenticated: false`.

### Authenticated session/password management

```text
GET    /api/auth/sessions                    active sessions for the current user
DELETE /api/auth/sessions/{sessionId}        revoke one owned session
POST   /api/auth/sessions/rotate             rotate the current session token
POST   /api/auth/logout-all                  revoke every session for the user
POST   /api/auth/password                    body: { currentPassword, newPassword }
```

Changing the password revokes other sessions. There is no password-reset or
email-verification endpoint yet.

### TOTP for controlled verification

```text
GET    /api/mfa/totp        { enrolled, secret, otpauthUri }
POST   /api/mfa/totp/enroll issue/replace the authenticator secret
DELETE /api/mfa/totp        disable TOTP
```

TOTP is used as an out-of-band channel for payment `VERIFY` challenges. It is
not login MFA. `GET` always returns `secret: null` and `otpauthUri: null`; only
`POST /enroll` returns the new secret and provisioning URI.

## 3. Devices

Device registration requires a signed-in user. The raw device credential is
returned exactly once; the backend stores only its hash.

```text
GET    /api/devices                         registered devices (no credential)
POST   /api/devices                         body: { deviceId?, label? }
POST   /api/devices/{deviceId}/rotate       issue a replacement credential
DELETE /api/devices/{deviceId}              revoke the device
```

Registration/rotation response:

```json
{ "deviceId": "...", "deviceToken": "...", "label": "...", "createdAt": "..." }
```

Device-signal intake is not cookie-authenticated:

```text
POST /api/device-signals/call-state
header: X-GuardPay-Device-Token: <per-device credential>
body:   { "deviceId": "...", "activeCall": true, "observedAt": "..." }
```

The backend rejects revoked credentials, stale/replayed observations, and
far-future timestamps. Observations older than five minutes are stale and never
count as an active call. This endpoint is CSRF-exempt because it uses the
device credential, not the session cookie.

## 4. Guarded payment flow

```text
POST /api/payments/guarded
header: Idempotency-Key: <optional stable key>
body:   payment/business context only
```

Request fields:

| Field | Required | Notes |
|---|---|---|
| `amount` | yes | `0.01`–`100000.00` |
| `currency` | yes | must be `USD` |
| `description` | no | max 255 characters |
| `recipientId` | no | owner-scoped known recipient |
| `recipientName` | no | resolved/learned when no id is supplied |
| `recipientEmail` | no | required before PayPal order creation |
| `recipientPaypalAccountId` | no | alternative PayPal destination |
| `merchantId` / `merchantName` | no | optional known/learned merchant |
| `deviceId` | no | paired device for trusted call state |
| `deviceFingerprint` | no | device hint, never a verdict |
| `contentText` | no | message/invoice text, max 4000 characters |
| `contentUrl` | no | URL for structural analysis, max 2048 characters |

`clientIp`, `geoCountry`, `geoCity`, `asn`, and `vpnOrProxy` are populated by
the server or a trusted resolver; JSON cannot forge them. There are deliberately
no risk fields on this request.

Response:

```json
{
  "paymentId": "...",
  "risk": {
    "score": 45,
    "riskLevel": "MEDIUM",
    "recommendedAction": "VERIFY",
    "explanation": "...",
    "automaticSignals": [
      {
        "type": "NEW_RECIPIENT",
        "detected": true,
        "source": "TRANSACTION_HISTORY",
        "reason": null,
        "evidence": "...",
        "severity": null,
        "confidence": null
      }
    ],
    "findings": [
      {
        "code": "NEW_RECIPIENT",
        "title": "New recipient",
        "description": "...",
        "points": 20,
        "triggered": true
      }
    ],
    "aiStatus": "AVAILABLE"
  },
  "paymentStatus": "VERIFICATION_REQUIRED",
  "paypalOrder": null,
  "verification": {
    "verificationId": "...",
    "status": "PENDING",
    "expiresAt": "...",
    "attemptsRemaining": 3,
    "simulated": true,
    "channelHint": "..."
  }
}
```

- `aiStatus` is `NOT_REQUESTED | AVAILABLE | UNAVAILABLE`.
- `paymentStatus` is `BLOCKED | VERIFICATION_REQUIRED |
  PAYPAL_AUTHORIZATION_REQUIRED | PENDING | APPROVED | COMPLETED | CANCELLED |
  FAILED`.
- A PayPal order is present only for `PAYPAL_AUTHORIZATION_REQUIRED` and later
  states.
- Repeating an `Idempotency-Key` with identical content returns the stored
  response and the same PayPal order. Reusing a key with different content
  returns `409`.

### Controlled VERIFY

```text
POST /api/payments/{paymentId}/verification/confirm
body: { "code": "123456" }
```

A `VERIFY` decision creates no PayPal order. The response carries
`verificationStatus`, `paymentStatus`, `expiresAt`, `attemptsRemaining`, and a
message; a PayPal order appears only after server-verified success. Wrong codes
do not verify. Expiry or exhausted attempts end the challenge.

The code is never returned by the payment API or written to logs. In the demo,
the simulated channel stores it in a loopback-only outbox:

```text
GET /api/dev/verifications/{paymentId}/code
```

The outbox is loopback-only and dev-only; disable it with
`GUARDPAY_DEV_VERIFICATION_OUTBOX=false`. If TOTP is enrolled, the challenge
uses the authenticator instead of the simulated channel.

## 5. Transaction history

```text
GET /api/transactions?page=&size=
GET /api/transactions/{id}
```

The list returns `{ items, page, size, totalItems, totalPages }`; each item has
amount, currency, status, timestamp, description, recipient/merchant names, and
the stored risk score/level/action. Detail adds PayPal order/capture/transaction
references, the stored assessment with findings, and verification status.
Unknown or another user's transaction returns `404`.

## 6. Automatic risk evaluation

```text
POST /api/risk/evaluate-automatic
```

Accepts payment context only: `amount`, `currency`, optional
`recipientId`/`merchantId`/`transactionId`, optional `deviceId`,
`deviceFingerprint`, server-observed network fields, and optional
`contentText`/`contentUrl`. There is no `activeCall` or other risk boolean.
The response has the same score/level/action/explanation/signals/findings/AI
status shape as the guarded risk block. This endpoint does not persist a
transaction and is not the order-creation path.

The legacy `POST /api/risk/evaluate` exists for regression/tooling with explicit
boolean inputs and is never used by the guarded flow.

## 7. Alerts and dashboard

```text
GET /api/alerts?limit=20       recent owner-scoped alerts
GET /api/alerts/stream         SSE; events: connected, snapshot, alert
GET /api/dashboard/summary     live aggregates
```

Alert rows carry id, transaction id, type, severity, title, message, score,
recommended action, amount/currency, recipient name, signal codes, and
creation time. The SSE snapshot seeds the feed on connect; new alerts are
broadcast after the notification transaction commits.

Dashboard summary:

```json
{
  "decisions": 12,
  "allowCount": 7,
  "verifyCount": 3,
  "stopCount": 2,
  "blockedAmount": 4998.00,
  "averageScore": 41.7,
  "latency": { "p50Ms": 12, "p95Ms": 45, "samples": 12 },
  "ai": { "requests": 10, "available": 8, "unavailable": 2, "availabilityPct": 80.0 },
  "alerts": { "total": 5, "critical": 2, "pending": 1, "sent": 4, "p95DeliveryMs": 120 },
  "verification": { "pending": 1, "verified": 2, "failed": 1, "expired": 0, "successRatePct": 50.0 }
}
```

Alerts are enqueued in `notification_outbox`; critical alerts enqueue email and
SMS delivery. Delivery is asynchronous and failure-isolated from the risk
decision.

## 8. Blocklist

```text
GET    /api/blocklist/recipients
POST   /api/blocklist/recipients       body: { recipientEmail?, recipientName?, reason? }
DELETE /api/blocklist/recipients/{recipientId}
```

The blocklist is owner-scoped. A blocklisted recipient is treated as a hard
block by the deterministic engine: the score is forced to 100 (`CRITICAL` →
`STOP`), regardless of other signal weights. This API has no dedicated
management screen yet; it is exercised by API tests and closes the loop with
the risk engine.

## 9. PayPal Sandbox

```text
GET  /api/paypal/health
POST /api/paypal/orders/{orderId}/capture       authenticated; stored-decision checked
POST /api/paypal/webhooks                       no user session; signature-verified
```

- `health` reports `UP` only when PayPal Sandbox credentials are configured.
- Capture is the only capture path. It re-checks the stored ALLOW/VERIFY state,
  uses a row lock, and replays the stored capture result without re-calling
  PayPal when already captured.
- Webhooks verify the PayPal signature, deduplicate by event id, persist the
  raw event, and update only PayPal payment state. They cannot change the
  GuardPay risk decision.
- Supported webhook transitions include order created/approved → `PENDING`,
  capture pending → `PENDING`, capture completed → `COMPLETED`, capture denied →
  `FAILED`. Refunds and unknown types are acknowledged without effect.
- External webhook delivery needs a public tunnel/deployment.

## 10. Developer-only endpoints

Loopback-only and intended for local demos/tests:

```text
GET  /api/dev/ai                                   read simulated AI outage flag
POST /api/dev/ai                                   toggle simulated AI outage
GET  /api/dev/verifications/{paymentId}/code       read simulated verification code
```

These are not production API surface.

## 11. Spring Boot → AI service

```text
POST /api/v1/analyze
header: X-GuardPay-Internal-Token: <optional shared secret>
body:   { transaction, content, device }
```

Request:

```json
{
  "transaction": {
    "amount": 2499.00,
    "currency": "USD",
    "merchant_name": "Unknown Shop",
    "recipient_name": "Fresh Stranger",
    "is_new_recipient": true,
    "is_unknown_merchant": true,
    "unexpected_invoice": true
  },
  "content": {
    "text": "You need to pay this invoice immediately...",
    "url": "https://example-suspicious-domain.test/pay"
  },
  "device": { "active_call": false }
}
```

Response (evidence only, never a decision):

```json
{
  "analysis_id": "...",
  "findings": [
    {
      "code": "IMPERSONATION",
      "title": "Impersonation",
      "description": "The message claims to be official support.",
      "severity": "HIGH",
      "confidence": 0.88,
      "evidence": "This is the official support department."
    }
  ],
  "summary": "1 scam indicator observed in the supplied context; see findings for evidence."
}
```

Controlled AI codes: `URGENT_LANGUAGE`, `IMPERSONATION`, `PRESSURE_TO_PAY`,
`SECRECY_REQUEST`, `SUSPICIOUS_PAYMENT_INSTRUCTION`, `SCAM_PATTERN`,
`SUSPICIOUS_URL_STRUCTURE`, `UNEXPECTED_INVOICE_CONTEXT`.

Spring Boot discards unknown codes, confidence below `0.5`, blank
title/description/evidence, and AI attempts to assert deterministic facts such
as `ACTIVE_CALL`. Five social codes fold into the single `SOCIAL_ENGINEERING`
signal; urgent language, suspicious URL structure, and unexpected invoice
context fold into their existing engine signals. AI can only add evidence.

AI service health:

```text
GET /health  { "status": "ok", "service": "guardpay-ai-service", "ai_configured": true|false }
```

## 12. Explicitly out of contract

- No `/api/payments/analyze`, `/api/payments/create`, `/api/payments/{id}/verify`,
  `/api/payments/{id}/stop`, `/api/transactions/profile`,
  `/api/subscriptions`, `/api/scam-reports`, or `/api/risk/{transactionId}`.
- No public PayPal order-creation route.
- No client-supplied risk verdict, owner, or `userId`.
- No password-reset, email-verification, OAuth, or MFA-at-login endpoints.
- No live Google Safe Browsing or external URL-reputation integration; URL
  analysis is structural and local.

## 13. Behavioral profiling (read-only)

### GET /api/behavior/profile

Returns the authenticated user's learned behavioral profile: status, transaction
count, last updated, and per-dimension availability (amount, frequency, time,
recipient, location, device). No raw statistics that could enable profile
poisoning are exposed. All ownership is enforced server-side through the
authenticated session.

Response shape:

```json
{
  "userId": "...",
  "profileStatus": "ESTABLISHED",
  "transactionCount": 85,
  "profileLastUpdated": "...",
  "dataQualityScore": "0.90",
  "amountBehavior": { "availability": "AVAILABLE", "medianAmount": 1000.00, ... },
  "frequencyBehavior": { "availability": "AVAILABLE", "paymentsPerDay": 3.5, ... },
  "timeBehavior": { "availability": "AVAILABLE", "peakHour": 14, ... },
  "recipientBehavior": { "availability": "AVAILABLE", "uniqueRecipientCount": 12, ... },
  "locationBehavior": { "availability": "AVAILABLE", "geoCountryCount": 3, ... },
  "deviceBehavior": { "availability": "AVAILABLE", "deviceCount": 2, ... },
  "behavioralDimensions": [...]
}
```

### GET /api/behavior/summary

Returns a safe summary of established behavior and recent anomalies for
dashboard display. No raw statistics.

### Behavioral findings in risk responses

`POST /api/risk/evaluate-automatic` and `POST /api/payments/guarded` now include
`behavioralProfileStatus` (`AVAILABLE`, `INSUFFICIENT_DATA`, `UNAVAILABLE`) in
the risk block, and automatic signals may include behavioral types
(`AMOUNT_DEVIATION`, `UNUSUAL_PAYMENT_HOUR`, `RECIPIENT_BEHAVIOR_DEVIATION`,
`TRANSACTION_FREQUENCY_ANOMALY`, `GEOGRAPHIC_BEHAVIOR_DEVIATION`,
`DEVICE_BEHAVIOR_DEVIATION`, `OVERALL_BEHAVIOR_DEVIATION`) sourced from
`BEHAVIORAL_PROFILE`.

