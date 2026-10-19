"""GuardPay AI Service — FastAPI entry point.

GET  /health          liveness + whether the model is configured.
POST /api/v1/analyze structured scam-evidence analysis (Spring Boot only).

Configuration errors fail clearly (503); model failures fail safely (502);
nothing secret is ever logged or returned.
"""

import logging

from fastapi import FastAPI, Request
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
def analyze(request: AnalyzeRequest) -> AnalyzeResponse | JSONResponse:
    if not settings.ai_configured:
        logger.warning("analyze rejected: OPENAI_API_KEY is not configured")
        return JSONResponse(
            status_code=503, content={"detail": "AI is not configured: set OPENAI_API_KEY"}
        )
    try:
        llm = OpenAiLlmClient(
            api_key=settings.openai_api_key,
            model=settings.openai_model,
            timeout_seconds=settings.llm_timeout_seconds,
            base_url=settings.openai_base_url,
        )
        return analyze_payment(request, llm)
    except LlmError as exc:
        logger.warning("analyze failed: %s", exc)
        return JSONResponse(status_code=502, content={"detail": str(exc)})


@app.exception_handler(RequestValidationError)
async def validation_handler(request: Request, exc: RequestValidationError) -> JSONResponse:
    return JSONResponse(status_code=422, content={"detail": "Invalid analysis request"})
