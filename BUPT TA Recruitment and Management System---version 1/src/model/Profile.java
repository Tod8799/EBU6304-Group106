package model;

public class Profile {
    private String taId;
    private String name;
    private String studentId;
    private String major;
    private String phone;

    public Profile(String taId, String name, String studentId, String major, String phone) {
        this.taId = taId;
        this.name = name;
        this.studentId = studentId;
        this.major = major;
        this.phone = phone;
    }

    public String getTaId() { return taId; }
    public String getName() { return name; }
    public String getStudentId() { return studentId; }
    public String getMajor() { return major; }
    public String getPhone() { return phone; }

    public String toCsvLine() {
        return taId + "," + name + "," + studentId + "," + major + "," + phone;
    }
}
