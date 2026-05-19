package model;

/**
 * Represents an audit/log entry for tracking operations.
 */
public class AuditLog {
    private String timestamp;
    private String userId;
    private String role;
    private String action;
    private String detail;

    /**
     * Create a new audit log entry.
     *
     * @param timestamp formatted timestamp
     * @param userId    user id performing the action
     * @param role      user role
     * @param action    action code
     * @param detail    additional details
     */
    public AuditLog(String timestamp, String userId, String role, String action, String detail) {
        this.timestamp = timestamp;
        this.userId = userId;
        this.role = role;
        this.action = action;
        this.detail = detail;
    }

    /** Returns the timestamp. */
    public String getTimestamp() { return timestamp; }

    /** Returns the user id. */
    public String getUserId() { return userId; }

    /** Returns the role. */
    public String getRole() { return role; }

    /** Returns the action code. */
    public String getAction() { return action; }

    /** Returns the detail text. */
    public String getDetail() { return detail; }

    /** Render audit log as CSV line. */
    public String toCsvLine() {
        return timestamp + "," + userId + "," + role + "," + action + "," + detail;
    }
}
