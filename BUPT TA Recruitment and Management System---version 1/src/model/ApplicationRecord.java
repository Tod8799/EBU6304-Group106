package model;

/**
 * Represents a TA application record for a job.
 */
public class ApplicationRecord {
    private String appId;
    private String jobId;
    private String taId;
    private String status;
    private String appliedAt;
    private String rejectReason;

    /**
     * Convenience constructor without reject reason.
     */
    public ApplicationRecord(String appId, String jobId, String taId, String status, String appliedAt) {
        this(appId, jobId, taId, status, appliedAt, "");
    }

    /**
     * Full constructor.
     */
    public ApplicationRecord(String appId, String jobId, String taId, String status, String appliedAt, String rejectReason) {
        this.appId = appId;
        this.jobId = jobId;
        this.taId = taId;
        this.status = status;
        this.appliedAt = appliedAt;
        this.rejectReason = rejectReason == null ? "" : rejectReason;
    }

    /** Returns the application id. */
    public String getAppId() { return appId; }

    /** Returns the job id. */
    public String getJobId() { return jobId; }

    /** Returns the TA id. */
    public String getTaId() { return taId; }

    /** Returns the status. */
    public String getStatus() { return status; }

    /** Returns the applied timestamp. */
    public String getAppliedAt() { return appliedAt; }

    /** Returns the reject reason if present. */
    public String getRejectReason() { return rejectReason; }

    /** Sets the application status. */
    public void setStatus(String status) { this.status = status; }

    /** Sets the reject reason (safely handles null). */
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason == null ? "" : rejectReason; }

    /** Render application as CSV line, escaping commas in the reject reason. */
    public String toCsvLine() {
        String safeReason = rejectReason.replace("\r", " ").replace("\n", " ").replace(",", "，");
        return appId + "," + jobId + "," + taId + "," + status + "," + appliedAt + "," + safeReason;
    }
}
