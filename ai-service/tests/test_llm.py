"""OpenAI-compatible client tests — constructor wiring only, no network."""

import pytest

from app.services.llm import LlmError, OpenAiLlmClient


class FakeCompletions:
    def __init__(self, outer):
        self._outer = outer

    def create(self, **kwargs):
        self._outer.calls.append(kwargs)

        class Message:
            content = '{"findings": []}'

        class Choice:
            message = Message()

        class Response:
            choices = [Choice()]

        return Response()


class FakeChat:
    def __init__(self, outer):
        self.completions = FakeCompletions(outer)


class FakeOpenAI:
    instances = []

    def __init__(self, **kwargs):
        self.kwargs = kwargs
        self.calls = []
        self.chat = FakeChat(self)
        FakeOpenAI.instances.append(self)


def test_base_url_and_key_forwarded(monkeypatch):
    import openai

    monkeypatch.setattr(openai, "OpenAI", FakeOpenAI)
    FakeOpenAI.instances.clear()

    client = OpenAiLlmClient(
        api_key="secret", model="some-model", base_url="https://openrouter.ai/api/v1"
    )
    result = client.complete_json(system="sys", user="usr")

    assert result == {"findings": []}
    built = FakeOpenAI.instances[-1]
    assert built.kwargs["base_url"] == "https://openrouter.ai/api/v1"
    assert built.kwargs["api_key"] == "secret"
    assert built.calls[0]["model"] == "some-model"


def test_missing_key_fails_fast():
    with pytest.raises(LlmError):
        OpenAiLlmClient(api_key="", model="some-model")
