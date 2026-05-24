package model;

/**
 * Represents a TA's application for a job.
 * <p>
 * Tracks the application ID, the job, the applicant (TA), the current status,
 * the submission time, and an optional rejection reason.
 * </p>
 */
public class ApplicationRecord {
    private String appId;
    private String jobId;
    private String taId;
    private String status;
    private String appliedAt;
    private String rejectReason;

    /**
     * Creates an application with no reject reason (default "").
     *
     * @param appId     unique application ID
     * @param jobId     the job applied for
     * @param taId      the TA who applied
     * @param status    initial status (usually "Pending")
     * @param appliedAt timestamp when applied (yyyy-MM-dd HH:mm:ss)
     */
    public ApplicationRecord(String appId, String jobId, String taId, String status, String appliedAt) {
        this(appId, jobId, taId, status, appliedAt, "");
    }

    /**
     * Creates an application with a reject reason.
     *
     * @param appId        unique application ID
     * @param jobId        the job applied for
     * @param taId         the TA who applied
     * @param status       current status
     * @param appliedAt    timestamp when applied
     * @param rejectReason why the application was rejected (may be empty)
     */
    public ApplicationRecord(String appId, String jobId, String taId, String status,
                             String appliedAt, String rejectReason) {
        this.appId = appId;
        this.jobId = jobId;
        this.taId = taId;
        this.status = status;
        this.appliedAt = appliedAt;
        this.rejectReason = rejectReason == null ? "" : rejectReason;
    }

    /** @return the application ID */
    public String getAppId() { return appId; }
    /** @return the job ID */
    public String getJobId() { return jobId; }
    /** @return the TA ID */
    public String getTaId() { return taId; }
    /** @return the current status */
    public String getStatus() { return status; }
    /** @return the submission timestamp */
    public String getAppliedAt() { return appliedAt; }
    /** @return the rejection reason (empty if not rejected) */
    public String getRejectReason() { return rejectReason; }

    /** @param status new status value */
    public void setStatus(String status) { this.status = status; }

    /** @param rejectReason new rejection reason */
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason == null ? "" : rejectReason; }

    /**
     * Formats this application as a CSV line.
     * Replaces commas and newlines in the reject reason so the line remains valid.
     *
     * @return CSV line: appId,jobId,taId,status,appliedAt,rejectReason
     */
    public String toCsvLine() {
        String safeReason = rejectReason.replace("\r", " ").replace("\n", " ").replace(",", "，");
        return appId + "," + jobId + "," + taId + "," + status + "," + appliedAt + "," + safeReason;
    }
}