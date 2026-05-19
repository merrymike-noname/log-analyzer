package org.kovalenko.config;

/**
 * Constants for RabbitMQ topology: exchanges, queues, and routing keys.
 * Centralizing names here avoids typos across producer/consumer/config.
 */
public final class RabbitQueues {

    // Analyze flow: backend -> ML worker
    public static final String ANALYZE_EXCHANGE = "analyze.exchange";
    public static final String ANALYZE_QUEUE = "analyze.queue";
    public static final String ANALYZE_ROUTING_KEY = "analyze";

    // Results flow: ML worker -> backend
    public static final String RESULTS_EXCHANGE = "results.exchange";
    public static final String RESULTS_QUEUE = "results.queue";
    public static final String RESULTS_ROUTING_KEY = "result";

    // Dead letter for failed messages
    public static final String DLX_EXCHANGE = "dlx.exchange";
    public static final String DLQ_QUEUE = "dlq.queue";

    private RabbitQueues() {}
}