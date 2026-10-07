"""Entry point for `adk web adk_agents` / `adk run adk_agents/backoffice_agent`.

ADK's loader picks up a module-level `app` (with plugins) before
`root_agent`, so the security plugin is active in the dev UI too.
"""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "src"))

from secagent.agent import build_app  # noqa: E402
from secagent.telemetry import setup_tracing  # noqa: E402

setup_tracing()
# adk web expects the app name to match this directory name.
app = build_app(name="backoffice_agent")
