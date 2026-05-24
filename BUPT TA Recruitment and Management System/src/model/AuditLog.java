package model;

/**
 * Represents a single audit log entry.
 * <p>
 * Each log records a user action with a timestamp, the user who performed it,
 * their role, the action type, and additional details.
 * </p>
 */
public class AuditLog {
    private String timestamp;
    private String userId;
    private String role;
    private String action;
    private String detail;

    /**
     * Constructs a new audit log entry.
     *
     * @param timestamp when the action happened (yyyy-MM-dd HH:mm:ss)
     * @param userId    the ID of the user who performed the action
     * @param role      the role of that user (TA, MO, Admin)
     * @param action    a short code describing the action (e.g., LOGIN, TA_APPLY)
     * @param detail    extra information about the action
     */
    public AuditLog(String timestamp, String userId, String role, String action, String detail) {
        this.timestamp = timestamp;
        this.userId = userId;
        this.role = role;
        this.action = action;
        this.detail = detail;
    }

    /** @return the timestamp string */
    public String getTimestamp() { return timestamp; }
    /** @return the user ID */
    public String getUserId() { return userId; }
    /** @return the role name */
    public String getRole() { return role; }
    /** @return the action code */
    public String getAction() { return action; }
    /** @return the detail text */
    public String getDetail() { return detail; }

    /**
     * Formats this log entry as a single CSV line.
     * The fields are: timestamp, userId, role, action, detail.
     *
     * @return a comma-separated string
     */
    public String toCsvLine() {
        return timestamp + "," + userId + "," + role + "," + action + "," + detail;
    }
}