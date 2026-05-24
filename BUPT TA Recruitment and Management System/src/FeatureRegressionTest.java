import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Comprehensive feature regression test suite for the TA Recruitment System.
 * <p>
 * Sends real HTTP requests to a running {@code WebServer} instance and
 * validates every core user story, including registration, resume upload,
 * job application, status lifecycle, and admin analytics.
 * <p>
 * Run this class after starting the web server with:
 * <pre>java -cp out FeatureRegressionTest</pre>
 */
public class FeatureRegressionTest {
    private static final String BASE_URL = "http://localhost:8080";
    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static final Path DATA_DIR = Path.of("data");

    private int passed = 0;
    private int failed = 0;

    /**
     * Test entry point.
     *
     * @param args not used
     * @throws Exception if a critical test fails
     */
    public static void main(String[] args) throws Exception {
        new FeatureRegressionTest().run();
    }

    private void run() throws Exception {
        ensureServerRunning();
        Path backup = Files.createTempDirectory("ta-system-data-backup-");
        copyDirectory(DATA_DIR, backup);
        try {
            resetMutableData();
            testTaRegistration();
            Scenario scenario = testPdfProfileJobApplyAndResumeAccess();
            testStatusLifecycle(scenario);
            testNoResumeApplicantIsHiddenFromPdfFlow(scenario.jobId);
            testAdminMetricsAndWorkloads();
        } finally {
            restoreData(backup);
            deleteDirectory(backup);
        }

        System.out.println();
        System.out.println("FeatureRegressionTest: " + passed + " passed, " + failed + " failed");
        if (failed > 0) {
            throw new AssertionError(failed + " regression checks failed");
        }
    }

    /**
     * Verifies that TA registration works and rejects invalid input.
     */
    private void testTaRegistration() throws Exception {
        String email = "regression.ta." + System.currentTimeMillis() + "@bupt.edu";
        Response created = postForm("/api/register", Map.of(
                "email", email,
                "password", "ta123456"
        ));
        expectStatus(created, 200, "TA registration succeeds");
        expectContains(created.body, "\"role\":\"TA\"", "registered account has TA role");
        String taId = valueOf(created.body, "id");
        expectTrue(taId.matches("T\\d{3}"), "registered TA id uses Txxx format");

        Response duplicate = postForm("/api/register", Map.of(
                "email", email,
                "password", "ta123456"
        ));
        expectStatus(duplicate, 400, "duplicate registration is rejected");

        Response shortPassword = postForm("/api/register", Map.of(
                "email", "short.password." + System.currentTimeMillis() + "@bupt.edu",
                "password", "123"
        ));
        expectStatus(shortPassword, 400, "short password is rejected");
    }

    /**
     * Tests PDF profile creation, job application, and resume access.
     */
    private Scenario testPdfProfileJobApplyAndResumeAccess() throws Exception {
        String email = "pdf.ta." + System.currentTimeMillis() + "@bupt.edu";
        String taId = valueOf(postForm("/api/register", Map.of(
                "email", email,
                "password", "ta123456"
        )).body, "id");

        Response profile = saveProfile(taId, "Regression PDF TA", "20239901",
                "Computer Science", "13900139001", "regression-resume.pdf", minimalPdfBytes("Regression PDF Resume"));
        expectStatus(profile, 200, "PDF profile save succeeds");
        expectContains(profile.body, "data/resumes/", "profile save returns stored PDF path");

        Response queriedProfile = get("/api/ta/profile?taId=" + enc(taId));
        expectStatus(queriedProfile, 200, "profile query succeeds");
        expectContains(queriedProfile.body, "\"resumeUploaded\":true", "profile reports uploaded PDF");
        expectContains(Files.readString(Path.of("data", "profiles.csv")), "data/resumes/", "profiles.csv records resume path");

        Response invalidResume = saveProfile(taId, "Regression PDF TA", "20239901",
                "Computer Science", "13900139001", "resume.txt", "%PDF-1.4".getBytes(StandardCharsets.UTF_8));
        expectStatus(invalidResume, 400, "non-PDF resume extension is rejected");

        String jobId = valueOf(postForm("/api/mo/job", Map.of(
                "moId", "M001",
                "title", "Regression Java TA",
                "requirements", "English Level:CET-6; Work Duration:Within one semester; Weekend Availability:Yes",
                "deadline", LocalDate.now().plusDays(30).toString()
        )).body, "jobId");
        expectTrue(jobId.startsWith("JOB"), "MO can create a job");

        String appId = valueOf(postForm("/api/ta/apply", Map.of(
                "taId", taId,
                "jobId", jobId
        )).body, "appId");
        expectTrue(appId.startsWith("APP"), "TA can apply for the job");

        Response applicants = get("/api/mo/applicants?moId=M001&jobId=" + enc(jobId));
        expectStatus(applicants, 200, "MO can view applicants for own job");
        expectContains(applicants.body, "\"resumeUploaded\":true", "applicant JSON reports uploaded PDF");

        Response resume = get("/api/mo/resume?moId=M001&appId=" + enc(appId));
        expectStatus(resume, 200, "MO can open applicant PDF after permission check");
        expectContains(resume.headers, "application/pdf", "resume endpoint returns application/pdf");
        expectTrue(resume.body.startsWith("%PDF-"), "resume endpoint returns PDF bytes");

        Response forbiddenResume = get("/api/mo/resume?moId=M999&appId=" + enc(appId));
        expectStatus(forbiddenResume, 403, "wrong MO cannot open resume PDF");

        return new Scenario(taId, jobId, appId);
    }

    /**
     * Tests the full application status lifecycle (Interview, Hired, Reject).
     */
    private void testStatusLifecycle(Scenario scenario) throws Exception {
        Response interview = postForm("/api/mo/status", Map.of(
                "moId", "M001",
                "appId", scenario.appId,
                "status", "Interview",
                "rejectReason", ""
        ));
        expectStatus(interview, 200, "MO can move applicant to Interview");
        expectContains(interview.body, "\"status\":\"Interview\"", "Interview status persisted");

        Response hired = postForm("/api/mo/status", Map.of(
                "moId", "M001",
                "appId", scenario.appId,
                "status", "Hired",
                "rejectReason", ""
        ));
        expectStatus(hired, 200, "MO can move applicant to Hired");
        expectContains(hired.body, "\"status\":\"Hired\"", "Hired status persisted");

        Response rejectWithoutReason = postForm("/api/mo/status", Map.of(
                "moId", "M001",
                "appId", scenario.appId,
                "status", "Rejected",
                "rejectReason", ""
        ));
        expectStatus(rejectWithoutReason, 400, "Reject without reason is blocked");
    }

    /**
     * Ensures that an applicant without a resume is shown correctly and cannot access PDF endpoints.
     */
    private void testNoResumeApplicantIsHiddenFromPdfFlow(String jobId) throws Exception {
        String taId = valueOf(postForm("/api/register", Map.of(
                "email", "no.resume." + System.currentTimeMillis() + "@bupt.edu",
                "password", "ta123456"
        )).body, "id");
        Response profile = postForm("/api/ta/profile", Map.of(
                "taId", taId,
                "name", "No Resume TA",
                "studentId", "20239902",
                "major", "Data Science",
                "phone", "13900139002",
                "resumeFileName", "",
                "resumeFileBase64", ""
        ));
        expectStatus(profile, 200, "profile can be saved without PDF");

        String appId = valueOf(postForm("/api/ta/apply", Map.of(
                "taId", taId,
                "jobId", jobId
        )).body, "appId");

        Response applicants = get("/api/mo/applicants?moId=M001&jobId=" + enc(jobId));
        expectStatus(applicants, 200, "MO can view no-resume applicant");
        expectContains(applicants.body, "\"resumeUploaded\":false", "no-resume applicant reports false");

        Response resume = get("/api/mo/resume?moId=M001&appId=" + enc(appId));
        expectStatus(resume, 404, "no-resume applicant has no PDF endpoint result");
    }

    /**
     * Verifies admin metrics and workload endpoints.
     */
    private void testAdminMetricsAndWorkloads() throws Exception {
        Response metrics = get("/api/admin/metrics");
        expectStatus(metrics, 200, "admin metrics endpoint works");
        expectContains(metrics.body, "\"totalJobs\":1", "metrics include created job");
        expectContains(metrics.body, "\"totalApplications\":2", "metrics include applications");

        Response workloads = get("/api/admin/workloads");
        expectStatus(workloads, 200, "admin workload endpoint works");
        expectContains(workloads.body, "\"activeWorkload\":1", "workloads count active Hired/Interview/Shortlisted tasks");
        expectContains(workloads.body, "Regression PDF TA", "workloads include profile names");
    }

    private void resetMutableData() throws Exception {
        Response reset = postForm("/api/reset", Map.of());
        expectStatus(reset, 200, "reset endpoint prepares isolated test data");
    }

    // ==================== Utility Methods ====================

    private static Response get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + path)).GET().build();
        HttpResponse<byte[]> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
        return new Response(response.statusCode(), response.headers().firstValue("Content-Type").orElse(""),
                new String(response.body(), StandardCharsets.ISO_8859_1));
    }

    private static Response postForm(String path, Map<String, String> data) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + path))
                .header("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(formBody(data)))
                .build();
        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new Response(response.statusCode(), response.headers().firstValue("Content-Type").orElse(""), response.body());
    }

    private static Response saveProfile(String taId, String name, String studentId, String major,
                                        String phone, String fileName, byte[] resumeBytes) throws Exception {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("taId", taId);
        data.put("name", name);
        data.put("studentId", studentId);
        data.put("major", major);
        data.put("phone", phone);
        data.put("resumeFileName", fileName);
        data.put("resumeFileBase64", Base64.getEncoder().encodeToString(resumeBytes));
        return postForm("/api/ta/profile", data);
    }

    private static String formBody(Map<String, String> data) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, String> entry : data.entrySet()) {
            parts.add(enc(entry.getKey()) + "=" + enc(entry.getValue()));
        }
        return String.join("&", parts);
    }

    private static String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String valueOf(String json, String key) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        if (!matcher.find()) {
            throw new AssertionError("Missing JSON key: " + key + " in " + json);
        }
        return matcher.group(1);
    }

    private static byte[] minimalPdfBytes(String text) {
        String safe = text.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
        return ("%PDF-1.4\n"
                + "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n"
                + "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n"
                + "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                + "/Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>\nendobj\n"
                + "4 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n"
                + "5 0 obj\n<< /Length 52 >>\nstream\nBT /F1 18 Tf 72 720 Td (" + safe + ") Tj ET\nendstream\nendobj\n"
                + "trailer\n<< /Root 1 0 R >>\n%%EOF").getBytes(StandardCharsets.ISO_8859_1);
    }

    private void expectStatus(Response response, int expected, String label) {
        expectTrue(response.status == expected, label + " (HTTP " + response.status + ", expected " + expected + ")");
    }

    private void expectContains(String text, String needle, String label) {
        expectTrue(text != null && text.contains(needle), label + " (missing: " + needle + ")");
    }

    private void expectTrue(boolean ok, String label) {
        if (ok) {
            passed++;
            System.out.println("PASS " + label);
        } else {
            failed++;
            System.out.println("FAIL " + label);
        }
    }

    private static void ensureServerRunning() throws Exception {
        try {
            get("/api/admin/metrics");
        } catch (Exception e) {
            throw new IllegalStateException("Start the web server first with .\\run.ps1, then run this test.", e);
        }
    }

    private static void copyDirectory(Path source, Path target) throws IOException {
        if (!Files.exists(source)) {
            return;
        }
        try (var paths = Files.walk(source)) {
            for (Path path : paths.collect(Collectors.toList())) {
                Path relative = source.relativize(path);
                Path destination = target.resolve(relative);
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void restoreData(Path backup) throws IOException {
        deleteDirectory(DATA_DIR);
        Files.createDirectories(DATA_DIR);
        copyDirectory(backup, DATA_DIR);
    }

    private static void deleteDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).collect(Collectors.toList())) {
                Files.deleteIfExists(path);
            }
        }
    }

    // ==================== Inner Helper Classes ====================

    private static class Response {
        private final int status;
        private final String headers;
        private final String body;

        private Response(int status, String headers, String body) {
            this.status = status;
            this.headers = headers;
            this.body = body;
        }
    }

    private static class Scenario {
        private final String taId;
        private final String jobId;
        private final String appId;

        private Scenario(String taId, String jobId, String appId) {
            this.taId = taId;
            this.jobId = jobId;
            this.appId = appId;
        }
    }
}