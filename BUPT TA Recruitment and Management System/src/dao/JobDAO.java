package dao;

import model.Job;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Data access object for job postings stored in {@code data/jobs.csv}.
 * <p>
 * Provides methods to save a job, get all jobs, filter by MO,
 * get open (non-expired) jobs, and find a job by ID.
 * </p>
 */
public class JobDAO {
    private static final String FILE_PATH = "data/jobs.csv";

    /**
     * Creates the DAO and ensures the jobs file exists.
     */
    public JobDAO() {
        ensureFile();
    }

    private void ensureFile() {
        File file = new File(FILE_PATH);
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                System.out.println("[Error] Failed to initialize jobs file: " + e.getMessage());
            }
        }
    }

    /**
     * Appends a new job to the CSV file.
     * @param job the job to save
     */
    public void saveJob(Job job) {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(FILE_PATH, true))) {
            bw.write(job.toCsvLine());
            bw.newLine();
        } catch (IOException e) {
            System.out.println("[Error] Failed to write job: " + e.getMessage());
        }
    }

    /**
     * Reads all jobs from the CSV file.
     * @return list of all jobs
     */
    public List<Job> getAllJobs() {
        List<Job> jobs = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(FILE_PATH))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] data = line.split(",", -1);
                if (data.length == 6) {
                    jobs.add(new Job(data[0], data[1], data[2], data[3], data[4], data[5]));
                }
            }
        } catch (IOException e) {
            System.out.println("[Error] Failed to read jobs: " + e.getMessage());
        }
        return jobs;
    }

    /**
     * Returns all jobs posted by a specific Module Organiser.
     * @param moId the MO's user ID
     * @return list of jobs belonging to that MO
     */
    public List<Job> getJobsByMoId(String moId) {
        List<Job> result = new ArrayList<>();
        for (Job job : getAllJobs()) {
            if (job.getMoId().equalsIgnoreCase(moId)) {
                result.add(job);
            }
        }
        return result;
    }

    /**
     * Returns all jobs whose deadline is today or in the future.
     * @return list of open jobs
     */
    public List<Job> getOpenJobs() {
        List<Job> openJobs = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (Job job : getAllJobs()) {
            try {
                LocalDate deadline = LocalDate.parse(job.getDeadline());
                if (!deadline.isBefore(today)) {
                    openJobs.add(job);
                }
            } catch (Exception ignored) {
            }
        }
        return openJobs;
    }

    /**
     * Finds a job by its unique ID.
     * @param jobId the job ID
     * @return the job or {@code null} if not found
     */
    public Job getByJobId(String jobId) {
        for (Job job : getAllJobs()) {
            if (job.getJobId().equalsIgnoreCase(jobId)) {
                return job;
            }
        }
        return null;
    }
}