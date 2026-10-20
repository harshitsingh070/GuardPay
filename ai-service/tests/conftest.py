"""Test isolation: never read a developer key or call a live model.

The service intentionally loads ai-service/.env for local runs. Tests must not
inherit that key, otherwise "not configured" paths accidentally hit the API.
"""

import os

import pytest

os.environ["OPENAI_API_KEY"] = ""

from app.config import settings  # noqa: E402  (must come after the env override)


@pytest.fixture(autouse=True)
def _no_live_model(monkeypatch):
    monkeypatch.setattr(settings, "openai_api_key", "")
