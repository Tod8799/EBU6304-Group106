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
 * Manages read/write operations for applications.csv, supports status updates and queries.
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

    /** Save an application record (append). */
    public void saveApplication(ApplicationRecord record) {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(FILE_PATH, true))) {
            bw.write(record.toCsvLine());
            bw.newLine();
        } catch (IOException e) {
            System.out.println("[Error] Failed to write application: " + e.getMessage());
        }
    }

    /** Read all application records; compatible with legacy formats. */
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

    /** Get application records by TA ID. */
    public List<ApplicationRecord> getByTaId(String taId) {
        List<ApplicationRecord> result = new ArrayList<>();
        for (ApplicationRecord record : getAllApplications()) {
            if (record.getTaId().equalsIgnoreCase(taId)) {
                result.add(record);
            }
        }
        return result;
    }

    /** Get application records by jobId. */
    public List<ApplicationRecord> getByJobId(String jobId) {
        List<ApplicationRecord> result = new ArrayList<>();
        for (ApplicationRecord record : getAllApplications()) {
            if (record.getJobId().equalsIgnoreCase(jobId)) {
                result.add(record);
            }
        }
        return result;
    }

    /** Find a single application by appId. */
    public ApplicationRecord getByAppId(String appId) {
        for (ApplicationRecord record : getAllApplications()) {
            if (record.getAppId().equalsIgnoreCase(appId)) {
                return record;
            }
        }
        return null;
    }

    /** Check whether a TA has already applied for a given job. */
    public boolean existsForTaAndJob(String taId, String jobId) {
        for (ApplicationRecord record : getAllApplications()) {
            if (record.getTaId().equalsIgnoreCase(taId) && record.getJobId().equalsIgnoreCase(jobId)) {
                return true;
            }
        }
        return false;
    }

    /** Update application status (optionally with rejection reason). */
    public boolean updateStatus(String appId, String newStatus) {
        return updateStatus(appId, newStatus, "");
    }

    /** Update the status and persist changes to file. */
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
