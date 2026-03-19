package uk.ac.ed.acp4.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import uk.ac.ed.acp4.model.AgentResponse;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@Service
public class ClaudeAgentService {

    private static final Logger log = LoggerFactory.getLogger(ClaudeAgentService.class);

    @Value("${claude.api.key}")
    private String apiKey;

    @Value("${claude.api.url}")
    private String apiUrl;

    @Value("${claude.api.model}")
    private String model;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public AgentResponse analyze(String serviceName, List<String> logLines) {
        String sourceCode = loadSourceCode(serviceName);
        String prompt = buildPrompt(serviceName, logLines, sourceCode);
        try {
            String responseText = callClaude(prompt);
            return parseResponse(responseText);
        } catch (Exception e) {
            log.error("Claude API call failed: {}", e.getMessage());
            return null;
        }
    }

    private String buildPrompt(String serviceName, List<String> logLines, String sourceCode) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an expert backend engineer analyzing logs from a banking system.\n\n");
        sb.append("Service: ").append(serviceName).append("\n\n");
        sb.append("Log lines:\n");
        logLines.forEach(line -> sb.append(line).append("\n"));

        if (sourceCode != null) {
            sb.append("\nRelevant source code:\n").append(sourceCode).append("\n");
        }

        sb.append("""

                Analyze these logs and respond ONLY with a valid JSON object (no markdown, no explanation outside JSON):
                {
                  "serviceName": "name of the service",
                  "problem": "clear one-line description of the problem",
                  "rootCause": "detailed root cause explanation",
                  "severity": "LOW|MEDIUM|HIGH|CRITICAL",
                  "recommendedFix": "step by step fix instructions",
                  "eta": "estimated time to resolve e.g. 2 hours",
                  "affectedUsers": "description of who is affected",
                  "codeFilePath": "the file that needs to be changed",
                  "codePatch": "the exact code fix as a diff or replacement snippet",
                  "codeExplanation": "why this code change fixes the problem"
                }

                Only respond with the JSON. No other text.
                """);

        return sb.toString();
    }

    private String callClaude(String prompt) throws Exception {
        WebClient client = WebClient.builder()
                .baseUrl(apiUrl)
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", "2023-06-01")
                .defaultHeader("content-type", "application/json")
                .build();

        Map<String, Object> body = Map.of(
                "model", model,
                "max_tokens", 4096,
                "messages", List.of(
                        Map.of("role", "user", "content", prompt)
                )
        );

        String response = client.post()
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        JsonNode root = objectMapper.readTree(response);
        return root.path("content").get(0).path("text").asText();
    }

    private AgentResponse parseResponse(String responseText) {
        if (responseText == null) return null;
        try {
            String clean = responseText
                    .replaceAll("```json", "")
                    .replaceAll("```", "")
                    .trim();
            return objectMapper.readValue(clean, AgentResponse.class);
        } catch (Exception e) {
            log.error("Failed to parse agent JSON response: {}", e.getMessage());
            log.error("Raw response was: {}", responseText);
            return null;
        }
    }

    private String loadSourceCode(String serviceName) {
        try {
            String filename = switch (serviceName) {
                case "card-service"    -> "CardService.java";
                case "payment-service" -> "PaymentService.java";
                case "auth-service"    -> "AuthService.java";
                default                -> null;
            };
            if (filename == null) return null;
            return Files.readString(Paths.get("sample-source-code/" + filename));
        } catch (Exception e) {
            log.warn("Could not load source code for {}: {}", serviceName, e.getMessage());
            return null;
        }
    }
}