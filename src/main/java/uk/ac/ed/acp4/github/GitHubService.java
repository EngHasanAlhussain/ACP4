package uk.ac.ed.acp4.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Service
public class GitHubService {

    private static final Logger log = LoggerFactory.getLogger(GitHubService.class);

    @Value("${github.token}")
    private String token;

    @Value("${github.owner}")
    private String owner;

    @Value("${github.repo}")
    private String repo;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private WebClient client() {
        return WebClient.builder()
                .baseUrl("https://api.github.com")
                .defaultHeader("Authorization", "Bearer " + token)
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    // ── Read a file from the repo ─────────────────────────────────────────
    public String readFile(String filePath) {
        try {
            String response = client().get()
                    .uri("/repos/{owner}/{repo}/contents/{path}", owner, repo, filePath)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode node = objectMapper.readTree(response);
            String encoded = node.path("content").asText().replaceAll("\\s", "");
            return new String(Base64.getDecoder().decode(encoded));
        } catch (WebClientResponseException e) {
            log.warn("GitHub file not found: {} — {}", filePath, e.getStatusCode());
            return null;
        } catch (Exception e) {
            log.error("Failed to read file from GitHub: {}", e.getMessage());
            return null;
        }
    }

    // ── Get the SHA of the default branch HEAD ────────────────────────────
    private String getMainSha() throws Exception {
        String response = client().get()
                .uri("/repos/{owner}/{repo}/git/ref/heads/main", owner, repo)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        JsonNode node = objectMapper.readTree(response);
        return node.path("object").path("sha").asText();
    }

    // ── Get the SHA of an existing file (needed to update it) ────────────
    private String getFileSha(String filePath) {
        try {
            String response = client().get()
                    .uri("/repos/{owner}/{repo}/contents/{path}", owner, repo, filePath)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode node = objectMapper.readTree(response);
            return node.path("sha").asText();
        } catch (Exception e) {
            return null;
        }
    }

    // ── Create a new branch from main ─────────────────────────────────────
    private void createBranch(String branchName, String sha) throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("ref", "refs/heads/" + branchName);
        body.put("sha", sha);

        client().post()
                .uri("/repos/{owner}/{repo}/git/refs", owner, repo)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        log.info("Created branch: {}", branchName);
    }

    // ── Commit a patched file to the branch ──────────────────────────────
    private void commitFile(String branchName, String filePath,
                            String newContent, String commitMessage) throws Exception {
        String encoded = Base64.getEncoder().encodeToString(newContent.getBytes());
        String fileSha = getFileSha(filePath);

        Map<String, Object> body = new HashMap<>();
        body.put("message", commitMessage);
        body.put("content", encoded);
        body.put("branch", branchName);
        if (fileSha != null) body.put("sha", fileSha);

        client().put()
                .uri("/repos/{owner}/{repo}/contents/{path}", owner, repo, filePath)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        log.info("Committed file {} to branch {}", filePath, branchName);
    }

    // ── Open a Pull Request ───────────────────────────────────────────────
    private String openPullRequest(String branchName, String title, String body) throws Exception {
        Map<String, Object> prBody = new HashMap<>();
        prBody.put("title", title);
        prBody.put("body", body);
        prBody.put("head", branchName);
        prBody.put("base", "main");

        String response = client().post()
                .uri("/repos/{owner}/{repo}/pulls", owner, repo)
                .bodyValue(prBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        JsonNode node = objectMapper.readTree(response);
        String prUrl = node.path("html_url").asText();
        log.info("Pull request opened: {}", prUrl);
        return prUrl;
    }

    // ── Main method called on ticket approval ─────────────────────────────
    public String createFixPullRequest(Long ticketId, String serviceName,
                                       String filePath, String patchedContent,
                                       String problem, String explanation) {
        try {
            String branchName = "fix/ticket-" + ticketId;
            String mainSha = getMainSha();

            createBranch(branchName, mainSha);

            commitFile(
                    branchName,
                    filePath,
                    patchedContent,
                    "fix(ticket-" + ticketId + "): " + problem
            );

            String prDescription = buildPrDescription(ticketId, serviceName, problem, explanation);
            String prUrl = openPullRequest(
                    branchName,
                    "[LogSentinel] Ticket #" + ticketId + " — " + problem,
                    prDescription
            );

            return prUrl;

        } catch (Exception e) {
            log.error("Failed to create PR for ticket {}: {}", ticketId, e.getMessage());
            return null;
        }
    }

    private String buildPrDescription(Long ticketId, String serviceName,
                                      String problem, String explanation) {
        return """
                ## 🤖 Auto-generated fix by LogSentinel
                
                **Ticket:** #%d
                **Service:** %s
                **Problem:** %s
                
                ### What changed
                %s
                
                ### Review checklist
                - [ ] Verify the fix addresses the root cause
                - [ ] Run unit tests locally
                - [ ] Check no regressions in related services
                - [ ] Approve and merge when ready
                
                > ⚠️ This PR was automatically generated by the LogSentinel AI agent.
                > A human engineer must review and approve before merging.
                """.formatted(ticketId, serviceName, problem, explanation);
    }

    // ── Map service name to file path in repo ────────────────────────────
    public static String serviceToFilePath(String serviceName) {
        return switch (serviceName) {
            case "card-service"    -> "CardService.java";
            case "payment-service" -> "PaymentService.java";
            case "auth-service"    -> "AuthService.java";
            default                -> null;
        };
    }
}