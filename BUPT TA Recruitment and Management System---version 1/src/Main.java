import dao.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import model.*;

/**
 * Console entry point for the TA Recruitment System.
 * <p>Provides a simple interactive console UI to demonstrate user authentication,
 * job posting, applications and review workflows.</p>
 */
public class Main {
    private static Scanner scanner = new Scanner(System.in);
    private static UserDAO userDAO = new UserDAO();
    private static ProfileDAO profileDAO = new ProfileDAO();
    private static JobDAO jobDAO = new JobDAO();
    private static ApplicationDAO applicationDAO = new ApplicationDAO();
    private static AuditLogDAO auditLogDAO = new AuditLogDAO();
    private static int jobSequence = 1;
    private static int appSequence = 1;

    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Application entry point. Starts the interactive console loop.
     *
     * @param args command line arguments (unused)
     */
    public static void main(String[] args) {
        System.out.println("=========================================");
        System.out.println("  TA Recruitment System - Version 2.0    ");
        System.out.println("=========================================");
        initializeSequenceNumbers();

        while (true) {
            System.out.println("\n--- Login Interface ---");
            System.out.println("Enter 'exit' as email to quit.");
            System.out.print("Email: ");
            String email = scanner.nextLine();
            if ("exit".equalsIgnoreCase(email)) {
                System.out.println("System exited.");
                return;
            }

            System.out.print("Password: ");
            String password = scanner.nextLine();

            User user = userDAO.authenticate(email, password);

            if (user != null) {
                System.out.println("\n[Success] Welcome, " + user.getId());
                System.out.println("Role identified as: " + user.getRole());

                writeLog(user, "LOGIN", "User logged in.");
                showDashboard(user);
            } else {
                System.out.println("\n[Denied] Invalid email or password. Try again.");
            }
        }
    }

    /**
     * Show the dashboard corresponding to the user's role and handle logout.
     *
     * @param user the currently authenticated user
     */
    private static void showDashboard(User user) {
        System.out.println("\n-----------------------------------------");
        switch (user.getRole()) {
            case "Admin":
                adminMenu(user);
                break;
            case "MO":
                moMenu(user);
                break;
            case "TA":
                taMenu(user);
                break;
            default:
                System.out.println(">> Unknown Role Permission.");
        }
        System.out.println("-----------------------------------------");
        writeLog(user, "LOGOUT", "User logged out.");
        System.out.println("Logged out. Returning to login screen.");
    }

    /**
     * Interactive menu for TA role operations (profile, browsing and applying).
     *
     * @param user the TA user
     */
    private static void taMenu(User user) {
        while (true) {
            System.out.println("\n>> TA DASHBOARD");
            System.out.println("1. Create or Update Profile");
            System.out.println("2. View My Profile");
            System.out.println("3. Browse Open Jobs");
            System.out.println("4. Apply for a Job");
            System.out.println("5. View My Applications");
            System.out.println("0. Logout");
            System.out.print("Select: ");
            String option = scanner.nextLine();

            if ("1".equals(option)) {
                createOrUpdateProfile(user);
            } else if ("2".equals(option)) {
                showProfile(user);
            } else if ("3".equals(option)) {
                browseOpenJobs();
            } else if ("4".equals(option)) {
                applyForJob(user);
            } else if ("5".equals(option)) {
                viewMyApplications(user);
            } else if ("0".equals(option)) {
                return;
            } else {
                System.out.println("[Denied] Invalid option.");
            }
        }
    }

    /**
     * Interactive menu for MO role operations (post jobs, view applicants).
     *
     * @param user the MO user
     */
    private static void moMenu(User user) {
        while (true) {
            System.out.println("\n>> MO DASHBOARD");
            System.out.println("1. Post New Job");
            System.out.println("2. View My Jobs");
            System.out.println("3. View Applicants by Job");
            System.out.println("4. Update Applicant Status");
            System.out.println("0. Logout");
            System.out.print("Select: ");
            String option = scanner.nextLine();

            if ("1".equals(option)) {
                postJob(user);
            } else if ("2".equals(option)) {
                viewMyJobs(user);
            } else if ("3".equals(option)) {
                viewApplicantsByJob(user);
            } else if ("4".equals(option)) {
                updateApplicantStatus(user);
            } else if ("0".equals(option)) {
                return;
            } else {
                System.out.println("[Denied] Invalid option.");
            }
        }
    }

    /**
     * Interactive menu for Admin role operations (metrics and logs read-only).
     *
     * @param user the admin user
     */
    private static void adminMenu(User user) {
        while (true) {
            System.out.println("\n>> ADMIN DASHBOARD");
            System.out.println("1. View Recruitment Metrics");
            System.out.println("2. View Operation Logs (Read Only)");
            System.out.println("0. Logout");
            System.out.print("Select: ");
            String option = scanner.nextLine();

            if ("1".equals(option)) {
                showMetrics();
            } else if ("2".equals(option)) {
                showLogsReadOnly();
            } else if ("0".equals(option)) {
                return;
            } else {
                System.out.println("[Denied] Invalid option.");
            }
        }
    }

    /**
     * Create or update the TA profile for the given user with input validation.
     *
     * @param user the TA user creating/updating the profile
     */
    private static void createOrUpdateProfile(User user) {
        System.out.println("\n--- Create or Update TA Profile ---");
        System.out.print("Name: ");
        String name = scanner.nextLine().trim();

        System.out.print("Student ID (8 digits): ");
        String studentId = scanner.nextLine().trim();
        if (!studentId.matches("\\d{8}")) {
            System.out.println("[Denied] Invalid student ID format. Require exactly 8 digits.");
            return;
        }

        System.out.print("Major: ");
        String major = scanner.nextLine().trim();

        System.out.print("Phone (11 digits): ");
        String phone = scanner.nextLine().trim();
        if (!phone.matches("\\d{11}")) {
            System.out.println("[Denied] Invalid phone format. Require exactly 11 digits.");
            return;
        }

        Profile profile = new Profile(user.getId(), name, studentId, major, phone);
        profileDAO.saveOrUpdate(profile);
        writeLog(user, "TA_PROFILE_SAVE", "TA profile saved.");
        System.out.println("[Success] Profile saved.");
    }

    /**
     * Display the TA profile for the given user.
     *
     * @param user the TA user whose profile is requested
     */
    private static void showProfile(User user) {
        Profile profile = profileDAO.getByTaId(user.getId());
        if (profile == null) {
            System.out.println("[Info] No profile found.");
            return;
        }

        System.out.println("\n--- My Profile ---");
        System.out.println("TA ID: " + profile.getTaId());
        System.out.println("Name: " + profile.getName());
        System.out.println("Student ID: " + profile.getStudentId());
        System.out.println("Major: " + profile.getMajor());
        System.out.println("Phone: " + profile.getPhone());
    }

    /**
     * Print a list of currently open jobs (not expired).
     */
    private static void browseOpenJobs() {
        List<Job> jobs = jobDAO.getOpenJobs();
        System.out.println("\n--- Open Jobs (Not Expired) ---");
        if (jobs.isEmpty()) {
            System.out.println("[Info] No open jobs.");
            return;
        }

        for (Job job : jobs) {
            System.out.println(job.getJobId() + " | " + job.getTitle() + " | Deadline: " + job.getDeadline());
            System.out.println("  Requirements: " + job.getRequirements());
            System.out.println("  Posted by MO: " + job.getMoId());
        }
    }

    /**
     * Apply the current TA user for a chosen job after validating profile and deadline.
     *
     * @param user the TA user applying for a job
     */
    private static void applyForJob(User user) {
        Profile profile = profileDAO.getByTaId(user.getId());
        if (profile == null) {
            System.out.println("[Denied] Please create your profile first.");
            return;
        }

        browseOpenJobs();
        System.out.print("Enter Job ID to apply: ");
        String jobId = scanner.nextLine().trim();
        Job job = jobDAO.getByJobId(jobId);

        if (job == null) {
            System.out.println("[Denied] Job not found.");
            return;
        }

        LocalDate deadline;
        try {
            deadline = LocalDate.parse(job.getDeadline());
        } catch (Exception e) {
            System.out.println("[Denied] Job deadline is invalid.");
            return;
        }

        if (deadline.isBefore(LocalDate.now())) {
            System.out.println("[Denied] Job expired. Cannot apply.");
            return;
        }

        if (applicationDAO.existsForTaAndJob(user.getId(), jobId)) {
            System.out.println("[Denied] You already applied for this job.");
            return;
        }

        String appId = String.format("APP%03d", appSequence++);
        ApplicationRecord record = new ApplicationRecord(
                appId,
                jobId,
                user.getId(),
                "Pending",
                LocalDateTime.now().format(TS_FORMAT)
        );
        applicationDAO.saveApplication(record);
        writeLog(user, "TA_APPLY", "Applied for " + jobId + " with appId " + appId);
        System.out.println("[Success] Application submitted. App ID: " + appId);
    }

    /**
     * Show all applications submitted by the given TA user.
     *
     * @param user the TA user whose applications will be displayed
     */
    private static void viewMyApplications(User user) {
        List<ApplicationRecord> records = applicationDAO.getByTaId(user.getId());
        System.out.println("\n--- My Applications ---");
        if (records.isEmpty()) {
            System.out.println("[Info] No applications found.");
            return;
        }

        for (ApplicationRecord record : records) {
            System.out.println(record.getAppId()
                    + " | Job: " + record.getJobId()
                    + " | Status: " + record.getStatus()
                    + " | Applied At: " + record.getAppliedAt());
        }
    }

    /**
     * Post a new job on behalf of the MO user with validation on the deadline.
     *
     * @param user the MO user posting the job
     */
    private static void postJob(User user) {
        System.out.println("\n--- Post New Job ---");
        System.out.print("Title: ");
        String title = scanner.nextLine().trim();
        System.out.print("Core Requirements: ");
        String requirements = scanner.nextLine().trim();
        System.out.print("Deadline (yyyy-MM-dd): ");
        String deadline = scanner.nextLine().trim();

        LocalDate date;
        try {
            date = LocalDate.parse(deadline);
        } catch (Exception e) {
            System.out.println("[Denied] Invalid date format.");
            return;
        }

        if (date.isBefore(LocalDate.now())) {
            System.out.println("[Denied] Deadline cannot be in the past.");
            return;
        }

        String jobId = String.format("JOB%03d", jobSequence++);
        Job job = new Job(jobId, user.getId(), title, requirements, deadline, LocalDateTime.now().format(TS_FORMAT));
        jobDAO.saveJob(job);
        writeLog(user, "MO_POST_JOB", "Posted job " + jobId);
        System.out.println("[Success] Job posted. Job ID: " + jobId);
    }

    /**
     * Display jobs posted by the given MO user.
     *
     * @param user the MO user
     */
    private static void viewMyJobs(User user) {
        List<Job> jobs = jobDAO.getJobsByMoId(user.getId());
        System.out.println("\n--- My Posted Jobs ---");
        if (jobs.isEmpty()) {
            System.out.println("[Info] No jobs posted yet.");
            return;
        }

        for (Job job : jobs) {
            System.out.println(job.getJobId() + " | " + job.getTitle() + " | Deadline: " + job.getDeadline());
            System.out.println("  Requirements: " + job.getRequirements());
        }
    }

    /**
     * View applicants for a specific job owned by the MO user.
     *
     * @param user the MO user requesting applicants
     */
    private static void viewApplicantsByJob(User user) {
        viewMyJobs(user);
        System.out.print("Enter Job ID to view applicants: ");
        String jobId = scanner.nextLine().trim();

        Job job = jobDAO.getByJobId(jobId);
        if (job == null || !job.getMoId().equalsIgnoreCase(user.getId())) {
            System.out.println("[Denied] Access denied: this job is not under your ownership.");
            return;
        }

        List<ApplicationRecord> records = applicationDAO.getByJobId(jobId);
        if (records.isEmpty()) {
            System.out.println("[Info] No applicants for this job yet.");
            return;
        }

        System.out.println("\nApplicants for " + jobId + ":");
        for (ApplicationRecord record : records) {
            Profile p = profileDAO.getByTaId(record.getTaId());
            String displayName = p == null ? "N/A" : p.getName();
            System.out.println(record.getAppId()
                    + " | TA: " + record.getTaId()
                    + " (" + displayName + ")"
                    + " | Status: " + record.getStatus());
        }
    }

    /**
     * Update the status of an application (Pending/Shortlisted/Rejected/Interview/Hired).
     * Ensures permission checks and writes audit logs on success.
     *
     * @param user the MO user performing the update
     */
    private static void updateApplicantStatus(User user) {
        System.out.print("Enter App ID to update status: ");
        String appId = scanner.nextLine().trim();
        ApplicationRecord record = applicationDAO.getByAppId(appId);
        if (record == null) {
            System.out.println("[Denied] Application not found.");
            return;
        }

        Job job = jobDAO.getByJobId(record.getJobId());
        if (job == null || !job.getMoId().equalsIgnoreCase(user.getId())) {
            System.out.println("[Denied] Access denied: cannot update applications outside your jobs.");
            return;
        }

        System.out.print("New status (Pending/Shortlisted/Rejected/Interview/Hired): ");
        String status = scanner.nextLine().trim();
        if (!isValidStatus(status)) {
            System.out.println("[Denied] Invalid status value.");
            return;
        }

        boolean ok = applicationDAO.updateStatus(appId, status);
        if (ok) {
            writeLog(user, "MO_UPDATE_STATUS", "Updated " + appId + " to " + status);
            System.out.println("[Success] Status updated and synced.");
        } else {
            System.out.println("[Denied] Status update failed.");
        }
    }

    /**
     * Validate if the provided status is one of the supported statuses.
     *
     * @param status status string to validate
     * @return true if valid, false otherwise
     */
    private static boolean isValidStatus(String status) {
        return "Pending".equalsIgnoreCase(status)
                || "Shortlisted".equalsIgnoreCase(status)
                || "Rejected".equalsIgnoreCase(status)
                || "Interview".equalsIgnoreCase(status)
                || "Hired".equalsIgnoreCase(status);
    }

    /**
     * Compute and display basic recruitment metrics and status distribution.
     */
    private static void showMetrics() {
        List<Job> jobs = jobDAO.getAllJobs();
        List<ApplicationRecord> records = applicationDAO.getAllApplications();
        LocalDate today = LocalDate.now();

        int openJobs = 0;
        for (Job job : jobs) {
            try {
                LocalDate deadline = LocalDate.parse(job.getDeadline());
                if (!deadline.isBefore(today)) {
                    openJobs++;
                }
            } catch (Exception ignored) {
            }
        }

        int processed = 0;
        Map<String, Integer> statusCounter = new HashMap<>();
        for (ApplicationRecord record : records) {
            statusCounter.put(record.getStatus(), statusCounter.getOrDefault(record.getStatus(), 0) + 1);
            if (!"Pending".equalsIgnoreCase(record.getStatus())) {
                processed++;
            }
        }

        double completionRate = records.isEmpty() ? 0.0 : (processed * 100.0 / records.size());

        System.out.println("\n--- Recruitment Metrics ---");
        System.out.println("Total Jobs: " + jobs.size());
        System.out.println("Open Jobs: " + openJobs);
        System.out.println("Total Applications: " + records.size());
        System.out.printf("Application Processing Completion: %.2f%%\n", completionRate);
        System.out.println("Status Distribution:");
        if (statusCounter.isEmpty()) {
            System.out.println("  (empty)");
        } else {
            for (Map.Entry<String, Integer> entry : statusCounter.entrySet()) {
                System.out.println("  " + entry.getKey() + ": " + entry.getValue());
            }
        }
    }

    /**
     * Print audit logs in read-only mode for admin users.
     */
    private static void showLogsReadOnly() {
        List<AuditLog> logs = auditLogDAO.getAllLogs();
        System.out.println("\n--- Operation Logs (Read Only) ---");
        if (logs.isEmpty()) {
            System.out.println("[Info] No logs yet.");
            return;
        }

        for (AuditLog log : logs) {
            System.out.println(log.getTimestamp()
                    + " | " + log.getUserId()
                    + " | " + log.getRole()
                    + " | " + log.getAction()
                    + " | " + log.getDetail());
        }
    }

    /**
     * Helper to append an audit log entry for a user action.
     *
     * @param user   the user performing the action
     * @param action short action code
     * @param detail descriptive detail text
     */
    private static void writeLog(User user, String action, String detail) {
        auditLogDAO.append(new AuditLog(
                LocalDateTime.now().format(TS_FORMAT),
                user.getId(),
                user.getRole(),
                action,
                detail
        ));
    }

    /**
     * Initialize job and application sequence counters from persisted records to avoid ID collisions.
     */
    private static void initializeSequenceNumbers() {
        int maxJob = 0;
        int maxApp = 0;
        for (Job job : jobDAO.getAllJobs()) {
            if (job.getJobId().startsWith("JOB")) {
                try {
                    maxJob = Math.max(maxJob, Integer.parseInt(job.getJobId().substring(3)));
                } catch (Exception ignored) {
                }
            }
        }
        for (ApplicationRecord record : applicationDAO.getAllApplications()) {
            if (record.getAppId().startsWith("APP")) {
                try {
                    maxApp = Math.max(maxApp, Integer.parseInt(record.getAppId().substring(3)));
                } catch (Exception ignored) {
                }
            }
        }
        jobSequence = maxJob + 1;
        appSequence = maxApp + 1;
    }
}
