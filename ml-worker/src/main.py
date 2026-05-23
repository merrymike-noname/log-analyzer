"""ML worker entrypoint."""
import logging
import signal
import sys

from config import config
from messages import AnalyzeTaskMessage, JobResultMessage
from model_loader import load_model
from pipeline import analyze_file
from rabbit_client import RabbitClient

logging.basicConfig(
    level=config.LOG_LEVEL,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
logging.getLogger("pika").setLevel(logging.WARNING)
logger = logging.getLogger("ml-worker")


def main() -> int:
    try:
        model = load_model()
    except Exception:
        logger.exception("Failed to load model — worker cannot start")
        return 1

    def handle_analyze_task(task: AnalyzeTaskMessage, client: RabbitClient) -> JobResultMessage:
        client.publish_result(JobResultMessage(jobId=task.jobId, status="STARTED"))
        try:
            line_count = analyze_file(task.jobId, task.parsedPath, model)
            return JobResultMessage(jobId=task.jobId, status="DONE", lineCount=line_count)
        except Exception as e:
            logger.exception("Failed to analyze job %s", task.jobId)
            return JobResultMessage(jobId=task.jobId, status="FAILED", errorMessage=str(e))

    client = RabbitClient()

    def shutdown(signum, frame):
        logger.info("Received signal %s, requesting graceful stop", signum)
        client.request_stop()

    signal.signal(signal.SIGTERM, shutdown)
    signal.signal(signal.SIGINT, shutdown)

    try:
        client.connect_with_retry()
        client.consume_analyze_tasks(handle_analyze_task)
    except Exception:
        logger.exception("Worker crashed")
        return 1
    finally:
        client.close()
        logger.info("Worker shutdown complete")

    return 0


if __name__ == "__main__":
    sys.exit(main())