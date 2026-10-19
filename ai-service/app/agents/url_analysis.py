"""URL structure agent — transparent local heuristics, NO fetching.

Never visits URLs, never queries a reputation database, never claims a URL
is malicious from looks alone. Each rule below is documented and unit-tested;
anything subtler is left to future reputation integration.
"""

import ipaddress
from urllib.parse import urlsplit

from ..schemas.analysis import AiFinding

# Lure words matched against the registrable domain only (never bare
# subdomains, so legitimate support.example.com style hosts stay quiet).
LURE_WORDS = frozenset(
    {"verify", "secure", "login", "update", "support", "confirm", "account", "suspicious"}
)

# Brand names that only their owner should serve from a registrable domain.
BRAND_MARKS = frozenset({"paypal", "apple", "google", "microsoft", "amazon", "bank"})


def _registrable(host: str) -> str:
    labels = host.lower().split(".")
    return ".".join(labels[-2:]) if len(labels) >= 2 else host.lower()


def analyze_url(url: str | None) -> list[AiFinding]:
    """Return structural findings (possibly empty). Pure function of the string."""
    if not url or not url.strip():
        return []
    try:
        parts = urlsplit(url.strip())
        host = (parts.hostname or "").lower()
    except ValueError:
        return []
    if not host:
        return []

    findings: list[AiFinding] = []

    def add(code: str, title: str, description: str, evidence: str) -> None:
        findings.append(
            AiFinding(
                code=code,  # type: ignore[arg-type]
                title=title,
                description=description,
                severity="MEDIUM",
                confidence=0.7,
                evidence=evidence,
            )
        )

    if "@" in url.split("?", 1)[0].split("#", 1)[0]:
        add(
            "SUSPICIOUS_URL_STRUCTURE",
            "Obfuscated URL with credentials separator",
            "The URL contains an '@' separator that can hide the real destination.",
            f"URL contains '@' before the path: {host or 'unknown host'}.",
        )
    try:
        ipaddress.ip_address(host)
        add(
            "SUSPICIOUS_URL_STRUCTURE",
            "Bare IP address host",
            "The link points directly at an IP address instead of a named domain.",
            f"Host is a literal IP address: {host}.",
        )
    except ValueError:
        pass
    if "xn--" in host:
        add(
            "SUSPICIOUS_URL_STRUCTURE",
            "Internationalized (punycode) domain",
            "Punycode domains can visually impersonate trusted brands.",
            f"Host uses punycode encoding: {host}.",
        )
    labels = host.split(".")
    if len(labels) > 5:
        add(
            "SUSPICIOUS_URL_STRUCTURE",
            "Excessive subdomains",
            "Unusually deep subdomain chains are common in phishing links.",
            f"Host has {len(labels)} DNS labels.",
        )
    registrable = _registrable(host)
    root_labels = registrable.split(".")
    owner = root_labels[0] if root_labels else ""
    sub_labels = labels[:-2] if len(labels) > 2 else []
    if any(mark in label for label in sub_labels for mark in BRAND_MARKS) and not any(
        mark in owner for mark in BRAND_MARKS
    ):
        add(
            "SUSPICIOUS_URL_STRUCTURE",
            "Brand name inside a subdomain",
            "A trusted brand appears in a subdomain of an unrelated domain.",
            f"Brand-like label served from {registrable}.",
        )
    if any(word in owner for word in LURE_WORDS):
        add(
            "SUSPICIOUS_URL_STRUCTURE",
            "Lure words in domain",
            "The domain itself contains pressure/verification lure words.",
            f"Registrable domain contains lure wording: {registrable}.",
        )
    return findings
