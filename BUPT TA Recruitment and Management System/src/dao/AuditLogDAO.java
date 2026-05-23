package dao;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import model.AuditLog;

/**
 * Audit log persistence utility operating on data/logs.csv.
 */
public class AuditLogDAO {
    private static final String FILE_PATH = "data/logs.csv";

    public AuditLogDAO() {
        ensureFile();
    }

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

    /** Append an audit log entry. */
    public void append(AuditLog log) {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(FILE_PATH, true))) {
            bw.write(log.toCsvLine());
            bw.newLine();
        } catch (IOException e) {
            System.out.println("[Error] Failed to write log: " + e.getMessage());
        }
    }

    /** Read all audit logs. */
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
