package model;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class Profile {
    private String taId;
    private String name;
    private String studentId;
    private String major;
    private String phone;
    private String resumeText;

    public Profile(String taId, String name, String studentId, String major, String phone) {
        this(taId, name, studentId, major, phone, "");
    }

    public Profile(String taId, String name, String studentId, String major, String phone, String resumeText) {
        this.taId = taId;
        this.name = name;
        this.studentId = studentId;
        this.major = major;
        this.phone = phone;
        this.resumeText = resumeText == null ? "" : resumeText;
    }

    public String getTaId() { return taId; }
    public String getName() { return name; }
    public String getStudentId() { return studentId; }
    public String getMajor() { return major; }
    public String getPhone() { return phone; }
    public String getResumeText() { return resumeText; }

    public String toCsvLine() {
        return taId + "," + name + "," + studentId + "," + major + "," + phone + "," + encodeBase64(resumeText);
    }

    private String encodeBase64(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    public static String decodeBase64(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(text);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            // Backward compatibility for old raw content.
            return text;
        }
    }
}
