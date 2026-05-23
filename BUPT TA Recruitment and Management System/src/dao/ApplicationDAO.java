package dao;

import model.ApplicationRecord;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Data access object for job applications stored in {@code data/applications.csv}.
 * <p>
 * Provides methods to save, read, filter by TA or job, check duplicates,
 * and update application status (including rejection reason).
 * </p>
 */
public class ApplicationDAO {
    private static final String FILE_PATH = "data/applications.csv";
    private static final List<String> STATUSES = List.of("Pending", "Shortlisted", "Rejected", "Interview", "Hired");

    /**
     * Creates the DAO and ensures the applications file exists.
     */
    public ApplicationDAO() {
        ensureFile();
    }

    private void ensureFile() {
        File file = new File(FILE_PATH);
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                System.out.println("[Error] Failed to initialize applications file: " + e.getMessage());
            }
        }
    }

    /**
     * Appends a new application record to the CSV file.
     *
     * @param record the application to save
     */
    public void saveApplication(ApplicationRecord record) {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(FILE_PATH, true))) {
            bw.write(record.toCsvLine());
            bw.newLine();
        } catch (IOException e) {
            System.out.println("[Error] Failed to write application: " + e.getMessage());
        }
    }

    /**
     * Reads all application records from the CSV file.
     * <p>
     * Supports both the old format (6 fields with a legacy path) and the new format.
     * </p>
     *
     * @return list of all applications
     */
    public List<ApplicationRecord> getAllApplications() {
        List<ApplicationRecord> records = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(FILE_PATH))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] data = line.split(",", -1);
                if (data.length == 5) {
                    records.add(new ApplicationRecord(data[0], data[1], data[2], data[3], data[4], ""));
                } else if (data.length >= 6) {
                    if (isKnownStatus(data[3])) {
                        String reason = String.join(",", Arrays.copyOfRange(data, 5, data.length));
                        records.add(new ApplicationRecord(data[0], data[1], data[2], data[3], data[4], reason));
                    } else {
                        // Legacy format: skip index 3 (old cvPath)
                        records.add(new ApplicationRecord(data[0], data[1], data[2], data[4], data[5], ""));
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("[Error] Failed to read applications: " + e.getMessage());
        }
        return records;
    }

    /**
     * Returns all applications submitted by a specific TA.
     *
     * @param taId the TA's user ID
     * @return list of applications belonging to that TA
     */
    public List<ApplicationRecord> getByTaId(String taId) {
        List<ApplicationRecord> result = new ArrayList<>();
        for (ApplicationRecord record : getAllApplications()) {
            if (record.getTaId().equalsIgnoreCase(taId)) {
                result.add(record);
            }
        }
        return result;
    }

    /**
     * Returns all applications for a specific job.
     *
     * @param jobId the job ID
     * @return list of applications for that job
     */
    public List<ApplicationRecord> getByJobId(String jobId) {
        List<ApplicationRecord> result = new ArrayList<>();
        for (ApplicationRecord record : getAllApplications()) {
            if (record.getJobId().equalsIgnoreCase(jobId)) {
                result.add(record);
            }
        }
        return result;
    }

    /**
     * Finds an application by its unique ID.
     *
     * @param appId the application ID
     * @return the application record or {@code null}
     */
    public ApplicationRecord getByAppId(String appId) {
        for (ApplicationRecord record : getAllApplications()) {
            if (record.getAppId().equalsIgnoreCase(appId)) {
                return record;
            }
        }
        return null;
    }

    /**
     * Checks if a TA has already applied for a given job.
     *
     * @param taId  the TA ID
     * @param jobId the job ID
     * @return true if a duplicate exists
     */
    public boolean existsForTaAndJob(String taId, String jobId) {
        for (ApplicationRecord record : getAllApplications()) {
            if (record.getTaId().equalsIgnoreCase(taId) && record.getJobId().equalsIgnoreCase(jobId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Updates the status of an application without a reject reason.
     *
     * @param appId     the application ID
     * @param newStatus the new status (e.g., "Shortlisted")
     * @return true if the update succeeded
     */
    public boolean updateStatus(String appId, String newStatus) {
        return updateStatus(appId, newStatus, "");
    }

    /**
     * Updates the status of an application and optionally sets a rejection reason.
     *
     * @param appId        the application ID
     * @param newStatus    the new status
     * @param rejectReason required when status is "Rejected"
     * @return true if the update succeeded
     */
    public boolean updateStatus(String appId, String newStatus, String rejectReason) {
        List<ApplicationRecord> records = getAllApplications();
        boolean updated = false;
        for (ApplicationRecord record : records) {
            if (record.getAppId().equalsIgnoreCase(appId)) {
                record.setStatus(newStatus);
                if ("Rejected".equalsIgnoreCase(newStatus)) {
                    record.setRejectReason(rejectReason == null ? "" : rejectReason);
                } else {
                    record.setRejectReason("");
                }
                updated = true;
                break;
            }
        }

        if (updated) {
            return rewriteAll(records);
        }
        return false;
    }

    private boolean rewriteAll(List<ApplicationRecord> records) {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(FILE_PATH, false))) {
            for (ApplicationRecord record : records) {
                bw.write(record.toCsvLine());
                bw.newLine();
            }
            return true;
        } catch (IOException e) {
            System.out.println("[Error] Failed to update applications: " + e.getMessage());
            return false;
        }
    }

    private boolean isKnownStatus(String value) {
        for (String status : STATUSES) {
            if (status.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }
}