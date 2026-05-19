package org.kovalenko.job.queue;

import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kovalenko.config.RabbitQueues;
import org.kovalenko.job.JobService;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JobResultConsumer {

    private final JobService jobService;

    @RabbitListener(queues = RabbitQueues.RESULTS_QUEUE)
    public void onResult(JobResultMessage message, Message rawMessage, Channel channel) throws IOException {
        long deliveryTag = rawMessage.getMessageProperties().getDeliveryTag();
        log.info("Received result for job {}: status={}", message.jobId(), message.status());

        try {
            jobService.applyResult(message);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("Failed to apply result for job {}", message.jobId(), e);
            // requeue=false → повідомлення піде в DLQ через x-dead-letter-exchange
            channel.basicNack(deliveryTag, false, false);
        }
    }
}