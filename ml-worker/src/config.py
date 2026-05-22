"""Configuration loaded from environment variables."""
import os


def _required(key: str) -> str:
    value = os.getenv(key)
    if not value:
        raise RuntimeError(f"Required environment variable '{key}' is not set")
    return value


class Config:
    RABBIT_HOST: str = _required("RABBIT_HOST")
    RABBIT_PORT: int = int(_required("RABBIT_PORT"))
    RABBIT_USER: str = _required("RABBIT_USER")
    RABBIT_PASS: str = _required("RABBIT_PASS")

    DATA_DIR: str = os.getenv("DATA_DIR", "/data/jobs")
    LOG_LEVEL: str = os.getenv("LOG_LEVEL", "INFO")
    MODEL_PATH: str = os.getenv("MODEL_PATH", "/app/models/isolation_forest.pkl")

    # Topology constants (must match backend's RabbitQueues.java)
    ANALYZE_QUEUE: str = "analyze.queue"
    RESULTS_EXCHANGE: str = "results.exchange"
    RESULTS_ROUTING_KEY: str = "result"


config = Config()