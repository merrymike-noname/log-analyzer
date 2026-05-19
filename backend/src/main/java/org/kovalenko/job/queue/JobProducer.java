package org.kovalenko.job.queue;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kovalenko.config.RabbitQueues;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class JobProducer {

    private final RabbitTemplate rabbitTemplate;

    public void publishAnalyzeTask(AnalyzeTaskMessage message) {
        rabbitTemplate.convertAndSend(
                RabbitQueues.ANALYZE_EXCHANGE,
                RabbitQueues.ANALYZE_ROUTING_KEY,
                message
        );
        log.info("Published analyze task for job {}", message.jobId());
    }
}