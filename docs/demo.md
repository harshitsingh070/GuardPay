# GuardPay — end-to-end Sandbox demo

This is the runnable demo for the guarded payment flow: **automatic risk
decision → controlled verification → PayPal Sandbox authorization → server-side
capture → transaction history**. Everything except the human PayPal approval is
automated.

The LLM never decides. AI produces evidence; the Spring Boot deterministic risk
engine is the only source of `ALLOW` / `VERIFY` / `STOP`, and PayPal
authorization is never bypassed.

## 1. Prerequisites

- Docker + Docker Compose (for PostgreSQL), or a local PostgreSQL 16+.
- Java 17 + Maven 3.9+.
- Node.js 20+ + npm.
- Python 3.12+ (only needed if you want live AI evidence).
- A PayPal Developer **Sandbox** app (client id + secret) and a Sandbox
  Personal test account for the human approval step.
- A Sandbox payee email for the recipient field. The payee must be an account
  in your PayPal Sandbox so PayPal accepts the order.
- Optional: `OPENAI_API_KEY` for live AI evidence. Without it the AI service
  reports `UNAVAILABLE` and the deterministic signals decide alone.
- If the AI service is exposed beyond localhost, set the same
  `AI_SERVICE_INTERNAL_TOKEN` for both Spring Boot and FastAPI.

## 2. Start the stack

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
python -m pip install -r ai-service/requirements.txt
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
   recipients, alerts, idempotency keys, and PayPal captures are owner-scoped.
2. **Safe Payment** preset ($45, known names + Sandbox recipient email) ->
   `ALLOW`, PayPal order + approval link appear. Enter your own Sandbox payee
   email instead of the placeholder if PayPal rejects it.
3. **Suspicious Payment** preset (large amount, scam text + URL) -> `VERIFY` or
   `STOP` (AI-dependent). No PayPal order is created either way.
4. If the result is `VERIFY`, the controlled challenge panel appears. The code
   is delivered out of band (never returned in the payment response). On the
   backend host, read it from the loopback-only dev outbox:
   `Invoke-RestMethod "http://localhost:8080/api/dev/verifications/<paymentId>/code"`.
   Enter that code to continue to PayPal, or enter a wrong code to watch
   attempts decrement and the challenge eventually fail. No order exists until
   verification succeeds.
5. For an `ALLOW` payment: open the approval link, approve with your Sandbox
   Personal account, return, and click **I approved — capture payment**.
6. Open `http://localhost:3000/transactions` to see the stored history. Select a
   row for the detail view: amount, PayPal order/capture ids, verification
   outcome, and the exact decision with its contributing findings.

## 5. What is simulated vs real

| Concern | Status |
| --- | --- |
| Risk decision (`ALLOW`/`VERIFY`/`STOP`) | Real deterministic engine (server-side only) |
| AI evidence | Real when `OPENAI_API_KEY` is set; otherwise `UNAVAILABLE` fallback |
| PayPal order + capture | Real Sandbox REST calls |
| Webhooks | Real handler; external delivery needs a public tunnel |
| Verification challenge | **Simulated** channel (no identity provider configured) — the code is kept in a server-side outbox and readable only from loopback; it is never returned in the payment response or written to logs |
| Device call-state | Derivable from a paired Android device; unavailable otherwise |
| Authentication | BCrypt password + opaque `HttpOnly` cookie session; per-account data isolation |

## 6. Honest limitations

- PayPal approval needs a human with a Sandbox Personal account.
- External webhook delivery needs a public tunnel (not configured; never faked).
- Verification is a simulated channel; a production build must deliver the
  challenge out of band (the demo outbox is loopback-only) and replace it with a
  real identity signal. Disable `GUARDPAY_DEV_VERIFICATION_OUTBOX` in production.
- There is no multi-user authentication. Transaction history, guarded payments,
  verification, risk, and capture all use the fixed server-side `guardpay-demo`
  workspace; clients cannot supply an owner or user id.
- The guarded endpoint will not reach `STOP` on deterministic signals alone in a
  fresh database (deterministic signals top out at `VERIFY`); a `STOP` needs
  strong AI/device evidence.
