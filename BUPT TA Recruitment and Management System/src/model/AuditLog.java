package model;

/**
 * Simple audit log record entity.
 */
public class AuditLog {
    private String timestamp;
    private String userId;
    private String role;
    private String action;
    private String detail;

    /**
     * Construct an audit log record.
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
    /** Returns the user ID. */
    public String getUserId() { return userId; }
    /** Returns the role. */
    public String getRole() { return role; }
    /** Returns the action type. */
    public String getAction() { return action; }
    /** Returns the record detail. */
    public String getDetail() { return detail; }

    /** Serialize the log to a CSV line. */
    public String toCsvLine() {
        return timestamp + "," + userId + "," + role + "," + action + "," + detail;
    }
}
