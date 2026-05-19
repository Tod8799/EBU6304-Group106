package model;

/**
 * Represents a job posting created by an MO.
 */
public class Job {
    private String jobId;
    private String moId;
    private String title;
    private String requirements;
    private String deadline;
    private String createdAt;

    /**
     * Constructs a Job posting.
     */
    public Job(String jobId, String moId, String title, String requirements, String deadline, String createdAt) {
        this.jobId = jobId;
        this.moId = moId;
        this.title = title;
        this.requirements = requirements;
        this.deadline = deadline;
        this.createdAt = createdAt;
    }

    /** Returns the job id. */
    public String getJobId() { return jobId; }

    /** Returns the MO id who posted the job. */
    public String getMoId() { return moId; }

    /** Returns the job title. */
    public String getTitle() { return title; }

    /** Returns the core requirements. */
    public String getRequirements() { return requirements; }

    /** Returns the deadline (yyyy-MM-dd). */
    public String getDeadline() { return deadline; }

    /** Returns the job creation timestamp. */
    public String getCreatedAt() { return createdAt; }

    /** Render job as a CSV line. */
    public String toCsvLine() {
        return jobId + "," + moId + "," + title + "," + requirements + "," + deadline + "," + createdAt;
    }
}
