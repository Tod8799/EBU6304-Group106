package model;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Holds a TA's profile information including personal details
 * and the path to the uploaded resume PDF file.
 * <p>
 * The resume file is stored as a physical PDF under {@code data/resumes/},
 * and only its path and original file name are saved in the CSV record.
 * </p>
 */
public class Profile {
    private String taId;
    private String name;
    private String studentId;
    private String major;
    private String phone;
    private String resumePath;
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
     * Creates a profile with resume path but no file name.
     *
     * @param taId       TA user ID
     * @param name       full name
     * @param studentId  8-digit student ID
     * @param major      subject of study
     * @param phone      11-digit phone number
     * @param resumePath relative path to the saved PDF
     */
    public Profile(String taId, String name, String studentId, String major, String phone, String resumePath) {
        this(taId, name, studentId, major, phone, resumePath, "");
    }

    /**
     * Creates a full profile with resume path and original file name.
     *
     * @param taId            TA user ID
     * @param name            full name
     * @param studentId       8-digit student ID
     * @param major           subject of study
     * @param phone           11-digit phone number
     * @param resumePath      path to the uploaded resume PDF
     * @param resumeFileName  original upload file name (e.g., "resume.pdf")
     */
    public Profile(String taId, String name, String studentId, String major, String phone,
                   String resumePath, String resumeFileName) {
        this.taId = taId;
        this.name = name;
        this.studentId = studentId;
        this.major = major;
        this.phone = phone;
        this.resumePath = resumePath == null ? "" : resumePath;
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
    /** @return the path to the stored resume PDF */
    public String getResumePath() { return resumePath; }
    /** @return the original resume file name */
    public String getResumeFileName() { return resumeFileName; }

    /**
     * Converts this profile to a CSV line.
     * The file name is Base64-encoded so that commas and special characters
     * do not break the CSV format.
     *
     * @return a CSV line: taId,name,studentId,major,phone,resumePath,resumeFileName(Base64)
     */
    public String toCsvLine() {
        return taId + "," + name + "," + studentId + "," + major + "," + phone + ","
                + resumePath + "," + encodeBase64(resumeFileName);
    }

    private String encodeBase64(String text) {
        if (text == null || text.isEmpty()) return "";
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Decodes a Base64-encoded string stored in CSV back to plain text.
     * If the string is not valid Base64, it is returned as-is (for backward compatibility).
     *
     * @param text Base64-encoded or plain string
     * @return the decoded plain text
     */
    public static String decodeBase64(String text) {
        if (text == null || text.isEmpty()) return "";
        try {
            byte[] bytes = Base64.getDecoder().decode(text);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            return text;
        }
    }
}