# GuardPay — end-to-end Sandbox demo

This is the runnable demo for the guarded payment flow: **automatic risk
decision → controlled verification → PayPal Sandbox authorization → server-side
capture → transaction history**, plus the live dashboard, alerts, and TOTP
verification channel. Everything except the human PayPal approval is automated.

The LLM never decides. AI produces evidence; the Spring Boot deterministic risk
engine is the only source of `ALLOW` / `VERIFY` / `STOP`, and PayPal
authorization is never bypassed.

## 1. Prerequisites

- Docker + Docker Compose for the fast path, or the manual prerequisites:
  Java 17 + Maven 3.9+, Node.js 20+ + npm, Python 3.12+, and PostgreSQL 16+.
- A PayPal Developer **Sandbox** app (client id + secret) and a Sandbox
  Personal test account for the human approval step. Only needed for live
  order creation/capture.
- A Sandbox payee email for the recipient field. The payee must be an account
  in your PayPal Sandbox so PayPal accepts the order.
- Optional: `OPENAI_API_KEY` for live LLM evidence. Without it the AI service
  still runs and returns local structural URL findings only; the deterministic
  signals decide alone.
- If the AI service is exposed beyond localhost, set the same
  `AI_SERVICE_INTERNAL_TOKEN` for both Spring Boot and FastAPI.

## 2. Start the stack

### Fast path (Docker)

```powershell
# Optional: PayPal and OpenAI credentials. Never commit these.
$env:PAYPAL_BASE_URL      = "https://api-m.sandbox.paypal.com"
$env:PAYPAL_CLIENT_ID     = "<sandbox-client-id>"
$env:PAYPAL_CLIENT_SECRET = "<sandbox-client-secret>"
$env:OPENAI_API_KEY       = "<openai-key>"   # optional

docker compose up --build
# Frontend: http://localhost:3000
# Backend:  http://localhost:8080
# AI:       http://localhost:8001
```

Without PayPal credentials, registration, risk decisions, verification, history,
and the dashboard still work; only live order creation/capture is unavailable.
Without an OpenAI key, the AI service returns local structural URL findings
only and the deterministic engine decides alone.

### Manual development path

```powershell
# PostgreSQL
docker compose up -d db

# PayPal Sandbox credentials (never commit these)
$env:PAYPAL_BASE_URL      = "https://api-m.sandbox.paypal.com"
$env:PAYPAL_CLIENT_ID     = "<sandbox-client-id>"
$env:PAYPAL_CLIENT_SECRET = "<sandbox-client-secret>"

# Backend (Flyway applies the schema on startup)
mvn -f backend spring-boot:run

# Frontend (separate terminal) — http://localhost:3000
npm --prefix frontend install
npm --prefix frontend run dev

# AI service (optional, separate terminal) — http://localhost:8001
cd ai-service
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe -m uvicorn app.main:app --port 8001
cd ..
```

## 3. Automated safety-invariant suite

The script registers/signs in a demo account, keeps the session cookie, drives
the real backend over HTTP, and checks the invariants that must always hold,
independent of any particular score:

- a non-`ALLOW` decision **never** creates a PayPal order;
- a `VERIFY` decision issues a controlled challenge and creates no order;
- a wrong verification code never verifies and never creates an order;
- the correct code verifies and only then creates the PayPal order;
- transaction history reflects the runs.

```powershell
pwsh -File scripts/demo-guarded-flow.ps1 -RecipientEmail sb-merchant@business.example.com
# or: $env:DEMO_RECIPIENT_EMAIL = "sb-merchant@business.example.com"
#     powershell -ExecutionPolicy Bypass -File scripts/demo-guarded-flow.ps1
```

Pass a real Sandbox payee email; the built-in default is only a placeholder and
PayPal may reject the order without a valid Sandbox recipient. Set
`DEMO_EMAIL`/`DEMO_PASSWORD` to reuse an existing account; otherwise the script
registers `guardpay-demo-runner@guardpay.local` on first run.

Exit code `0` means every checked invariant held. The script deliberately does
**not** assert specific scores (AI availability varies) and never fakes a
capture — human Sandbox approval stays manual.

## 4. Manual walkthrough (UI + Sandbox approval)

1. Open `http://localhost:3000`, register or sign in, then go to `/payment`.
   The backend resolves the account from the session cookie; transactions,
   recipients, devices, alerts, idempotency keys, blocklist entries, and PayPal
   captures are owner-scoped. Unsafe requests use the CSRF token automatically.
2. **Safe Payment** preset ($45, known names + placeholder recipient email) ->
   `ALLOW`, PayPal order + approval link appear. Enter your own Sandbox payee
   email instead of the placeholder if PayPal rejects it. On a fresh account the
   new-recipient/unknown-merchant signals may still keep the preset at LOW/ALLOW.
3. **Suspicious Payment** preset (large amount, scam text + URL) -> `VERIFY` or
   `STOP`. Deterministic signals can reach `STOP` without AI or a device; AI can
   add evidence and raise a borderline payment. No PayPal order is created either
   way.
4. If the result is `VERIFY`, the controlled challenge panel appears. By default
   the channel is simulated; on the backend host, read the code from the
   loopback-only dev outbox:
   `Invoke-RestMethod "http://localhost:8080/api/dev/verifications/<paymentId>/code"`.
   Enter that code to continue to PayPal, or enter a wrong code to watch
   attempts decrement and the challenge eventually fail. No order exists until
   verification succeeds. If TOTP is enrolled on `/dashboard`, enter the live
   authenticator code instead.
5. For an `ALLOW` payment: open the approval link, approve with your Sandbox
   Personal account, return, and click **I approved — capture payment**.
6. Open `http://localhost:3000/transactions` to see the stored history. Select a
   row for the detail view: amount, PayPal order/capture ids, verification
   outcome, and the exact decision with its contributing findings.
7. Open `http://localhost:3000/dashboard` to show live decision counts, blocked
   value, p50/p95 latency, AI availability, alert delivery, and verification
   success. The dashboard also enrolls/disables TOTP and can simulate an AI
   outage.
8. High-risk alerts appear in the in-app banner via SSE and in the dashboard's
   recent-alerts list. Alerts are persisted and enqueued in the notification
   outbox; critical alerts enqueue email/SMS delivery. Delivery failure does not
   change the payment decision.
9. The blocklist API (`/api/blocklist/recipients`) is available for a reported
   recipient; a blocked recipient forces `CRITICAL / STOP`. There is no dedicated
   blocklist management screen yet.

## 5. What is simulated vs real

| Concern | Status |
| --- | --- |
| Risk decision (`ALLOW`/`VERIFY`/`STOP`) | Real deterministic engine (server-side only) |
| AI evidence | Real when `OPENAI_API_KEY` is set; without a key only local URL findings are returned; a failed/stopped service falls back to `UNAVAILABLE` |
| PayPal order + capture | Real Sandbox REST calls |
| Webhooks | Real handler; external delivery needs a public tunnel |
| Verification challenge | **Simulated** by default, or **TOTP** when enrolled on `/dashboard`; the code is never returned in the payment response or written to logs |
| Device call-state | Derivable from a paired Android device; unavailable otherwise |
| Authentication | BCrypt password + opaque `HttpOnly` cookie session, CSRF, idle/absolute expiry, session rotation/revocation; per-account data isolation |
| Dashboard + alerts | Real persisted aggregates and SSE alerts; notification delivery is asynchronous |
| Blocklist | Real owner-scoped API and hard-block rule; no dedicated management UI yet |

## 6. Honest limitations

- PayPal approval needs a human with a Sandbox Personal account.
- External webhook delivery needs a public tunnel (not configured; never faked).
- Verification is simulated unless TOTP is enrolled; a production build must
  deliver the challenge through a real identity channel. Disable
  `GUARDPAY_DEV_VERIFICATION_OUTBOX` in production.
- Accounts and ownership are implemented, but there is no password reset, email
  verification, OAuth, or MFA at login. TOTP is a payment-verification channel,
  not a login second factor.
- The guarded endpoint can reach `STOP` on deterministic signals alone (for
  example a high-value scam with several content signals, or a blocklisted
  recipient). AI and device evidence are optional additions, not prerequisites.
- Call detection is foreground-only; the APK is debug-signed.
