"""Structured AI-analysis contract (internal: Spring Boot only, never browsers)."""

from decimal import Decimal
from typing import Literal
from uuid import UUID, uuid4

from pydantic import BaseModel, Field

FindingCode = Literal[
    "URGENT_LANGUAGE",
    "IMPERSONATION",
    "PRESSURE_TO_PAY",
    "SECRECY_REQUEST",
    "SUSPICIOUS_PAYMENT_INSTRUCTION",
    "SCAM_PATTERN",
    "SUSPICIOUS_URL_STRUCTURE",
    "UNEXPECTED_INVOICE_CONTEXT",
]

Severity = Literal["LOW", "MEDIUM", "HIGH", "CRITICAL"]


class TransactionContext(BaseModel):
    amount: Decimal = Field(ge=0)
    currency: str = "USD"
    merchant_name: str | None = None
    recipient_name: str | None = None
    is_new_recipient: bool = False
    is_unknown_merchant: bool = False
    unexpected_invoice: bool = False


class ContentContext(BaseModel):
    text: str | None = None
    url: str | None = None


class DeviceContext(BaseModel):
    active_call: bool | None = None


class AnalyzeRequest(BaseModel):
    transaction: TransactionContext
    content: ContentContext = ContentContext()
    device: DeviceContext = DeviceContext()


class AiFinding(BaseModel):
    """Evidence only — never a payment decision. Confidence is NOT a risk score."""

    code: FindingCode
    title: str = Field(min_length=1)
    description: str = Field(min_length=1)
    severity: Severity
    confidence: float = Field(ge=0.0, le=1.0)
    evidence: str = Field(min_length=1)


class AnalyzeResponse(BaseModel):
    analysis_id: UUID = Field(default_factory=uuid4)
    findings: list[AiFinding] = []
    summary: str
