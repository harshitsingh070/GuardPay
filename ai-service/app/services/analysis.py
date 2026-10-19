"""Deterministic orchestration: agents in fixed order, merged findings.

No CrewAI: three focused agents with a fixed pipeline need no multi-agent
framework, and skipping it avoids heavy dependency/version churn for zero
behavioral gain. The pipeline order is fixed, so output ordering is stable.
"""

import logging
from uuid import uuid4

from ..agents.content_scam import ContentScamAgent
from ..agents.social_engineering import SocialEngineeringAgent
from ..agents.url_analysis import analyze_url
from ..schemas.analysis import AiFinding, AnalyzeRequest, AnalyzeResponse
from ..services.llm import LlmClient

logger = logging.getLogger(__name__)


def analyze_payment(request: AnalyzeRequest, llm: LlmClient) -> AnalyzeResponse:
    """Run all agents; dedupe identical codes keeping highest confidence."""
    findings: list[AiFinding] = []
    findings.extend(SocialEngineeringAgent(llm).analyze(request))
    findings.extend(ContentScamAgent(llm).analyze(request))
    findings.extend(analyze_url(request.content.url))

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
