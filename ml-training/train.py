"""
Train Isolation Forest on Windows CBS logs.

Pipeline:
  raw .log file -> CBS parser -> Drain3 -> feature extraction -> IsolationForest -> .pkl

Usage:
    python train.py --input datasets/Windows.log --output ../ml-worker/models/isolation_forest.pkl
"""
import argparse
import json
import logging
import re
import sys
from datetime import datetime
from pathlib import Path

import joblib
from drain3 import TemplateMiner
from drain3.template_miner_config import TemplateMinerConfig
from sklearn.ensemble import IsolationForest
from tqdm import tqdm

# Import feature extractor from ml-worker — single source of truth for features.
ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "ml-worker" / "src"))
from feature_extractor import FeatureExtractor  # noqa: E402

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("train")


CBS_LINE_RE = re.compile(
    r"^(\d{4}-\d{2}-\d{2})\s+(\d{2}:\d{2}:\d{2}),\s+(\S+)\s+(\S+)\s+(.*)$"
)


def parse_line(line: str, line_id: int) -> dict | None:
    """CBS log line -> structured dict. Mirrors backend's CbsLogParser."""
    match = CBS_LINE_RE.match(line)
    if not match:
        return None
    date, time, level, component, content = match.groups()
    return {
        "lineId": line_id,
        "timestamp": f"{date}T{time}",
        "level": level,
        "component": component,
        "content": content,
    }


def extract_features(log_path: Path, limit: int | None = None) -> list[list[float]]:
    """Streams the log file, runs Drain3 + feature extraction, returns feature matrix."""
    drain = TemplateMiner(config=TemplateMinerConfig())
    extractor = FeatureExtractor()
    features: list[list[float]] = []

    skipped = 0
    with log_path.open("r", encoding="utf-8", errors="replace") as f:
        progress = tqdm(f, desc="Processing", unit=" lines")
        for line_id, raw in enumerate(progress, start=1):
            if limit and line_id > limit:
                break

            entry = parse_line(raw.rstrip("\n\r"), line_id)
            if entry is None:
                skipped += 1
                continue

            content = entry["content"]
            result = drain.add_log_message(content)
            template_id = f"T{result['cluster_id']}"

            extractor.update_template_count(template_id)
            feature_vector = extractor.extract(entry, template_id)
            features.append(feature_vector.to_list())

    logger.info("Parsed %d entries (skipped %d), %d unique templates",
                len(features), skipped, len(drain.drain.clusters))
    return features


def train(features: list[list[float]], contamination: float) -> IsolationForest:
    logger.info("Training IsolationForest on %d samples (contamination=%.3f)",
                len(features), contamination)
    model = IsolationForest(
        n_estimators=100,
        contamination=contamination,
        random_state=42,
        n_jobs=-1,
    )
    model.fit(features)
    logger.info("Training complete")
    return model


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True, type=Path, help="Path to raw Windows.log")
    parser.add_argument("--output", required=True, type=Path, help="Where to save the .pkl model")
    parser.add_argument("--limit", type=int, default=None, help="Optional cap on number of lines")
    parser.add_argument("--contamination", type=float, default=0.01, help="Expected ratio of anomalies (default: 0.01)")
    args = parser.parse_args()

    if not args.input.exists():
        logger.error("Input file not found: %s", args.input)
        sys.exit(1)

    features = extract_features(args.input, limit=args.limit)
    if not features:
        logger.error("No features extracted — is the input a valid CBS log?")
        sys.exit(1)

    model = train(features, args.contamination)

    args.output.parent.mkdir(parents=True, exist_ok=True)
    joblib.dump(model, args.output)
    size_kb = args.output.stat().st_size / 1024
    logger.info("Model saved to %s (%.1f KB)", args.output, size_kb)


if __name__ == "__main__":
    main()