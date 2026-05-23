package model;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Represents a TA profile, including resume content (may be stored Base64-encoded).
 */
public class Profile {
    private String taId;
    private String name;
    private String studentId;
    private String major;
    private String phone;
    private String resumeText;
    private String resumeFileName;

    /**
     * Construct a Profile without resume information.
     */
    public Profile(String taId, String name, String studentId, String major, String phone) {
        this(taId, name, studentId, major, phone, "", "");
    }

    /**
     * Construct a Profile with resume text.
     */
    public Profile(String taId, String name, String studentId, String major, String phone, String resumeText) {
        this(taId, name, studentId, major, phone, resumeText, "");
    }

    /**
     * Full constructor.
     */
    public Profile(String taId, String name, String studentId, String major, String phone, String resumeText, String resumeFileName) {
        this.taId = taId;
        this.name = name;
        this.studentId = studentId;
        this.major = major;
        this.phone = phone;
        this.resumeText = resumeText == null ? "" : resumeText;
        this.resumeFileName = resumeFileName == null ? "" : resumeFileName;
    }

    /** Returns TA ID. */
    public String getTaId() { return taId; }
    /** Returns name. */
    public String getName() { return name; }
    /** Returns student ID. */
    public String getStudentId() { return studentId; }
    /** Returns major. */
    public String getMajor() { return major; }
    /** Returns phone number. */
    public String getPhone() { return phone; }
    /** Returns decoded resume text (Base64-decoded or raw). */
    public String getResumeText() { return resumeText; }
    /** Returns resume file name (may be Base64-encoded). */
    public String getResumeFileName() { return resumeFileName; }

    /**
     * Serialize the Profile to a CSV line, encoding resume fields with Base64.
     */
    public String toCsvLine() {
        return taId + "," + name + "," + studentId + "," + major + "," + phone + ","
                + encodeBase64(resumeText) + "," + encodeBase64(resumeFileName);
    }

    private String encodeBase64(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Decode Base64 text; if decoding fails, return the original text for backward compatibility.
     */
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
