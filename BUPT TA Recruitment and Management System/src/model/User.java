package model;

/**
 * Represents a user in the system.
 * Contains the user's id, email, password and role ("TA", "MO", "Admin").
 */
public class User {
    private String id;
    private String email;
    private String password;
    private String role; // "TA", "MO", or "Admin"

    /**
     * Create a new User instance.
     * @param id unique user identifier
     * @param email user email
     * @param password user password (stored in plain text; should be hashed in real projects)
     * @param role user role
     */
    public User(String id, String email, String password, String role) {
        this.id = id;
        this.email = email;
        this.password = password;
        this.role = role;
    }

    /** Returns the user ID. */
    public String getId() { return id; }
    /** Returns the user email. */
    public String getEmail() { return email; }
    /** Returns the user password (plain text). */
    public String getPassword() { return password; }
    /** Returns the user role. */
    public String getRole() { return role; }

    @Override
    public String toString() {
        return id + "," + email + "," + password + "," + role;
    }
}
