"""RabbitMQ client: consumer for analyze queue + producer for results."""
import json
import logging
import time
from typing import Callable

import pika
from pika.adapters.blocking_connection import BlockingChannel

from config import config
from messages import AnalyzeTaskMessage, JobResultMessage

logger = logging.getLogger(__name__)


class RabbitClient:
    def __init__(self):
        self._connection: pika.BlockingConnection | None = None
        self._channel: BlockingChannel | None = None

    def connect(self) -> None:
        credentials = pika.PlainCredentials(config.RABBIT_USER, config.RABBIT_PASS)
        parameters = pika.ConnectionParameters(
            host=config.RABBIT_HOST,
            port=config.RABBIT_PORT,
            credentials=credentials,
            heartbeat=60,
            blocked_connection_timeout=300,
        )
        self._connection = pika.BlockingConnection(parameters)
        self._channel = self._connection.channel()
        self._channel.basic_qos(prefetch_count=1)

        self._channel.queue_declare(
            queue=config.ANALYZE_QUEUE,
            durable=True,
            arguments={"x-dead-letter-exchange": "dlx.exchange"}
        )
        self._channel.exchange_declare(
            exchange=config.RESULTS_EXCHANGE,
            exchange_type="direct",
            durable=True
        )

        logger.info("Connected to RabbitMQ at %s:%s", config.RABBIT_HOST, config.RABBIT_PORT)

    def connect_with_retry(self, max_attempts: int = 10, initial_delay: float = 1.0) -> None:
        delay = initial_delay
        for attempt in range(1, max_attempts + 1):
            try:
                self.connect()
                return
            except (pika.exceptions.AMQPError, OSError) as e:
                if attempt == max_attempts:
                    logger.error("Failed to connect after %d attempts", max_attempts)
                    raise
                logger.warning("Connection attempt %d/%d failed (%s): %s. Retrying in %.1fs",
                               attempt, max_attempts, type(e).__name__, e, delay)
                time.sleep(delay)
                delay = min(delay * 2, 30)

    def close(self) -> None:
        if self._connection and not self._connection.is_closed:
            self._connection.close()
            logger.info("RabbitMQ connection closed")

    def consume_analyze_tasks(
            self,
            handler: Callable[[AnalyzeTaskMessage, "RabbitClient"], JobResultMessage]
    ) -> None:
        """Handler receives the task plus a reference to this client so it
        can publish intermediate status messages (e.g. STARTED)."""
        if self._channel is None:
            raise RuntimeError("Not connected")

        def _on_message(ch: BlockingChannel, method, properties, body: bytes):
            delivery_tag = method.delivery_tag
            try:
                payload = json.loads(body)
                if "jobId" not in payload and len(payload) == 1:
                    payload = next(iter(payload.values()))
                message = AnalyzeTaskMessage.from_dict(payload)
                logger.info("Received analyze task for job %s", message.jobId)

                final_result = handler(message, self)
                self.publish_result(final_result)

                ch.basic_ack(delivery_tag=delivery_tag)
                logger.info("Job %s acknowledged", message.jobId)

            except Exception as e:
                logger.exception("Failed to process message: %s", e)
                ch.basic_nack(delivery_tag=delivery_tag, requeue=False)

        self._channel.basic_consume(queue=config.ANALYZE_QUEUE, on_message_callback=_on_message)
        logger.info("Waiting for messages on queue '%s'. Ctrl+C to exit.", config.ANALYZE_QUEUE)
        self._channel.start_consuming()

    def publish_result(self, message: JobResultMessage) -> None:
        if self._channel is None:
            raise RuntimeError("Not connected")
        body = json.dumps(message.to_dict())
        self._channel.basic_publish(
            exchange=config.RESULTS_EXCHANGE,
            routing_key=config.RESULTS_ROUTING_KEY,
            body=body,
            properties=pika.BasicProperties(
                content_type="application/json",
                delivery_mode=2,
            ),
        )
        logger.info("Published result for job %s: status=%s", message.jobId, message.status)