package uk.ac.ed.acp4.ticket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import uk.ac.ed.acp4.model.Ticket;

import java.time.format.DateTimeFormatter;
import java.util.Map;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${vendor.email.default:oncall-escalation@example.com}")
    private String defaultVendorEmail;

    private static final Map<String, String> VENDOR_EMAILS = Map.of(
            "visa",        "support@visa.com",
            "mastercard",  "support@mastercard.com",
            "gift",        "api-support@giftrewards.example.com",
            "fraud",       "ops@frauddetection.example.com"
    );

    private static final Map<String, String> VENDOR_NAMES = Map.of(
            "visa",        "Visa",
            "mastercard",  "Mastercard",
            "gift",        "Gift Rewards API",
            "fraud",       "Fraud Detection Service"
    );

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public String sendVendorEscalation(Ticket ticket, String approvedBy) {
        String vendorKey   = ticket.getExternalVendor() != null
                ? ticket.getExternalVendor().toLowerCase() : "unknown";
        String vendorEmail = VENDOR_EMAILS.getOrDefault(vendorKey, defaultVendorEmail);
        String vendorName  = VENDOR_NAMES.getOrDefault(vendorKey, vendorKey);

        String subject = String.format("[LogSentinel] Incident #%d — %s — %s — Severity: %s",
                ticket.getId(), ticket.getServiceName(), vendorName, ticket.getSeverity());

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(vendorEmail);
            message.setSubject(subject);
            message.setText(buildBody(ticket, approvedBy, vendorName));
            mailSender.send(message);

            log.info("Vendor escalation sent to {} ({}) for ticket #{}",
                    vendorName, vendorEmail, ticket.getId());
            return vendorEmail;

        } catch (Exception e) {
            log.error("Failed to send vendor email for ticket #{}: {}", ticket.getId(), e.getMessage());
            throw new RuntimeException("Email delivery failed: " + e.getMessage());
        }
    }

    private String buildBody(Ticket ticket, String approvedBy, String vendorName) {
        String timestamp = ticket.getCreatedAt() != null
                ? ticket.getCreatedAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss"))
                : "Unknown";

        return String.format("""
                Dear %s Support Team,

                We are writing to report a service incident affecting our integration
                with your platform. This escalation has been reviewed and approved by
                our engineering team.

                ─────────────────────────────────────────
                INCIDENT DETAILS
                ─────────────────────────────────────────
                Ticket ID        : #%d
                Affected Service : %s
                Severity         : %s
                Detected At      : %s
                Escalated By     : %s

                PROBLEM
                %s

                ROOT CAUSE
                %s

                AFFECTED USERS
                %s

                RECOMMENDED ACTION
                %s

                ─────────────────────────────────────────
                This issue was automatically detected and classified as an external
                dependency failure by our LogSentinel monitoring system.

                Please investigate and provide an estimated resolution time.

                Regards,
                LogSentinel Incident Management
                ─────────────────────────────────────────
                """,
                vendorName,
                ticket.getId(),
                ticket.getServiceName(),
                ticket.getSeverity(),
                timestamp,
                approvedBy,
                ticket.getProblem()        != null ? ticket.getProblem()        : "—",
                ticket.getRootCause()      != null ? ticket.getRootCause()      : "—",
                ticket.getAffectedUsers()  != null ? ticket.getAffectedUsers()  : "None identified",
                ticket.getRecommendedFix() != null ? ticket.getRecommendedFix() : "—"
        );
    }
}