"""Cheap, deterministic detectors used inline by the security plugin.

These are signals, not verdicts: the policy engine decides what to do with
them, and the blue team consumes them as span attributes.
"""

from __future__ import annotations

import json
import re
from dataclasses import dataclass, field
from typing import Any

_SECRET_PATTERNS = [
    re.compile(r"sk-ant-[A-Za-z0-9_\-]{10,}"),
    re.compile(r"AKIA[0-9A-Z]{16}"),
    re.compile(r"AIza[0-9A-Za-z_\-]{30,}"),
    re.compile(r"-----BEGIN [A-Z ]*PRIVATE KEY-----"),
    re.compile(r"(?i)\b(password|passwd|secret|api[_-]?key)\s*[:=]\s*\S{6,}"),
]
_EMAIL = re.compile(r"[A-Za-z0-9._%+\-]+@([A-Za-z0-9.\-]+\.[A-Za-z]{2,})")
_URL = re.compile(r"https?://([A-Za-z0-9.\-]+)")

# Phrases typical of indirect prompt injection. Deliberately simple: this is
# a first-pass score for telemetry, to be replaced or complemented by a
# classifier (e.g. Model Armor) in production.
_INJECTION_MARKERS = [
    r"ignore (all |any )?(previous|prior|above) instructions",
    r"disregard (the )?(previous|prior|above)",
    r"you are now",
    r"system prompt",
    r"do not tell the user",
    r"new instructions?:",
    r"以前の指示を無視",
    r"これまでの指示を無視",
    r"ユーザーには(言わ|伝え)ない",
    r"次の指示に従",
    r"管理者として",
]
_INJECTION_RE = [re.compile(p, re.IGNORECASE) for p in _INJECTION_MARKERS]


@dataclass
class Findings:
  canary_hit: list[str] = field(default_factory=list)
  secret_in_args: bool = False
  domains: list[str] = field(default_factory=list)

  def as_attributes(self) -> dict[str, Any]:
    return {
        "sec.detect.canary_hit": bool(self.canary_hit),
        "sec.detect.secret_in_args": self.secret_in_args,
        "sec.tool.args.domains": self.domains,
    }


class Detectors:

  def __init__(self, canary_tokens: tuple[str, ...] = ()):
    self.canary_tokens = canary_tokens

  def scan_args(self, args: dict[str, Any]) -> Findings:
    text = _flatten(args)
    domains = {m.lower() for m in _EMAIL.findall(text)}
    domains |= {m.lower() for m in _URL.findall(text)}
    return Findings(
        canary_hit=[c for c in self.canary_tokens if c in text],
        secret_in_args=any(p.search(text) for p in _SECRET_PATTERNS),
        domains=sorted(domains),
    )

  def injection_score(self, text: str) -> float:
    hits = sum(1 for r in _INJECTION_RE if r.search(text))
    return min(1.0, hits / 2)

  def contains_canary(self, text: str) -> bool:
    return any(c in text for c in self.canary_tokens)


def _flatten(obj: Any) -> str:
  if isinstance(obj, str):
    return obj
  return json.dumps(obj, ensure_ascii=False, default=str)
