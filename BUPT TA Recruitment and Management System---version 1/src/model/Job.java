package model;

public class Job {
    private String jobId;
    private String moId;
    private String title;
    private String requirements;
    private String deadline;
    private String createdAt;

    public Job(String jobId, String moId, String title, String requirements, String deadline, String createdAt) {
        this.jobId = jobId;
        this.moId = moId;
        this.title = title;
        this.requirements = requirements;
        this.deadline = deadline;
        this.createdAt = createdAt;
    }

    public String getJobId() { return jobId; }
    public String getMoId() { return moId; }
    public String getTitle() { return title; }
    public String getRequirements() { return requirements; }
    public String getDeadline() { return deadline; }
    public String getCreatedAt() { return createdAt; }

    public String toCsvLine() {
        return jobId + "," + moId + "," + title + "," + requirements + "," + deadline + "," + createdAt;
    }
}
