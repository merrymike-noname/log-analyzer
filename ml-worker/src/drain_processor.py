"""Drain3 template mining wrapper.

Per-job instance: a fresh Drain tree is built for each job, ensuring template_ids
are stable within one analysis but isolated between jobs.
"""
import logging

from drain3 import TemplateMiner
from drain3.template_miner_config import TemplateMinerConfig

logger = logging.getLogger(__name__)


class DrainProcessor:
    """Wraps a single Drain3 TemplateMiner instance."""

    def __init__(self):
        cfg = TemplateMinerConfig()
        # using defaults
        self._miner = TemplateMiner(config=cfg)

    def process(self, content: str) -> tuple[str, str]:
        """
        Feeds a log message to Drain and returns (templateId, template).
        templateId is formatted as 'T<n>' for nicer presentation.
        """
        result = self._miner.add_log_message(content)
        cluster_id = result["cluster_id"]
        template = result["template_mined"]
        return f"T{cluster_id}", template