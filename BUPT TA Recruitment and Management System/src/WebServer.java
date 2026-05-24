import model.*;
import dao.*;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Main web server for the TA Recruitment System.
 * <p>
 * Uses JDK's built-in {@link HttpServer} to provide a REST-like API
 * and serves static files from the {@code web/} directory.
 * All data is stored in CSV files via the DAO layer.
 * </p>
 */
public class WebServer {
    private static final int PORT = 8080;
    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_INFLATED_STREAM_BYTES = 2 * 1024 * 1024;
    private static final int MAX_PDF_BYTES = 5 * 1024 * 1024;
    private static final int MAX_PDF_TOKEN_CHUNKS = 200;
    private static final int MAX_FLATE_STREAMS_TO_SCAN = 40;
    private static final int MAX_PDF_SCAN_CHARS = 2_000_000;
    private static final int MAX_PDF_STRING_LOOKBACK = 600;
    private static final int MAX_PDF_ARRAY_LOOKBACK = 8000;

    private static final UserDAO userDAO = new UserDAO();
    private static final ProfileDAO profileDAO = new ProfileDAO();
    private static final JobDAO jobDAO = new JobDAO();
    private static final ApplicationDAO applicationDAO = new ApplicationDAO();
    private static final AuditLogDAO auditLogDAO = new AuditLogDAO();

    private static int jobSequence = 1;
    private static int appSequence = 1;

    // 服务容器与路由
    // 使用 JDK 内置 HttpServer 启动服务并集中注册接口，实现单进程运行。
    // 实现位置：创建服务 WebServer.java:42，注册路由 WebServer.java:44。
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
        server.createContext("/api/ta/profile", new TaProfileHandler());
        server.createContext("/api/ta/jobs-open", new TaOpenJobsHandler());
        server.createContext("/api/ta/apply", new TaApplyHandler());
        server.createContext("/api/ta/applications", new TaApplicationsHandler());
        server.createContext("/api/mo/job", new MoPostJobHandler());
        server.createContext("/api/mo/jobs", new MoJobsHandler());
        server.createContext("/api/mo/applicants", new MoApplicantsHandler());
        server.createContext("/api/mo/status", new MoStatusHandler());
        server.createContext("/api/admin/metrics", new AdminMetricsHandler());
        server.createContext("/api/admin/logs", new AdminLogsHandler());
        server.createContext("/api/reset", new ResetHandler());

        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();
        System.out.println("Web UI started: http://localhost:" + PORT);
    }

    // Handler 分治
    // 每个业务域独立 Handler，登录、TA、MO、Admin 各自封装，避免“大控制器”。
    // 示例：登录 WebServer.java:91，TA投递 WebServer.java:188，MO审核 WebServer.java:376，Admin指标 WebServer.java:426。

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
     * Handles GET and POST for /api/ta/profile.
     * <p>
     * GET returns the TA's profile; POST creates or updates it,
     * including CV upload.
     * </p>
     */
    static class TaProfileHandler implements HttpHandler {
        // 业务约束后端兜底
        // 关键规则全部在服务端判定，防止绕过前端造成脏数据。
        // 示例：TA建档格式校验 WebServer.java:148，投递前置校验 WebServer.java:201，MO审核状态与权限校验 WebServer.java:390。
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
                    String safeResumeText = sanitizeResumeTextForResponse(p.getResumeText());
                    boolean resumeUploaded = !p.getResumeFileName().isBlank() || !safeResumeText.isBlank();
                    String json = "{\"ok\":true,\"exists\":true,\"profile\":{"
                            + "\"taId\":\"" + esc(p.getTaId()) + "\","
                            + "\"name\":\"" + esc(p.getName()) + "\","
                            + "\"studentId\":\"" + esc(p.getStudentId()) + "\","
                            + "\"major\":\"" + esc(p.getMajor()) + "\","
                            + "\"phone\":\"" + esc(p.getPhone()) + "\","
                            + "\"resumeText\":\"" + esc(safeResumeText) + "\","
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
                String resumeText = data.getOrDefault("resumeText", "");
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
                if (!resumeFileBase64.isBlank()) {
                    byte[] fileBytes;
                    try {
                        fileBytes = Base64.getDecoder().decode(resumeFileBase64);
                    } catch (IllegalArgumentException e) {
                        sendJson(exchange, 400, jsonError("Resume file encoding is invalid"));
                        return;
                    }
                    if (fileBytes.length > 512 * 1024) {
                        sendJson(exchange, 400, jsonError("Resume file is too large (max 512KB)"));
                        return;
                    }
                    resumeText = extractResumeText(resumeFileName, fileBytes);
                }
                if (resumeText.length() > 20000) {
                    sendJson(exchange, 400, jsonError("Resume text is too long (max 20000 chars)"));
                    return;
                }

                profileDAO.saveOrUpdate(new Profile(taId, name, studentId, major, phone, resumeText, resumeFileName));
                writeLog(taId, "TA", "TA_PROFILE_SAVE", "TA profile saved.");
                sendJson(exchange, 200, "{\"ok\":true,\"parsedLength\":" + resumeText.length() + "}");
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
            String moId = data.getOrDefault("moId", "").trim();
            String title = data.getOrDefault("title", "").trim();
            String requirements = data.getOrDefault("requirements", "").trim();
            String deadline = data.getOrDefault("deadline", "").trim();

            if (moId.isBlank() || title.isBlank() || requirements.isBlank() || deadline.isBlank()) {
                sendJson(exchange, 400, jsonError("All job fields are required"));
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
                String safeResumeText = p == null ? "" : sanitizeResumeTextForResponse(p.getResumeText());
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
                        .append("\"resumeText\":\"").append(esc(safeResumeText)).append("\",")
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
    // 实现位置：序列初始化 WebServer.java:530，重置接口 WebServer.java:506。
    /**
     * Resets all business data (profiles, jobs, applications, logs) for testing purposes.
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
            initializeSequenceNumbers();
            sendJson(exchange, 200, "{\"ok\":true}");
        }
    }

    // ===== Helper methods =====

    /**
     * Checks if a string is one of the valid application statuses.
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
     * Counts how many active tasks (Shortlisted/Interview/Hired) a TA currently has.
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
     * Extracts readable text from a resume file based on its extension.
     * <p>
     * Supports TXT, DOCX, and PDF. For PDFs, a hybrid approach is used:
     * try embedded text first, then OCR fallback (if PDF is scanned).
     * </p>
     *
     * @param fileName  original file name (used to guess type)
     * @param fileBytes raw file content
     * @return the extracted text, whitespace-normalized
     * @throws IllegalArgumentException if the type is unsupported or no readable text found
     */
    private static String extractResumeText(String fileName, byte[] fileBytes) {
        String extension = "";
        int dot = fileName.lastIndexOf('.');
        if (dot >= 0 && dot < fileName.length() - 1) {
            extension = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        }

        String text;
        switch (extension) {
            case "txt":
                text = new String(fileBytes, StandardCharsets.UTF_8);
                break;
            case "docx":
                text = extractDocxText(fileBytes);
                break;
            case "pdf":
                if (fileBytes.length > MAX_PDF_BYTES) {
                    return "";
                }
                text = extractPdfText(fileBytes);
                break;
            default:
                throw new IllegalArgumentException("Unsupported resume type. Please upload TXT, DOCX, or PDF");
        }

        String normalized = normalizeWhitespace(text);
        if (normalized.isBlank()) {
            if ("pdf".equals(extension)) {
                throw new IllegalArgumentException("Cannot extract readable text from image-only scanned PDF");
            }
            throw new IllegalArgumentException("Cannot extract readable text from the uploaded resume");
        }
        return normalized;
    }

    /**
     * Extracts readable text from a DOCX file (ZIP containing word/document.xml).
     */
    private static String extractDocxText(byte[] fileBytes) {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(fileBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!"word/document.xml".equals(entry.getName())) {
                    continue;
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                zis.transferTo(out);
                String xml = out.toString(StandardCharsets.UTF_8);
                String withParagraphBreaks = xml.replace("</w:p>", "\n");
                Matcher matcher = Pattern.compile("<w:t[^>]*>(.*?)</w:t>", Pattern.DOTALL).matcher(withParagraphBreaks);
                StringBuilder sb = new StringBuilder();
                while (matcher.find()) {
                    sb.append(unescapeXml(matcher.group(1))).append(' ');
                }
                if (sb.length() > 0) {
                    return sb.toString();
                }
                return withParagraphBreaks.replaceAll("<[^>]+>", " ");
            }
        } catch (IOException ignored) {
        }
        throw new IllegalArgumentException("Cannot read DOCX file");
    }

    /**
     * Extracts readable text from a PDF file.
     * <p>
     * Tries to find text in visible operators (Tj, TJ) and in FlateDecode streams.
     * Falls back to plain parenthesized strings if no tokens found.
     * </p>
     */
    private static String extractPdfText(byte[] fileBytes) {
        String source = new String(fileBytes, StandardCharsets.ISO_8859_1);
        List<String> chunks = new ArrayList<>();

        collectPdfTextTokens(source, chunks);

        // Many PDFs store readable operators inside FlateDecode streams.
        for (String streamText : extractFlateDecodedStreams(fileBytes)) {
            collectPdfTextTokens(streamText, chunks);
        }

        // Fallback for form-like PDFs: plain parenthesized strings without Tj/TJ operators.
        if (chunks.isEmpty()) {
            collectPlainPdfParenStrings(source, chunks);
        }

        if (chunks.isEmpty()) return "";
        return cleanupPdfReadableText(chunks);
    }

    private static void collectPdfTextTokens(String source, List<String> chunks) {
        if (source == null || source.isEmpty()) return;
        int scanLen = Math.min(source.length(), MAX_PDF_SCAN_CHARS);
        String bounded = source.substring(0, scanLen);
        collectPdfTextBeforeOperator(bounded, "Tj", chunks);
        collectPdfTextBeforeOperator(bounded, "TJ", chunks);
        collectPdfArrayText(bounded, chunks);
    }

    private static void collectPdfTextBeforeOperator(String source, String operator, List<String> chunks) {
        int from = 0;
        while (chunks.size() < MAX_PDF_TOKEN_CHUNKS) {
            int opPos = source.indexOf(operator, from);
            if (opPos < 0) break;

            int closeParen = opPos - 1;
            while (closeParen >= 0 && Character.isWhitespace(source.charAt(closeParen))) {
                closeParen--;
            }
            if (closeParen < 0 || source.charAt(closeParen) != ')') {
                from = opPos + operator.length();
                continue;
            }

            int openParen = closeParen - 1;
            int depth = 0;
            boolean found = false;
            while (openParen >= 0 && closeParen - openParen <= MAX_PDF_STRING_LOOKBACK) {
                char c = source.charAt(openParen);
                if (c == ')') {
                    depth++;
                } else if (c == '(') {
                    if (depth == 0) {
                        chunks.add(decodePdfString(source.substring(openParen + 1, closeParen)));
                        found = true;
                        break;
                    }
                    depth--;
                }
                openParen--;
            }
            if (!found) {
                from = opPos + operator.length();
                continue;
            }
            from = opPos + operator.length();
        }
    }

    private static void collectPdfArrayText(String source, List<String> chunks) {
        int from = 0;
        while (chunks.size() < MAX_PDF_TOKEN_CHUNKS) {
            int tjPos = source.indexOf(" TJ", from);
            if (tjPos < 0) {
                tjPos = source.indexOf("\nTJ", from);
            }
            if (tjPos < 0) {
                break;
            }

            int bracket = source.lastIndexOf('[', tjPos);
            if (bracket < 0 || tjPos - bracket > MAX_PDF_ARRAY_LOOKBACK) {
                from = tjPos + 3;
                continue;
            }

            String block = source.substring(bracket + 1, tjPos);
            collectParenStringsFromBlock(block, chunks);
            from = tjPos + 3;
        }
    }

    private static void collectPlainPdfParenStrings(String source, List<String> chunks) {
        int scanLen = Math.min(source.length(), MAX_PDF_SCAN_CHARS);
        collectParenStringsFromBlock(source.substring(0, scanLen), chunks);
    }

    private static void collectParenStringsFromBlock(String block, List<String> chunks) {
        for (int i = 0; i < block.length() && chunks.size() < MAX_PDF_TOKEN_CHUNKS; i++) {
            if (block.charAt(i) != '(') {
                continue;
            }
            int depth = 1;
            StringBuilder raw = new StringBuilder();
            for (int j = i + 1; j < block.length() && depth > 0; j++) {
                char c = block.charAt(j);
                if (c == '\\' && j + 1 < block.length()) {
                    raw.append(c).append(block.charAt(j + 1));
                    j++;
                    continue;
                }
                if (c == '(') {
                    depth++;
                } else if (c == ')') {
                    depth--;
                    if (depth == 0) {
                        if (raw.length() > 0 && raw.length() <= 200) {
                            chunks.add(decodePdfString(raw.toString()));
                        }
                        i = j;
                        break;
                    }
                }
                if (depth > 0) {
                    raw.append(c);
                }
            }
        }
    }

    private static List<String> extractFlateDecodedStreams(byte[] pdfBytes) {
        List<String> streams = new ArrayList<>();
        String source = new String(pdfBytes, StandardCharsets.ISO_8859_1);
        int cursor = 0;
        while (true) {
            if (streams.size() >= MAX_FLATE_STREAMS_TO_SCAN) break;
            int streamPos = source.indexOf("stream", cursor);
            if (streamPos < 0) break;
            int endStreamPos = source.indexOf("endstream", streamPos);
            if (endStreamPos < 0) break;

            int dictStart = source.lastIndexOf("<<", streamPos);
            int dictEnd = dictStart < 0 ? -1 : source.indexOf(">>", dictStart);
            if (dictStart < 0 || dictEnd < 0 || dictEnd > streamPos) {
                cursor = endStreamPos + 9;
                continue;
            }

            String dict = source.substring(dictStart, dictEnd + 2);
            if (!dict.contains("/FlateDecode")) {
                cursor = endStreamPos + 9;
                continue;
            }

            int dataStart = streamPos + "stream".length();
            while (dataStart < source.length()) {
                char c = source.charAt(dataStart);
                if (c == '\r' || c == '\n' || c == ' ') {
                    dataStart++;
                } else {
                    break;
                }
            }

            int dataEnd = endStreamPos;
            while (dataEnd > dataStart) {
                char c = source.charAt(dataEnd - 1);
                if (c == '\r' || c == '\n' || c == ' ') {
                    dataEnd--;
                } else {
                    break;
                }
            }
            if (dataEnd <= dataStart) {
                cursor = endStreamPos + 9;
                continue;
            }

            byte[] raw = source.substring(dataStart, dataEnd).getBytes(StandardCharsets.ISO_8859_1);
            String decoded = tryInflate(raw);
            if (!decoded.isBlank()) {
                streams.add(decoded);
            }
            cursor = endStreamPos + 9;
        }
        return streams;
    }

    private static String tryInflate(byte[] raw) {
        String decoded = inflateWith(raw, false);
        if (!decoded.isBlank()) return decoded;
        return inflateWith(raw, true);
    }

    private static String inflateWith(byte[] raw, boolean nowrap) {
        try (ByteArrayInputStream in = new ByteArrayInputStream(raw);
             InflaterInputStream inflater = new InflaterInputStream(in, new Inflater(nowrap));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int total = 0;
            int n;
            while ((n = inflater.read(buf)) != -1) {
                total += n;
                if (total > MAX_INFLATED_STREAM_BYTES) {
                    return "";
                }
                out.write(buf, 0, n);
            }
            return out.toString(StandardCharsets.ISO_8859_1);
        } catch (Exception ignored) {
            return "";
        }
    }

    /**
     * Decodes PDF string escaping (backslash sequences, octal escapes).
     */
    private static String decodePdfString(String text) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c != '\\') {
                out.append(c);
                continue;
            }
            if (i + 1 >= text.length()) {
                break;
            }
            char next = text.charAt(++i);
            switch (next) {
                case 'n': out.append('\n'); break;
                case 'r': out.append('\r'); break;
                case 't': out.append('\t'); break;
                case 'b': out.append('\b'); break;
                case 'f': out.append('\f'); break;
                case '(': out.append('('); break;
                case ')': out.append(')'); break;
                case '\\': out.append('\\'); break;
                default:
                    if (next >= '0' && next <= '7') {
                        StringBuilder oct = new StringBuilder();
                        oct.append(next);
                        for (int k = 0; k < 2 && i + 1 < text.length(); k++) {
                            char d = text.charAt(i + 1);
                            if (d < '0' || d > '7') {
                                break;
                            }
                            oct.append(d);
                            i++;
                        }
                        try {
                            out.append((char) Integer.parseInt(oct.toString(), 8));
                        } catch (NumberFormatException ignored) {
                        }
                    } else {
                        out.append(next);
                    }
                    break;
            }
        }
        return out.toString();
    }

    private static String unescapeXml(String value) {
        return value
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&apos;", "'");
    }

    /**
     * Normalizes whitespace: replaces sequences of spaces/tabs/newlines with a single space,
     * compacts multiple newlines into double newline, and trims.
     */
    private static String normalizeWhitespace(String value) {
        if (value == null) {
            return "";
        }
        String text = value.replace('\u0000', ' ');
        text = text.replaceAll("[\\t\\x0B\\f\\r]+", " ");
        text = text.replaceAll(" +", " ");
        text = text.replaceAll("\\n{3,}", "\n\n");
        return text.trim();
    }

    private static boolean isPdfMetadataNoise(String text) {
        if (text == null || text.isBlank()) {
            return true;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        String[] noiseTokens = new String[] {
                "/cidfonttype",
                "/fontdescriptor",
                "/basefont",
                "/fontbbox",
                "/fontname",
                "/resources",
                "/mediabox",
                "endstream",
                "endobj",
                "/catalog",
                "/type /font"
        };
        int hits = 0;
        for (String token : noiseTokens) {
            if (lower.contains(token)) {
                hits++;
            }
        }
        long slashCount = text.chars().filter(ch -> ch == '/').count();
        boolean tooManySlashes = slashCount > Math.max(20, text.length() / 18);
        return hits >= 3 || tooManySlashes;
    }

    /**
     * Sanitizes resume text before sending to the client:
     * removes PDF metadata noise and normalizes whitespace.
     */
    private static String sanitizeResumeTextForResponse(String text) {
        String normalized = normalizeWhitespace(text);
        if (normalized.isBlank()) {
            return "";
        }
        if (!isPdfMetadataNoise(normalized)) return normalized;
        // Legacy dirty data may already contain metadata noise. Clean it silently.
        List<String> lines = new ArrayList<>();
        for (String line : normalized.split("\\n")) {
            String cleaned = normalizeWhitespace(line);
            if (!cleaned.isBlank() && !isPdfMetadataNoise(cleaned)) {
                lines.add(cleaned);
            }
        }
        return normalizeWhitespace(String.join("\n", lines));
    }

    private static String cleanupPdfReadableText(List<String> rawChunks) {
        List<String> kept = new ArrayList<>();
        for (String chunk : rawChunks) {
            String text = normalizeWhitespace(chunk);
            if (text.isBlank()) continue;
            if (!looksLikeHumanReadableText(text)) continue;
            if (isPdfMetadataNoise(text)) continue;
            kept.add(text);
        }
        String merged = normalizeWhitespace(String.join("\n", kept));
        if (isPdfMetadataNoise(merged)) return "";
        return merged;
    }

    private static boolean looksLikeHumanReadableText(String text) {
        if (text == null || text.isBlank()) return false;
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("/type") || lower.contains("/font") || lower.contains("/catalog")) return false;
        long letterOrDigit = text.chars().filter(Character::isLetterOrDigit).count();
        if (letterOrDigit < 3) return false;
        long plainPrintable = text.chars().filter(ch ->
                (ch >= 'a' && ch <= 'z')
                        || (ch >= 'A' && ch <= 'Z')
                        || (ch >= '0' && ch <= '9')
                        || ch == ' '
                        || ch == '.'
                        || ch == ','
                        || ch == ':'
                        || ch == ';'
                        || ch == '-'
                        || ch == '_'
                        || ch == '/'
                        || ch == '@'
                        || ch == '('
                        || ch == ')'
        ).count();
        if (plainPrintable * 100 / Math.max(1, text.length()) < 70) return false;
        long slashCount = text.chars().filter(ch -> ch == '/').count();
        if (slashCount > Math.max(3, text.length() / 12)) return false;
        return true;
    }

    /**
     * Reads existing job and application IDs from CSV files to set the correct next sequence numbers.
     * This prevents ID collisions when the system is restarted.
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
    // 日志写入方法 WebServer.java:553，调用点如登录 WebServer.java:109、发岗 WebServer.java:301、审核 WebServer.java:421。
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
    // 实现位置：WebServer.java:563 和 WebServer.java:572。
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
     * Parses a URL-encoded query string (or form body) into key-value pairs.
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
    // 实现位置：WebServer.java:592 和 WebServer.java:613。
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
}