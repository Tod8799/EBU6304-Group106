package model;

/**
 * Represents a TA profile containing personal and academic information.
 */
public class Profile {
    private String taId;
    private String name;
    private String studentId;
    private String major;
    private String phone;

    /**
     * Constructs a TA profile.
     */
    public Profile(String taId, String name, String studentId, String major, String phone) {
        this.taId = taId;
        this.name = name;
        this.studentId = studentId;
        this.major = major;
        this.phone = phone;
    }

    /** Returns the TA id. */
    public String getTaId() { return taId; }

    /** Returns the name. */
    public String getName() { return name; }

    /** Returns the student id. */
    public String getStudentId() { return studentId; }

    /** Returns the major. */
    public String getMajor() { return major; }

    /** Returns the phone number. */
    public String getPhone() { return phone; }

    /** Render profile as a CSV line. */
    public String toCsvLine() {
        return taId + "," + name + "," + studentId + "," + major + "," + phone;
    }
}
