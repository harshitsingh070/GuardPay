"""Orchestrator + API tests (no network, no API key)."""

import json
import logging

from fastapi.testclient import TestClient

from app.config import settings
from app.main import app
from app.schemas.analysis import AnalyzeRequest
from app.services.analysis import analyze_payment
from app.services.llm import LlmError

client = TestClient(app)


class StubLlm:
    def __init__(self, *payloads):
        self._payloads = list(payloads)

    def complete_json(self, *, system, user):
        payload = self._payloads.pop(0)
        if isinstance(payload, Exception):
            raise payload
        return json.loads(payload)


def finding(code, confidence=0.9):
    return {
        "code": code,
        "title": f"{code} title",
        "description": f"{code} description",
        "severity": "HIGH",
        "confidence": confidence,
        "evidence": "evidence",
    }


def test_health_reports_configuration():
    response = client.get("/health")

    assert response.status_code == 200
    body = response.json()
    assert body["service"] == "guardpay-ai-service"
    assert body["ai_configured"] is False  # no key in test env


def test_analyze_without_key_still_returns_local_url_findings():
    response = client.post(
        "/api/v1/analyze",
        json={
            "transaction": {"amount": "45"},
            "content": {"text": "Hello", "url": "http://192.0.2.10/pay"},
        },
    )

    assert response.status_code == 200
    body = response.json()
    assert any(f["code"] == "SUSPICIOUS_URL_STRUCTURE" for f in body["findings"])


def test_analyze_requires_the_configured_internal_token(monkeypatch):
    monkeypatch.setattr(settings, "internal_token", "internal-secret")
    payload = {"transaction": {"amount": "45"}, "content": {"text": "Hello"}}

    assert client.post("/api/v1/analyze", json=payload).status_code == 401
    assert (
        client.post(
            "/api/v1/analyze",
            json=payload,
            headers={"X-GuardPay-Internal-Token": "wrong"},
        ).status_code
        == 401
    )
    accepted = client.post(
        "/api/v1/analyze",
        json=payload,
        headers={"X-GuardPay-Internal-Token": "internal-secret"},
    )
    assert accepted.status_code == 200


def test_analyze_without_key_and_without_context_returns_200():
    response = client.post(
        "/api/v1/analyze",
        json={"transaction": {"amount": "45"}, "content": {"text": "Hello"}},
    )

    assert response.status_code == 200
    assert response.json()["findings"] == []


def test_analyze_rejects_invalid_body():
    response = client.post("/api/v1/analyze", json={"transaction": {}})

    assert response.status_code == 422


def test_orchestrator_merges_and_dedupes():
    payload = json.dumps(
        {
            "findings": [
                finding("URGENT_LANGUAGE", confidence=0.6),
                finding("URGENT_LANGUAGE", confidence=0.9),
                finding("IMPERSONATION"),
            ]
        }
    )
    request = AnalyzeRequest.model_validate(
        {"transaction": {"amount": "100"}, "content": {"text": "Pay now."}}
    )

    result = analyze_payment(request, StubLlm(payload, payload))

    assert [f.code for f in result.findings] == ["IMPERSONATION", "URGENT_LANGUAGE"]
    assert result.findings[1].confidence == 0.9
    assert result.analysis_id is not None


def test_one_failing_agent_does_not_discard_other_findings():
    request = AnalyzeRequest.model_validate(
        {
            "transaction": {"amount": "100"},
            "content": {"text": "Pay now.", "url": "http://192.0.2.10/pay"},
        }
    )
    llm = StubLlm(
        LlmError("social agent timed out"),
        json.dumps({"findings": [finding("IMPERSONATION")]}),
    )

    result = analyze_payment(request, llm)

    codes = {f.code for f in result.findings}
    assert "IMPERSONATION" in codes
    assert "SUSPICIOUS_URL_STRUCTURE" in codes


def test_orchestrator_without_llm_still_runs_local_url_checks():
    request = AnalyzeRequest.model_validate(
        {
            "transaction": {"amount": "100"},
            "content": {"url": "https://legit.test@evil.test/login"},
        }
    )

    result = analyze_payment(request, None)

    assert any(f.code == "SUSPICIOUS_URL_STRUCTURE" for f in result.findings)


def test_orchestrator_empty_context_summary():
    request = AnalyzeRequest.model_validate({"transaction": {"amount": "45"}})

    result = analyze_payment(request, StubLlm(json.dumps({"findings": []})))

    assert result.findings == []
    assert "No scam indicators" in result.summary


def test_no_secret_in_logs(caplog):
    with caplog.at_level(logging.INFO):
        client.post("/api/v1/analyze", json={"transaction": {"amount": "1"}})

    combined = "\n".join(record.getMessage() for record in caplog.records)
    assert "sk-" not in combined
    assert "OPENAI_API_KEY" in combined  # the safe config message is fine
