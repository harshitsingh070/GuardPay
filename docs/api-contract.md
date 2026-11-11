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
status shape as the guarded risk block, plus `calibrationVersion` (the deployed
learning calibration used for this decision, or `null` for deterministic
defaults). This endpoint does not persist a transaction and is not the
order-creation path.

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

### GET /api/behavior/high-value/baseline

Authenticated, read-only, and owner-scoped. Optional `currency` query parameter
(default `USD`). Returns the currency-specific high-value spending baseline used
by the first-time high-value engine. When the profile service is unavailable it
returns `profileStatus="UNAVAILABLE"`, `transactionCount=0`, null metrics, and
`established=false`; it never fabricates a baseline.

```json
{
  "currency": "USD",
  "profileStatus": "ESTABLISHED",
  "transactionCount": 24,
  "medianAmount": 1800.00,
  "madAmount": 240.00,
  "historicalMax": 9500.00,
  "p75Amount": 3100.00,
  "p90Amount": 7200.00,
  "p95Amount": 8800.00,
  "typicalHighValueThreshold": 7200.00,
  "established": true
}
```

### Behavioral findings in risk responses

`POST /api/risk/evaluate-automatic` and `POST /api/payments/guarded` now include
`behavioralProfileStatus` (`AVAILABLE`, `INSUFFICIENT_DATA`, `UNAVAILABLE`) in
the risk block, and automatic signals may include behavioral types
(`AMOUNT_DEVIATION`, `UNUSUAL_PAYMENT_HOUR`, `RECIPIENT_BEHAVIOR_DEVIATION`,
`TRANSACTION_FREQUENCY_ANOMALY`, `GEOGRAPHIC_BEHAVIOR_DEVIATION`,
`DEVICE_BEHAVIOR_DEVIATION`, `OVERALL_BEHAVIOR_DEVIATION`) sourced from
`BEHAVIORAL_PROFILE`.

First-time high-value evidence uses these additional behavioral signal codes:

- `FIRST_HIGH_VALUE_PAYMENT`
- `FIRST_HIGH_VALUE_TO_RECIPIENT`
- `FIRST_PAYMENT_TO_RECIPIENT_HIGH_VALUE`
- `SIGNIFICANT_AMOUNT_DEVIATION`
- `AMOUNT_EXCEEDS_HISTORICAL_MAXIMUM`
- `RAPID_AMOUNT_ESCALATION`

Findings may carry `severity`, `confidence`, `deviationRatio`, `observedValue`,
`expectedValue`, `profileWindow`, `contributingFactors`, and `evidence`. Cold
start produces no high-value finding. A specialized high-value signal suppresses
the generic `AMOUNT_DEVIATION` points so amount evidence is not counted twice.
See [`first-time-high-value.md`](first-time-high-value.md).

## 14. Global recipient intelligence

All routes are authenticated, owner-scoped, and CSRF-protected for mutations.
Identifiers are never accepted in a response and are hashed before storage.
Missing intelligence configuration returns `UNAVAILABLE`/503; it is never
presented as a clean recipient.

### POST /api/intelligence/recipients/reports

Submits a fraud report for a recipient the caller has paid. Either
`relatedTransactionId` (owned by the caller) or an explicit
`identifierType` + `identifierValue` is required. `recipientReference`
alone is not accepted. Rate limited per reporter and per recipient/day.
Reports store structured evidence only; there is no free-text description.

Request:

`json
{
  "relatedTransactionId": "���",
  "incidentAt": "2026-01-01T10:00:00Z"
}
`

Response `201`:

`json
{
  "reportId": "�",
  "recipientReference": "�",
  "status": "SUBMITTED",
  "submittedAt": "�"
}
`

### GET /api/intelligence/recipients/{recipientReference}/summary

Returns an evidence-only summary for a recipient the caller has a relationship
with. Never includes other users' reports or evidence detail.

### GET /api/intelligence/reports

Paged list of the caller's own reports.

### GET /api/intelligence/reports/{reportId}

One of the caller's own reports.

### GET /api/intelligence/summary

Aggregate counts for the caller's reports.

### Admin review

`/api/admin/intelligence/**` requires `users.admin = true`. Admin review is
the only path that can set `CONFIRMED_FRAUD`; automatic evidence can never
reach that status. Review actions are recorded in
`recipient_review_events`.

### Reputation in payment responses

`POST /api/risk/evaluate-automatic` and `POST /api/payments/guarded` include
an identifier-free `recipientReputation` object:

`json
{
  "status": "MATCH_FOUND",
  "reputationStatus": "REPORTED",
  "category": "ELEVATED_RISK",
  "score": 40,
  "points": 10,
  "matchLevel": "EXACT_VERIFIED_MATCH",
  "identityConfidence": "VERY_HIGH",
  "evidenceCount": 2,
  "reportCount": 2,
  "reason": "GLOBAL_RECIPIENT_REPORTED_EXACT_VERIFIED_MATCH"
}
`

`status` is one of `NO_DATA`, `NO_MATCH`, `MATCH_FOUND`,
`UNAVAILABLE`. `points` is bounded at 40 and already included in the score;
the engine also emits a `GLOBAL_RECIPIENT_*` finding for display.

### Admin bootstrap

`guardpay.intelligence.admin-emails` / `GUARDPAY_INTELLIGENCE_ADMIN_EMAILS`
promotes matching existing users to admin on application startup. The list is
server-side configuration only; clients cannot grant themselves admin.

## 15. Explicit fraud feedback

Feedback is an evaluation label, not a payment decision. A label snapshots the
latest risk decision for the transaction so metrics remain reproducible after
the engine changes. The canonical base path is `/api/fraud-feedback`; labels are
`FRAUD` or `LEGITIMATE`.

A label has a lifecycle:

```text
SUBMITTED -> UNDER_REVIEW -> VERIFIED_FRAUD | VERIFIED_LEGITIMATE
                         \-> REJECTED -> UNDER_REVIEW (reopen)
correction -> SUBMITTED (UPDATED event; verified corrections invalidate learning)
```

Only `VERIFIED_FRAUD` / `VERIFIED_LEGITIMATE` are eligible for learning.
`SUPERSEDED` exists in the status enum for stored-history compatibility but is
not produced by the current correction path.
Every review writes an append-only `fraud_feedback_events` row and a
`fraud_feedback_reviews` row. A user cannot verify their own label.

### POST /api/fraud-feedback/transactions/{transactionId}

Optional header: `Idempotency-Key: <stable key>`. The body is:

```json
{
  "label": "FRAUD",
  "feedbackType": "SCAM_OR_SOCIAL_ENGINEERING",
  "reasonCode": "SOCIAL_ENGINEERING",
  "relatedFindingCode": "SOCIAL_ENGINEERING",
  "idempotencyKey": "..."
}
```

Only `label` is required. `feedbackType` and `reasonCode` are bounded enums;
`relatedFindingCode` is a bounded code; no free-text description is stored.
Changing a verified label resets it to `SUBMITTED`, records an `UPDATED` event,
and invalidates affected learning artifacts. The transaction must belong to the
caller. Response `201`:

```json
{
  "id": "...",
  "transactionId": "...",
  "label": "FRAUD",
  "feedbackType": "SCAM_OR_SOCIAL_ENGINEERING",
  "feedbackStatus": "SUBMITTED",
  "source": "USER",
  "decisionAction": "VERIFY",
  "riskScore": 65,
  "riskAssessmentId": "...",
  "recipientEntityId": null,
  "relatedFindingCode": "SOCIAL_ENGINEERING",
  "reasonCode": "SOCIAL_ENGINEERING",
  "reviewerReference": null,
  "reviewerConfidence": null,
  "reviewedAt": null,
  "createdAt": "...",
  "updatedAt": "..."
}
```

`source` is `USER` or `ADMIN` based on who submitted. Repeating the same
idempotency key with identical content returns the stored row; different
content under the same key is rejected.

### User feedback reads

```text
GET /api/fraud-feedback/transactions/{transactionId}   current label or 404
GET /api/fraud-feedback/mine?page=&size=                caller's feedback (paged)
GET /api/fraud-feedback/summary                        counts by lifecycle status
GET /api/fraud-feedback/{feedbackId}                   detail + events + reviews
```

`FeedbackDetailDto` is `{ feedback, events[], reviews[] }`.

### Admin review

All routes require an admin user.

```text
GET  /api/admin/fraud-feedback?status=&page=&size=
GET  /api/admin/fraud-feedback/summary
GET  /api/admin/fraud-feedback/metrics
GET  /api/admin/fraud-feedback/export
GET  /api/admin/fraud-feedback/{feedbackId}
POST /api/admin/fraud-feedback/{feedbackId}/review
```

Review body:

```json
{ "action": "VERIFY_FRAUD", "confidence": 0.9, "note": "..." }
```

`action` is `START_REVIEW | VERIFY_FRAUD | VERIFY_LEGITIMATE | REJECT | REOPEN`.
`confidence` is optional, defaults to `0.9` for verification actions, and must
be `0..1`. `REJECT` is not allowed from a verified state; `START_REVIEW` is not
allowed from `SUPERSEDED`; `REOPEN` is not allowed from `SUBMITTED`. Invalid
transitions return `409`. A verified fraud label is also bridged to recipient
intelligence as verified investigation evidence; it can move a recipient to
`UNDER_REVIEW`, never directly to `CONFIRMED_FRAUD`.

`GET /api/admin/fraud-feedback/metrics` returns:

```json
{
  "total": 10,
  "fraudLabels": 6,
  "legitimateLabels": 4,
  "truePositive": 5,
  "falsePositive": 1,
  "trueNegative": 3,
  "falseNegative": 1,
  "unclassified": 0,
  "precision": 0.8333,
  "recall": 0.8333
}
```

A flagged decision is `VERIFY` or `STOP`. Feedback with no recorded decision
action is `unclassified` and excluded from precision/recall denominators.
`precision` or `recall` is `null` when its denominator is zero; the API never
fabricates a metric. `export` is the admin-only JSON list used for offline
evaluation.

## 16. Continuous learning (admin-only)

The learning console builds immutable datasets from admin-verified feedback,
trains candidate rule calibrations, and requires explicit promotion. It never
auto-deploys and never changes signal weights or scoring.

```text
GET  /api/admin/learning/overview
GET  /api/admin/learning/datasets
POST /api/admin/learning/datasets
GET  /api/admin/learning/datasets/{datasetId}
GET  /api/admin/learning/datasets/{datasetId}/exclusions
POST /api/admin/learning/datasets/{datasetId}/train
GET  /api/admin/learning/models
GET  /api/admin/learning/models/{modelId}
POST /api/admin/learning/models/{modelId}/approve
POST /api/admin/learning/models/{modelId}/reject
POST /api/admin/learning/models/{modelId}/deploy
POST /api/admin/learning/models/{modelId}/rollback
GET  /api/admin/learning/events
```

All endpoints require an admin; non-admins receive `403`.

- `POST /datasets` builds from `VERIFIED_FRAUD` + `VERIFIED_LEGITIMATE` feedback
  and returns the dataset even when it is `INSUFFICIENT_DATA`. Minimums are
  `20` records, `5` fraud labels, and `5` legitimate labels. Features come from
  the persisted decision-time `RiskAssessment`; labels are separate. Split is
  chronological 70/15/15 and each non-empty split keeps at least one row.
  Dataset rows, labels, and checksum never change; invalidation only marks
  status/metadata. Rebuilding an identical dataset returns the existing version.
- `GET /datasets/{id}/exclusions` explains why a feedback row was not eligible
  (`MISSING_LABEL`, `NO_DECISION_ASSESSMENT`, `NO_TRANSACTION`,
  `FEATURE_SERIALIZATION_FAILED`).
- `POST /datasets/{id}/train` returns `TrainingResult`:
  `{ modelVersion, status, trained, failureReason, verifyThreshold,
  stopThreshold, precision, recall, f1, accuracy, falsePositiveRate,
  falseNegativeRate, validationCount, testCount, championComparisonJson }`.
  `trained=false` with a concrete `failureReason` is returned for insufficient
  or unusable data; no metrics are fabricated. The candidate is never active.
- `Model` adds model type (`RULE_CALIBRATION`), dataset version/checksum,
  label/feature versions, thresholds, metrics, failure reason, champion
  comparison JSON, rollback reference, and audit timestamps. `GET /models/{id}`
  returns one model; list is newest first.
- Promotion: `approve` only from `CANDIDATE`; `deploy` only from `APPROVED`
  (retires the previous champion and records a rollback reference); `rollback`
  only from `ACTIVE` (restores the referenced champion or deterministic
  defaults). Invalid transitions return `409`. `reject` accepts `CANDIDATE` or
  `APPROVED`.
- `overview` returns dataset/model counts, the active calibration
  (`modelVersion`, `datasetVersion`, `verifyThreshold`, `stopThreshold`,
  `deployed`), and recent events. `events` is the newest-first audit trail.
- The active calibration is persisted on each new decision as
  `risk_assessments.calibration_version` (absent means deterministic defaults)
  and returned by `POST /api/risk/evaluate-automatic`. Missing/malformed/
  out-of-range deployed artifacts fall back to the deterministic defaults:
  `verify=40`, `stop=85`.