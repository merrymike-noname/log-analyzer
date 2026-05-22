"""Loads the trained Isolation Forest model from disk.

Fail-fast: if the model is missing or unreadable, the worker exits.
"""
import logging
from pathlib import Path

import joblib
from sklearn.ensemble import IsolationForest

from config import config

logger = logging.getLogger(__name__)


def load_model() -> IsolationForest:
    model_path = Path(config.MODEL_PATH)
    if not model_path.exists():
        raise FileNotFoundError(f"Model file not found at {model_path}")

    logger.info("Loading model from %s", model_path)
    model = joblib.load(model_path)
    logger.info("Model loaded: %s", type(model).__name__)
    return model