package uk.ac.ed.acp4.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import uk.ac.ed.acp4.agent.ClaudeAgentService;
import uk.ac.ed.acp4.model.AgentResponse;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LogConsumerService {

    private static final Logger log = LoggerFactory.getLogger(LogConsumerService.class);

    private final ClaudeAgentService agentService;
    private final RabbitTemplate rabbitTemplate;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.log.batch-size}")
    private int batchSize;

    @Value("${app.dedup.ttl-seconds}")
    private long dedupTtlSeconds;

    @Value("${app.rabbitmq.queue}")
    private String ticketQueue;

    private final Map<String, List<String>> errorBuffer = new ConcurrentHashMap<>();

    public LogConsumerService(ClaudeAgentService agentService,
                              RabbitTemplate rabbitTemplate,
                              RedisTemplate<String, String> redisTemplate) {
        this.agentService = agentService;
        this.rabbitTemplate = rabbitTemplate;
        this.redisTemplate = redisTemplate;
    }

    @KafkaListener(topics = "bank-logs", groupId = "${spring.kafka.consumer.group-id}")
    public void consume(String message) {
        try {
            JsonNode logNode = objectMapper.readTree(message);
            String level   = logNode.path("level").asText();
            String service = logNode.path("service").asText();
            String text    = logNode.path("message").asText();
            String timestamp = logNode.path("timestamp").asText();

            if (!level.equals("ERROR") && !level.equals("WARN")) return;

            String logLine = String.format("[%s] %s - %s", level, timestamp, text);
            errorBuffer.computeIfAbsent(service, k -> new ArrayList<>()).add(logLine);

            List<String> buffer = errorBuffer.get(service);
            if (buffer.size() >= batchSize) {
                List<String> batch = new ArrayList<>(buffer);
                buffer.clear();
                processBatch(service, batch);
            }

        } catch (Exception e) {
            log.error("Failed to process Kafka message: {}", e.getMessage());
        }
    }

    private void processBatch(String serviceName, List<String> logLines) {
        String dedupKey = "dedup:" + serviceName + ":" + extractErrorKey(logLines);

        Boolean alreadySeen = redisTemplate.hasKey(dedupKey);
        if (Boolean.TRUE.equals(alreadySeen)) {
            log.info("Duplicate error pattern for {} — skipping", serviceName);
            return;
        }

        redisTemplate.opsForValue().set(dedupKey, "1", Duration.ofSeconds(dedupTtlSeconds));
        log.info("Sending {} error logs from {} to AI agent", logLines.size(), serviceName);

        AgentResponse response = agentService.analyze(serviceName, logLines);

        if (response != null) {
            try {
                String payload = objectMapper.writeValueAsString(response);
                rabbitTemplate.convertAndSend(ticketQueue, payload);
                log.info("AgentResponse published to RabbitMQ for: {}", serviceName);
            } catch (Exception e) {
                log.error("Failed to publish to RabbitMQ: {}", e.getMessage());
            }
        } else {
            log.warn("AI agent returned null for service: {}", serviceName);
        }
    }

    private String extractErrorKey(List<String> logLines) {
        return logLines.stream()
                .filter(l -> l.contains("ERROR"))
                .findFirst()
                .map(l -> l.replaceAll("[^a-zA-Z]", ""))
                .map(l -> l.substring(0, Math.min(20, l.length())))
                .orElse("unknown");
    }
}