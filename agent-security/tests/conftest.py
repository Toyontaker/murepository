import warnings

import pytest
from opentelemetry import trace
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import SimpleSpanProcessor
from opentelemetry.sdk.trace.export.in_memory_span_exporter import \
    InMemorySpanExporter

from secagent import tools

warnings.filterwarnings("ignore", category=UserWarning)

_EXPORTER = InMemorySpanExporter()
_provider = TracerProvider()
_provider.add_span_processor(SimpleSpanProcessor(_EXPORTER))
trace.set_tracer_provider(_provider)


@pytest.fixture
def spans():
  _EXPORTER.clear()
  yield _EXPORTER
  _EXPORTER.clear()


@pytest.fixture(autouse=True)
def _reset_side_effects():
  tools.SIDE_EFFECTS.clear()
  yield
  tools.SIDE_EFFECTS.clear()
