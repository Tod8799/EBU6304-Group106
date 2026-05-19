package dao;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import model.ApplicationRecord;

/**
 * DAO for `ApplicationRecord` persisted in `data/applications.csv`.
 * <p>Supports reading, writing and updating application status.</p>
 */
public class ApplicationDAO {
    private static final String FILE_PATH = "data/applications.csv";
    private static final List<String> STATUSES = List.of("Pending", "Shortlisted", "Rejected", "Interview", "Hired");

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
     * Append an application record to storage.
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
        * Read all application records from the CSV storage.
        * <p>This method supports both legacy and current CSV formats and will
        * normalize fields into {@link model.ApplicationRecord} instances.</p>
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
                        // New format: appId,jobId,taId,status,appliedAt,rejectReason
                        String reason = String.join(",", java.util.Arrays.copyOfRange(data, 5, data.length));
                        records.add(new ApplicationRecord(data[0], data[1], data[2], data[3], data[4], reason));
                    } else {
                        // Legacy format: appId,jobId,taId,cvPath(ignored),status,appliedAt
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
     * Find applications by TA id.
     *
     * @param taId the TA id to filter by
     * @return list of matching application records (may be empty)
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
     * Find applications by job id.
     *
     * @param jobId the job id to filter by
     * @return list of matching application records (may be empty)
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
     * Find a single application by its application id.
     *
     * @param appId application id
     * @return ApplicationRecord or null when not found
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
     * Check whether a TA already applied for a specific job.
     *
     * @param taId  TA id
     * @param jobId job id
     * @return true if an application exists, false otherwise
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
     * Update application status; returns whether an update occurred.
     *
     * @param appId     application id to update
     * @param newStatus new status value
     * @return true if the record was found and updated, false otherwise
     */
    public boolean updateStatus(String appId, String newStatus) {
        return updateStatus(appId, newStatus, "");
    }

    /**
     * Update application status with optional reject reason.
     *
     * @param appId        application id
     * @param newStatus    new status value
     * @param rejectReason reason for rejection (used when status is Rejected)
     * @return true if update succeeded, false otherwise
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
            rewriteAll(records);
        }
        return updated;
    }

    /**
     * Overwrite the CSV storage with the provided list of records.
     *
     * @param records list of application records to persist
     */
    private void rewriteAll(List<ApplicationRecord> records) {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(FILE_PATH, false))) {
            for (ApplicationRecord record : records) {
                bw.write(record.toCsvLine());
                bw.newLine();
            }
        } catch (IOException e) {
            System.out.println("[Error] Failed to update applications: " + e.getMessage());
        }
    }

    /**
     * Check whether the provided value is a known application status.
     */
    private boolean isKnownStatus(String value) {
        for (String status : STATUSES) {
            if (status.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }
}
