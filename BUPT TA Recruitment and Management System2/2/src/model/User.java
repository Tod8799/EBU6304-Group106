package model;

public class User {
    private String id;
    private String email;
    private String password;
    private String role; // "TA", "MO", or "Admin"

    public User(String id, String email, String password, String role) {
        this.id = id;
        this.email = email;
        this.password = password;
        this.role = role;
    }

    public String getId() { return id; }
    public String getEmail() { return email; }
    public String getPassword() { return password; }
    public String getRole() { return role; }

    @Override
    public String toString() {
        return id + "," + email + "," + password + "," + role;
    }
}
