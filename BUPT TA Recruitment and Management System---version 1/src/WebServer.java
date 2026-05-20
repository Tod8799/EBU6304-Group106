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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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

    // 服务容器与路由
    // 使用 JDK 内置 HttpServer 启动服务并集中注册接口，实现单进程运行。
    // 实现位置：创建服务 WebServer.java:42，注册路由 WebServer.java:44。
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

    // Handler 分治
    // 每个业务域独立 Handler，登录、TA、MO、Admin 各自封装，避免“大控制器”。
    // 示例：登录 WebServer.java:91，TA投递 WebServer.java:188，MO审核 WebServer.java:376，Admin指标 WebServer.java:426。
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
                    String json = "{\"ok\":true,\"exists\":true,\"profile\":{"
                            + "\"taId\":\"" + esc(p.getTaId()) + "\","
                            + "\"name\":\"" + esc(p.getName()) + "\","
                            + "\"studentId\":\"" + esc(p.getStudentId()) + "\","
                            + "\"major\":\"" + esc(p.getMajor()) + "\","
                            + "\"phone\":\"" + esc(p.getPhone()) + "\","
                        + "\"resumeText\":\"" + esc(sanitizeResumeTextForResponse(p.getResumeText())) + "\","
                        + "\"resumeUploaded\":" + (!sanitizeResumeTextForResponse(p.getResumeText()).isBlank()) + "}}";
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
                if (!resumeText.isBlank() && isPdfMetadataNoise(normalizeWhitespace(resumeText))) {
                    sendJson(exchange, 400, jsonError("Detected unreadable PDF metadata text. Please re-upload an editable PDF, or use DOCX/TXT"));
                    return;
                }

                profileDAO.saveOrUpdate(new Profile(taId, name, studentId, major, phone, resumeText));
                writeLog(taId, "TA", "TA_PROFILE_SAVE", "TA profile saved.");
                sendJson(exchange, 200, "{\"ok\":true}");
            } catch (IllegalArgumentException e) {
                sendJson(exchange, 400, jsonError(e.getMessage()));
            } catch (Exception e) {
                e.printStackTrace();
                sendJson(exchange, 500, jsonError("Server error while saving profile: " + e.getClass().getSimpleName()));
            }
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

            String detail = "Updated " + appId + " to " + status;
            if ("Rejected".equalsIgnoreCase(status)) {
                detail += " (reason: " + rejectReason + ")";
            }
            writeLog(moId, "MO", "MO_UPDATE_STATUS", detail);
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

    // 序列号与重置机制
    // 启动时重建 JOB/APP 序列避免 ID 冲突，提供 reset 接口便于演示快速复位。
    // 实现位置：序列初始化 WebServer.java:530，重置接口 WebServer.java:506。
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

    private static boolean isActiveTaskStatus(String status) {
        return "Shortlisted".equalsIgnoreCase(status)
                || "Interview".equalsIgnoreCase(status)
                || "Hired".equalsIgnoreCase(status);
    }

    private static int countActiveTasksForTa(String taId) {
        int count = 0;
        for (ApplicationRecord record : applicationDAO.getByTaId(taId)) {
            if (isActiveTaskStatus(record.getStatus())) {
                count++;
            }
        }
        return count;
    }

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
                text = extractPdfText(fileBytes);
                break;
            default:
                throw new IllegalArgumentException("Unsupported resume type. Please upload TXT, DOCX, or PDF");
        }

        String normalized = normalizeWhitespace(text);
        if (normalized.isBlank()) {
            if ("pdf".equals(extension)) {
                throw new IllegalArgumentException("Cannot extract readable text from PDF. Please try an editable PDF, or convert it to DOCX/TXT");
            }
            throw new IllegalArgumentException("Cannot extract readable text from the uploaded resume");
        }
        return normalized;
    }

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

    private static String extractPdfText(byte[] fileBytes) {
        String source = new String(fileBytes, StandardCharsets.ISO_8859_1);
        List<String> chunks = new ArrayList<>();

        Matcher direct = Pattern.compile("\\((?:\\\\.|[^\\\\)])*\\)\\s*T[Jj]").matcher(source);
        while (direct.find()) {
            String token = direct.group();
            int start = token.indexOf('(');
            int end = token.lastIndexOf(')');
            if (start >= 0 && end > start) {
                chunks.add(decodePdfString(token.substring(start + 1, end)));
            }
        }

        Matcher arrayText = Pattern.compile("\\[(.*?)\\]\\s*TJ", Pattern.DOTALL).matcher(source);
        while (arrayText.find()) {
            String block = arrayText.group(1);
            Matcher pieces = Pattern.compile("\\((?:\\\\.|[^\\\\)])*\\)").matcher(block);
            while (pieces.find()) {
                String piece = pieces.group();
                chunks.add(decodePdfString(piece.substring(1, piece.length() - 1)));
            }
        }

        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("Cannot extract readable text from PDF. Please try an editable PDF, or convert it to DOCX/TXT");
        }
        String merged = normalizeWhitespace(String.join("\n", chunks));
        if (isPdfMetadataNoise(merged)) {
            throw new IllegalArgumentException("Cannot extract readable text from PDF. Please try an editable PDF, or convert it to DOCX/TXT");
        }
        return merged;
    }

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

    private static String sanitizeResumeTextForResponse(String text) {
        String normalized = normalizeWhitespace(text);
        if (normalized.isBlank()) {
            return "";
        }
        if (isPdfMetadataNoise(normalized)) {
            return "[Unreadable PDF metadata content detected. Please re-upload an editable PDF, or use DOCX/TXT.]";
        }
        return normalized;
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

    // 持久化与可追踪
    // 接口最终调用 DAO 写入 CSV，并同步写审计日志，形成可回放轨迹。
    // 日志写入方法 WebServer.java:553，调用点如登录 WebServer.java:109、发岗 WebServer.java:301、审核 WebServer.java:421。
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
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            return s;
        }
    }

    // 响应与错误统一
    // 所有接口统一 JSON 返回，成功带 ok:true，失败统一 jsonError，前端可直接按 error 展示。
    // 实现位置：WebServer.java:592 和 WebServer.java:613。
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