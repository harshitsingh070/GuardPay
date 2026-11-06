# GuardPay — Fraud Detection Analysis Report

## 1. Executive Summary

GuardPay is an AI-assisted, rules-based payment safety prototype built around one central principle:

> **The AI provides evidence. The deterministic Java risk engine makes the final decision.**

It does not try to “recognize a scammer.” It evaluates a payment the way a fraud analyst would: it collects independent risk facts — new payee, unusual amount, active phone call, urgent language, suspicious link, invoice pressure, repeated attempts — assigns fixed point weights, sums them, caps the score at 100, and maps the result to `ALLOW`, `VERIFY`, or `STOP`.

In the project’s own validated scenarios:

- A canonical social-engineering scam reaches **85/100 CRITICAL → STOP** with no AI and no device signal.
- The demo “suspicious payment” reaches **75/100 HIGH → VERIFY**, or **85/100 STOP** when AI URL evidence is available.
- A safe payment scores **0–30 LOW → ALLOW**, depending on whether the payee is already known.
- A legitimate large invoice to a known counterparty remains **20/100 LOW → ALLOW**, showing the system does not block solely on amount.
- AI alone cannot force a payment through: one strong AI finding adds only 10 points and can still remain `ALLOW`.
- AI plus deterministic evidence can reach **105 raw points, capped at 100 → STOP**.

**Overall assessment:** GuardPay is a strong demonstration of *defence-in-depth payment safety* and is highly effective against the specific fraud class it targets: social-engineering scams where several warning signs co-occur. Its detection efficiency is **not statistically proven** against real fraud data, and it has significant blind spots for account takeover, known-payee authorized-push-payment fraud, zero-day phishing domains, and non-English/obfuscated scams. It is a well-engineered hackathon-grade prototype, not production fraud infrastructure.

---

## 2. What the System Is

### 2.1 Stack

| Layer | Technology | Role |
|---|---|---|
| Frontend | React + Vite + TypeScript | Checkout, warnings, verification, transaction history |
| Backend | Java 17 + Spring Boot + PostgreSQL | Orchestration, signal collection, deterministic risk engine, PayPal gate |
| AI service | Python FastAPI + optional OpenAI LLM | Structured evidence only; never a payment decision |
| Android | Kotlin WebView shell | Real phone-call-state detection |
| External | PayPal Sandbox REST APIs + webhooks | Order creation, approval, capture, state sync |

### 2.2 Core architecture

The real flow is:

1. User submits payment context: amount, currency, recipient, merchant, message, URL, optional device ID.
2. Spring Boot resolves or learns the recipient and merchant.
3. Automatic signals are derived from trusted backend data.
4. Optional AI analysis adds validated evidence.
5. The deterministic risk engine scores everything.
6. The decision controls whether a PayPal order may exist.
7. The frontend shows the decision, reason, and warnings.
8. Transaction history stores the decision and evidence for audit.

A critical trust boundary exists: the guarded payment request has **no risk fields at all** — no score, no level, no action, no “activeCall”, no “suspicious” boolean. The backend derives every signal. A forged client flag cannot influence the decision.

---

## 3. The Detection Pipeline in Detail

### 3.1 Step 1 — Payment context intake

The guarded endpoint accepts business context only. The server resolves the authenticated user from the session, resolves or creates the recipient and merchant, and prevents a payment with no counterparty. Unknown payee names are learned into the catalog, but “known” status is based on **completed transaction history**, not merely the existence of a name record.

### 3.2 Step 2 — Automatic signal collection

The signal collector derives evidence from:

- Completed transaction history
- Recipient history and recipient amount anomalies
- Merchant history
- Recent payment attempt velocity (10-minute, hourly, daily)
- Split-payment patterns
- Owner-scoped recipient blocklist
- Payment message text
- Payment URL structure
- Paired Android device call state and device novelty
- Geo/network context (new country, impossible travel, VPN/proxy)
- Deterministic content analysis

Anything that cannot be derived reliably is reported as unavailable with a reason code rather than guessed as “safe.” That design choice is important: the system prefers “unknown” over a false reassurance.

### 3.3 Step 3 — AI evidence

The backend calls the Python AI service only when message text or a URL exists. The AI service runs local URL rules always, and optional LLM agents for social engineering and content-scam patterns. AI findings are validated, filtered, and mapped onto the existing engine signals. AI never outputs or controls `ALLOW`, `VERIFY`, or `STOP`.

### 3.4 Step 4 — Deterministic scoring

The engine evaluates twenty signals in a fixed order, sums the weights of triggered signals, applies the blocklist hard-block override, caps the score at 100, and maps score to risk level and action.

### 3.5 Step 5 — Enforcement

`ALLOW` may create a PayPal order. `VERIFY` creates no order until a server-verified challenge succeeds. `STOP` creates no order and the payment is marked cancelled/blocked. Capture later re-checks the stored decision, so the frontend cannot force a payment through.

---

## 4. Core Detection Logic

### 4.1 The twenty risk signals and their weights

| # | Signal | Points | Source | Trigger rule |
|---:|---|---:|---|---|
| 1 | `ACTIVE_CALL` | +15 | Device | A fresh paired-device observation says the user is on a call. Observations older than five minutes never count as active. |
| 2 | `NEW_RECIPIENT` | +20 | Transaction history | No prior **completed** transaction to this recipient, same currency, current attempt excluded. Unavailable identity is not guessed. |
| 3 | `LARGE_AMOUNT` | +20 | Transaction history | Current amount is at least **5×** the account-wide completed-history average. Missing/zero/negative history does not fire. |
| 4 | `RECIPIENT_AMOUNT_ANOMALY` | +20 | Recipient history | At least 3 completed samples exist and the amount is at least **5×** the recipient median. Suppressed when `LARGE_AMOUNT` already fires. |
| 5 | `REPEATED_ATTEMPTS` | +15 | Transaction history | At least **3 attempts including the current one** within **10 minutes**, any status, same currency. |
| 6 | `VELOCITY_HOURLY` | +15 | Transaction history | At least **5 attempts including the current one** within **1 hour**. |
| 7 | `VELOCITY_DAILY` | +20 | Transaction history | At least **10 attempts including the current one** within **24 hours**. |
| 8 | `BLOCKLISTED_RECIPIENT` | +25 | Blocklist | Recipient is on the owner's fraud blocklist. This also forces the final score to 100. |
| 9 | `UNKNOWN_MERCHANT` | +10 | Merchant history | No previous completed transaction with this merchant. |
| 10 | `UNEXPECTED_INVOICE` | +15 | Payment content / AI | Invoice/billing/overdue/refund wording combined with unexpected/pressure context, or validated AI `UNEXPECTED_INVOICE_CONTEXT`. |
| 11 | `URGENT_LANGUAGE` | +10 | Payment content / AI | Urgency/pressure phrasing such as “urgent,” “immediately,” “act now,” “final warning,” or validated AI `URGENT_LANGUAGE`. |
| 12 | `SUSPICIOUS_URL` | +10 | URL structure / AI | Local structural URL rules fire, or validated AI `SUSPICIOUS_URL_STRUCTURE` fires. |
| 13 | `SOCIAL_ENGINEERING` | +20 | Content + counterparty + call / AI | Multiple independent scam indicators co-occur, or a validated AI social code fires. Counted once. |
| 14 | `KNOWN_RECIPIENT_SCAM` | +50 | Recipient history + content | An established recipient sends a new high-risk payment instruction, lookalike invoice/link, or scam-pattern combination consistent with a compromised payee. |
| 15 | `SPLIT_PAYMENT` | +40 | Transaction history | At least 3 payments to the same recipient, each below `$1,000`, totaling at least `$1,000` within 24 hours (current attempt included). |
| 16 | `MISSING_CONTEXT` | +20 | Payment context | High-value payment with no message/link and no trusted device observation; `$1,000`, or `$500` for a new recipient. |
| 17 | `NEW_DEVICE` | +20 | Transaction history | The account has prior device observations and this device has not been seen. A first-ever payment reports unavailable, not new. |
| 18 | `NEW_GEO` | +15 | Transaction history / network | The account has prior geolocated payments and this country has not been seen. Missing geo reports unavailable. |
| 19 | `IMPOSSIBLE_TRAVEL` | +25 | Transaction history / network | A different country is seen within **6 hours** of the previous geolocated payment. |
| 20 | `VPN_PROXY` | +10 | Network | Trusted network reputation reports a VPN, proxy, or hosting connection. |

The weights are fixed constants in `RiskSignal`, not learned or statistically calibrated. `BLOCKLISTED_RECIPIENT` is a hard block: the score is forced to 100 and the action to `STOP`. Social-engineering evidence is consolidated into a **single +20 signal**, so multiple AI findings cannot multiply the score.

### 4.2 Score-to-action policy

| Score | Level | Action | Meaning |
|---:|---|---|---|
| 0–39 | LOW | `ALLOW` | Proceed to PayPal authorization |
| 40–69 | MEDIUM | `VERIFY` | No PayPal order; controlled challenge required |
| 70–84 | HIGH | `VERIFY` | Same action as MEDIUM, but a high-risk warning is shown |
| 85–100 | CRITICAL | `STOP` | No PayPal order, payment blocked |
| >100 | — | Capped at 100 | Total is always capped to prevent runaway accumulation |

This threshold design gives the system a useful safety margin: many suspicious-but-not-conclusive payments land in `VERIFY` rather than being outright blocked. Only a strong accumulation of independent evidence reaches `STOP`.

### 4.3 Deterministic content analysis

Two independent content analyzers exist:

1. **Backend local analyzer** — regex-based, runs before and independently of AI.
2. **AI-service local URL analyzer** — structural URL rules, runs even if no LLM key is configured.

The backend analyzer detects:

- Urgency/pressure wording
- High-risk payment instructions: gift cards, iTunes/Steam/Google Play cards, Bitcoin/crypto/USDT, wire transfer, Western Union, MoneyGram, Zelle, Cash App, Venmo, friends-and-family, remote access tools (TeamViewer/AnyDesk), screen sharing, one-time codes/OTP/verification codes
- Invoice/billing/overdue/refund wording
- “Unexpected” wording such as “did not request,” “unsolicited,” “unauthorized,” “you never ordered”
- Suspicious URL structure

The AI-service URL analyzer additionally detects:

- `@` obfuscation
- Bare IP hosts
- Punycode/internationalized domains
- Excessive subdomains
- Brand names hidden inside subdomains of unrelated domains
- Lure words in the registrable domain: verify, secure, login, update, support, confirm, account, suspicious

This distinction matters: a domain like `example-suspicious-domain.test` is not flagged by the backend’s structural rules, but the AI service’s lure-word rule does flag it as suspicious. That is why the same demo payment can be `VERIFY` when the AI service is unavailable and `STOP` when it is reachable.

### 4.4 Social-engineering combination logic

A single indicator is usually not enough. The social-engineering signal fires when independent indicators combine:

- The content analyzer detects a scam-pattern combination: urgency plus a high-risk payment instruction, payment instruction plus a suspicious URL, or unexpected invoice plus payment instruction.
- A high-risk payment instruction occurs together with a new/unknown counterparty or an active call.
- An unexpected invoice occurs with a new/unknown counterparty and urgency or an active call.
- An active call occurs with urgency, an unexpected invoice, or a high-risk payment instruction.

This is deliberately conservative. It reduces false positives from a single word like “urgent,” while catching the classic scam structure: a new payee, pressure, a payment method that is hard to reverse, and possibly a phone call.

### 4.5 AI evidence pipeline

The AI service performs:

1. **Local URL analysis always** — no network fetch, no reputation database, pure string/structure heuristics.
2. **Social-engineering agent** — LLM analysis of message text for urgency, impersonation, pressure to pay, secrecy requests, suspicious payment instructions, and scam patterns.
3. **Content-scam agent** — LLM analysis of invoice/message/merchant/recipient context for invoice scams and scam-like payment requests.

Prompt safeguards:

- The model must return only a controlled vocabulary of finding codes.
- Severity must be one of `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`.
- Confidence is certainty in the observation, not fraud probability.
- The model is explicitly forbidden from outputting `ALLOW`, `VERIFY`, `STOP`, or risk scores.
- Payment content is wrapped as untrusted data, with instructions not to follow any instructions inside it. This is prompt-injection mitigation.
- JSON mode and temperature zero are used.

Output validation:

- Pydantic validates the schema and the allowed codes.
- Findings from both agents are merged by code, keeping the highest-confidence version.
- One failing agent does not discard the other agent’s findings or the local URL findings.

Backend validation:

- Unknown AI codes are discarded.
- Confidence below **0.5** is discarded.
- Blank title, description, or evidence is discarded.
- An AI-supplied `ACTIVE_CALL` is rejected — call state can only come from the device path.
- Mapped AI codes fold into existing engine flags:
  - `URGENT_LANGUAGE` → urgent language
  - `IMPERSONATION`, `PRESSURE_TO_PAY`, `SECRECY_REQUEST`, `SUSPICIOUS_PAYMENT_INSTRUCTION`, `SCAM_PATTERN` → one social-engineering signal
  - `SUSPICIOUS_URL_STRUCTURE` → suspicious URL
  - `UNEXPECTED_INVOICE_CONTEXT` → unexpected invoice

The AI can only **add** evidence. It cannot subtract risk, lower a score, or make a blocked payment allowed. This is an asymmetric safety design: AI false negatives do not weaken deterministic protection, though AI false positives can add friction.

### 4.6 Device call-state signal

Android reads only the binary telephony call state via `TelephonyManager` / `TelephonyCallback` with `READ_PHONE_STATE`. It does not read audio, phone numbers, contacts, SMS, or location.

The phone sends only the device identifier, active-call boolean, and observation time to the backend, authenticated with a per-device credential issued through the signed-in `/api/devices` API. The backend:

- Stores only a SHA-256 hash of the credential and rejects revoked devices.
- Rejects older observations so a replay cannot overwrite a newer state.
- Clamps far-future timestamps.
- Treats observations older than 5 minutes as stale and never active.
- Never accepts a client-supplied `activeCall` on the payment request; it resolves the trusted stored state from the device ID.

On the web, browsers cannot read call state. The web UI asks the user a self-check question, but that self-check is a UX warning only; it does not add the `ACTIVE_CALL` risk signal unless a paired device reported it.

---

## 5. How GuardPay Catches Fraud — Worked Examples

### Scenario A — Safe payment

Input: $45, “Known Co,” “Known Shop,” normal subscription message, no URL.

Signals:

- If both counterparties are already established: 0 points → LOW → `ALLOW`.
- On a fresh demo database where the names are new: new recipient +20, unknown merchant +10 = 30 → LOW → `ALLOW`.

This shows that “new counterparty” alone does not block a payment.

### Scenario B — Demo “Suspicious payment”

Input: $2,499, “Fresh Stranger,” “Unknown Shop,” message: “You need to pay this invoice immediately to avoid account suspension. Do not contact anyone else. This is the official support department. Use the payment link below.” URL: `https://example-suspicious-domain.test/pay`.

Deterministic signals:

- New recipient: +20
- Unknown merchant: +10
- Urgent language (“immediately”): +10
- Unexpected invoice (invoice wording + urgency): +15
- Social engineering (unexpected invoice + new counterparty + urgency): +20
- Backend URL structure: no match → 0
- Large amount: no history → 0
- Missing context: content exists → 0

Total: **75 → HIGH → VERIFY**. No PayPal order is created.

If the AI service is reachable, its local URL lure-word rule sees “suspicious” in the domain and adds +10 for `SUSPICIOUS_URL_STRUCTURE`. Total: **85 → CRITICAL → STOP**. This is exactly the documented “VERIFY or STOP, AI-dependent” behavior.

### Scenario C — Canonical scam caught without AI or device

This is the strongest validated case, covered by the deterministic scam regression test.

Input: $2,499, new recipient, unknown merchant, text: “You did not request this invoice. URGENT: your account is locked. Pay immediately with gift cards and send the codes.” URL: `http://paypal.com.secure-login.example/pay`.

Signals:

- New recipient: +20
- Unknown merchant: +10
- Large amount: no completed history → 0
- Unexpected invoice: invoice + “did not request” → +15
- Urgent language: “URGENT,” “immediately” → +10
- Suspicious URL: non-HTTPS and brand-lookalike host → +10
- Social engineering: urgency + gift-card instruction, and unexpected invoice + gift-card instruction → +20

Total: **85 → CRITICAL → STOP**.

No AI call and no phone-call evidence were needed. This proves the deterministic core can independently stop a high-signal social-engineering scam.

### Scenario D — AI adds evidence but does not decide

Input: $45, benign message “Thanks for the update,” no history.

Deterministic score: 0.

AI returns urgent language (0.9) and impersonation (0.8):

- Urgent language: +10
- Social engineering, counted once for all social-group AI codes: +20

Total: **30 → LOW → ALLOW**.

This demonstrates that even strong AI findings do not automatically block a payment. The engine still decides.

### Scenario E — Combined evidence reaches the cap

Input: $600, history average $100, new recipient, unknown merchant, message “Pay this invoice immediately,” AI adds urgent language, impersonation, suspicious URL structure, and unexpected invoice context.

Score contributions:

- Large amount (600 is at least 5×100): +20
- New recipient: +20
- Unknown merchant: +10
- Unexpected invoice: +15
- Urgent language: +10
- Social engineering (counted once despite multiple AI codes): +20
- AI suspicious URL: +10

Raw total: **105**, capped to **100 → CRITICAL → STOP**.

This is the intended AI-assisted fraud path: AI contributes evidence, but the engine’s arithmetic still produces the decision.

### Scenario F — Legitimate large invoice to a known counterparty

Input: $2,499, known recipient, known merchant, normal invoice text, safe HTTPS URL.

Signals:

- Large amount: +20
- New recipient: 0
- Unknown merchant: 0
- Urgent language: 0
- Unexpected invoice: 0
- Suspicious URL: 0
- Social engineering: 0

Total: **20 → LOW → ALLOW**.

This is important evidence against over-blocking: a high-value payment to an established counterparty is not treated as fraud merely because it is large.

---

## 6. Warning and User-Protection Behaviour

### 6.1 Blocking call warning

When the Android device reports an active call, the checkout shows a blocking dialog:

- “You are on a phone call.”
- Warns that the person on the call may be a fraudster and that scammers often keep victims on the line.
- Tells the user to hang up and call back using a number they find themselves.
- Tells the user never to share a one-time code, password, or remote-access access while paying.
- Offers “Cancel payment” (focused by default) and “Continue anyway.”

The dialog is intentionally not a dismissible toast. A fraudster often stays on the line while pressuring the victim, so the user must make an explicit choice.

On the web, the platform cannot read call state, so the user is asked a self-check question before paying. This is a warning, not a risk signal.

### 6.2 Decision panel

For every payment, the frontend shows:

- Final action: allowed, verification required, or blocked
- Risk level badge
- Score out of 100
- Explanation text
- AI status: available, unavailable, or not requested
- The top contributing signals with their point values
- A note if more signals contributed

The UI never re-scores or invents signals. It displays exactly what the backend decided.

### 6.3 Verification

A `VERIFY` decision creates **no PayPal order**. The user receives a controlled challenge:

- 6-digit code
- Server-side SHA-256 hash, salted with the transaction ID
- 10-minute expiry
- Maximum 3 attempts
- Constant-time comparison
- Wrong codes never verify
- Expiry or attempt exhaustion ends the challenge
- The code is delivered out of band; the public payment API never returns it
- Only after successful verification does the backend create the PayPal order

The verification channel is TOTP when an authenticator is enrolled, otherwise simulated. The development outbox is loopback-only and must be disabled in production.

### 6.4 Receipt and transaction history

Every guarded payment stores:

- Transaction details
- Amount, currency, recipient, merchant
- Risk score, risk level, recommended action
- Explanation
- Full findings and signal evidence
- PayPal order and capture references
- Verification outcome

The transaction history UI shows the stored decision and the reasons behind it. This makes the system auditable and reproducible after the fact.

---

## 7. Enforcement and Anti-Bypass Design

The system’s safety does not depend only on detection. It also depends on strict enforcement:

- There is no public endpoint that creates a PayPal order with a client-supplied risk verdict.
- The guarded flow is the only order-creation path.
- `ALLOW` is the only action that can create an order.
- `VERIFY` creates no order until server-verified.
- `STOP` creates no order and marks the transaction cancelled/blocked.
- Capture re-checks the stored risk decision and verification state.
- A `VERIFY` decision can only be captured after the verification is marked verified.
- A `STOP` decision can never be captured.
- Idempotency keys prevent duplicate payments and duplicate orders.
- Capture uses a row lock and stored result, so double-clicks cannot double-charge.
- PayPal webhooks only update PayPal payment state; they cannot change the GuardPay risk decision.
- Webhook events are signature-verified and deduplicated by event ID.
- The AI service cannot be reached directly by the browser in production; an optional internal token protects the Spring Boot → AI hop.
- The device token is compared in constant time and never logged.
- Secrets, tokens, signatures, and verification codes are masked in logs.

The most important anti-bypass property is:

> **PayPal payment state and GuardPay risk decision are separate. A paid or approved PayPal state cannot retroactively make a risky payment allowed.**

---

## 8. Efficiency Assessment

“Efficiency” has two meanings here: computational efficiency and detection efficiency.

### 8.1 Computational efficiency

| Aspect | Assessment |
|---|---|
| Deterministic scoring | Constant twenty checks; very fast |
| Database reads | Several queries per payment: history average, velocity window, recipient history, merchant history |
| AI latency | Connect timeout 2 seconds, read timeout 8 seconds |
| Worst-case AI delay | About 10 seconds before fallback |
| Circuit breaker | Opens after 3 consecutive AI failures; stays open for 30 seconds |
| Transaction handling | AI evaluation happens outside the database transaction, so a slow model does not hold a DB connection or row lock |
| UI behavior | Pay button stays disabled and “Checking this payment” is shown while evaluation runs |
| Scaling concern | Completed-history lookup loads the user’s completed transactions and filters in memory; cost grows with history size |

The deterministic path is fast and predictable. The AI path is time-bounded and failure-tolerant. The main scalability weakness is that history-based calculations are not optimized for very large per-user transaction volumes.

### 8.2 Detection efficiency by fraud type

| Fraud type | Detection strength | Mechanism | Typical outcome |
|---|---|---|---|
| Fake PayPal/support call scam | High | Active call + new payee + urgency + payment instruction + social combination | VERIFY to STOP |
| Gift-card / crypto / wire-transfer scam | High | High-risk payment instruction regex + urgency + social combination | VERIFY to STOP |
| Urgent fake invoice to a new payee | Medium-high | Unexpected invoice + urgency + new counterparty + social combination | VERIFY, sometimes STOP with AI |
| Suspicious phishing URL | Medium | Structural URL heuristics + AI URL rules | +10 evidence, rarely decisive alone |
| Brand-impersonation domain | Medium | Brand-lookalike, punycode, subdomain-brand rules | +10 evidence |
| Unusual large amount | Medium | 5× historical average | +20, usually VERIFY when combined |
| Repeated pressured attempts | Medium | 3 attempts in 10 minutes | +15 |
| High-value blind payment | Medium | Missing context +20 at $1,000+ with no message/link/device | VERIFY when combined |
| Authorized push payment to a known payee | Low | No anomaly, no new counterparty, no content | Often ALLOW |
| Account takeover / stolen credentials | Low-medium | Payment-path device/geo novelty; no login anomaly or session-risk model | Can still ALLOW if the attacker keeps the victim's payee, amount, and context |
| Fake merchant / non-delivery fraud | Low | No delivery, chargeback, or merchant reputation data | Not detected |
| Mule account / beneficiary risk | Low | No recipient reputation or cross-user intelligence | Not detected |
| Non-English or obfuscated scam text | Low-medium | English regex; relies on LLM | AI-dependent |
| QR-code or phone-number-only scam | Low | No QR/phone reputation analysis | Not detected |
| Zero-day phishing domain with benign structure | Low | No URL fetching or reputation database | May pass deterministic rules |

### 8.3 Strengths

- **Deterministic and explainable.** Same input always produces the same score and action. Every point has a reason.
- **AI cannot override the decision.** This avoids the biggest risk of LLM-based fraud systems: a hallucinated “safe” verdict.
- **Graceful degradation.** If AI is down, slow, unconfigured, or circuit-open, deterministic protection continues.
- **Multi-signal correlation.** The system does not block on one weak indicator; it looks for co-occurring evidence.
- **Fail-safe unknown handling.** Unavailable data is reported with a reason, not treated as safe.
- **Strong enforcement.** Detection is backed by server-side gates, idempotency, and capture re-checks.
- **Good auditability.** Decisions, findings, and evidence are persisted.
- **Low false-positive blocking risk.** Many suspicious cases go to `VERIFY`; only 85+ reaches `STOP`.

### 8.4 Weaknesses and blind spots

- **No real-world statistical validation.** The synthetic benchmark reports precision/recall and threshold trade-offs, but there is no field-test measurement against real fraud data or calibrated probability. The weights are prototype heuristics.
- **No learning loop.** Chargebacks, confirmed fraud, false positives, and user feedback are not fed back into the model.
- **Per-user workspaces.** No population-level intelligence, no cross-user mule detection, no merchant global reputation.
- **Known-payee fraud is largely invisible.** If the attacker uses the victim’s normal payee, normal amount, and no suspicious text, the score can be near zero.
- **Account takeover is only partly modelled.** Payment-path device novelty, new-country, impossible-travel, and VPN/proxy signals exist, but there is no login anomaly, session-risk model, or credential-theft detection. An attacker using the victim's known payee, amount, and clean context can still pass.
- **URL analysis is structural only.** The system does not fetch URLs or query Google Safe Browsing or any reputation service. A clean-looking phishing domain can pass.
- **English-centric regex.** Non-English, leetspeak, or heavily obfuscated text relies on the LLM.
- **AI findings are not severity-weighted.** Severity is recorded but does not change points; every finding of a given type contributes the same fixed weight.
- **AI false positives can add friction.** AI can add up to +10 urgent, +10 URL, +15 unexpected invoice, and +20 social, so a confident but wrong AI can push a borderline payment from VERIFY to STOP.
- **The AI model itself is not deterministic run to run.** Strict schema, confidence gating, and deterministic scoring absorb most of this, but evidence availability can vary.
- **History queries scale linearly.** Large completed-history sets are loaded and filtered in memory.
- **Demo limitations.** No password reset, email verification, OAuth, or MFA-at-login; verification is simulated unless TOTP is enrolled; debug-signed Android APK; foreground-only call detection; external webhook delivery requires a tunnel.

### 8.5 False-positive and false-negative behaviour

**False positives are mostly converted into friction, not blockage.** A legitimate first-time high-value payment can easily reach:

- New recipient +20
- Unknown merchant +10
- Large amount +20
- Missing context +20

That is 70 → HIGH → `VERIFY`. The user is challenged but not blocked. This is a reasonable trade-off.

**False negatives are the larger concern.** A payment to a known recipient, for a normal amount, with no message or link, during no call, can score 0 and be allowed. The system is designed around the assumption that social-engineering scams leave evidence; it does not detect fraud that looks behaviourally normal.

---

## 9. Security and Privacy Observations

Positive controls:

- No client-supplied risk booleans on the guarded path.
- Risk evaluation is server-side only.
- AI endpoint is internal and optionally token-protected.
- Device observations are authenticated, freshness-checked, replay-resistant, and constant-time compared.
- Prompt injection is mitigated by wrapping untrusted content and constraining output.
- AI privacy is minimized: amount, names, flags, text, URL, and call boolean only; no audio, contacts, phone numbers, or emails.
- Logs mask message content, URLs, names, email destinations, tokens, secrets, signatures, and verification codes.
- Verification codes are hashed, expiring, attempt-limited, and never returned by the normal payment API.
- PayPal webhooks are signature-verified and deduplicated.
- Capture is server-side and re-checks the stored decision.

Important demo limitations:

- Cookie sessions only; no password reset, email verification, OAuth, or MFA at login. TOTP is a payment-verification channel, not login MFA.
- No real identity provider for the default simulated channel; TOTP is available when enrolled.
- The Android app is a development pairing mechanism, not production attestation.
- The dev verification outbox must be disabled in production.
- External webhook delivery was not live-tested without a public tunnel.

---

## 10. Validation Evidence and Testing

The repository contains substantial automated coverage:

- **63 backend Java test classes**
- **8 frontend test files**
- **4 AI-service test modules**

The synthetic benchmark is the only quantitative evaluation. On the 54-scenario
dev set, `DETERMINISTIC_ONLY` reaches 75.00% precision, 92.31% recall, 82.76%
F1, and 81.48% accuracy; `VALIDATED_AI` reaches 74.29% precision, 100.00%
recall, 85.25% F1, and 83.33% accuracy. On the 32-scenario PayPal-specific
held-out set, both modes reach 88.89% precision, 100.00% recall, 94.12% F1, and
93.75% accuracy. These are synthetic regression figures, not real-world
accuracy. See `docs/fraud-benchmark.md`.

Key validated behaviours:

- Score boundaries at 39/40, 69/70, 84/85.
- The 100-point cap.
- The hero scenario reaching 100/CRITICAL/STOP.
- Canonical scam reaching 85/CRITICAL/STOP with no AI and no device.
- Normal invoice to a known counterparty remaining LOW/ALLOW.
- AI unavailable fallback preserving the deterministic decision.
- AI findings merging into the engine without double counting.
- Multiple AI social findings collapsing into one +20 signal.
- AI additions capped at 100.
- A single high-severity AI finding contributing only 10 points and still allowing.
- AI not called when there is no content.
- Collector reporting unavailable reasons instead of guessing.
- No boolean risk fields in the collection request.
- Device signal freshness, staleness, and replay handling.
- Verification wrong-code/expiry/attempt-limit behaviour.
- PayPal idempotency and capture gating.
- Webhook risk separation: webhooks cannot change the risk decision.
- End-to-end demo script checking that non-ALLOW never creates a PayPal order and that VERIFY creates no order until verified.

Documentation note: the AI service does **not** use CrewAI; it uses two focused LLM agents plus local URL rules. Deterministic content signals can reach `STOP` without AI or device evidence. The current implementation contract is `docs/architecture.md`, `docs/api-contract.md`, and `docs/risk-engine.md`.

---

## 11. Final Verdict

### What GuardPay does well

GuardPay is very effective at catching the fraud pattern it was designed for: **social-engineering payment scams with multiple visible warning signs**. It combines transaction history, counterparty novelty, amount anomaly, velocity, message pressure, suspicious URL structure, high-risk payment instructions, and phone-call context into an explainable score. Its decision layer is deterministic, auditable, and resistant to AI hallucination and client forgery. Its enforcement layer prevents PayPal orders from existing unless the decision allows them.

### What it cannot yet claim

It cannot claim a real-world fraud detection rate. The available precision/recall comes from a small synthetic benchmark, not field data; there is no production feedback loop or cross-user intelligence. It can still miss account takeover, known-payee authorized-push-payment fraud, clean-looking phishing domains, non-English scams, and fraud that leaves no textual or behavioural trace.

### Overall efficiency rating

| Dimension | Rating |
|---|---|
| Detection of scripted social-engineering scams | High |
| Detection of invoice/pressure scams | Medium-high |
| Detection of URL phishing | Medium |
| Detection of account takeover / known-payee fraud | Low |
| Explainability and auditability | High |
| Latency and reliability | High |
| False-positive control | Good |
| Statistical validation | Not established |
| Production readiness | Prototype / demo-grade |

**Bottom line:** GuardPay caught the demonstrated scam not because it “knew” the scammer, but because the payment carried many independent, verified risk facts that accumulated past the STOP threshold — and the system was architected so that no AI model, browser, or PayPal state could override that decision. That is its core strength. Its main limitation is that its detection power comes from hand-tuned heuristics focused on social engineering, not from empirically trained fraud intelligence.
