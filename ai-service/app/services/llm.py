"""LLM access — JSON-mode chat completions only. Secrets never logged."""

import json
import logging
from typing import Protocol

logger = logging.getLogger(__name__)


class LlmError(Exception):
    """Model transport, timeout, or output failure. Message is always safe."""


class LlmClient(Protocol):
    def complete_json(self, *, system: str, user: str) -> dict:
        """Return the model's parsed JSON object (raises LlmError otherwise)."""
        ...


class OpenAiLlmClient:
    """Real model calls. Import of the SDK is deferred so unit tests run lean."""

    def __init__(
        self,
        api_key: str,
        model: str,
        timeout_seconds: float = 30.0,
        base_url: str = "https://api.groq.com/openai/v1",
    ) -> None:
        if not api_key:
            raise LlmError("OPENAI_API_KEY/GROQ_API_KEY is not configured")
        self._api_key = api_key
        self._model = model
        self._timeout = timeout_seconds
        self._base_url = base_url or "https://api.groq.com/openai/v1"

    def complete_json(self, *, system: str, user: str) -> dict:
        try:
            from openai import OpenAI

            client = OpenAI(
                api_key=self._api_key, timeout=self._timeout, base_url=self._base_url
            )
            response = client.chat.completions.create(
                model=self._model,
                messages=[
                    {"role": "system", "content": system},
                    {"role": "user", "content": user},
                ],
                response_format={"type": "json_object"},
                temperature=0,
            )
            text = (response.choices[0].message.content or "").strip()
        except LlmError:
            raise
        except Exception as exc:  # transport/auth/timeout — type varies by SDK version
            logger.warning("model request failed: %s", type(exc).__name__)
            raise LlmError("model request failed") from exc
        try:
            parsed = json.loads(text)
        except json.JSONDecodeError as exc:
            logger.warning("model returned non-JSON output")
            raise LlmError("model returned non-JSON output") from exc
        if not isinstance(parsed, dict):
            raise LlmError("model returned an unexpected JSON shape")
        return parsed
