package dao;

import model.ApplicationRecord;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ApplicationDAO {
    private static final String FILE_PATH = "data/applications.csv";

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

    public void saveApplication(ApplicationRecord record) {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(FILE_PATH, true))) {
            bw.write(record.toCsvLine());
            bw.newLine();
        } catch (IOException e) {
            System.out.println("[Error] Failed to write application: " + e.getMessage());
        }
    }

    public List<ApplicationRecord> getAllApplications() {
        List<ApplicationRecord> records = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(FILE_PATH))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] data = line.split(",", -1);
                if (data.length == 5) {
                    records.add(new ApplicationRecord(data[0], data[1], data[2], data[3], data[4]));
                } else if (data.length == 6) {
                    // Legacy format: appId,jobId,taId,cvPath(ignored),status,appliedAt
                    records.add(new ApplicationRecord(data[0], data[1], data[2], data[4], data[5]));
                }
            }
        } catch (IOException e) {
            System.out.println("[Error] Failed to read applications: " + e.getMessage());
        }
        return records;
    }

    public List<ApplicationRecord> getByTaId(String taId) {
        List<ApplicationRecord> result = new ArrayList<>();
        for (ApplicationRecord record : getAllApplications()) {
            if (record.getTaId().equalsIgnoreCase(taId)) {
                result.add(record);
            }
        }
        return result;
    }

    public List<ApplicationRecord> getByJobId(String jobId) {
        List<ApplicationRecord> result = new ArrayList<>();
        for (ApplicationRecord record : getAllApplications()) {
            if (record.getJobId().equalsIgnoreCase(jobId)) {
                result.add(record);
            }
        }
        return result;
    }

    public ApplicationRecord getByAppId(String appId) {
        for (ApplicationRecord record : getAllApplications()) {
            if (record.getAppId().equalsIgnoreCase(appId)) {
                return record;
            }
        }
        return null;
    }

    public boolean existsForTaAndJob(String taId, String jobId) {
        for (ApplicationRecord record : getAllApplications()) {
            if (record.getTaId().equalsIgnoreCase(taId) && record.getJobId().equalsIgnoreCase(jobId)) {
                return true;
            }
        }
        return false;
    }

    public boolean updateStatus(String appId, String newStatus) {
        List<ApplicationRecord> records = getAllApplications();
        boolean updated = false;
        for (ApplicationRecord record : records) {
            if (record.getAppId().equalsIgnoreCase(appId)) {
                record.setStatus(newStatus);
                updated = true;
                break;
            }
        }

        if (updated) {
            rewriteAll(records);
        }
        return updated;
    }

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
}
