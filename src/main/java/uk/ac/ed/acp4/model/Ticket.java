package uk.ac.ed.acp4.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "tickets")
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "service_name", nullable = false)
    private String serviceName;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String problem;

    @Column(name = "root_cause", nullable = false, columnDefinition = "TEXT")
    private String rootCause;

    @Column(nullable = false)
    private String severity;

    @Column(name = "recommended_fix", nullable = false, columnDefinition = "TEXT")
    private String recommendedFix;

    private String eta;

    @Column(name = "affected_users", columnDefinition = "TEXT")
    private String affectedUsers;

    @Column(nullable = false)
    private String status = "OPEN";

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Ticket() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }

    public String getProblem() { return problem; }
    public void setProblem(String problem) { this.problem = problem; }

    public String getRootCause() { return rootCause; }
    public void setRootCause(String rootCause) { this.rootCause = rootCause; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getRecommendedFix() { return recommendedFix; }
    public void setRecommendedFix(String recommendedFix) { this.recommendedFix = recommendedFix; }

    public String getEta() { return eta; }
    public void setEta(String eta) { this.eta = eta; }

    public String getAffectedUsers() { return affectedUsers; }
    public void setAffectedUsers(String affectedUsers) { this.affectedUsers = affectedUsers; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}