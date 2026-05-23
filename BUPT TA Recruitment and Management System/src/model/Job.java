package model;

/**
 * Represents a job posting record.
 */
public class Job {
    private String jobId;
    private String moId;
    private String title;
    private String requirements;
    private String deadline;
    private String createdAt;

    /**
     * Create a job record.
     */
    public Job(String jobId, String moId, String title, String requirements, String deadline, String createdAt) {
        this.jobId = jobId;
        this.moId = moId;
        this.title = title;
        this.requirements = requirements;
        this.deadline = deadline;
        this.createdAt = createdAt;
    }
    /** Returns the job ID. */
    public String getJobId() { return jobId; }
    /** Returns the MO ID who posted the job. */
    public String getMoId() { return moId; }
    /** Returns the job title. */
    public String getTitle() { return title; }
    /** Returns the job requirements. */
    public String getRequirements() { return requirements; }
    /** Returns the job deadline (string). */
    public String getDeadline() { return deadline; }
    /** Returns the job creation timestamp. */
    public String getCreatedAt() { return createdAt; }

    /** Serialize the job to a CSV line. */
    public String toCsvLine() {
        return jobId + "," + moId + "," + title + "," + requirements + "," + deadline + "," + createdAt;
    }
}
