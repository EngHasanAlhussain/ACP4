package uk.ac.ed.acp4.model;

public class AgentResponse {

    private String serviceName;
    private String problem;
    private String rootCause;
    private String severity;
    private String recommendedFix;
    private String eta;
    private String affectedUsers;
    private String codeFilePath;
    private String codePatch;
    private String codeExplanation;

    private String sqlScript;
    private String sqlExplanation;

    public AgentResponse() {}

    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }


    public String getSqlScript() { return sqlScript; }
    public void setSqlScript(String sqlScript) { this.sqlScript = sqlScript; }

    public String getSqlExplanation() { return sqlExplanation; }
    public void setSqlExplanation(String sqlExplanation) { this.sqlExplanation = sqlExplanation; }

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

    public String getCodeFilePath() { return codeFilePath; }
    public void setCodeFilePath(String codeFilePath) { this.codeFilePath = codeFilePath; }

    public String getCodePatch() { return codePatch; }
    public void setCodePatch(String codePatch) { this.codePatch = codePatch; }

    public String getCodeExplanation() { return codeExplanation; }
    public void setCodeExplanation(String codeExplanation) { this.codeExplanation = codeExplanation; }

    private String fixType; // CODE_CHANGE, CONFIG_CHANGE, DB_OPERATION, EXTERNAL, INVESTIGATION

    public String getFixType() { return fixType; }
    public void setFixType(String fixType) { this.fixType = fixType; }
}