package model;

/**
 * Represents an application record of a TA for a job.
 */
public class ApplicationRecord {
    private String appId;
    private String jobId;
    private String taId;
    private String status;
    private String appliedAt;
    private String rejectReason;

    /**
     * Create an application record without a rejection reason.
     */
    public ApplicationRecord(String appId, String jobId, String taId, String status, String appliedAt) {
        this(appId, jobId, taId, status, appliedAt, "");
    }

    /**
     * Create a full application record.
     */
    public ApplicationRecord(String appId, String jobId, String taId, String status, String appliedAt, String rejectReason) {
        this.appId = appId;
        this.jobId = jobId;
        this.taId = taId;
        this.status = status;
        this.appliedAt = appliedAt;
        this.rejectReason = rejectReason == null ? "" : rejectReason;
    }

    /** Returns the application ID. */
    public String getAppId() { return appId; }
    /** Returns the job ID. */
    public String getJobId() { return jobId; }
    /** Returns the TA ID. */
    public String getTaId() { return taId; }
    /** Returns the application status. */
    public String getStatus() { return status; }
    /** Returns the applied timestamp. */
    public String getAppliedAt() { return appliedAt; }
    /** Returns the rejection reason, if any. */
    public String getRejectReason() { return rejectReason; }

    /** Set the application status. */
    public void setStatus(String status) { this.status = status; }
    /** Set the rejection reason. */
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason == null ? "" : rejectReason; }

    /** Serialize the record to a CSV line (sanitizes rejection reason). */
    public String toCsvLine() {
        String safeReason = rejectReason.replace("\r", " ").replace("\n", " ").replace(",", "，");
        return appId + "," + jobId + "," + taId + "," + status + "," + appliedAt + "," + safeReason;
    }
}
