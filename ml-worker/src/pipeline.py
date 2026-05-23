"""Streaming pipeline: parsed.jsonl -> analyzed.jsonl."""
import json
import logging
from collections import Counter
from pathlib import Path

from sklearn.ensemble import IsolationForest

from config import config
from drain_processor import DrainProcessor
from feature_extractor import FeatureExtractor
from severity_calculator import compute_severity

logger = logging.getLogger(__name__)


def analyze_file(job_id: str, parsed_path: str, model: IsolationForest) -> int:
    parsed = Path(parsed_path)
    if not parsed.exists():
        raise FileNotFoundError(f"Parsed file not found: {parsed_path}")

    analyzed = Path(config.DATA_DIR) / job_id / "analyzed.jsonl"
    analyzed.parent.mkdir(parents=True, exist_ok=True)

    drain = DrainProcessor()
    extractor = FeatureExtractor()

    entries: list[dict] = []
    features: list = []          # FeatureVector objects, kept for severity calc
    feature_lists: list[list[float]] = []   # numeric inputs for the model

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
            features.append(feature_vec)
            feature_lists.append(feature_vec.to_list())

    if not entries:
        logger.warning("No entries to analyze for job %s", job_id)
        return 0

    raw_scores = model.score_samples(feature_lists)
    anomaly_scores = [float(-s) for s in raw_scores]

    severity_counts: Counter[str] = Counter()

    with analyzed.open("w", encoding="utf-8") as fout:
        for entry, feature_vec, score in zip(entries, features, anomaly_scores):
            severity = compute_severity(score, feature_vec)
            severity_counts[severity] += 1

            entry["anomalyScore"] = round(score, 4)
            entry["severity"] = severity
            fout.write(json.dumps(entry))
            fout.write("\n")

    logger.info("Analyzed %d entries for job %s — severity breakdown: %s",
                len(entries), job_id, dict(severity_counts))
    return len(entries)