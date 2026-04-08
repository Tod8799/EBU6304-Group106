package model;

public class AuditLog {
    private String timestamp;
    private String userId;
    private String role;
    private String action;
    private String detail;

    public AuditLog(String timestamp, String userId, String role, String action, String detail) {
        this.timestamp = timestamp;
        this.userId = userId;
        this.role = role;
        this.action = action;
        this.detail = detail;
    }

    public String getTimestamp() { return timestamp; }
    public String getUserId() { return userId; }
    public String getRole() { return role; }
    public String getAction() { return action; }
    public String getDetail() { return detail; }

    public String toCsvLine() {
        return timestamp + "," + userId + "," + role + "," + action + "," + detail;
    }
}
