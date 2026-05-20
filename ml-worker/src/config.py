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

    DATA_DIR: str = os.getenv("DATA_DIR", "/data/jobs")  # це не секрет, дефолт ок

    ANALYZE_QUEUE: str = "analyze.queue"
    RESULTS_EXCHANGE: str = "results.exchange"
    RESULTS_ROUTING_KEY: str = "result"

    LOG_LEVEL: str = os.getenv("LOG_LEVEL", "INFO")


config = Config()