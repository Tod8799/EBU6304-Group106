package dao;

import model.Profile;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * CSV-backed DAO for `Profile` stored in `data/profiles.csv`.
 */
public class ProfileDAO {
    private static final String FILE_PATH = "data/profiles.csv";

    public ProfileDAO() {
        ensureFile();
    }

    private void ensureFile() {
        File file = new File(FILE_PATH);
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                System.out.println("[Error] Failed to initialize profiles file: " + e.getMessage());
            }
        }
    }

    /**
     * Return all profiles.
     *
     * @return list of profiles
     */
    public List<Profile> getAllProfiles() {
        List<Profile> profiles = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(FILE_PATH))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] data = line.split(",", -1);
                if (data.length == 5) {
                    profiles.add(new Profile(data[0], data[1], data[2], data[3], data[4]));
                }
            }
        } catch (IOException e) {
            System.out.println("[Error] Failed to read profiles: " + e.getMessage());
        }
        return profiles;
    }

    /**
     * Find a profile by TA id.
     *
     * @param taId ta id
     * @return Profile or null
     */
    public Profile getByTaId(String taId) {
        for (Profile profile : getAllProfiles()) {
            if (profile.getTaId().equalsIgnoreCase(taId)) {
                return profile;
            }
        }
        return null;
    }

    /**
     * Save a new profile or update an existing one.
     *
     * @param profile profile to save or update
     */
    public void saveOrUpdate(Profile profile) {
        List<Profile> all = getAllProfiles();
        boolean updated = false;

        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).getTaId().equalsIgnoreCase(profile.getTaId())) {
                all.set(i, profile);
                updated = true;
                break;
            }
        }

        if (!updated) {
            all.add(profile);
        }

        rewriteAll(all);
    }

    private void rewriteAll(List<Profile> profiles) {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(FILE_PATH, false))) {
            for (Profile profile : profiles) {
                bw.write(profile.toCsvLine());
                bw.newLine();
            }
        } catch (IOException e) {
            System.out.println("[Error] Failed to write profiles: " + e.getMessage());
        }
    }
}
