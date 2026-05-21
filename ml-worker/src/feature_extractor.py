"""Feature extraction for log entries.

Converts a parsed/templated log entry into a numeric feature vector suitable
for unsupervised anomaly detection (Isolation Forest).
"""
import re
from dataclasses import dataclass
from datetime import datetime
from typing import Optional


HRESULT_RE = re.compile(r"0x[0-9A-Fa-f]{8}")
ERROR_KEYWORDS = ("failed", "error", "exception", "fatal")
WARNING_KEYWORDS = ("warning", "warn")

# Components seen in CBS logs; extendable
COMPONENT_ENCODING = {"CBS": 0, "CSI": 1}
UNKNOWN_COMPONENT_CODE = -1


@dataclass
class FeatureVector:
    template_frequency: int
    content_length: int
    has_hresult: int
    has_error_keyword: int
    has_warning_keyword: int
    component_encoded: int
    time_since_previous: float

    def to_list(self) -> list[float]:
        """Order matches model training input."""
        return [
            float(self.template_frequency),
            float(self.content_length),
            float(self.has_hresult),
            float(self.has_error_keyword),
            float(self.has_warning_keyword),
            float(self.component_encoded),
            self.time_since_previous,
        ]


class FeatureExtractor:
    """
    Stateful extractor — tracks template frequencies and previous timestamp
    across a single file. Reset by creating a new instance per job.
    """

    def __init__(self):
        self._template_counts: dict[str, int] = {}
        self._prev_ts: Optional[datetime] = None

    def update_template_count(self, template_id: str) -> int:
        """Call this for every entry to track frequencies. Returns new count."""
        count = self._template_counts.get(template_id, 0) + 1
        self._template_counts[template_id] = count
        return count

    def extract(self, entry: dict, template_id: str) -> FeatureVector:
        content = entry.get("content") or ""
        content_lower = content.lower()

        return FeatureVector(
            template_frequency=self._template_counts.get(template_id, 0),
            content_length=len(content),
            has_hresult=1 if HRESULT_RE.search(content) else 0,
            has_error_keyword=1 if any(k in content_lower for k in ERROR_KEYWORDS) else 0,
            has_warning_keyword=1 if any(k in content_lower for k in WARNING_KEYWORDS) else 0,
            component_encoded=COMPONENT_ENCODING.get(entry.get("component"), UNKNOWN_COMPONENT_CODE),
            time_since_previous=self._time_delta(entry.get("timestamp")),
        )

    def _time_delta(self, timestamp_str: Optional[str]) -> float:
        if not timestamp_str:
            return 0.0
        try:
            ts = datetime.fromisoformat(timestamp_str)
        except ValueError:
            return 0.0

        if self._prev_ts is None:
            self._prev_ts = ts
            return 0.0

        delta = (ts - self._prev_ts).total_seconds()
        self._prev_ts = ts
        return max(delta, 0.0)