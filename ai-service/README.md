# GuardPay AI Service

Evidence-only scam analysis for GuardPay. The Python service produces
**structured findings** (urgency, impersonation, pressure, secrecy, scam
patterns, URL structure); the **Spring Boot deterministic risk engine makes
every payment decision**. AI findings are evidence, not payment decisions.

No CrewAI: a fixed deterministic pipeline — local URL rules plus two focused
LLM agents (social engineering, content scams) — runs in a fixed order, so a
multi-agent framework would add version weight for zero behavioral gain.

## Run locally

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt

# Without a key the service still boots and returns local URL findings only.
.\.venv\Scripts\python.exe -m uvicorn app.main:app --port 8001

# With a key (never commit it) — either export it or put it in ai-service/.env:
# Groq is the default provider; get a key at https://console.groq.com/keys.
$env:OPENAI_API_KEY = "<groq-key>"          # GROQ_API_KEY also works
# or: add OPENAI_API_KEY=<groq-key> to ai-service/.env (gitignored, auto-loaded)
$env:OPENAI_MODEL = "openai/gpt-oss-20b"    # optional; default
$env:OPENAI_BASE_URL = "https://api.groq.com/openai/v1"  # optional; default
.\.venv\Scripts\python.exe -m uvicorn app.main:app --port 8001

# To use OpenAI instead:
# OPENAI_BASE_URL=https://api.openai.com/v1
# OPENAI_MODEL=gpt-4o-mini
```

Health: `GET http://localhost:8001/health` → `ai_configured` tells whether an
LLM key is configured. Model failures on analyze return `502`; invalid
requests return `422`; a bad shared token returns `401`.

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
