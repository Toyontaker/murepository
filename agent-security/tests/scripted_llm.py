"""A deterministic stand-in for the model, for offline end-to-end tests.

It replays a fixed script of model turns, so a test can make the agent
"behave as if compromised" and check that the security plugin, not the
model, is what stops the attack.
"""

from __future__ import annotations

from typing import Any, AsyncGenerator

from google.adk.models.base_llm import BaseLlm
from google.adk.models.llm_request import LlmRequest
from google.adk.models.llm_response import LlmResponse
from google.genai import types
from pydantic import PrivateAttr


def call(name: str, **args: Any) -> types.Part:
  return types.Part(function_call=types.FunctionCall(name=name, args=args))


def text(t: str) -> types.Part:
  return types.Part(text=t)


class ScriptedLlm(BaseLlm):
  model: str = "scripted"
  _turns: list[list[types.Part]] = PrivateAttr(default_factory=list)
  requests: list[LlmRequest] = []

  def __init__(self, turns: list[list[types.Part] | types.Part], **kw):
    super().__init__(**kw)
    self._turns = [t if isinstance(t, list) else [t] for t in turns]
    self.requests = []

  async def generate_content_async(
      self, llm_request: LlmRequest,
      stream: bool = False) -> AsyncGenerator[LlmResponse, None]:
    self.requests.append(llm_request)
    parts = self._turns.pop(0) if self._turns else [text("(script exhausted)")]
    yield LlmResponse(content=types.Content(role="model", parts=parts))
