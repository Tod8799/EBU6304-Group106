package dao;

import model.User;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data access object for user accounts stored in {@code data/users.csv}.
 * <p>
 * On first access, the file is created with three default accounts (Admin, MO, TA).
 * Provides methods to save, read, authenticate, look up users, and check email existence.
 * </p>
 */
public class UserDAO {
    private static final String FILE_PATH = "data/users.csv";

    /**
     * Ensures the CSV file exists. If not, creates it and seeds default accounts.
     */
    public UserDAO() {
        File file = new File(FILE_PATH);
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
                System.out.println("[System] Initializing data file: " + FILE_PATH);
                saveUser(new User("A001", "admin@bupt.edu", "admin123", "Admin"));
                saveUser(new User("M001", "mo@bupt.edu", "mo123", "MO"));
                saveUser(new User("T001", "ta@bupt.edu", "ta123", "TA"));
            } catch (IOException e) {
                System.out.println("[Error] Failed to create data file: " + e.getMessage());
            }
        }
    }

    /**
     * Appends a new user to the CSV file.
     * @param user the user to save
     */
    public void saveUser(User user) {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(FILE_PATH, true))) {
            bw.write(user.toString());
            bw.newLine();
        } catch (IOException e) {
            System.out.println("[Error] Write failed: " + e.getMessage());
        }
    }

    /**
     * Reads all users from the CSV file.
     * @return a list of all users
     */
    public List<User> getAllUsers() {
        List<User> users = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(FILE_PATH))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] data = line.split(",");
                if (data.length == 4) {
                    users.add(new User(data[0], data[1], data[2], data[3]));
                }
            }
        } catch (IOException e) {
            System.out.println("[Error] Read failed: " + e.getMessage());
        }
        return users;
    }

    /**
     * Checks if the given email and password match a saved user.
     *
     * @param email    user email
     * @param password user password (plain text)
     * @return the matching {@link User} or {@code null} if not found
     */
    public User authenticate(String email, String password) {
        for (User user : getAllUsers()) {
            if (user.getEmail().equalsIgnoreCase(email) && user.getPassword().equals(password)) {
                return user;
            }
        }
        return null;
    }

    /**
     * Finds a user by their unique ID.
     * @param id user ID (e.g., "T001")
     * @return the user or {@code null} if not found
     */
    public User getById(String id) {
        for (User user : getAllUsers()) {
            if (user.getId().equalsIgnoreCase(id)) {
                return user;
            }
        }
        return null;
    }

    /**
     * Checks whether an email is already registered.
     * @param email the email to test
     * @return {@code true} if the email already exists
     */
    public boolean emailExists(String email) {
        for (User user : getAllUsers()) {
            if (user.getEmail().equalsIgnoreCase(email)) {
                return true;
            }
        }
        return false;
    }
}