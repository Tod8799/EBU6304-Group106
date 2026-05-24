package model;

/**
 * Represents a system user with an ID, email, password, and role.
 * <p>
 * Roles are "TA", "MO", or "Admin". This is a simple data class
 * used by {@link dao.UserDAO} for authentication and user management.
 * </p>
 */
public class User {
    private String id;
    private String email;
    private String password;
    private String role;

    /**
     * Creates a new user.
     *
     * @param id       unique user identifier (e.g., "T001")
     * @param email    login email
     * @param password plain-text password (demo only)
     * @param role     either "TA", "MO", or "Admin"
     */
    public User(String id, String email, String password, String role) {
        this.id = id;
        this.email = email;
        this.password = password;
        this.role = role;
    }

    /** @return the user ID */
    public String getId() { return id; }

    /** @return the email */
    public String getEmail() { return email; }

    /** @return the password */
    public String getPassword() { return password; }

    /** @return the role */
    public String getRole() { return role; }

    /**
     * Returns a CSV representation: id,email,password,role
     * @return comma-separated line
     */
    @Override
    public String toString() {
        return id + "," + email + "," + password + "," + role;
    }
}