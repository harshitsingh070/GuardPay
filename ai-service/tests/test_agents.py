"""Agent tests with a stub LLM (test double — production always calls the model)."""

import json

import pytest

from app.agents.content_scam import ContentScamAgent
from app.agents.social_engineering import SocialEngineeringAgent
from app.agents.url_analysis import analyze_url
from app.schemas.analysis import AnalyzeRequest
from app.services.llm import LlmError


class StubLlm:
    """Canned JSON-mode responses; records prompts for assertions."""

    def __init__(self, payloads):
        self._payloads = list(payloads)
        self.prompts = []

    def complete_json(self, *, system, user):
        self.prompts.append((system, user))
        payload = self._payloads.pop(0)
        if isinstance(payload, Exception):
            raise payload
        return json.loads(payload)


def request_with_text(text):
    return AnalyzeRequest.model_validate(
        {
            "transaction": {"amount": "2499.00", "merchant_name": "Example"},
            "content": {"text": text, "url": "https://example-suspicious-domain.test/pay"},
        }
    )


def finding_json(*codes):
    findings = [
        {
            "code": code,
            "title": f"{code} title",
            "description": f"{code} description",
            "severity": "HIGH",
            "confidence": 0.9,
            "evidence": f"evidence for {code}",
        }
        for code in codes
    ]
    return json.dumps({"findings": findings})


def test_social_findings_structured():
    llm = StubLlm([finding_json("URGENT_LANGUAGE", "IMPERSONATION")])
    findings = SocialEngineeringAgent(llm).analyze(request_with_text("Pay now or else."))

    assert [finding.code for finding in findings] == ["URGENT_LANGUAGE", "IMPERSONATION"]
    assert all(finding.confidence == 0.9 for finding in findings)


def test_social_agent_skips_empty_text():
    llm = StubLlm([finding_json("URGENT_LANGUAGE")])
    request = AnalyzeRequest.model_validate({"transaction": {"amount": "45"}})

    assert SocialEngineeringAgent(llm).analyze(request) == []
    assert llm.prompts == []


def test_content_agent_skips_empty_context():
    llm = StubLlm([finding_json("SCAM_PATTERN")])
    request = AnalyzeRequest.model_validate({"transaction": {"amount": "45"}})

    assert ContentScamAgent(llm).analyze(request) == []


def test_malformed_model_output_handled():
    llm = StubLlm(['{"findings": "not-a-list"}'])
    with pytest.raises(LlmError):
        SocialEngineeringAgent(llm).analyze(request_with_text("Pay now."))


def test_invalid_code_in_output_handled():
    llm = StubLlm([finding_json("MAKE_IT_STOP")])
    with pytest.raises(LlmError):
        ContentScamAgent(llm).analyze(request_with_text("Pay now."))


def test_url_agent_never_fetches():
    # Pure string analysis: http:// URL must not trigger network — if it did,
    # this test would fail trying to reach a reserved .test domain.
    findings = analyze_url("https://example-suspicious-domain.test/pay")

    assert len(findings) >= 1
    assert all(f.code == "SUSPICIOUS_URL_STRUCTURE" for f in findings)


def test_url_rules():
    assert analyze_url(None) == []
    assert analyze_url("") == []
    assert analyze_url("https://www.paypal.com/signin") == []
    assert analyze_url("https://support.example.com/help") == []
    assert [f.title for f in analyze_url("http://192.0.2.10/pay")]


def test_url_ip_and_userinfo_flagged():
    assert analyze_url("http://192.0.2.10/pay")
    assert analyze_url("https://legit.test@evil.test/login")
