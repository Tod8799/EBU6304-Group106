package model;

/**
 * Represents a job posting created by a Module Organiser (MO).
 * <p>
 * Each job has a unique ID, a title, requirements, a deadline,
 * and a creation timestamp.
 * </p>
 */
public class Job {
    private String jobId;
    private String moId;
    private String title;
    private String requirements;
    private String deadline;
    private String createdAt;

    /**
     * Constructs a new job.
     *
     * @param jobId        unique job identifier (e.g., "JOB001")
     * @param moId         the MO who posted the job
     * @param title        job title
     * @param requirements description of required skills/qualifications
     * @param deadline     application deadline in "yyyy-MM-dd" format
     * @param createdAt    timestamp when the job was created (yyyy-MM-dd HH:mm:ss)
     */
    public Job(String jobId, String moId, String title, String requirements, String deadline, String createdAt) {
        this.jobId = jobId;
        this.moId = moId;
        this.title = title;
        this.requirements = requirements;
        this.deadline = deadline;
        this.createdAt = createdAt;
    }

    /** @return the job ID */
    public String getJobId() { return jobId; }
    /** @return the MO ID */
    public String getMoId() { return moId; }
    /** @return the job title */
    public String getTitle() { return title; }
    /** @return the requirements text */
    public String getRequirements() { return requirements; }
    /** @return the deadline string */
    public String getDeadline() { return deadline; }
    /** @return the creation timestamp */
    public String getCreatedAt() { return createdAt; }

    /**
     * Formats this job as a CSV line.
     * @return comma-separated line: jobId,moId,title,requirements,deadline,createdAt
     */
    public String toCsvLine() {
        return jobId + "," + moId + "," + title + "," + requirements + "," + deadline + "," + createdAt;
    }
}