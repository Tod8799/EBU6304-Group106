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
 * DAO for `Job` persisted in `data/jobs.csv`.
 */
public class JobDAO {
    private static final String FILE_PATH = "data/jobs.csv";

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
     * Append a job to storage.
     *
     * @param job job to save
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
     * Read all jobs.
     *
     * @return list of jobs
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
     * Return jobs posted by given MO.
     *
     * @param moId mo id
     * @return list of jobs
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
     * Return jobs whose deadline is not before today.
     *
     * @return open jobs
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
     * Find a job by id.
     *
     * @param jobId job id
     * @return Job or null
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
