"""Streaming pipeline: parsed.jsonl -> analyzed.jsonl."""
import json
import logging
from pathlib import Path

from sklearn.ensemble import IsolationForest

from config import config
from drain_processor import DrainProcessor
from feature_extractor import FeatureExtractor

logger = logging.getLogger(__name__)


def analyze_file(job_id: str, parsed_path: str, model: IsolationForest) -> int:
    """
    Read parsed.jsonl, run Drain3 + features + IsolationForest, write analyzed.jsonl.
    Returns number of analyzed entries.

    Step 6: anomaly scores are computed via the trained model.
    Severity remains LOW for all entries until step 7 (severity rules).
    """
    parsed = Path(parsed_path)
    if not parsed.exists():
        raise FileNotFoundError(f"Parsed file not found: {parsed_path}")

    analyzed = Path(config.DATA_DIR) / job_id / "analyzed.jsonl"
    analyzed.parent.mkdir(parents=True, exist_ok=True)

    drain = DrainProcessor()
    extractor = FeatureExtractor()

    entries: list[dict] = []
    features: list[list[float]] = []

    # First pass: parse, extract templates and features
    with parsed.open("r", encoding="utf-8") as fin:
        for line in fin:
            line = line.strip()
            if not line:
                continue

            entry = json.loads(line)
            content = entry.get("content") or ""
            template_id, template = drain.process(content)
            extractor.update_template_count(template_id)
            feature_vec = extractor.extract(entry, template_id)

            entry["templateId"] = template_id
            entry["template"] = template
            entries.append(entry)
            features.append(feature_vec.to_list())

    if not entries:
        logger.warning("No entries to analyze for job %s", job_id)
        return 0

    # Batch scoring — much faster than per-entry calls
    raw_scores = model.score_samples(features)
    anomaly_scores = [float(-s) for s in raw_scores]

    # Second pass: enrich with scores and write
    with analyzed.open("w", encoding="utf-8") as fout:
        for entry, score in zip(entries, anomaly_scores):
            entry["anomalyScore"] = round(score, 4)
            entry["severity"] = "LOW"
            fout.write(json.dumps(entry))
            fout.write("\n")

    logger.info("Analyzed %d entries for job %s (score range: %.3f - %.3f)",
                len(entries), job_id, min(anomaly_scores), max(anomaly_scores))
    return len(entries)