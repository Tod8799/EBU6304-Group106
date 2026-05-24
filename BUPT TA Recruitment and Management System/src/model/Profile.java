package model;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Holds a TA's profile information including personal details and uploaded resume.
 * <p>
 * Resumes are stored as plain text (extracted from uploaded files) and the file name
 * is saved for reference. Both fields are Base64-encoded when written to CSV.
 * </p>
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
     * Creates a basic profile without a resume.
     *
     * @param taId      TA user ID
     * @param name      full name
     * @param studentId 8-digit student ID
     * @param major     subject of study
     * @param phone     11-digit phone number
     */
    public Profile(String taId, String name, String studentId, String major, String phone) {
        this(taId, name, studentId, major, phone, "", "");
    }

    /**
     * Creates a profile with resume text but no file name.
     *
     * @param taId       TA user ID
     * @param name       full name
     * @param studentId  8-digit student ID
     * @param major      subject of study
     * @param phone      11-digit phone number
     * @param resumeText extracted resume content
     */
    public Profile(String taId, String name, String studentId, String major, String phone, String resumeText) {
        this(taId, name, studentId, major, phone, resumeText, "");
    }

    /**
     * Creates a full profile with resume text and file name.
     *
     * @param taId            TA user ID
     * @param name            full name
     * @param studentId       8-digit student ID
     * @param major           subject of study
     * @param phone           11-digit phone number
     * @param resumeText      extracted resume content
     * @param resumeFileName  original upload file name (e.g., "resume.pdf")
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

    /** @return the TA ID */
    public String getTaId() { return taId; }

    /** @return the name */
    public String getName() { return name; }

    /** @return the student ID */
    public String getStudentId() { return studentId; }

    /** @return the major */
    public String getMajor() { return major; }

    /** @return the phone number */
    public String getPhone() { return phone; }

    /** @return the resume text (plain text) */
    public String getResumeText() { return resumeText; }

    /** @return the original resume file name */
    public String getResumeFileName() { return resumeFileName; }

    /**
     * Converts this profile to a CSV line.
     * <p>
     * The resume text and file name are Base64-encoded so that commas and special characters
     * do not break the CSV format.
     * </p>
     *
     * @return a CSV line in the format: taId,name,studentId,major,phone,resumeTextBase64,resumeFileNameBase64
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
     * Decodes a Base64-encoded string stored in CSV back to plain text.
     * <p>
     * If the string is not valid Base64, it is returned as-is (for backward compatibility).
     * </p>
     *
     * @param text Base64-encoded or plain string
     * @return the decoded plain text
     */
    public static String decodeBase64(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(text);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            return text;
        }
    }
}