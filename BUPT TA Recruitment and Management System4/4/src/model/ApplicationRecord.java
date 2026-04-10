package model;

public class ApplicationRecord {
    private String appId;
    private String jobId;
    private String taId;
    private String status;
    private String appliedAt;

    public ApplicationRecord(String appId, String jobId, String taId, String status, String appliedAt) {
        this.appId = appId;
        this.jobId = jobId;
        this.taId = taId;
        this.status = status;
        this.appliedAt = appliedAt;
    }

    public String getAppId() { return appId; }
    public String getJobId() { return jobId; }
    public String getTaId() { return taId; }
    public String getStatus() { return status; }
    public String getAppliedAt() { return appliedAt; }

    public void setStatus(String status) { this.status = status; }

    public String toCsvLine() {
        return appId + "," + jobId + "," + taId + "," + status + "," + appliedAt;
    }
}
