"""ML worker entrypoint."""
import logging
import signal
import sys

from config import config
from messages import AnalyzeTaskMessage, JobResultMessage
from rabbit_client import RabbitClient

logging.basicConfig(
    level=config.LOG_LEVEL,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
logger = logging.getLogger("ml-worker")


def handle_analyze_task(task: AnalyzeTaskMessage) -> JobResultMessage:
    """Step 1 stub: just acknowledge the task as DONE without any processing."""
    logger.info("Stub handler: pretending to analyze %s", task.parsedPath)
    return JobResultMessage(jobId=task.jobId, status="DONE", lineCount=None)


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