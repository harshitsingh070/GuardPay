"""Social-engineering evidence agent (LLM-backed, schema-validated)."""

import logging

from pydantic import ValidationError

from ..prompts.templates import SOCIAL_ENGINEERING_SYSTEM, social_user_text
from ..schemas.analysis import AiFinding, AnalyzeRequest
from ..services.llm import LlmClient, LlmError

logger = logging.getLogger(__name__)


class SocialEngineeringAgent:
    """Finds manipulation evidence (urgency, impersonation, pressure, secrecy...)."""

    def __init__(self, llm: LlmClient) -> None:
        self._llm = llm

    def analyze(self, request: AnalyzeRequest) -> list[AiFinding]:
        text = (request.content.text or "").strip()
        if not text:
            return []
        flags = _flags(request)
        on_call = (
            "yes" if request.device.active_call is True
            else "no" if request.device.active_call is False
            else "unknown"
        )
        user = social_user_text(
            amount=str(request.transaction.amount),
            currency=request.transaction.currency,
            merchant=request.transaction.merchant_name,
            recipient=request.transaction.recipient_name,
            flags=flags,
            text=text,
            on_call=on_call,
        )
        try:
            payload = self._llm.complete_json(system=SOCIAL_ENGINEERING_SYSTEM, user=user)
            raw = payload.get("findings", [])
            if not isinstance(raw, list):
                raise LlmError("model output failed schema validation")
            return [AiFinding.model_validate(item) for item in raw]
        except ValidationError as exc:
            logger.warning("social-engineering output failed validation")
            raise LlmError("model output failed schema validation") from exc


def _flags(request: AnalyzeRequest) -> str:
    active = [
        name
        for name, value in [
            ("new_recipient", request.transaction.is_new_recipient),
            ("unknown_merchant", request.transaction.is_unknown_merchant),
            ("unexpected_invoice", request.transaction.unexpected_invoice),
        ]
        if value
    ]
    return ", ".join(active) if active else "none"
