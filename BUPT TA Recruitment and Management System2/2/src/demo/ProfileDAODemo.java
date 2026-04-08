package demo;

import dao.ProfileDAO;
import model.Profile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class ProfileDAODemo {
    private static final Path PROFILE_FILE = Path.of("data", "profiles.csv");

    public static void main(String[] args) {
        String originalContent = readOriginalContent();

        try {
            ProfileDAO profileDAO = new ProfileDAO();

            System.out.println("=== ProfileDAO Demo ===");
            List<Profile> before = profileDAO.getAllProfiles();
            System.out.println("[1] Existing profile count: " + before.size());

            Profile demoProfile = new Profile(
                    "T002",
                    "Bob",
                    "20239876",
                    "SoftwareEngineering",
                    "13900139000"
            );

            profileDAO.saveOrUpdate(demoProfile);
            System.out.println("[2] Saved profile: " + demoProfile.toCsvLine());

            Profile loaded = profileDAO.getByTaId("T002");
            System.out.println("[3] Query by TA ID (T002): "
                    + (loaded == null ? "null" : loaded.toCsvLine()));

            List<Profile> after = profileDAO.getAllProfiles();
            System.out.println("[4] Profile count after saveOrUpdate: " + after.size());
        } finally {
            restoreOriginalContent(originalContent);
        }
    }

    private static String readOriginalContent() {
        try {
            if (Files.exists(PROFILE_FILE)) {
                return Files.readString(PROFILE_FILE, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            System.out.println("[Warn] Failed to backup profiles.csv: " + e.getMessage());
        }
        return "";
    }

    private static void restoreOriginalContent(String originalContent) {
        try {
            Files.createDirectories(PROFILE_FILE.getParent());
            Files.writeString(PROFILE_FILE, originalContent, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.out.println("[Warn] Failed to restore profiles.csv: " + e.getMessage());
        }
    }
}
