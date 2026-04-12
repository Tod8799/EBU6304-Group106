package model;

public class ApplicationRecord {
    private String appId;
    private String jobId;
    private String taId;
    private String status;
    private String appliedAt;
    private String rejectReason;

    public ApplicationRecord(String appId, String jobId, String taId, String status, String appliedAt) {
        this(appId, jobId, taId, status, appliedAt, "");
    }

    public ApplicationRecord(String appId, String jobId, String taId, String status, String appliedAt, String rejectReason) {
        this.appId = appId;
        this.jobId = jobId;
        this.taId = taId;
        this.status = status;
        this.appliedAt = appliedAt;
        this.rejectReason = rejectReason == null ? "" : rejectReason;
    }

    public String getAppId() { return appId; }
    public String getJobId() { return jobId; }
    public String getTaId() { return taId; }
    public String getStatus() { return status; }
    public String getAppliedAt() { return appliedAt; }
    public String getRejectReason() { return rejectReason; }

    public void setStatus(String status) { this.status = status; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason == null ? "" : rejectReason; }

    public String toCsvLine() {
        String safeReason = rejectReason.replace("\r", " ").replace("\n", " ").replace(",", "，");
        return appId + "," + jobId + "," + taId + "," + status + "," + appliedAt + "," + safeReason;
    }
}
