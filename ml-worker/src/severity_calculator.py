"""Computes severity from anomaly score + content-based signals.

Hybrid approach: ML score is the main driver, expert rules elevate severity
for entries with strong error indicators (HRESULT codes, error keywords).
"""
from feature_extractor import FeatureVector


# Thresholds based on observed score distribution for CBS logs
CRITICAL_SCORE = 0.75
HIGH_SCORE = 0.65


def compute_severity(score: float, features: FeatureVector) -> str:
    """Returns one of CRITICAL / HIGH / MEDIUM / LOW."""

    has_hresult = features.has_hresult == 1
    has_error = features.has_error_keyword == 1

    if score >= CRITICAL_SCORE and has_hresult:
        return "CRITICAL"

    if score >= CRITICAL_SCORE:
        return "HIGH"

    if score >= HIGH_SCORE and has_hresult:
        return "HIGH"

    if score >= HIGH_SCORE or has_error:
        return "MEDIUM"

    return "LOW"