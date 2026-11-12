# GuardPay — Submission Guide

This is the judge-facing submission guide: clean setup, demo access, the tools
used, a 3-minute video script, ready-to-paste Devpost copy, and the final manual
checklist.

> **Before submitting, fill in the two placeholders below.**
>
> - **Demo video (public):** `[ADD PUBLIC YOUTUBE URL HERE]`
> - **Sandbox test credentials (optional):** see [Demo access](#demo-access).
>   Never commit real PayPal/OpenAI secrets.

## 1. What GuardPay is

GuardPay is an AI-assisted payment-safety layer for the PayPal Sandbox. It
detects when a user may be manipulated into making a fraudulent payment — fake
support calls, urgent payment requests, suspicious invoices and URLs, and
unusual transaction behavior — then explains the risk and returns a
server-authoritative **ALLOW / VERIFY / STOP** decision.

- **Repository:** <https://github.com/harshitsingh070/GuardPay>
- **License:** MIT (`LICENSE`)
- **Stack:** React + Vite + TypeScript, Spring Boot (Java 17) + PostgreSQL +
  Flyway, Python FastAPI, Kotlin Android WebView shell, Docker Compose.
- **Core security principle:** the LLM never decides. AI produces validated
  evidence only; a deterministic Spring Boot risk engine is the sole
  `ALLOW`/`VERIFY`/`STOP` authority. A PayPal order is created only after
  `ALLOW` (or after a controlled `VERIFY` succeeds).

## 2. Judge quickstart (recommended)

Verified environment: Java 17, Node 20+, Python 3.12+, Docker Compose.

```powershell
# Optional credentials — the stack boots and the demo runs without them.
$env:PAYPAL_BASE_URL      = "https://api-m.sandbox.paypal.com"
$env:PAYPAL_CLIENT_ID     = "<sandbox-client-id>"
$env:PAYPAL_CLIENT_SECRET = "<sandbox-client-secret>"
$env:OPENAI_API_KEY       = "<openai-key>"   # optional

docker compose up --build
```

Then open `http://localhost:3000`, register an account, and follow
[What to click](#4-what-to-click-3-minute-demo). Services:

| Service    | URL                   |
| ---------- | --------------------- |
| Frontend   | http://localhost:3000 |
| Backend    | http://localhost:8080 |
| AI service | http://localhost:8001 |
| PostgreSQL | localhost:5432        |

Without PayPal credentials everything except live order creation/capture still
works. Without an OpenAI key the AI service returns local structural URL
findings only and the deterministic engine decides alone.

**Manual path** (if Docker is unavailable) and the full walkthrough are in
`docs/demo.md`. In short:

```powershell
docker compose up -d db
mvn -f backend spring-boot:run                 # :8080
npm --prefix frontend install; npm --prefix frontend run dev   # :3000
# optional AI service (separate terminal, from ai-service/):
python -m venv .venv; .\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe -m uvicorn app.main:app --port 8001  # :8001
```

## 3. Demo access

- **Accounts:** registration is self-service on the login page. Create a fresh
  account; all transactions, recipients, devices, alerts, blocklist entries, and
  alerts are owner-scoped to that account.
- **PayPal Sandbox (for live order + capture):** use an ALLOW payment, open the
  approval link, approve with a **Sandbox Personal** test account, then capture.
  The recipient email must be a payee that exists in your Sandbox.
- **Simulated verification:** for a `VERIFY` decision, read the code from the
  loopback-only dev outbox (backend host only):
  `Invoke-RestMethod "http://localhost:8080/api/dev/verifications/<paymentId>/code"`.
  If no authenticator is enrolled, the challenge is simulated. Enroll TOTP on
  `/dashboard` to use a real authenticator code instead.
- **Sandbox test credentials (optional):** `[ADD SANDBOX TEST EMAIL / NOTES HERE — never a real secret]`

Automated safety-invariant suite (backend must be running):

```powershell
pwsh -File scripts/demo-guarded-flow.ps1 -RecipientEmail <sandbox-payee@example.com>
```

Exit code `0` means every invariant held: a non-ALLOW decision never creates an
order, VERIFY issues a challenge with no order, a wrong code never verifies, the
correct code creates the order only afterward, and history reflects the runs.

## 4. What to click (3-minute demo)

1. `/login` — register or sign in. Point out the private, owner-scoped workspace.
2. `/payment` — select **Safe Payment** ($45, known names). Show automatic risk
   analysis → **LOW / ALLOW**, contributing signals, and a real Sandbox order +
   approval link.
3. `/payment` — select **Suspicious Payment** ($2,499, new names, scam text +
   URL). Show AI evidence (impersonation, urgency, pressure, secrecy, URL) and
   the deterministic calculation → **VERIFY** or **STOP**.
4. If **VERIFY**: challenge appears and **no PayPal order exists**. Show a wrong
   code decrementing attempts, then verify (TOTP or dev outbox) and only then
   the approval link appears.
5. If **STOP**: show **PayPal Order ID: None** — no order was created.
6. `/transactions` — every run is stored with its decision. Select a row to show
   amount, PayPal references, verification outcome, and **why** (findings).
7. `/dashboard` — decision counts, blocked value, p50/p95 latency, AI
   availability, verification success, TOTP enrollment, AI-outage simulator, and
   live SSE alerts.
8. Admin pages (optional) — `/admin/feedback` and `/admin/learning` show the
   admin-reviewed feedback lifecycle and the continuous-learning console where a
   deployed calibration can only move the `VERIFY`/`STOP` thresholds.

## 5. Video script (≤ 3 minutes)

| Time | Scene | Say / show |
| --- | --- | --- |
| 0:00–0:15 | Title | "GuardPay — AI-assisted payment safety for PayPal. The LLM never decides; a deterministic engine does." |
| 0:15–0:30 | Architecture | Show the README diagram: signals + validated AI evidence → deterministic engine → ALLOW/VERIFY/STOP → PayPal. |
| 0:30–1:05 | Safe payment | Run **Safe Payment** → LOW/ALLOW, real Sandbox order + approval link. |
| 1:05–1:45 | Scam payment | Run **Suspicious Payment** → VERIFY/STOP, show AI evidence and the deterministic score breakdown. Emphasize **no order is created**. |
| 1:45–2:10 | Verification | Show the controlled VERIFY challenge (no order exists), a wrong code, then success and only then the approval link. |
| 2:10–2:35 | History + dashboard | `/transactions` explains the decision and findings; `/dashboard` shows live metrics and alerts. |
| 2:35–2:50 | Safety invariants | "The frontend can never force ALLOW. Capture re-checks the stored decision. Idempotency and row locks prevent double charges." |
| 2:50–3:00 | Close | "GuardPay doesn't ask the user to spot the scam — it automatically analyzes payment context and only lets payments reach PayPal when the decision allows." |

Optional B-roll: the paired Android shell showing automatic call detection and
the "You are on a phone call" warning (foreground only, debug APK).

## 6. Tools used

- **PayPal Developer Platform:** Orders v2 REST API, Sandbox approvals and
  captures, and signature-verified webhooks. Order creation is the only
  money-moving path and is reachable only after `ALLOW` (or a successful
  controlled `VERIFY`). Capture is server-side and re-checks the stored decision.
- **AI:** an OpenAI-compatible LLM client (default `gpt-4o-mini`) with two focused
  evidence agents (social engineering, content scams) plus deterministic local
  URL rules. AI returns structured findings only; it never scores or decides.
- **Backend:** Java 17, Spring Boot 3.x, Spring Data JPA/Hibernate, PostgreSQL,
  Flyway, Maven.
- **Frontend:** React, Vite, TypeScript, Tailwind CSS, shadcn/ui.
- **AG Grid (sponsor tool):** the transaction history grid in `/transactions`.
- **Mobile:** Kotlin Android WebView shell with foreground call-state reporting
  (`READ_PHONE_STATE` + `INTERNET` only).
- **Packaging:** Docker Compose for the full local stack.

## 7. Evidence

- **Tests:** backend **480** tests (`mvn -f backend clean test`, 0 failures),
  frontend **55** tests (`npm --prefix frontend run test`), ai-service **32**
  tests (`python -m pytest ai-service -q`).
- **Fraud benchmark** (`docs/fraud-benchmark.md`, offline harness on the real
  engine):
  - Dev set (66 scenarios): VALIDATED_AI precision 80.56%, recall 93.55%,
    F1 86.57%.
  - Held-out set (32 PayPal-specific scenarios, fixed policy): precision 88.89%,
    recall 100%, F1 94.12%.
  - Signal precision/recall 100% on both sets; engine latency well under 2 ms
    p95. AI evidence raises recall on borderline payments.
- **End-to-end:** guarded flow exercised against PayPal Sandbox — ALLOW with a
  real order, idempotent replay, VERIFY with no order until the challenge passes,
  forged-field rejection, AI-unavailable fallback, and leak-free logs.

## 8. What is real vs simulated

| Concern | Status |
| --- | --- |
| Risk decision (`ALLOW`/`VERIFY`/`STOP`) | Real deterministic engine, server-side only |
| AI evidence | Real with `OPENAI_API_KEY`; local URL rules without a key; `UNAVAILABLE` on failure |
| PayPal order + capture | Real Sandbox REST calls |
| Webhooks | Real signature-verified handler; external delivery needs a public tunnel |
| Verification challenge | Simulated by default, or TOTP when enrolled; never returned in the payment response or logs |
| Device call-state | From a paired Android device when a fresh observation exists; unavailable otherwise |
| Authentication | BCrypt password + opaque `HttpOnly` cookie session, CSRF, expiry, rotation/revocation |
| Dashboard + alerts | Real persisted aggregates and SSE alerts; notification delivery is asynchronous |

## 9. Honest limitations

- PayPal Sandbox approval/capture needs a human Sandbox Personal account.
- External webhook delivery needs a public tunnel (not configured; never faked).
- Device call-state is foreground-only and the APK is debug-signed.
- Verification is simulated unless TOTP is enrolled; disable
  `GUARDPAY_DEV_VERIFICATION_OUTBOX` in production.
- No password reset, email verification, OAuth, or MFA at login yet; TOTP is a
  payment-verification channel, not a login second factor.
- No live URL-reputation / Safe Browsing integration; URL analysis is structural
  and local.
- Learning is only as good as verified feedback volume and its calibration only
  moves `VERIFY`/`STOP` thresholds, never the score.

## 10. Devpost description (ready to paste)

**Inspiration.** Social-engineering scams convince people to send money they
would never send on their own — fake support calls, urgent invoices, lookalike
links. Payment apps ask the user to spot the scam at the worst possible moment.
We wanted the safety check to happen automatically, and we wanted the model to
never be the thing that authorizes a payment.

**What it does.** GuardPay sits in front of PayPal payment authorization. It
collects automatic signals (amount, recipient/merchant history, velocity,
content and URL structure, device, network, active call), validates optional AI
evidence, and a deterministic engine returns `ALLOW`, `VERIFY`, or `STOP`.
`VERIFY` issues a controlled challenge and creates no order until the server
verifies it. `STOP` creates nothing. Only `ALLOW` (or a passed verification)
creates a PayPal Sandbox order; capture is server-side and re-checks the stored
decision. Every decision is persisted with its findings so history explains
*why*.

**How we built it.** A Spring Boot backend owns auth, signals, the risk engine,
PayPal integration, webhooks, verification, alerts, and a feedback/learning
lifecycle. A FastAPI service returns structured AI findings only — no scoring.
A React/Vite frontend and a thin Kotlin WebView shell provide the checkout,
history (AG Grid), dashboard, and call-state reporting. PostgreSQL + Flyway own
the schema; Docker Compose runs the whole stack.

**Challenges.** Keeping the LLM strictly out of the decision path; making
`VERIFY` order-free until server verification; idempotent, race-safe capture;
and honest handling of unavailable evidence instead of guessing it safe.

**Accomplishments.** A real end-to-end Sandbox flow with enforced safety
invariants; 480 backend + 55 frontend + 32 AI tests; an offline fraud benchmark
with 100% recall on the held-out set at 88.89% precision; owner-scoped data,
audited feedback, and threshold-only calibrated learning.

**What we learned.** AI is most useful as validated evidence, not as the
authority. Deterministic, explainable decisions are easier to test, audit, and
trust — and they degrade safely when the model or device is unavailable.

**What's next.** Real challenge delivery, webhook tunneling/deployment, live URL
reputation, production mobile call monitoring, and MFA at login.

## 11. Final submission checklist

Repository (can be done from this repo):

- [ ] Working tree committed and pushed to `main`.
- [ ] Repository is **public** on GitHub.
- [ ] Demo video uploaded and the public URL pasted into section 0 above.
- [ ] Screenshots added (checkout ALLOW, VERIFY challenge, STOP, transactions,
      dashboard) — optional but recommended.
- [ ] Any Sandbox test credentials/notes filled in (never real secrets).

Devpost:

- [ ] Project title, tagline, and description (section 10).
- [ ] Video URL attached.
- [ ] Built-with tags: PayPal, Python, FastAPI, OpenAI, Java, Spring Boot,
      PostgreSQL, React, TypeScript, Vite, Tailwind CSS, AG Grid, Kotlin, Docker.
- [ ] Sponsor prize checkboxes (e.g. AG Grid) selected where applicable.
- [ ] Repository URL and license (MIT) listed.
- [ ] Submit and confirm the confirmation email.

## 12. Verify locally before submitting

```powershell
mvn -f backend clean test          # backend (480 tests)
npm --prefix frontend run test     # frontend (55 tests)
python -m pytest ai-service -q     # ai-service (32 tests)

# end-to-end safety invariants (backend must be running)
pwsh -File scripts/demo-guarded-flow.ps1 -RecipientEmail <sandbox-payee@example.com>
```
