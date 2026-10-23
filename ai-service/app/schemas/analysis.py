"""Structured AI-analysis contract (internal: Spring Boot only, never browsers).

The canonical wire format is snake_case. Request aliases also accept the
camelCase names used by older Java builds so a version skew cannot silently
drop risk context.
"""

from decimal import Decimal
from typing import Literal
from uuid import UUID, uuid4

from pydantic import AliasChoices, BaseModel, ConfigDict, Field

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
    model_config = ConfigDict(populate_by_name=True)

    amount: Decimal = Field(ge=0)
    currency: str = "USD"
    merchant_name: str | None = Field(
        default=None, validation_alias=AliasChoices("merchant_name", "merchantName")
    )
    recipient_name: str | None = Field(
        default=None, validation_alias=AliasChoices("recipient_name", "recipientName")
    )
    is_new_recipient: bool = Field(
        default=False, validation_alias=AliasChoices("is_new_recipient", "isNewRecipient")
    )
    is_unknown_merchant: bool = Field(
        default=False, validation_alias=AliasChoices("is_unknown_merchant", "isUnknownMerchant")
    )
    unexpected_invoice: bool = Field(
        default=False, validation_alias=AliasChoices("unexpected_invoice", "unexpectedInvoice")
    )


class ContentContext(BaseModel):
    text: str | None = None
    url: str | None = None


class DeviceContext(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    active_call: bool | None = Field(
        default=None, validation_alias=AliasChoices("active_call", "activeCall")
    )


class AnalyzeRequest(BaseModel):
    transaction: TransactionContext
    content: ContentContext = Field(default_factory=ContentContext)
    device: DeviceContext = Field(default_factory=DeviceContext)


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
    findings: list[AiFinding] = Field(default_factory=list)
    summary: str
