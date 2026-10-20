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
- Optional: `OPENAI_API_KEY` for live AI evidence. Without it the AI service
  reports `UNAVAILABLE` and the deterministic signals decide alone.

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

The script drives the real backend over HTTP and checks the invariants that
must always hold, independent of any particular score:

- a non-`ALLOW` decision **never** creates a PayPal order;
- a `VERIFY` decision issues a controlled challenge and creates no order;
- a wrong verification code never verifies and never creates an order;
- the correct code verifies and only then creates the PayPal order;
- transaction history reflects the runs.

```powershell
pwsh -File scripts/demo-guarded-flow.ps1
# or: powershell -ExecutionPolicy Bypass -File scripts/demo-guarded-flow.ps1
```

Exit code `0` means every checked invariant held. The script deliberately does
**not** assert specific scores (AI availability varies) and never fakes a
capture — human Sandbox approval stays manual.

## 4. Manual walkthrough (UI + Sandbox approval)

1. Open `http://localhost:3000/payment`.
2. **Safe Payment** preset ($45, known names) → `ALLOW`, PayPal order + approval
   link appear.
3. **Suspicious Payment** preset (large amount, scam text + URL) → `VERIFY` or
   `STOP` (AI-dependent). No PayPal order is created either way.
4. If the result is `VERIFY`, the controlled challenge panel appears. Enter the
   code shown (simulated channel — clearly labelled) to continue to PayPal, or
   enter a wrong code to watch attempts decrement and the challenge eventually
   fail. No order exists until verification succeeds.
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
| Verification challenge | **Simulated** channel (no identity provider configured) — the code is returned in the response and the panel labels it as a prototype stand-in |
| Device call-state | Derivable from a paired Android device; unavailable otherwise |

## 6. Honest limitations

- PayPal approval needs a human with a Sandbox Personal account.
- External webhook delivery needs a public tunnel (not configured; never faked).
- Verification is a simulated channel; a production build must deliver the
  challenge out of band and replace it with a real identity signal.
- There is no authentication yet: history is scoped by an optional user id, and
  an omitted id resolves to the documented demo user.
- The guarded endpoint will not reach `STOP` on deterministic signals alone in a
  fresh database (deterministic signals top out at `VERIFY`); a `STOP` needs
  strong AI/device evidence.
