import model.*;
import dao.*;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Main web server for the TA Recruitment System.
 * <p>
 * Uses JDK's built-in {@link HttpServer} to provide a REST‑like API
 * and serves static files from the {@code web/} directory.
 * All data is stored in CSV files via the DAO layer.
 * </p>
 */
public class WebServer {
    private static final int PORT = 8080;
    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_PDF_BYTES = 5 * 1024 * 1024;
    private static final Path RESUME_DIR = Path.of("data", "resumes");

    private static final UserDAO userDAO = new UserDAO();
    private static final ProfileDAO profileDAO = new ProfileDAO();
    private static final JobDAO jobDAO = new JobDAO();
    private static final ApplicationDAO applicationDAO = new ApplicationDAO();
    private static final AuditLogDAO auditLogDAO = new AuditLogDAO();

    private static int jobSequence = 1;
    private static int appSequence = 1;

    // 服务容器与路由
    // 使用 JDK 内置 HttpServer 启动服务并集中注册接口，实现单进程运行。
    /**
     * Starts the web server on port 8080 and registers all API endpoints.
     *
     * @param args not used
     * @throws IOException if the server cannot start
     */
    public static void main(String[] args) throws IOException {
        initializeSequenceNumbers();

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/", new StaticHandler());
        server.createContext("/api/login", new LoginHandler());
        server.createContext("/api/register", new RegisterHandler());
        server.createContext("/api/ta/profile", new TaProfileHandler());
        server.createContext("/api/ta/jobs-open", new TaOpenJobsHandler());
        server.createContext("/api/ta/apply", new TaApplyHandler());
        server.createContext("/api/ta/applications", new TaApplicationsHandler());
        server.createContext("/api/mo/job", new MoPostJobHandler());
        server.createContext("/api/mo/jobs", new MoJobsHandler());
        server.createContext("/api/mo/applicants", new MoApplicantsHandler());
        server.createContext("/api/mo/status", new MoStatusHandler());
        server.createContext("/api/mo/resume", new MoResumeHandler());
        server.createContext("/api/admin/metrics", new AdminMetricsHandler());
        server.createContext("/api/admin/workloads", new AdminWorkloadsHandler());
        server.createContext("/api/admin/logs", new AdminLogsHandler());
        server.createContext("/api/reset", new ResetHandler());

        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();
        System.out.println("Web UI started: http://localhost:" + PORT);
    }

    // Handler 分治
    // 每个业务域独立 Handler，登录、TA、MO、Admin 各自封装，避免“大控制器”。

    /**
     * Serves static resources (HTML, CSS, JS) from the {@code web/} folder.
     * The default path "/" maps to {@code web/index.html}.
     */
    static class StaticHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method Not Allowed");
                return;
            }

            String reqPath = exchange.getRequestURI().getPath();
            if ("/".equals(reqPath)) {
                reqPath = "/web/index.html";
            }
            if ("/favicon.ico".equals(reqPath)) {
                reqPath = "/web/favicon.ico";
            }

            Path filePath = Path.of("." + reqPath).normalize();
            if (!Files.exists(filePath) || Files.isDirectory(filePath)) {
                sendText(exchange, 404, "Not Found");
                return;
            }

            byte[] bytes = Files.readAllBytes(filePath);
            Headers headers = exchange.getResponseHeaders();
            headers.add("Content-Type", getContentType(filePath.toString()));
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    /**
     * Handles POST /api/login. Verifies email and password.
     */
    static class LoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, jsonError("Method Not Allowed"));
                return;
            }

            Map<String, String> data = parseFormBody(exchange);
            String email = data.getOrDefault("email", "").trim();
            String password = data.getOrDefault("password", "").trim();

            User user = userDAO.authenticate(email, password);
            if (user == null) {
                sendJson(exchange, 401, jsonError("Invalid email or password"));
                return;
            }

            writeLog(user.getId(), user.getRole(), "LOGIN", "User logged in.");
            sendJson(exchange, 200, "{\"ok\":true,\"id\":\"" + esc(user.getId()) + "\",\"role\":\"" + esc(user.getRole()) + "\"}");
        }
    }

    /**
     * Handles POST /api/register. Creates a new TA account after validation.
     */
    static class RegisterHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, jsonError("Method Not Allowed"));
                return;
            }

            Map<String, String> data = parseFormBody(exchange);
            String email = data.getOrDefault("email", "").trim();
            String password = data.getOrDefault("password", "").trim();

            if (!email.matches("^[^@\\s,]+@[^@\\s,]+\\.[^@\\s,]+$")) {
                sendJson(exchange, 400, jsonError("Please enter a valid email address"));
                return;
            }
            if (password.length() < 6) {
                sendJson(exchange, 400, jsonError("Password must be at least 6 characters"));
                return;
            }
            if (userDAO.emailExists(email)) {
                sendJson(exchange, 400, jsonError("Email is already registered"));
                return;
            }

            String taId = nextTaUserId();
            User user = new User(taId, email, password, "TA");
            userDAO.saveUser(user);
            writeLog(taId, "TA", "REGISTER", "TA account registered.");
            sendJson(exchange, 200, "{\"ok\":true,\"id\":\"" + esc(taId) + "\",\"role\":\"TA\"}");
        }
    }

    /**
     * Handles GET and POST for /api/ta/profile.
     * <p>
     * GET returns the TA's profile; POST creates or updates it,
     * including PDF resume upload.
     * </p>
     */
    static class TaProfileHandler implements HttpHandler {
        // 业务约束后端兜底
        // 关键规则全部在服务端判定，防止绕过前端造成脏数据。
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    Map<String, String> query = parseQuery(exchange.getRequestURI().getQuery());
                    String taId = query.getOrDefault("taId", "");
                    Profile p = profileDAO.getByTaId(taId);
                    if (p == null) {
                        sendJson(exchange, 200, "{\"ok\":true,\"exists\":false}");
                        return;
                    }
                    boolean resumeUploaded = hasStoredResumePdf(p);
                    String json = "{\"ok\":true,\"exists\":true,\"profile\":{"
                            + "\"taId\":\"" + esc(p.getTaId()) + "\","
                            + "\"name\":\"" + esc(p.getName()) + "\","
                            + "\"studentId\":\"" + esc(p.getStudentId()) + "\","
                            + "\"major\":\"" + esc(p.getMajor()) + "\","
                            + "\"phone\":\"" + esc(p.getPhone()) + "\","
                            + "\"resumePath\":\"" + esc(p.getResumePath()) + "\","
                            + "\"resumeFileName\":\"" + esc(p.getResumeFileName()) + "\","
                            + "\"resumeUploaded\":" + resumeUploaded + "}}";
                    sendJson(exchange, 200, json);
                    return;
                }

                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendJson(exchange, 405, jsonError("Method Not Allowed"));
                    return;
                }

                Map<String, String> data = parseFormBody(exchange);
                String taId = data.getOrDefault("taId", "").trim();
                String name = data.getOrDefault("name", "").trim();
                String studentId = data.getOrDefault("studentId", "").trim();
                String major = data.getOrDefault("major", "").trim();
                String phone = data.getOrDefault("phone", "").trim();
                String resumeFileName = data.getOrDefault("resumeFileName", "").trim();
                String resumeFileBase64 = data.getOrDefault("resumeFileBase64", "").trim();

                if (!studentId.matches("\\d{8}")) {
                    sendJson(exchange, 400, jsonError("Student ID must be exactly 8 digits"));
                    return;
                }
                if (!phone.matches("\\d{11}")) {
                    sendJson(exchange, 400, jsonError("Phone must be exactly 11 digits"));
                    return;
                }
                Profile existing = profileDAO.getByTaId(taId);
                String resumePath = existing == null ? "" : existing.getResumePath();
                String storedResumeFileName = existing == null ? "" : existing.getResumeFileName();
                if (!resumeFileBase64.isBlank()) {
                    String previousResumePath = resumePath;
                    byte[] fileBytes;
                    try {
                        fileBytes = Base64.getDecoder().decode(resumeFileBase64);
                    } catch (IllegalArgumentException e) {
                        sendJson(exchange, 400, jsonError("Resume file encoding is invalid"));
                        return;
                    }
                    if (fileBytes.length > MAX_PDF_BYTES) {
                        sendJson(exchange, 400, jsonError("Resume file is too large (max 5MB)"));
                        return;
                    }
                    resumePath = saveResumePdf(taId, resumeFileName, fileBytes);
                    storedResumeFileName = resumeFileName;
                    deleteResumeIfOwned(previousResumePath, resumePath);
                }

                profileDAO.saveOrUpdate(new Profile(taId, name, studentId, major, phone, resumePath, storedResumeFileName));
                writeLog(taId, "TA", "TA_PROFILE_SAVE", "TA profile saved.");
                sendJson(exchange, 200, "{\"ok\":true,\"resumePath\":\"" + esc(resumePath) + "\"}");
            } catch (IllegalArgumentException e) {
                sendJson(exchange, 400, jsonError(e.getMessage()));
            } catch (Exception e) {
                e.printStackTrace();
                sendJson(exchange, 500, jsonError("Server error while saving profile: " + e.getClass().getSimpleName()));
            } catch (Throwable t) {
                t.printStackTrace();
                sendJson(exchange, 500, jsonError("Server fatal error while saving profile"));
            }
        }
    }

    /**
     * Returns all open jobs as JSON.
     */
    static class TaOpenJobsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, jsonError("Method Not Allowed"));
                return;
            }
            List<Job> jobs = jobDAO.getOpenJobs();
            StringBuilder sb = new StringBuilder();
            sb.append("{\"ok\":true,\"jobs\":[");
            for (int i = 0; i < jobs.size(); i++) {
                Job job = jobs.get(i);
                if (i > 0) {
                    sb.append(',');
                }
                sb.append("{\"jobId\":\"").append(esc(job.getJobId())).append("\",")
                        .append("\"moId\":\"").append(esc(job.getMoId())).append("\",")
                        .append("\"title\":\"").append(esc(job.getTitle())).append("\",")
                        .append("\"requirements\":\"").append(esc(job.getRequirements())).append("\",")
                        .append("\"deadline\":\"").append(esc(job.getDeadline())).append("\"}");
            }
            sb.append("]}");
            sendJson(exchange, 200, sb.toString());
        }
    }

    /**
     * Handles job application submission.
     */
    static class TaApplyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, jsonError("Method Not Allowed"));
                return;
            }
            Map<String, String> data = parseFormBody(exchange);
            String taId = data.getOrDefault("taId", "").trim();
            String jobId = data.getOrDefault("jobId", "").trim();

            Profile profile = profileDAO.getByTaId(taId);
            if (profile == null) {
                sendJson(exchange, 400, jsonError("Please create profile first"));
                return;
            }

            Job job = jobDAO.getByJobId(jobId);
            if (job == null) {
                sendJson(exchange, 404, jsonError("Job not found"));
                return;
            }

            try {
                LocalDate deadline = LocalDate.parse(job.getDeadline());
                if (deadline.isBefore(LocalDate.now())) {
                    sendJson(exchange, 400, jsonError("Job expired"));
                    return;
                }
            } catch (Exception e) {
                sendJson(exchange, 400, jsonError("Job deadline invalid"));
                return;
            }

            if (applicationDAO.existsForTaAndJob(taId, jobId)) {
                sendJson(exchange, 400, jsonError("Already applied for this job"));
                return;
            }

            String appId = String.format("APP%03d", appSequence++);
            ApplicationRecord record = new ApplicationRecord(
                    appId,
                    jobId,
                    taId,
                    "Pending",
                    LocalDateTime.now().format(TS_FORMAT)
            );
            applicationDAO.saveApplication(record);
            writeLog(taId, "TA", "TA_APPLY", "Applied for " + jobId + " with appId " + appId);
            sendJson(exchange, 200, "{\"ok\":true,\"appId\":\"" + esc(appId) + "\"}");
        }
    }

    /**
     * Returns the logged-in TA's own applications.
     */
    static class TaApplicationsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, jsonError("Method Not Allowed"));
                return;
            }
            Map<String, String> query = parseQuery(exchange.getRequestURI().getQuery());
            String taId = query.getOrDefault("taId", "");
            List<ApplicationRecord> records = applicationDAO.getByTaId(taId);

            StringBuilder sb = new StringBuilder();
            sb.append("{\"ok\":true,\"applications\":[");
            for (int i = 0; i < records.size(); i++) {
                ApplicationRecord r = records.get(i);
                Job job = jobDAO.getByJobId(r.getJobId());
                if (i > 0) {
                    sb.append(',');
                }
                sb.append("{\"appId\":\"").append(esc(r.getAppId())).append("\",")
                        .append("\"jobId\":\"").append(esc(r.getJobId())).append("\",")
                        .append("\"jobTitle\":\"").append(esc(job == null ? "" : job.getTitle())).append("\",")
                        .append("\"status\":\"").append(esc(r.getStatus())).append("\",")
                        .append("\"rejectReason\":\"").append(esc(r.getRejectReason())).append("\",")
                        .append("\"appliedAt\":\"").append(esc(r.getAppliedAt())).append("\"}");
            }
            sb.append("]}");
            sendJson(exchange, 200, sb.toString());
        }
    }

    /**
     * Handles job posting by an MO.
     */
    static class MoPostJobHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, jsonError("Method Not Allowed"));
                return;
            }

            Map<String, String> data = parseFormBody(exchange);
            String moId = data.getOrDefault("moId", "").strip();
            String title = data.getOrDefault("title", "").strip();
            String requirements = data.getOrDefault("requirements", "").strip();
            String deadline = data.getOrDefault("deadline", "").strip();

            if (isEffectivelyBlank(moId) || isEffectivelyBlank(title)
                    || isEffectivelyBlank(requirements) || isEffectivelyBlank(deadline)) {
                sendJson(exchange, 400, jsonError("Job fields cannot be empty"));
                return;
            }

            LocalDate date;
            try {
                date = LocalDate.parse(deadline);
            } catch (Exception e) {
                sendJson(exchange, 400, jsonError("Invalid date format, use yyyy-MM-dd"));
                return;
            }
            if (date.isBefore(LocalDate.now())) {
                sendJson(exchange, 400, jsonError("Deadline cannot be in the past"));
                return;
            }

            String jobId = String.format("JOB%03d", jobSequence++);
            Job job = new Job(jobId, moId, title, requirements, deadline, LocalDateTime.now().format(TS_FORMAT));
            jobDAO.saveJob(job);
            writeLog(moId, "MO", "MO_POST_JOB", "Posted job " + jobId);
            sendJson(exchange, 200, "{\"ok\":true,\"jobId\":\"" + esc(jobId) + "\"}");
        }
    }

    /**
     * Returns the jobs posted by a specific MO.
     */
    static class MoJobsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, jsonError("Method Not Allowed"));
                return;
            }
            Map<String, String> query = parseQuery(exchange.getRequestURI().getQuery());
            String moId = query.getOrDefault("moId", "");

            List<Job> jobs = jobDAO.getJobsByMoId(moId);
            StringBuilder sb = new StringBuilder();
            sb.append("{\"ok\":true,\"jobs\":[");
            for (int i = 0; i < jobs.size(); i++) {
                Job job = jobs.get(i);
                if (i > 0) {
                    sb.append(',');
                }
                sb.append("{\"jobId\":\"").append(esc(job.getJobId())).append("\",")
                        .append("\"title\":\"").append(esc(job.getTitle())).append("\",")
                        .append("\"requirements\":\"").append(esc(job.getRequirements())).append("\",")
                        .append("\"deadline\":\"").append(esc(job.getDeadline())).append("\"}");
            }
            sb.append("]}");
            sendJson(exchange, 200, sb.toString());
        }
    }

    /**
     * Returns applicants for a job, sorted by active task count (ascending).
     */
    static class MoApplicantsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, jsonError("Method Not Allowed"));
                return;
            }
            Map<String, String> query = parseQuery(exchange.getRequestURI().getQuery());
            String moId = query.getOrDefault("moId", "").trim();
            String jobId = query.getOrDefault("jobId", "").trim();

            Job job = jobDAO.getByJobId(jobId);
            if (job == null || !job.getMoId().equalsIgnoreCase(moId)) {
                sendJson(exchange, 403, jsonError("Access denied for this job"));
                return;
            }

            List<ApplicationRecord> records = applicationDAO.getByJobId(jobId);
            records.sort(
                    Comparator.comparingInt((ApplicationRecord r) -> countActiveTasksForTa(r.getTaId()))
                            .thenComparing(ApplicationRecord::getAppliedAt)
            );
            StringBuilder sb = new StringBuilder();
            sb.append("{\"ok\":true,\"applicants\":[");
            for (int i = 0; i < records.size(); i++) {
                ApplicationRecord r = records.get(i);
                Profile p = profileDAO.getByTaId(r.getTaId());
                User u = userDAO.getById(r.getTaId());
                String name = p == null ? "N/A" : p.getName();
                boolean resumeUploaded = hasStoredResumePdf(p);
                int activeTaskCount = countActiveTasksForTa(r.getTaId());
                if (i > 0) {
                    sb.append(',');
                }
                sb.append("{\"appId\":\"").append(esc(r.getAppId())).append("\",")
                        .append("\"taId\":\"").append(esc(r.getTaId())).append("\",")
                        .append("\"email\":\"").append(esc(u == null ? "" : u.getEmail())).append("\",")
                        .append("\"name\":\"").append(esc(name)).append("\",")
                        .append("\"studentId\":\"").append(esc(p == null ? "" : p.getStudentId())).append("\",")
                        .append("\"major\":\"").append(esc(p == null ? "" : p.getMajor())).append("\",")
                        .append("\"phone\":\"").append(esc(p == null ? "" : p.getPhone())).append("\",")
                        .append("\"resumePath\":\"").append(esc(p == null ? "" : p.getResumePath())).append("\",")
                        .append("\"resumeFileName\":\"").append(esc(p == null ? "" : p.getResumeFileName())).append("\",")
                        .append("\"resumeUploaded\":").append(resumeUploaded).append(",")
                        .append("\"activeTaskCount\":").append(activeTaskCount).append(",")
                        .append("\"status\":\"").append(esc(r.getStatus())).append("\"}");
            }
            sb.append("]}");
            sendJson(exchange, 200, sb.toString());
        }
    }

    /**
     * Updates an application's status (MO action).
     */
    static class MoStatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, jsonError("Method Not Allowed"));
                return;
            }
            Map<String, String> data = parseFormBody(exchange);
            String moId = data.getOrDefault("moId", "").trim();
            String appId = data.getOrDefault("appId", "").trim();
            String status = data.getOrDefault("status", "").trim();
            String rejectReason = data.getOrDefault("rejectReason", "").trim();

            if (!isValidStatus(status)) {
                sendJson(exchange, 400, jsonError("Invalid status"));
                return;
            }

            if ("Rejected".equalsIgnoreCase(status) && rejectReason.isBlank()) {
                sendJson(exchange, 400, jsonError("Reject reason is required"));
                return;
            }

            ApplicationRecord record = applicationDAO.getByAppId(appId);
            if (record == null) {
                sendJson(exchange, 404, jsonError("Application not found"));
                return;
            }

            Job job = jobDAO.getByJobId(record.getJobId());
            if (job == null || !job.getMoId().equalsIgnoreCase(moId)) {
                sendJson(exchange, 403, jsonError("Access denied for this application"));
                return;
            }

            boolean updated = applicationDAO.updateStatus(appId, status, rejectReason);
            if (!updated) {
                sendJson(exchange, 500, jsonError("Failed to update status"));
                return;
            }
            ApplicationRecord latest = applicationDAO.getByAppId(appId);
            if (latest == null || !status.equalsIgnoreCase(latest.getStatus())) {
                sendJson(exchange, 500, jsonError("Status update was not persisted"));
                return;
            }

            String detail = "Updated " + appId + " to " + status;
            if ("Rejected".equalsIgnoreCase(status)) {
                detail += " (reason: " + rejectReason + ")";
            }
            writeLog(moId, "MO", "MO_UPDATE_STATUS", detail);
            sendJson(exchange, 200, "{\"ok\":true,\"status\":\"" + esc(latest.getStatus()) + "\"}");
        }
    }

    /**
     * Serves the resume PDF to an authorized MO.
     */
    static class MoResumeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, jsonError("Method Not Allowed"));
                return;
            }

            Map<String, String> query = parseQuery(exchange.getRequestURI().getQuery());
            String moId = query.getOrDefault("moId", "").trim();
            String appId = query.getOrDefault("appId", "").trim();

            ApplicationRecord record = applicationDAO.getByAppId(appId);
            if (record == null) {
                sendJson(exchange, 404, jsonError("Application not found"));
                return;
            }

            Job job = jobDAO.getByJobId(record.getJobId());
            if (job == null || !job.getMoId().equalsIgnoreCase(moId)) {
                sendJson(exchange, 403, jsonError("Access denied for this resume"));
                return;
            }

            Profile profile = profileDAO.getByTaId(record.getTaId());
            if (profile == null || profile.getResumePath().isBlank()) {
                sendJson(exchange, 404, jsonError("Resume PDF not uploaded"));
                return;
            }

            Path resumePath = Path.of(profile.getResumePath()).normalize();
            Path allowedRoot = RESUME_DIR.toAbsolutePath().normalize();
            Path absoluteResumePath = resumePath.toAbsolutePath().normalize();
            if (!absoluteResumePath.startsWith(allowedRoot) || !Files.exists(absoluteResumePath) || Files.isDirectory(absoluteResumePath)) {
                sendJson(exchange, 404, jsonError("Resume PDF not found"));
                return;
            }

            byte[] bytes = Files.readAllBytes(absoluteResumePath);
            Headers headers = exchange.getResponseHeaders();
            headers.add("Content-Type", "application/pdf");
            headers.add("Content-Disposition", "inline; filename=\"" + safePdfDownloadName(profile.getResumeFileName()) + "\"");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    /**
     * Returns system-wide recruitment metrics for Admin.
     */
    static class AdminMetricsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, jsonError("Method Not Allowed"));
                return;
            }

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

            StringBuilder dist = new StringBuilder();
            dist.append('{');
            int idx = 0;
            for (Map.Entry<String, Integer> entry : statusCounter.entrySet()) {
                if (idx++ > 0) {
                    dist.append(',');
                }
                dist.append("\"").append(esc(entry.getKey())).append("\":").append(entry.getValue());
            }
            dist.append('}');

            String json = "{\"ok\":true"
                    + ",\"totalJobs\":" + jobs.size()
                    + ",\"openJobs\":" + openJobs
                    + ",\"totalApplications\":" + records.size()
                    + ",\"completionRate\":" + String.format("%.2f", completionRate)
                    + ",\"statusDistribution\":" + dist + "}";
            sendJson(exchange, 200, json);
        }
    }

    /**
     * Returns detailed per‑TA workload statistics for Admin.
     */
    static class AdminWorkloadsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, jsonError("Method Not Allowed"));
                return;
            }

            Map<String, TaWorkloadSummary> summariesByTa = new HashMap<>();
            for (User user : userDAO.getAllUsers()) {
                if ("TA".equalsIgnoreCase(user.getRole())) {
                    summariesByTa.put(user.getId(), new TaWorkloadSummary(user.getId(), user.getEmail()));
                }
            }

            for (Profile profile : profileDAO.getAllProfiles()) {
                TaWorkloadSummary summary = getOrCreateWorkloadSummary(summariesByTa, profile.getTaId());
                summary.name = profile.getName();
                summary.studentId = profile.getStudentId();
                summary.major = profile.getMajor();
            }

            for (ApplicationRecord record : applicationDAO.getAllApplications()) {
                TaWorkloadSummary summary = getOrCreateWorkloadSummary(summariesByTa, record.getTaId());
                summary.totalApplications++;
                if ("Pending".equalsIgnoreCase(record.getStatus())) {
                    summary.pendingApplications++;
                } else {
                    summary.processedApplications++;
                }
                if ("Rejected".equalsIgnoreCase(record.getStatus())) {
                    summary.rejectedApplications++;
                }
                if ("Shortlisted".equalsIgnoreCase(record.getStatus())) {
                    summary.shortlistedApplications++;
                }
                if ("Interview".equalsIgnoreCase(record.getStatus())) {
                    summary.interviewApplications++;
                }
                if ("Hired".equalsIgnoreCase(record.getStatus())) {
                    summary.hiredApplications++;
                }
                if (isActiveTaskStatus(record.getStatus())) {
                    summary.activeWorkload++;
                    Job job = jobDAO.getByJobId(record.getJobId());
                    summary.activePositions.add(new TaActivePosition(
                            record.getJobId(),
                            job == null ? "" : job.getTitle(),
                            record.getStatus()
                    ));
                }
            }

            List<TaWorkloadSummary> summaries = new ArrayList<>(summariesByTa.values());
            summaries.sort(
                    Comparator.comparingInt((TaWorkloadSummary s) -> s.activeWorkload).reversed()
                            .thenComparing(Comparator.comparingInt((TaWorkloadSummary s) -> s.totalApplications).reversed())
                            .thenComparing(s -> s.taId)
            );

            StringBuilder sb = new StringBuilder();
            sb.append("{\"ok\":true,\"workloads\":[");
            for (int i = 0; i < summaries.size(); i++) {
                TaWorkloadSummary s = summaries.get(i);
                if (i > 0) {
                    sb.append(',');
                }
                sb.append("{\"taId\":\"").append(esc(s.taId)).append("\",")
                        .append("\"name\":\"").append(esc(s.name)).append("\",")
                        .append("\"email\":\"").append(esc(s.email)).append("\",")
                        .append("\"studentId\":\"").append(esc(s.studentId)).append("\",")
                        .append("\"major\":\"").append(esc(s.major)).append("\",")
                        .append("\"totalApplications\":").append(s.totalApplications).append(",")
                        .append("\"pendingApplications\":").append(s.pendingApplications).append(",")
                        .append("\"processedApplications\":").append(s.processedApplications).append(",")
                        .append("\"activeWorkload\":").append(s.activeWorkload).append(",")
                        .append("\"shortlistedApplications\":").append(s.shortlistedApplications).append(",")
                        .append("\"interviewApplications\":").append(s.interviewApplications).append(",")
                        .append("\"hiredApplications\":").append(s.hiredApplications).append(",")
                        .append("\"rejectedApplications\":").append(s.rejectedApplications).append(",")
                        .append("\"activePositions\":[");
                for (int j = 0; j < s.activePositions.size(); j++) {
                    TaActivePosition position = s.activePositions.get(j);
                    if (j > 0) {
                        sb.append(',');
                    }
                    sb.append("{\"jobId\":\"").append(esc(position.jobId)).append("\",")
                            .append("\"title\":\"").append(esc(position.title)).append("\",")
                            .append("\"status\":\"").append(esc(position.status)).append("\"}");
                }
                sb.append("]}");
            }
            sb.append("]}");
            sendJson(exchange, 200, sb.toString());
        }
    }

    /**
     * Returns all audit log entries for Admin.
     */
    static class AdminLogsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, jsonError("Method Not Allowed"));
                return;
            }
            List<AuditLog> logs = auditLogDAO.getAllLogs();
            StringBuilder sb = new StringBuilder();
            sb.append("{\"ok\":true,\"logs\":[");
            for (int i = 0; i < logs.size(); i++) {
                AuditLog log = logs.get(i);
                if (i > 0) {
                    sb.append(',');
                }
                sb.append("{\"timestamp\":\"").append(esc(log.getTimestamp())).append("\",")
                        .append("\"userId\":\"").append(esc(log.getUserId())).append("\",")
                        .append("\"role\":\"").append(esc(log.getRole())).append("\",")
                        .append("\"action\":\"").append(esc(log.getAction())).append("\",")
                        .append("\"detail\":\"").append(esc(log.getDetail())).append("\"}");
            }
            sb.append("]}");
            sendJson(exchange, 200, sb.toString());
        }
    }

    // 序列号与重置机制
    // 启动时重建 JOB/APP 序列避免 ID 冲突，提供 reset 接口便于演示快速复位。
    /**
     * Resets all business data (profiles, jobs, applications, logs, resume files)
     * for testing purposes.
     */
    static class ResetHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, jsonError("Method Not Allowed"));
                return;
            }
            clearFile("data/profiles.csv");
            clearFile("data/jobs.csv");
            clearFile("data/applications.csv");
            clearFile("data/logs.csv");
            clearDirectory(RESUME_DIR);
            initializeSequenceNumbers();
            sendJson(exchange, 200, "{\"ok\":true}");
        }
    }

    // ======================== Helper methods ========================

    private static TaWorkloadSummary getOrCreateWorkloadSummary(Map<String, TaWorkloadSummary> summariesByTa, String taId) {
        TaWorkloadSummary summary = summariesByTa.get(taId);
        if (summary == null) {
            summary = new TaWorkloadSummary(taId, "");
            summariesByTa.put(taId, summary);
        }
        return summary;
    }

    private static class TaWorkloadSummary {
        private final String taId;
        private final String email;
        private String name = "";
        private String studentId = "";
        private String major = "";
        private int totalApplications = 0;
        private int pendingApplications = 0;
        private int processedApplications = 0;
        private int activeWorkload = 0;
        private int shortlistedApplications = 0;
        private int interviewApplications = 0;
        private int hiredApplications = 0;
        private int rejectedApplications = 0;
        private final List<TaActivePosition> activePositions = new ArrayList<>();

        private TaWorkloadSummary(String taId, String email) {
            this.taId = taId;
            this.email = email;
        }
    }

    private static class TaActivePosition {
        private final String jobId;
        private final String title;
        private final String status;

        private TaActivePosition(String jobId, String title, String status) {
            this.jobId = jobId;
            this.title = title;
            this.status = status;
        }
    }

    /**
     * Checks if a string is one of the valid application statuses.
     *
     * @param status the status to test
     * @return true if valid (Pending, Shortlisted, Rejected, Interview, Hired)
     */
    private static boolean isValidStatus(String status) {
        return "Pending".equalsIgnoreCase(status)
                || "Shortlisted".equalsIgnoreCase(status)
                || "Rejected".equalsIgnoreCase(status)
                || "Interview".equalsIgnoreCase(status)
                || "Hired".equalsIgnoreCase(status);
    }

    private static boolean isActiveTaskStatus(String status) {
        return "Shortlisted".equalsIgnoreCase(status)
                || "Interview".equalsIgnoreCase(status)
                || "Hired".equalsIgnoreCase(status);
    }

    /**
     * Returns {@code true} if a string consists only of whitespace or is empty.
     */
    private static boolean isEffectivelyBlank(String value) {
        if (value == null || value.isEmpty()) {
            return true;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!Character.isWhitespace(c) && !Character.isSpaceChar(c)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Counts how many active tasks (Shortlisted/Interview/Hired) a TA currently has.
     *
     * @param taId the TA's user ID
     * @return number of active applications
     */
    private static int countActiveTasksForTa(String taId) {
        int count = 0;
        for (ApplicationRecord record : applicationDAO.getByTaId(taId)) {
            if (isActiveTaskStatus(record.getStatus())) {
                count++;
            }
        }
        return count;
    }

    /**
     * Generates the next available TA user ID in the format {@code Txxx}.
     *
     * @return a new unique ID (e.g., "T002", "T003")
     */
    private static String nextTaUserId() {
        int max = 0;
        for (User user : userDAO.getAllUsers()) {
            String id = user.getId();
            if (id != null && id.matches("T\\d+")) {
                try {
                    max = Math.max(max, Integer.parseInt(id.substring(1)));
                } catch (Exception ignored) {
                }
            }
        }
        return String.format("T%03d", max + 1);
    }

    /**
     * Saves a resume PDF to the filesystem and returns the relative path.
     *
     * @param taId      the TA's user ID
     * @param fileName  original file name (must end with .pdf)
     * @param fileBytes raw PDF bytes
     * @return the stored path (e.g., "data/resumes/T001_1234567890.pdf")
     * @throws IOException if the file cannot be written
     * @throws IllegalArgumentException if the file is not a valid PDF
     */
    private static String saveResumePdf(String taId, String fileName, byte[] fileBytes) throws IOException {
        if (fileName == null || !fileName.toLowerCase().endsWith(".pdf")) {
            throw new IllegalArgumentException("Only PDF resumes are supported");
        }
        if (fileBytes.length < 5 || fileBytes[0] != '%' || fileBytes[1] != 'P' || fileBytes[2] != 'D' || fileBytes[3] != 'F' || fileBytes[4] != '-') {
            throw new IllegalArgumentException("Uploaded resume must be a valid PDF file");
        }
        Files.createDirectories(RESUME_DIR);
        String safeTaId = taId.replaceAll("[^A-Za-z0-9_-]", "_");
        String storedName = safeTaId + "_" + System.currentTimeMillis() + ".pdf";
        Path target = RESUME_DIR.resolve(storedName).normalize();
        Files.write(target, fileBytes);
        return target.toString().replace('\\', '/');
    }

    /**
     * Returns a safe download file name for a PDF resume.
     */
    private static String safePdfDownloadName(String fileName) {
        String safeName = fileName == null || fileName.isBlank() ? "resume.pdf" : fileName;
        safeName = safeName.replace("\\", "_").replace("/", "_").replace("\"", "_");
        if (!safeName.toLowerCase().endsWith(".pdf")) {
            safeName += ".pdf";
        }
        return safeName;
    }

    /**
     * Checks whether a profile has a stored resume PDF on disk.
     */
    private static boolean hasStoredResumePdf(Profile profile) {
        if (profile == null || profile.getResumePath().isBlank()) {
            return false;
        }
        try {
            Path allowedRoot = RESUME_DIR.toAbsolutePath().normalize();
            Path resumePath = Path.of(profile.getResumePath()).toAbsolutePath().normalize();
            return resumePath.startsWith(allowedRoot)
                    && Files.isRegularFile(resumePath)
                    && resumePath.getFileName().toString().toLowerCase().endsWith(".pdf");
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * Deletes the previous resume PDF if it differs from the current one.
     */
    private static void deleteResumeIfOwned(String previousPath, String currentPath) throws IOException {
        if (previousPath == null || previousPath.isBlank() || previousPath.equals(currentPath)) {
            return;
        }
        Path allowedRoot = RESUME_DIR.toAbsolutePath().normalize();
        Path previous = Path.of(previousPath).toAbsolutePath().normalize();
        if (previous.startsWith(allowedRoot) && Files.isRegularFile(previous)) {
            Files.deleteIfExists(previous);
        }
    }

    /**
     * Reads existing job and application IDs from CSV files to set the correct next sequence numbers.
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

    // 持久化与可追踪
    // 接口最终调用 DAO 写入 CSV，并同步写审计日志，形成可回放轨迹。
    /**
     * Writes a single log entry to the audit log.
     *
     * @param userId the user who performed the action
     * @param role   the user's role
     * @param action the type of action (e.g., LOGIN, MO_POST_JOB)
     * @param detail additional details about the action
     */
    private static void writeLog(String userId, String role, String action, String detail) {
        auditLogDAO.append(new AuditLog(
                LocalDateTime.now().format(TS_FORMAT),
                userId,
                role,
                action,
                detail
        ));
    }

    // 请求解析统一
    // 前端使用表单编码提交，后端统一通过 parseFormBody 和 parseQuery 解析，避免多套协议。
    /**
     * Parses the request body as URL-encoded form data.
     *
     * @param exchange the HTTP exchange
     * @return a map of form parameters
     * @throws IOException if the body cannot be read
     */
    private static Map<String, String> parseFormBody(HttpExchange exchange) throws IOException {
        byte[] bytes;
        try (InputStream is = exchange.getRequestBody()) {
            bytes = is.readAllBytes();
        }
        String body = new String(bytes, StandardCharsets.UTF_8);
        return parseQuery(body);
    }

    /**
     * Parses a URL-encoded query string into key-value pairs.
     *
     * @param query the raw query string (may be null or empty)
     * @return a map of parameters (keys are URL-decoded)
     */
    private static Map<String, String> parseQuery(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null || query.isEmpty()) {
            return map;
        }

        String[] pairs = query.split("&");
        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
            String key = urlDecode(kv[0]);
            String value = kv.length > 1 ? urlDecode(kv[1]) : "";
            map.put(key, value);
        }
        return map;
    }

    private static String urlDecode(String s) {
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            return s;
        }
    }

    // 响应与错误统一
    // 所有接口统一 JSON 返回，成功带 ok:true，失败统一 jsonError，前端可直接按 error 展示。
    /**
     * Sends a JSON response with the given HTTP status.
     *
     * @param exchange the HTTP exchange
     * @param status   HTTP status code
     * @param json     the JSON string to send
     * @throws IOException if writing fails
     */
    private static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] data = json.getBytes(StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/json; charset=utf-8");
        headers.set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, data.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(data);
        }
    }

    /**
     * Sends a plain-text response.
     */
    private static void sendText(HttpExchange exchange, int status, String text) throws IOException {
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, data.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(data);
        }
    }

    /**
     * Builds a JSON error object: {"ok":false,"error":"...message..."}
     */
    private static String jsonError(String message) {
        return "{\"ok\":false,\"error\":\"" + esc(message) + "\"}";
    }

    /**
     * Escapes a string for safe inclusion in a JSON string value.
     */
    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        String normalized = s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", " ")
                .replace("\r", " ");
        // Strip other control chars to keep JSON valid.
        return normalized.replaceAll("[\\x00-\\x1F\\x7F]", " ");
    }

    /**
     * Determines the MIME type for static file serving based on the file extension.
     */
    private static String getContentType(String path) {
        if (path.endsWith(".html")) {
            return "text/html; charset=utf-8";
        }
        if (path.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        if (path.endsWith(".js")) {
            return "application/javascript; charset=utf-8";
        }
        if (path.endsWith(".json")) {
            return "application/json; charset=utf-8";
        }
        return "application/octet-stream";
    }

    private static void clearFile(String fileName) {
        try {
            Files.writeString(Path.of(fileName), "", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to clear file: " + fileName, e);
        }
    }

    /**
     * Deletes all regular files inside the given directory.
     *
     * @param directory the directory to clean
     */
    private static void clearDirectory(Path directory) {
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (var paths = Files.list(directory)) {
            for (Path path : paths.toList()) {
                if (Files.isRegularFile(path)) {
                    Files.deleteIfExists(path);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to clear directory: " + directory, e);
        }
    }
}