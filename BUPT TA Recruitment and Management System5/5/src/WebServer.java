import model.*;
import dao.*;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.File;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WebServer {
    private static final int PORT = 8080;
    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final UserDAO userDAO = new UserDAO();
    private static final ProfileDAO profileDAO = new ProfileDAO();
    private static final JobDAO jobDAO = new JobDAO();
    private static final ApplicationDAO applicationDAO = new ApplicationDAO();
    private static final AuditLogDAO auditLogDAO = new AuditLogDAO();

    private static int jobSequence = 1;
    private static int appSequence = 1;

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

        server.setExecutor(null);
        server.start();
        System.out.println("Web UI started: http://localhost:" + PORT);
    }

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

    static class TaProfileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                Map<String, String> query = parseQuery(exchange.getRequestURI().getQuery());
                String taId = query.getOrDefault("taId", "");
                Profile p = profileDAO.getByTaId(taId);
                if (p == null) {
                    sendJson(exchange, 200, "{\"ok\":true,\"exists\":false}");
                    return;
                }
                String json = "{\"ok\":true,\"exists\":true,\"profile\":{"
                        + "\"taId\":\"" + esc(p.getTaId()) + "\","
                        + "\"name\":\"" + esc(p.getName()) + "\","
                        + "\"studentId\":\"" + esc(p.getStudentId()) + "\","
                        + "\"major\":\"" + esc(p.getMajor()) + "\","
                        + "\"phone\":\"" + esc(p.getPhone()) + "\"}}";
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

            if (!studentId.matches("\\d{8}")) {
                sendJson(exchange, 400, jsonError("Student ID must be exactly 8 digits"));
                return;
            }
            if (!phone.matches("\\d{11}")) {
                sendJson(exchange, 400, jsonError("Phone must be exactly 11 digits"));
                return;
            }

            profileDAO.saveOrUpdate(new Profile(taId, name, studentId, major, phone));
            writeLog(taId, "TA", "TA_PROFILE_SAVE", "TA profile saved.");
            sendJson(exchange, 200, "{\"ok\":true}");
        }
    }

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
                if (i > 0) {
                    sb.append(',');
                }
                sb.append("{\"appId\":\"").append(esc(r.getAppId())).append("\",")
                        .append("\"jobId\":\"").append(esc(r.getJobId())).append("\",")
                        .append("\"status\":\"").append(esc(r.getStatus())).append("\",")
                        .append("\"appliedAt\":\"").append(esc(r.getAppliedAt())).append("\"}");
            }
            sb.append("]}");
            sendJson(exchange, 200, sb.toString());
        }
    }

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
            StringBuilder sb = new StringBuilder();
            sb.append("{\"ok\":true,\"applicants\":[");
            for (int i = 0; i < records.size(); i++) {
                ApplicationRecord r = records.get(i);
                Profile p = profileDAO.getByTaId(r.getTaId());
                String name = p == null ? "N/A" : p.getName();
                if (i > 0) {
                    sb.append(',');
                }
                sb.append("{\"appId\":\"").append(esc(r.getAppId())).append("\",")
                        .append("\"taId\":\"").append(esc(r.getTaId())).append("\",")
                        .append("\"name\":\"").append(esc(name)).append("\",")
                        .append("\"studentId\":\"").append(esc(p == null ? "" : p.getStudentId())).append("\",")
                        .append("\"major\":\"").append(esc(p == null ? "" : p.getMajor())).append("\",")
                        .append("\"phone\":\"").append(esc(p == null ? "" : p.getPhone())).append("\",")
                        .append("\"status\":\"").append(esc(r.getStatus())).append("\"}");
            }
            sb.append("]}");
            sendJson(exchange, 200, sb.toString());
        }
    }

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

            if (!isValidStatus(status)) {
                sendJson(exchange, 400, jsonError("Invalid status"));
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

            boolean updated = applicationDAO.updateStatus(appId, status);
            if (!updated) {
                sendJson(exchange, 500, jsonError("Failed to update status"));
                return;
            }

            writeLog(moId, "MO", "MO_UPDATE_STATUS", "Updated " + appId + " to " + status);
            sendJson(exchange, 200, "{\"ok\":true}");
        }
    }

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

    private static boolean isValidStatus(String status) {
        return "Pending".equalsIgnoreCase(status)
                || "Shortlisted".equalsIgnoreCase(status)
                || "Rejected".equalsIgnoreCase(status)
                || "Interview".equalsIgnoreCase(status)
                || "Hired".equalsIgnoreCase(status);
    }

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

    private static void writeLog(String userId, String role, String action, String detail) {
        auditLogDAO.append(new AuditLog(
                LocalDateTime.now().format(TS_FORMAT),
                userId,
                role,
                action,
                detail
        ));
    }

    private static Map<String, String> parseFormBody(HttpExchange exchange) throws IOException {
        byte[] bytes;
        try (InputStream is = exchange.getRequestBody()) {
            bytes = is.readAllBytes();
        }
        String body = new String(bytes, StandardCharsets.UTF_8);
        return parseQuery(body);
    }

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
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

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

    private static void sendText(HttpExchange exchange, int status, String text) throws IOException {
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, data.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(data);
        }
    }

    private static String jsonError(String message) {
        return "{\"ok\":false,\"error\":\"" + esc(message) + "\"}";
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ");
    }

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