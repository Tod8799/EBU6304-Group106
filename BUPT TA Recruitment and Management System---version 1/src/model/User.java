package model;

/**
 * Represents a system user.
 * <p>Users have an id, email, password and role ("TA", "MO", or "Admin").</p>
 */
public class User {
    private String id;
    private String email;
    private String password;
    private String role; // "TA", "MO", or "Admin"

    /**
     * Create a new user.
     *
     * @param id       unique user id
     * @param email    user email
     * @param password user password
     * @param role     user role (TA/MO/Admin)
     */
    public User(String id, String email, String password, String role) {
        this.id = id;
        this.email = email;
        this.password = password;
        this.role = role;
    }

    /** Returns the user id. */
    public String getId() { return id; }

    /** Returns the user email. */
    public String getEmail() { return email; }

    /** Returns the user password. */
    public String getPassword() { return password; }

    /** Returns the user role. */
    public String getRole() { return role; }

    @Override
    public String toString() {
        return id + "," + email + "," + password + "," + role;
    }
}
