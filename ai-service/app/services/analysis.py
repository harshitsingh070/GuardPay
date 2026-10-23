"""Deterministic orchestration: local checks first, optional LLM agents after.

No CrewAI: three focused agents with a fixed pipeline need no multi-agent
framework, and skipping it avoids heavy dependency/version churn for zero
behavioral gain. URL analysis is pure local logic and always runs, even when
the model is unconfigured or unavailable. One failing LLM agent must not
discard findings from the other agent or the local URL rules.
"""

import logging
from uuid import uuid4

from ..agents.content_scam import ContentScamAgent
from ..agents.social_engineering import SocialEngineeringAgent
from ..agents.url_analysis import analyze_url
from ..schemas.analysis import AiFinding, AnalyzeRequest, AnalyzeResponse
from ..services.llm import LlmClient, LlmError

logger = logging.getLogger(__name__)


def analyze_payment(
    request: AnalyzeRequest, llm: LlmClient | None = None
) -> AnalyzeResponse:
    """Run local checks always; run LLM agents only when a client is available."""
    findings: list[AiFinding] = []
    findings.extend(analyze_url(request.content.url))

    if llm is not None:
        for name, agent in (
            ("social-engineering", SocialEngineeringAgent(llm)),
            ("content-scam", ContentScamAgent(llm)),
        ):
            try:
                findings.extend(agent.analyze(request))
            except LlmError as exc:
                logger.warning("%s agent unavailable: %s", name, exc)

    merged: dict[str, AiFinding] = {}
    for finding in findings:
        current = merged.get(finding.code)
        if current is None or finding.confidence > current.confidence:
            merged[finding.code] = finding
    ordered = [merged[code] for code in sorted(merged)]

    if not ordered:
        summary = "No scam indicators were observed in the supplied context."
    else:
        kinds = len(ordered)
        summary = (
            f"{kinds} scam indicator{'s' if kinds != 1 else ''} observed in "
            "the supplied context; see findings for evidence."
        )
    return AnalyzeResponse(analysis_id=uuid4(), findings=ordered, summary=summary)
