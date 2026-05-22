"""
Manually inspect a trained Isolation Forest model.

Loads the .pkl, processes a test log file through the same pipeline as the worker,
and prints anomaly scores for each line. Useful for sanity-checking the model
before integrating it into ml-worker.

Usage:
    python inspect_model.py --model ../ml-worker/models/isolation_forest.pkl \
                            --input datasets/Windows_2k.log \
                            --top 20
"""
import argparse
import logging
import re
import sys
from pathlib import Path

import joblib
from drain3 import TemplateMiner
from drain3.template_miner_config import TemplateMinerConfig

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "ml-worker" / "src"))
from feature_extractor import FeatureExtractor  # noqa: E402

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("inspect")

CBS_LINE_RE = re.compile(
    r"^(\d{4}-\d{2}-\d{2})\s+(\d{2}:\d{2}:\d{2}),\s+(\S+)\s+(\S+)\s+(.*)$"
)


def parse_line(line: str, line_id: int) -> dict | None:
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


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", required=True, type=Path)
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--top", type=int, default=20, help="How many most anomalous lines to print")
    parser.add_argument("--limit", type=int, default=None, help="Cap on number of lines to process")
    args = parser.parse_args()

    if not args.model.exists():
        logger.error("Model not found: %s", args.model)
        sys.exit(1)

    logger.info("Loading model from %s", args.model)
    model = joblib.load(args.model)

    drain = TemplateMiner(config=TemplateMinerConfig())
    extractor = FeatureExtractor()

    entries: list[dict] = []
    features: list[list[float]] = []

    with args.input.open("r", encoding="utf-8", errors="replace") as f:
        for line_id, raw in enumerate(f, start=1):
            if args.limit and line_id > args.limit:
                break
            entry = parse_line(raw.rstrip("\n\r"), line_id)
            if entry is None:
                continue

            template_id = f"T{drain.add_log_message(entry['content'])['cluster_id']}"
            extractor.update_template_count(template_id)
            vec = extractor.extract(entry, template_id)

            entries.append(entry)
            features.append(vec.to_list())

    logger.info("Scoring %d entries", len(features))
    # score_samples returns higher = more normal. Invert so higher = more anomalous.
    raw_scores = model.score_samples(features)
    anomaly_scores = [-s for s in raw_scores]

    # Top-N most anomalous
    scored = list(zip(entries, anomaly_scores))
    scored.sort(key=lambda x: x[1], reverse=True)

    logger.info("Top %d most anomalous lines:", args.top)
    for entry, score in scored[: args.top]:
        content = entry["content"][:120] + ("..." if len(entry["content"]) > 120 else "")
        print(f"  score={score:.4f}  line={entry['lineId']:5d}  comp={entry['component']:5s}  {content}")

    # Stats
    scores_sorted = sorted(anomaly_scores)
    n = len(scores_sorted)
    print()
    logger.info("Score distribution:")
    logger.info("  min:    %.4f", scores_sorted[0])
    logger.info("  median: %.4f", scores_sorted[n // 2])
    logger.info("  p95:    %.4f", scores_sorted[int(n * 0.95)])
    logger.info("  p99:    %.4f", scores_sorted[int(n * 0.99)])
    logger.info("  max:    %.4f", scores_sorted[-1])


if __name__ == "__main__":
    main()