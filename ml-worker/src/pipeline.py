"""Streaming pipeline: parsed.jsonl -> analyzed.jsonl."""
import json
import logging
import os
from pathlib import Path

from config import config

logger = logging.getLogger(__name__)

def analyze_file(job_id: str, parsed_path: str) -> int:
    """
    Read parsed.jsonl line by line, enrich each entry, write to analyzed.jsonl.
    Returns number of analyzed entries.

    Step 2 stub: enriches with placeholder ML fields. Real Drain3 + IsolationForest
    will be added in later steps.
    """
    parsed = Path(parsed_path)
    if not parsed.exists():
        raise FileNotFoundError(f"Parsed file not found: {parsed_path}")

    analyzed = Path(config.DATA_DIR) / job_id / "analyzed.jsonl"
    analyzed.parent.mkdir(parents=True, exist_ok=True)

    count = 0
    with parsed.open("r", encoding="utf-8") as fin, analyzed.open("w", encoding="utf-8") as fout:
        for line in fin:
            line = line.strip()
            if not line:
                continue

            entry = json.loads(line)
            enriched = _enrich(entry)
            fout.write(json.dumps(enriched))
            fout.write("\n")
            count += 1

    logger.info("Analyzed %d entries for job %s -> %s", count, job_id, analyzed)
    return count


def _enrich(entry: dict) -> dict:
    """Adds placeholder ML fields. Will be replaced with real logic in later steps."""
    entry["templateId"] = None
    entry["template"] = None
    entry["anomalyScore"] = None
    entry["severity"] = "LOW"
    return entry