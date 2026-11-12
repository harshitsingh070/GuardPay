"""GuardPay AI Service — FastAPI entry point.

GET  /health          liveness + whether the model is configured.
POST /api/v1/analyze structured scam-evidence analysis (Spring Boot only).

Configuration errors fail clearly (503); model failures fail safely (502);
nothing secret is ever logged or returned.
"""

import hmac
import logging

from fastapi import FastAPI, Header, HTTPException, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from .config import settings
from .schemas.analysis import AnalyzeRequest, AnalyzeResponse
from .services.analysis import analyze_payment
from .services.llm import LlmError, OpenAiLlmClient

logger = logging.getLogger("guardpay.ai")

app = FastAPI(title="GuardPay AI Service", version="0.1.0")


@app.get("/health")
def health() -> dict:
    return {
        "status": "ok",
        "service": "guardpay-ai-service",
        "ai_configured": settings.ai_configured,
    }


@app.post("/api/v1/analyze", response_model=AnalyzeResponse)
def analyze(
    request: AnalyzeRequest,
    x_guardpay_internal_token: str | None = Header(default=None),
) -> AnalyzeResponse | JSONResponse:
    if settings.internal_token and not hmac.compare_digest(
        x_guardpay_internal_token or "", settings.internal_token
    ):
        raise HTTPException(status_code=401, detail="Unauthorized")
    llm = None
    if settings.ai_configured:
        llm = OpenAiLlmClient(
            api_key=settings.openai_api_key,
            model=settings.openai_model,
            timeout_seconds=settings.llm_timeout_seconds,
            base_url=settings.openai_base_url,
        )
    else:
        logger.info(
            "OPENAI_API_KEY/GROQ_API_KEY not configured; returning local URL findings only"
        )
    try:
        return analyze_payment(request, llm)
    except LlmError as exc:
        logger.warning("analyze failed: %s", exc)
        return JSONResponse(status_code=502, content={"detail": str(exc)})


@app.exception_handler(RequestValidationError)
async def validation_handler(request: Request, exc: RequestValidationError) -> JSONResponse:
    return JSONResponse(status_code=422, content={"detail": "Invalid analysis request"})
