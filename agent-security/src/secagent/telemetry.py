"""OTel setup and the ``sec.*`` attribute vocabulary.

ADK already emits ``invoke_agent`` / ``generate_content`` / ``execute_tool``
spans with ``gen_ai.*`` attributes. The security plugin adds ``sec.*``
attributes to those same spans (it runs inside the ``execute_tool`` span),
so the blue team gets one span per tool call carrying both.
"""

from __future__ import annotations

import os

from opentelemetry.sdk.trace.export import (ConsoleSpanExporter,
                                            SimpleSpanProcessor)

# --- attribute names (keep in sync with README and otel/collector.yaml) ---
MISSION_ID = "sec.mission.id"
MISSION_VERSION = "sec.mission.version"
GOAL_ID = "sec.goal.id"
GOAL_HASH = "sec.goal.hash"
TOOL_RISK = "sec.tool.risk_class"
TOOL_SOURCE = "sec.tool.source"
TOOL_ARGS_HASH = "sec.tool.args_hash"
TOOL_DESC_HASH = "sec.tool.description_hash"
RESULT_SIZE = "sec.tool.result.size"
RESULT_INJECTION = "sec.tool.result.injection_score"
RESULT_CANARY = "sec.tool.result.canary"
TAINT_LEVEL = "sec.taint.level"
TAINT_SOURCES = "sec.taint.sources"
TAINT_SENSITIVE = "sec.taint.sensitive_read"
POLICY_DECISION = "sec.policy.decision"
POLICY_RULE = "sec.policy.rule"
POLICY_DETAIL = "sec.policy.detail"
SKILL_ACTIVE = "sec.skill.active"
SKILL_VERSION = "sec.skill.version"
SKILL_HASH = "sec.skill.sha256"
SKILL_RISK = "sec.skill.risk_level"
SKILL_AFTER_UNTRUSTED = "sec.skill.loaded_after_untrusted"
PLAN_VERSION = "sec.plan.version"
PLAN_HASH = "sec.plan.hash"
PLAN_AFTER_UNTRUSTED = "sec.plan.changed_after_untrusted"
TEST_ATTACK_ID = "sec.test.attack_id"


def setup_tracing(console: bool | None = None) -> None:
  """Configures OTel via ADK's helper.

  OTLP export is driven by the standard ``OTEL_EXPORTER_OTLP_*`` env vars.
  Set ``SECAGENT_CONSOLE_SPANS=1`` to also print spans locally.
  """
  from google.adk.telemetry.setup import OTelHooks, maybe_set_otel_providers

  if console is None:
    console = os.environ.get("SECAGENT_CONSOLE_SPANS") == "1"
  hooks = []
  if console:
    hooks.append(
        OTelHooks(span_processors=[SimpleSpanProcessor(ConsoleSpanExporter())]))
  maybe_set_otel_providers(hooks)
