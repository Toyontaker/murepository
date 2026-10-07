"""Runner-wide security plugin.

Responsibilities, all inline and deterministic:
  * snapshot the task goal from the user turn (before any untrusted input),
  * track taint (untrusted input read, sensitive data read) in session state,
  * track which Skills are active and enforce their declared scope,
  * evaluate the mission/skill policy on every tool call and block or ask
    for human approval,
  * stamp ``sec.*`` attributes on ADK's own spans for the blue team.

Verified against google-adk 2.11.0 ``BasePlugin`` hook signatures.
"""

from __future__ import annotations

import hashlib
import json
import logging
from typing import Any, Callable, Optional

from google.adk.agents.callback_context import CallbackContext
from google.adk.agents.invocation_context import InvocationContext
from google.adk.events.event import Event
from google.adk.models.llm_request import LlmRequest
from google.adk.models.llm_response import LlmResponse
from google.adk.plugins.base_plugin import BasePlugin
from google.adk.tools.base_tool import BaseTool
from google.adk.tools.mcp_tool import McpTool
from google.adk.tools.tool_context import ToolContext
from google.genai import types
from opentelemetry import trace

from . import telemetry as T
from .detectors import Detectors
from .policy import PolicyEngine, SecurityState

logger = logging.getLogger(__name__)

# Session-state keys (session scope: taint must survive across turns).
_K_STATE = "sec_state"
_K_GOAL = "sec_goal"
_K_PLAN_VERSION = "sec_plan_version"
_K_ATTACK_ID = "sec_test_attack_id"
# Output key the planner agent writes its plan to.
PLAN_STATE_KEY = "plan"

GoalExtractor = Callable[[str], dict[str, Any]]


def _h(obj: Any) -> str:
  data = obj if isinstance(obj, str) else json.dumps(
      obj, sort_keys=True, ensure_ascii=False, default=str)
  return hashlib.sha256(data.encode("utf-8")).hexdigest()[:16]


def _load_state(state: Any) -> SecurityState:
  raw = state.get(_K_STATE) or {}
  return SecurityState(**raw)


def _save_state(state: Any, s: SecurityState) -> None:
  # Assign a fresh dict so ADK records the state delta.
  state[_K_STATE] = {
      "untrusted_seen": s.untrusted_seen,
      "untrusted_sources": list(s.untrusted_sources),
      "sensitive_read": s.sensitive_read,
      "active_skills": list(s.active_skills),
  }


class SecurityPlugin(BasePlugin):

  def __init__(
      self,
      engine: PolicyEngine,
      detectors: Detectors,
      untrusted_source_tools: set[str],
      sensitive_tools: set[str],
      goal_extractor: Optional[GoalExtractor] = None,
  ):
    super().__init__(name="security")
    self.engine = engine
    self.detectors = detectors
    self.untrusted_source_tools = untrusted_source_tools
    self.sensitive_tools = sensitive_tools
    self.goal_extractor = goal_extractor
    # Goals captured in on_user_message_callback, keyed by invocation id,
    # waiting to be written through a state-delta-recording context.
    self._pending_goals: dict[str, dict[str, Any]] = {}

  # ---- L2: goal snapshot -------------------------------------------------

  async def on_user_message_callback(
      self, *, invocation_context: InvocationContext,
      user_message: types.Content) -> Optional[types.Content]:
    text = "".join(p.text or "" for p in (user_message.parts or []))
    goal = (self.goal_extractor(text) if self.goal_extractor else {
        "objective": text[:2000]
    })
    goal_hash = _h(goal)
    goal_record = {
        "id": f"{invocation_context.invocation_id}",
        "hash": goal_hash,
        "source": "user_turn",
        "goal": goal,
    }
    # Taken before any tool output is read in this turn, so it can only come
    # from the user. Writing session.state directly here is not persisted,
    # so it is flushed in before_agent_callback.
    self._pending_goals[invocation_context.invocation_id] = goal_record
    span = trace.get_current_span()
    span.set_attributes({
        T.MISSION_ID: self.engine.mission.id,
        T.MISSION_VERSION: self.engine.mission.version,
        T.GOAL_ID: goal_record["id"],
        T.GOAL_HASH: goal_hash,
    })
    return None

  async def before_agent_callback(
      self, *, agent: Any,
      callback_context: CallbackContext) -> Optional[types.Content]:
    goal = self._pending_goals.pop(callback_context.invocation_id, None)
    if goal is not None:
      callback_context.state[_K_GOAL] = goal
    return None

  # ---- context taint visible on every model call -------------------------

  async def before_model_callback(
      self, *, callback_context: CallbackContext,
      llm_request: LlmRequest) -> Optional[LlmResponse]:
    s = _load_state(callback_context.state)
    trace.get_current_span().set_attributes({
        T.TAINT_LEVEL: "untrusted" if s.untrusted_seen else "trusted",
        T.TAINT_SOURCES: s.untrusted_sources,
        T.SKILL_ACTIVE: s.active_skills,
    })
    return None

  # ---- L3: per-action policy --------------------------------------------

  async def before_tool_callback(
      self, *, tool: BaseTool, tool_args: dict[str, Any],
      tool_context: ToolContext) -> Optional[dict]:
    span = trace.get_current_span()
    s = _load_state(tool_context.state)
    risk = self.engine.classify(tool.name)
    findings = self.detectors.scan_args(tool_args)

    span.set_attributes({
        T.MISSION_ID: self.engine.mission.id,
        T.TOOL_RISK: risk,
        T.TOOL_SOURCE: "mcp" if isinstance(tool, McpTool) else "local",
        T.TOOL_ARGS_HASH: _h(tool_args),
        T.TOOL_DESC_HASH: _h(tool.description or ""),
        T.TAINT_LEVEL: "untrusted" if s.untrusted_seen else "trusted",
        T.TAINT_SOURCES: s.untrusted_sources,
        T.TAINT_SENSITIVE: s.sensitive_read,
        T.SKILL_ACTIVE: s.active_skills,
        **findings.as_attributes(),
    })
    goal = tool_context.state.get(_K_GOAL) or {}
    if goal:
      span.set_attribute(T.GOAL_HASH, goal.get("hash", ""))
    attack_id = tool_context.state.get(_K_ATTACK_ID)
    if attack_id:
      span.set_attribute(T.TEST_ATTACK_ID, attack_id)

    decision = self.engine.evaluate(tool.name, tool_args, s, findings)

    if decision.action == "needs_approval":
      confirmation = tool_context.tool_confirmation
      if confirmation is None:
        tool_context.request_confirmation(
            hint=(f"[security] {tool.name} requires approval "
                  f"({decision.rule}: {decision.detail})"),
            payload={"tool": tool.name, "rule": decision.rule},
        )
        tool_context.actions.skip_summarization = True
        self._record(span, "needs_approval", decision.rule, decision.detail)
        return {"error": "This tool call requires human approval "
                         f"({decision.rule}). Waiting for confirmation."}
      if not confirmation.confirmed:
        self._record(span, "deny", "approval.rejected", decision.rule)
        return {"error": "This tool call was rejected by the approver."}
      self._record(span, "allow", "approval.granted", decision.rule)
      return None

    self._record(span, decision.action, decision.rule, decision.detail)
    if decision.action == "deny":
      span.add_event("sec.blocked", {"tool": tool.name, "rule": decision.rule})
      logger.warning("blocked %s: %s %s", tool.name, decision.rule,
                     decision.detail)
      return {"error": f"Blocked by security policy: {decision.rule}. "
                       "Do not retry this action; continue the task within "
                       "the active skill's scope or ask the user."}
    return None

  async def after_tool_callback(
      self, *, tool: BaseTool, tool_args: dict[str, Any],
      tool_context: ToolContext, result: dict) -> Optional[dict]:
    span = trace.get_current_span()
    text = json.dumps(result, ensure_ascii=False, default=str)
    if isinstance(result, dict) and "error" in result:
      span.set_attribute(T.RESULT_SIZE, len(text))
      return None

    s = _load_state(tool_context.state)
    injection = self.detectors.injection_score(text)
    span.set_attributes({
        T.RESULT_SIZE: len(text),
        T.RESULT_INJECTION: injection,
        T.RESULT_CANARY: self.detectors.contains_canary(text),
    })

    if tool.name in self.untrusted_source_tools or isinstance(tool, McpTool):
      s.untrusted_seen = True
      if tool.name not in s.untrusted_sources:
        s.untrusted_sources.append(tool.name)
    if tool.name in self.sensitive_tools:
      s.sensitive_read = True

    if tool.name == "load_skill":
      name = tool_args.get("skill_name")
      sp = self.engine.skills.get(name)
      if sp and name not in s.active_skills:
        s.active_skills.append(name)
      if sp:
        span.set_attributes({
            T.SKILL_VERSION: sp.version,
            T.SKILL_HASH: sp.sha256,
            T.SKILL_RISK: sp.risk_level,
            T.SKILL_AFTER_UNTRUSTED: s.untrusted_seen,
        })
    elif tool.name == "unload_skill":
      name = tool_args.get("skill_name")
      if name in s.active_skills:
        s.active_skills.remove(name)

    _save_state(tool_context.state, s)
    return None

  # ---- L2.5: plan tracking ----------------------------------------------

  async def on_event_callback(self, *, invocation_context: InvocationContext,
                              event: Event) -> Optional[Event]:
    delta = event.actions.state_delta if event.actions else None
    if not delta or PLAN_STATE_KEY not in delta:
      return None
    state = invocation_context.session.state
    version = int(state.get(_K_PLAN_VERSION) or 0) + 1
    s = _load_state(state)
    plan_hash = _h(delta[PLAN_STATE_KEY])
    # Recorded on the event so it lands in session state with the plan.
    delta[_K_PLAN_VERSION] = version
    attrs = {
        T.PLAN_VERSION: version,
        T.PLAN_HASH: plan_hash,
        T.PLAN_AFTER_UNTRUSTED: version > 1 and s.untrusted_seen,
    }
    span = trace.get_current_span()
    span.add_event("sec.plan.updated", attrs)
    return None

  @staticmethod
  def _record(span: trace.Span, action: str, rule: str, detail: str) -> None:
    span.set_attributes({
        T.POLICY_DECISION: action,
        T.POLICY_RULE: rule,
        T.POLICY_DETAIL: detail,
    })
