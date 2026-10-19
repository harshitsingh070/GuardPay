# GuardPay AI Service

Evidence-only scam analysis for GuardPay. The Python service produces
**structured findings** (urgency, impersonation, pressure, secrecy, scam
patterns, URL structure); the **Spring Boot deterministic risk engine makes
every payment decision**. AI findings are evidence, not payment decisions.

No CrewAI: three focused agents run in a fixed deterministic pipeline, so a
multi-agent framework would add version weight for zero behavioral gain.

## Run locally

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt

# Without a key the service still boots; analysis fails clearly (503).
.\.venv\Scripts\python.exe -m uvicorn app.main:app --port 8001

# With a key (never commit it) — either export it or put it in ai-service/.env:
$env:OPENAI_API_KEY = "<key>"
# or: add OPENAI_API_KEY=<key> to ai-service/.env (gitignored, auto-loaded)
$env:OPENAI_MODEL = "gpt-4o-mini"   # optional
.\.venv\Scripts\python.exe -m uvicorn app.main:app --port 8001
```

Health: `GET http://localhost:8001/health` → `ai_configured` tells whether
the model is reachable.

## Analyze (Spring Boot only — never browsers)

```powershell
curl -X POST http://localhost:8001/api/v1/analyze `
  -H "Content-Type: application/json" `
  -d '{"transaction": {"amount": "2499.00"}, "content": {"text": "Pay immediately."}}'
```

Response: `{analysis_id, findings[{code, title, description, severity,
confidence, evidence}], summary}`. Codes are a closed vocabulary; confidence
is observation certainty, NOT a risk score.

## Privacy boundaries

Only the minimum scam-analysis context is accepted (amount, names, flags,
message text, URL, call-state boolean). No audio, phone numbers, contacts,
emails, or credentials. Full message bodies are never logged.

## Tests

```powershell
.\.venv\Scripts\python.exe -m pytest tests -q   # no network, no API key
```
