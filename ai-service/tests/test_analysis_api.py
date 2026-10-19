"""Orchestrator + API tests (no network, no API key)."""

import json
import logging

from fastapi.testclient import TestClient

from app.main import app
from app.schemas.analysis import AnalyzeRequest
from app.services.analysis import analyze_payment

client = TestClient(app)


class StubLlm:
    def __init__(self, payload):
        self._payload = payload

    def complete_json(self, *, system, user):
        return json.loads(self._payload)


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


def test_analyze_without_key_is_clear_503():
    response = client.post(
        "/api/v1/analyze",
        json={"transaction": {"amount": "45"}, "content": {"text": "Hello"}},
    )

    assert response.status_code == 503
    assert "OPENAI_API_KEY" in response.json()["detail"]


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

    result = analyze_payment(request, StubLlm(payload))

    assert [f.code for f in result.findings] == ["IMPERSONATION", "URGENT_LANGUAGE"]
    assert result.findings[1].confidence == 0.9
    assert result.analysis_id is not None


def test_orchestrator_empty_context_summary():
    request = AnalyzeRequest.model_validate({"transaction": {"amount": "45"}})

    result = analyze_payment(request, StubLlm(json.dumps({"findings": []})))

    assert result.findings == []
    assert "No scam indicators" in result.summary


def test_no_secret_in_logs(caplog):
    with caplog.at_level(logging.WARNING):
        client.post("/api/v1/analyze", json={"transaction": {"amount": "1"}})

    combined = "\n".join(record.getMessage() for record in caplog.records)
    assert "sk-" not in combined
    assert "OPENAI_API_KEY" in combined  # the safe config message is fine
