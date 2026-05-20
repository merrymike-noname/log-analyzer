"""ML worker entrypoint."""
import logging
import signal
import sys

from config import config
from messages import AnalyzeTaskMessage, JobResultMessage
from pipeline import analyze_file
from rabbit_client import RabbitClient

logging.basicConfig(
    level=config.LOG_LEVEL,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
logging.getLogger("pika").setLevel(logging.WARNING)
logger = logging.getLogger("ml-worker")


def handle_analyze_task(task: AnalyzeTaskMessage) -> JobResultMessage:
    try:
        line_count = analyze_file(task.jobId, task.parsedPath)
        return JobResultMessage(jobId=task.jobId, status="DONE", lineCount=line_count)
    except Exception as e:
        logger.exception("Failed to analyze job %s", task.jobId)
        return JobResultMessage(jobId=task.jobId, status="FAILED", errorMessage=str(e))


def main() -> int:
    client = RabbitClient()

    def shutdown(signum, frame):
        logger.info("Received signal %s, shutting down", signum)
        client.close()
        sys.exit(0)

    signal.signal(signal.SIGTERM, shutdown)
    signal.signal(signal.SIGINT, shutdown)

    try:
        client.connect_with_retry()
        client.consume_analyze_tasks(handle_analyze_task)
    except Exception:
        logger.exception("Worker crashed")
        client.close()
        return 1

    return 0


if __name__ == "__main__":
    sys.exit(main())