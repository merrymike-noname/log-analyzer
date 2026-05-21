"""Streaming pipeline: parsed.jsonl -> analyzed.jsonl."""
import json
import logging
from pathlib import Path

from config import config
from drain_processor import DrainProcessor
from feature_extractor import FeatureExtractor

logger = logging.getLogger(__name__)


def analyze_file(job_id: str, parsed_path: str) -> int:
    """
    Read parsed.jsonl, run Drain3 + feature extraction, write analyzed.jsonl.
    Returns number of analyzed entries.

    Step 4: features are extracted but not yet used for scoring.
    Anomaly detection will be wired in steps 5-6.
    """
    parsed = Path(parsed_path)
    if not parsed.exists():
        raise FileNotFoundError(f"Parsed file not found: {parsed_path}")

    analyzed = Path(config.DATA_DIR) / job_id / "analyzed.jsonl"
    analyzed.parent.mkdir(parents=True, exist_ok=True)

    drain = DrainProcessor()
    extractor = FeatureExtractor()
    count = 0

    with parsed.open("r", encoding="utf-8") as fin, analyzed.open("w", encoding="utf-8") as fout:
        for line in fin:
            line = line.strip()
            if not line:
                continue

            entry = json.loads(line)
            content = entry.get("content") or ""
            template_id, template = drain.process(content)
            extractor.update_template_count(template_id)

            features = extractor.extract(entry, template_id)

            entry["templateId"] = template_id
            entry["template"] = template
            entry["anomalyScore"] = None
            entry["severity"] = "LOW"

            # Log features for first few entries — useful for debugging
            if count < 10:
                logger.debug("Job %s entry %d features: %s",
                             job_id, entry.get("lineId"), features.to_list())

            fout.write(json.dumps(entry))
            fout.write("\n")
            count += 1

    logger.info("Analyzed %d entries for job %s", count, job_id)
    return count