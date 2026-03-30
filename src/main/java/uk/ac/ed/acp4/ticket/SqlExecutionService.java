package uk.ac.ed.acp4.ticket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SqlExecutionService {

    private static final Logger log = LoggerFactory.getLogger(SqlExecutionService.class);

    // Only these statements are permitted — never DROP, DELETE, TRUNCATE
    private static final List<String> ALLOWED_PREFIXES = List.of(
            "INSERT", "UPDATE", "SELECT"
    );

    private final JdbcTemplate jdbcTemplate;

    public SqlExecutionService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public SqlExecutionResult execute(String sqlScript) {
        String trimmed = sqlScript.trim().toUpperCase();

        // Safety check — reject dangerous statements
        boolean allowed = ALLOWED_PREFIXES.stream().anyMatch(trimmed::startsWith);
        if (!allowed) {
            String msg = "SQL rejected — only INSERT, UPDATE, SELECT are permitted. Got: "
                    + trimmed.substring(0, Math.min(30, trimmed.length()));
            log.warn(msg);
            return SqlExecutionResult.failure(msg);
        }

        try {
            if (trimmed.startsWith("SELECT")) {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(sqlScript);
                String result = rows.stream()
                        .map(row -> row.entrySet().stream()
                                .map(e -> e.getKey() + "=" + e.getValue())
                                .collect(Collectors.joining(", ")))
                        .collect(Collectors.joining("\n"));
                log.info("SQL SELECT executed successfully. {} row(s) returned.", rows.size());
                return SqlExecutionResult.success(rows.size() + " row(s) returned:\n" + result);
            } else {
                int affected = jdbcTemplate.update(sqlScript);
                log.info("SQL executed successfully. {} row(s) affected.", affected);
                return SqlExecutionResult.success(affected + " row(s) affected.");
            }
        } catch (Exception e) {
            log.error("SQL execution failed: {}", e.getMessage());
            return SqlExecutionResult.failure("Execution failed: " + e.getMessage());
        }
    }

    public static class SqlExecutionResult {
        private final boolean success;
        private final String message;

        private SqlExecutionResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public static SqlExecutionResult success(String message) {
            return new SqlExecutionResult(true, message);
        }

        public static SqlExecutionResult failure(String message) {
            return new SqlExecutionResult(false, message);
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
    }
}