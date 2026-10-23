"""Schema contract tests — controlled vocabularies stay controlled."""

import json
from pathlib import Path

import pytest
from pydantic import ValidationError

from app.schemas.analysis import AiFinding, AnalyzeRequest, AnalyzeResponse

CONTRACTS = Path(__file__).resolve().parents[2] / "contracts"


def valid_finding(**overrides):
    base = {
        "code": "URGENT_LANGUAGE",
        "title": "Urgent pressure",
        "description": "Pay immediately.",
        "severity": "HIGH",
        "confidence": 0.91,
        "evidence": "Says 'immediately'.",
    }
    base.update(overrides)
    return base


def test_valid_request_accepted():
    request = AnalyzeRequest.model_validate(
        {
            "transaction": {"amount": "2499.00", "currency": "USD"},
            "content": {"text": "Pay now."},
        }
    )
    assert request.content.text == "Pay now."
    assert request.device.active_call is None


def test_missing_content_handled_with_defaults():
    request = AnalyzeRequest.model_validate({"transaction": {"amount": "45"}})
    assert request.content.text is None
    assert request.content.url is None


def test_valid_finding_accepted():
    assert AiFinding.model_validate(valid_finding()).code == "URGENT_LANGUAGE"


def test_invalid_finding_code_rejected():
    with pytest.raises(ValidationError):
        AiFinding.model_validate(valid_finding(code="ALLOW_THE_PAYMENT"))


def test_invalid_confidence_rejected():
    with pytest.raises(ValidationError):
        AiFinding.model_validate(valid_finding(confidence=1.5))
    with pytest.raises(ValidationError):
        AiFinding.model_validate(valid_finding(confidence=-0.1))


def test_blank_evidence_rejected():
    with pytest.raises(ValidationError):
        AiFinding.model_validate(valid_finding(evidence=""))


def test_java_contract_request_fixture_is_valid():
    payload = json.loads((CONTRACTS / "ai-analysis-request.json").read_text(encoding="utf-8"))

    request = AnalyzeRequest.model_validate(payload)

    assert request.transaction.merchant_name == "Example"
    assert request.transaction.recipient_name == "Someone"
    assert request.transaction.is_new_recipient is True
    assert request.transaction.is_unknown_merchant is True
    assert request.transaction.unexpected_invoice is True
    assert request.device.active_call is True


def test_legacy_camel_case_request_is_still_accepted():
    request = AnalyzeRequest.model_validate(
        {
            "transaction": {
                "amount": "45",
                "merchantName": "Example",
                "recipientName": "Someone",
                "isNewRecipient": True,
                "isUnknownMerchant": True,
                "unexpectedInvoice": True,
            },
            "device": {"activeCall": True},
        }
    )

    assert request.transaction.merchant_name == "Example"
    assert request.transaction.is_new_recipient is True
    assert request.device.active_call is True


def test_java_contract_response_fixture_is_valid():
    payload = json.loads((CONTRACTS / "ai-analysis-response.json").read_text(encoding="utf-8"))

    response = AnalyzeResponse.model_validate(payload)

    assert response.analysis_id is not None
    assert response.findings[0].code == "URGENT_LANGUAGE"
