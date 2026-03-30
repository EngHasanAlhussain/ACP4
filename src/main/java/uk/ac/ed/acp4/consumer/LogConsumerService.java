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
import uk.ac.ed.acp4.ticket.TicketService;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class LogConsumerService {

    private static final Logger log = LoggerFactory.getLogger(LogConsumerService.class);

    private final ClaudeAgentService agentService;
    private final RabbitTemplate rabbitTemplate;
    private final RedisTemplate<String, String> redisTemplate;
    private final TicketService ticketService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.log.batch-size:20}")
    private int batchSize;

    @Value("${app.dedup.ttl-seconds:1800}")
    private long dedupTtlSeconds;

    @Value("${app.rabbitmq.queue}")
    private String ticketQueue;

    private final Map<String, List<String>> errorBuffer = new ConcurrentHashMap<>();
    private static final Pattern USER_PATTERN = Pattern.compile("USR\\d+");

    public LogConsumerService(ClaudeAgentService agentService,
                              RabbitTemplate rabbitTemplate,
                              RedisTemplate<String, String> redisTemplate,
                              TicketService ticketService) {
        this.agentService = agentService;
        this.rabbitTemplate = rabbitTemplate;
        this.redisTemplate = redisTemplate;
        this.ticketService = ticketService;
    }

    @KafkaListener(topics = "bank-logs", groupId = "${spring.kafka.consumer.group-id}")
    public void consume(String message) {
        try {
            JsonNode logNode = objectMapper.readTree(message);
            String level     = logNode.path("level").asText();
            String service   = logNode.path("service").asText();
            String text      = logNode.path("message").asText();
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
        // One ticket per service per TTL window — simple and reliable
        String dedupKey = "dedup:" + serviceName;

        Boolean alreadySeen = redisTemplate.hasKey(dedupKey);

        if (Boolean.TRUE.equals(alreadySeen)) {
            List<String> newUsers = extractUserIds(logLines);
            if (!newUsers.isEmpty()) {
                ticketService.findOpenTicketByService(serviceName).ifPresent(ticket -> {
                    ticketService.appendAffectedUsers(ticket.getId(), newUsers);
                    log.info("Appended {} users to ticket #{} for {}",
                            newUsers.size(), ticket.getId(), serviceName);
                });
            }
            return;
        }

        // New window — mark immediately before any async work
        redisTemplate.opsForValue().set(dedupKey, "1", Duration.ofSeconds(dedupTtlSeconds));
        log.info("New error window for {}. Sending {} lines to AI.", serviceName, logLines.size());

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
            log.warn("AI agent returned null for: {}", serviceName);
        }
    }

    private List<String> extractUserIds(List<String> logLines) {
        return logLines.stream()
                .flatMap(line -> {
                    Matcher m = USER_PATTERN.matcher(line);
                    List<String> found = new ArrayList<>();
                    while (m.find()) found.add(m.group());
                    return found.stream();
                })
                .distinct()
                .collect(Collectors.toList());
    }
}