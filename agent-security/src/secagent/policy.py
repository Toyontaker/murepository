"""Mission (L1) and Skill-scope policy evaluation.

The policy is deterministic on purpose: it runs inline in
``before_tool_callback`` on every tool call, so it must be fast and must not
itself be prompt-injectable. LLM-based alignment judging belongs to the
async blue-team side.
"""

from __future__ import annotations

import hashlib
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Literal

import yaml

from .detectors import Findings

Action = Literal["allow", "deny", "needs_approval"]

RISK_LEVELS = ["low", "medium", "high"]

# Tool classes that move data outside the agent's trust boundary.
OUTBOUND_CLASSES = {"external_send"}

# ADK SkillToolset / control tools. run_skill_script executes code, so it is
# classified separately and has to be granted by a skill explicitly.
BUILTIN_TOOL_CLASSES = {
    "list_skills": "skill_meta",
    "search_skills": "skill_meta",
    "load_skill": "skill_meta",
    "unload_skill": "skill_meta",
    "load_skill_resource": "skill_meta",
    "run_skill_script": "code_exec",
    "exit_loop": "control",
}


@dataclass(frozen=True)
class Mission:
  id: str
  version: int
  purpose: str
  default_tool_classes: frozenset[str]
  forbidden_tool_classes: frozenset[str]
  requires_approval_classes: frozenset[str]
  allowed_recipient_domains: frozenset[str]
  skill_load_approval_risk_level: str
  canary_tokens: tuple[str, ...]

  @classmethod
  def load(cls, path: str | Path) -> "Mission":
    raw = yaml.safe_load(Path(path).read_text(encoding="utf-8"))["mission"]
    return cls(
        id=raw["id"],
        version=int(raw["version"]),
        purpose=raw["purpose"].strip(),
        default_tool_classes=frozenset(raw.get("default_tool_classes", [])),
        forbidden_tool_classes=frozenset(raw.get("forbidden_tool_classes", [])),
        requires_approval_classes=frozenset(
            raw.get("requires_approval_classes", [])
        ),
        allowed_recipient_domains=frozenset(
            raw.get("allowed_recipient_domains", [])
        ),
        skill_load_approval_risk_level=raw.get(
            "skill_load_approval_risk_level", "high"
        ),
        canary_tokens=tuple(raw.get("canary_tokens", [])),
    )


@dataclass(frozen=True)
class SkillPolicy:
  """Security-relevant part of a SKILL.md frontmatter.

  ADK 2.11 parses ``allowed-tools`` but treats it as "pre-approved", not as a
  restriction, so enforcement happens here. Extra constraints live under
  ``metadata.security``.
  """

  name: str
  version: str
  sha256: str
  risk_level: str
  allowed_tools: frozenset[str]
  allowed_tool_classes: frozenset[str]
  forbidden_tool_classes: frozenset[str]
  requires_approval_tools: frozenset[str]
  allowed_recipient_domains: frozenset[str]
  success_criteria: tuple[str, ...]

  @classmethod
  def from_skill(cls, skill: Any, skill_md: str) -> "SkillPolicy":
    fm = skill.frontmatter
    meta = fm.metadata or {}
    sec = meta.get("security", {}) or {}
    allowed = set((fm.allowed_tools or "").split())
    allowed |= set(meta.get("adk_additional_tools", []))
    return cls(
        name=fm.name,
        version=str(meta.get("version", "0")),
        sha256=hashlib.sha256(skill_md.encode("utf-8")).hexdigest(),
        risk_level=sec.get("risk_level", "medium"),
        allowed_tools=frozenset(allowed),
        allowed_tool_classes=frozenset(sec.get("allowed_tool_classes", [])),
        forbidden_tool_classes=frozenset(sec.get("forbidden_tool_classes", [])),
        requires_approval_tools=frozenset(sec.get("requires_approval", [])),
        allowed_recipient_domains=frozenset(
            sec.get("allowed_recipient_domains", [])
        ),
        success_criteria=tuple(meta.get("success_criteria", [])),
    )


@dataclass
class SecurityState:
  """Per-session security view, persisted in ADK session state."""

  untrusted_seen: bool = False
  untrusted_sources: list[str] = field(default_factory=list)
  sensitive_read: bool = False
  active_skills: list[str] = field(default_factory=list)


@dataclass(frozen=True)
class Decision:
  action: Action
  rule: str
  detail: str = ""


def _risk_at_least(level: str, threshold: str) -> bool:
  return RISK_LEVELS.index(level) >= RISK_LEVELS.index(threshold)


class PolicyEngine:

  def __init__(self, mission: Mission, skills: dict[str, SkillPolicy],
               tool_classes: dict[str, str]):
    self.mission = mission
    self.skills = skills
    self.tool_classes = {**BUILTIN_TOOL_CLASSES, **tool_classes}

  def classify(self, tool_name: str) -> str:
    # Unknown tools (e.g. a new MCP tool) are treated as the most dangerous
    # class that is still approvable, never silently as "read".
    return self.tool_classes.get(tool_name, "unknown")

  def evaluate(self, tool_name: str, args: dict[str, Any],
               state: SecurityState, findings: Findings) -> Decision:
    m = self.mission
    risk = self.classify(tool_name)
    outbound = risk in OUTBOUND_CLASSES

    # 1. Mission hard limits: no skill can lift these.
    if risk in m.forbidden_tool_classes:
      return Decision("deny", "mission.forbidden_class", risk)
    if findings.canary_hit:
      return Decision("deny", "detect.canary_in_args",
                      ",".join(findings.canary_hit))
    if outbound and findings.secret_in_args:
      return Decision("deny", "detect.secret_in_outbound_args")
    if (outbound and state.untrusted_seen and state.sensitive_read):
      return Decision("deny", "taint.exfiltration_pattern",
                      "untrusted input + sensitive read + outbound send")

    # 2. Skill catalogue tools.
    if risk in ("skill_meta", "control"):
      if tool_name == "load_skill":
        return self._evaluate_skill_load(args.get("skill_name"), state)
      return Decision("allow", "builtin.skill_meta")

    # 3. Scope: active skills, otherwise the mission default.
    active = [self.skills[s] for s in state.active_skills if s in self.skills]
    if active:
      for sp in active:
        if risk in sp.forbidden_tool_classes:
          return Decision("deny", "skill.forbidden_class", f"{sp.name}:{risk}")
      in_scope = any(
          tool_name in sp.allowed_tools or risk in sp.allowed_tool_classes
          for sp in active)
      if not in_scope:
        return Decision("deny", "skill.out_of_scope",
                        f"{tool_name} not granted by {[s.name for s in active]}")
    elif risk not in m.default_tool_classes:
      return Decision("deny", "mission.no_skill_scope",
                      f"{tool_name}({risk}) needs an active skill")

    # 4. Outbound destinations.
    if outbound:
      allowed = set(m.allowed_recipient_domains)
      for sp in active:
        allowed |= sp.allowed_recipient_domains
      bad = [d for d in findings.domains if not _domain_allowed(d, allowed)]
      if bad:
        return Decision("deny", "mission.recipient_domain_not_allowed",
                        ",".join(sorted(bad)))

    # 5. Human approval gates.
    if risk == "unknown":
      return Decision("needs_approval", "policy.unknown_tool", tool_name)
    if risk in m.requires_approval_classes:
      return Decision("needs_approval", "mission.requires_approval", risk)
    if any(tool_name in sp.requires_approval_tools for sp in active):
      return Decision("needs_approval", "skill.requires_approval", tool_name)
    if state.untrusted_seen and (outbound or risk in ("destructive",
                                                      "code_exec")):
      return Decision("needs_approval", "taint.risky_after_untrusted", risk)

    return Decision("allow", "policy.in_scope")

  def _evaluate_skill_load(self, skill_name: str | None,
                           state: SecurityState) -> Decision:
    if not skill_name or skill_name not in self.skills:
      return Decision("deny", "skill.unknown", str(skill_name))
    sp = self.skills[skill_name]
    if state.untrusted_seen and _risk_at_least(
        sp.risk_level, self.mission.skill_load_approval_risk_level):
      return Decision("needs_approval", "skill.load_after_untrusted",
                      f"{skill_name}({sp.risk_level})")
    return Decision("allow", "skill.load")


def _domain_allowed(domain: str, allowed: set[str]) -> bool:
  return any(domain == a or domain.endswith("." + a) for a in allowed)
