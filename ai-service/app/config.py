"""GuardPay AI service settings — OS env wins, ai-service/.env honored for local runs."""

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Never logs secrets; missing key fails /analyze clearly, not silently."""

    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    app_name: str = "guardpay-ai-service"
    port: int = Field(default=8001, validation_alias="AI_SERVICE_PORT")
    openai_api_key: str = ""
    openai_model: str = "gpt-4o-mini"
    # OpenAI-compatible endpoint. Default is OpenAI; OpenRouter and other
    # gateways work by overriding this (they accept the same client).
    openai_base_url: str = "https://api.openai.com/v1"
    llm_timeout_seconds: float = 30.0

    @property
    def ai_configured(self) -> bool:
        return bool(self.openai_api_key)


settings = Settings()
