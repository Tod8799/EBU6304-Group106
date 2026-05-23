package dao;

import model.AuditLog;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Data access object for audit logs stored in {@code data/logs.csv}.
 * <p>
 * Provides methods to append new logs and to read all logs.
 * The file is automatically created if it does not exist.
 * </p>
 */
public class AuditLogDAO {
    private static final String FILE_PATH = "data/logs.csv";

    /**
     * Creates a new DAO and ensures the log file exists.
     */
    public AuditLogDAO() {
        ensureFile();
    }

    /**
     * Creates the log file (and parent directory) if they are missing.
     */
    private void ensureFile() {
        File file = new File(FILE_PATH);
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                System.out.println("[Error] Failed to initialize logs file: " + e.getMessage());
            }
        }
    }

    /**
     * Appends a new log entry to the end of the CSV file.
     *
     * @param log the audit log to write
     */
    public void append(AuditLog log) {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(FILE_PATH, true))) {
            bw.write(log.toCsvLine());
            bw.newLine();
        } catch (IOException e) {
            System.out.println("[Error] Failed to write log: " + e.getMessage());
        }
    }

    /**
     * Reads all log entries from the CSV file.
     *
     * @return a list of audit logs, in the order they were written
     */
    public List<AuditLog> getAllLogs() {
        List<AuditLog> logs = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(FILE_PATH))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] data = line.split(",", 5);
                if (data.length == 5) {
                    logs.add(new AuditLog(data[0], data[1], data[2], data[3], data[4]));
                }
            }
        } catch (IOException e) {
            System.out.println("[Error] Failed to read logs: " + e.getMessage());
        }
        return logs;
    }
}