"""GuardPay AI service settings — OS env wins, ai-service/.env honored for local runs."""

from pydantic import AliasChoices, Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Never logs secrets; missing key fails /analyze clearly, not silently."""

    model_config = SettingsConfigDict(
        env_file=".env", extra="ignore", populate_by_name=True
    )

    app_name: str = "guardpay-ai-service"
    port: int = Field(default=8001, validation_alias="AI_SERVICE_PORT")
    # Groq is the default provider. Both OPENAI_API_KEY and GROQ_API_KEY are
    # accepted so the setup is intuitive, while the OpenAI SDK stays generic.
    openai_api_key: str = Field(
        default="",
        validation_alias=AliasChoices("OPENAI_API_KEY", "GROQ_API_KEY"),
    )
    # Any OpenAI-compatible model works. For OpenAI, set
    # OPENAI_BASE_URL=https://api.openai.com/v1 and OPENAI_MODEL=gpt-4o-mini.
    openai_model: str = "openai/gpt-oss-20b"
    openai_base_url: str = "https://api.groq.com/openai/v1"
    llm_timeout_seconds: float = 30.0
    # Optional shared secret for the Spring Boot -> AI hop. When set, requests
    # without a matching X-GuardPay-Internal-Token are rejected with 401.
    internal_token: str = Field(default="", validation_alias="AI_SERVICE_INTERNAL_TOKEN")

    @property
    def ai_configured(self) -> bool:
        return bool(self.openai_api_key)


settings = Settings()
