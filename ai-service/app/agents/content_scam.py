"""Invoice/content scam-pattern agent (LLM-backed, schema-validated)."""

import logging

from pydantic import ValidationError

from ..prompts.templates import CONTENT_SCAM_SYSTEM, content_user_text
from ..schemas.analysis import AiFinding, AnalyzeRequest
from ..services.llm import LlmClient, LlmError
from .social_engineering import _flags

logger = logging.getLogger(__name__)


class ContentScamAgent:
    """Finds scam-like invoice/payment-request context evidence."""

    def __init__(self, llm: LlmClient) -> None:
        self._llm = llm

    def analyze(self, request: AnalyzeRequest) -> list[AiFinding]:
        text = (request.content.text or "").strip()
        has_context = bool(
            text
            or request.transaction.merchant_name
            or request.transaction.recipient_name
        )
        if not has_context:
            return []
        user = content_user_text(
            amount=str(request.transaction.amount),
            currency=request.transaction.currency,
            merchant=request.transaction.merchant_name,
            recipient=request.transaction.recipient_name,
            flags=_flags(request),
            text=text,
        )
        try:
            payload = self._llm.complete_json(system=CONTENT_SCAM_SYSTEM, user=user)
            raw = payload.get("findings", [])
            if not isinstance(raw, list):
                raise LlmError("model output failed schema validation")
            return [AiFinding.model_validate(item) for item in raw]
        except ValidationError as exc:
            logger.warning("content-scam output failed validation")
            raise LlmError("model output failed schema validation") from exc
