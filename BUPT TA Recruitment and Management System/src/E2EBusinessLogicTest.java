import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * E2E-style business logic test program that simulates frontend-backend interactions
 * to validate key workflows.
 */
public class E2EBusinessLogicTest {
    private static final String BASE_URL = "http://localhost:8080";
    private static final String MO_ID = "M001";
    private static final String TA_ID = "T001";
    private static final String TA2_ID = "T002";

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private static final String[] DATA_FILES = {
            "data/profiles.csv",
            "data/jobs.csv",
            "data/applications.csv",
            "data/logs.csv"
    };

    private static final Map<String, byte[]> DATA_BACKUP = new LinkedHashMap<>();

    private static int passCount = 0;
    private static int failCount = 0;

    private static String job1Id;
    private static String job2Id;
    private static String taJob1AppId;
    private static String taJob2AppId;

    public static void main(String[] args) {
        System.out.println("========== BUPT TA Recruitment E2E Business Logic Test ==========");
        System.out.println("Server: " + BASE_URL);

        try {
            backupDataFiles();
            phase0Reset();
            phase1MoJobPostingAndValidation();
            phase2TaProfilePdfCrashAndApplications();
            phase3MoReviewGarbledTextSortingAndReject();
            phase4AdminMetrics();
        } catch (Exception e) {
            fail("Suite", "Unexpected suite error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            restoreDataFiles();
        }

        System.out.println();
        System.out.println("========== Summary ==========");
        System.out.println("✅ PASS: " + passCount);
        System.out.println("❌ FAIL: " + failCount);
    }

    private static void phase0Reset() {
        try {
            Response reset = postForm("/api/reset", Map.of());
            expectOk("Phase 0: Reset business data", reset);
        } catch (Exception e) {
            fail("Phase 0", "Could not reset test data: " + e.getMessage());
        }
    }

    private static void phase1MoJobPostingAndValidation() {
        System.out.println();
        System.out.println("---- Phase 1: MO Job Posting & Validation ----");

        try {
            Response login = postForm("/api/login", Map.of("email", "mo@bupt.edu", "password", "mo123"));
            expectOk("Phase 1.1: MO login", login);
            expectContains("Phase 1.1: MO role validation", login.body, "\"role\":\"MO\"");
        } catch (Exception e) {
            fail("Phase 1.1", "MO login request failed: " + e.getMessage());
        }

        try {
            Response emptyJob = postForm("/api/mo/job", Map.of(
                    "moId", "",
                    "title", "",
                    "requirements", "",
                    "deadline", ""
            ));

            if (emptyJob.status == 200 && emptyJob.body.contains("\"ok\":true")) {
                System.out.println("❌ FAIL Phase 1.2: Empty job fields were accepted by the backend (Security Vulnerability).");
                failCount++;
            } else if (emptyJob.status >= 400 && emptyJob.status < 500) {
                pass("Phase 1.2: Empty job fields rejected");
            } else if (emptyJob.body.contains("\"ok\":false") || emptyJob.body.toLowerCase().contains("error")) {
                pass("Phase 1.2: Empty job fields rejected with error body");
            } else {
                fail("Phase 1.2", "Unexpected response for empty job fields: " + emptyJob);
            }
        } catch (Exception e) {
            fail("Phase 1.2", "Empty job request crashed or failed: " + e.getMessage());
        }

        try {
            Response badDeadline = postForm("/api/mo/job", Map.of(
                    "moId", MO_ID,
                    "title", "Invalid Deadline Job",
                    "requirements", "Java support",
                    "deadline", "invalid_date"
            ));
            if (badDeadline.status >= 400 && badDeadline.status < 500) {
                pass("Phase 1.3: Invalid deadline rejected");
            } else {
                fail("Phase 1.3", "Invalid deadline should return 400-level error, got " + badDeadline);
            }
        } catch (Exception e) {
            fail("Phase 1.3", "Invalid deadline request crashed or failed: " + e.getMessage());
        }

        try {
            job1Id = createJob("E2E PDF Review Job", "Java tutoring plus PDF review");
            job2Id = createJob("E2E Reject Workflow Job", "Database tutoring plus rejection workflow");
            pass("Phase 1.4: Created 2 valid jobs");
        } catch (Exception e) {
            fail("Phase 1.4", "Could not create valid jobs: " + e.getMessage());
        }
    }

    private static void phase2TaProfilePdfCrashAndApplications() {
        System.out.println();
        System.out.println("---- Phase 2: TA Profile, PDF Upload Crash Trigger & Applications ----");

        try {
            Response login = postForm("/api/login", Map.of("email", "ta@bupt.edu", "password", "ta123"));
            expectOk("Phase 2.0: TA login", login);
            expectContains("Phase 2.0: TA role validation", login.body, "\"role\":\"TA\"");
        } catch (Exception e) {
            fail("Phase 2.0", "TA login request failed: " + e.getMessage());
        }

        try {
            byte[] oversized = new byte[512 * 1024 + 1];
            Response huge = saveProfileForm(TA_ID, "E2E TA", "20231234", "Computer Science",
                    "13800138000", "too_large.txt", oversized);
            if (huge.status >= 400 && huge.body.toLowerCase().contains("too large")) {
                pass("Phase 2.A1: Oversized resume rejected");
            } else {
                fail("Phase 2.A1", "Oversized resume should be rejected with 'too large', got " + huge);
            }
        } catch (Exception e) {
            fail("Phase 2.A1", "Oversized upload request failed: " + e.getMessage());
        }

        try {
            Response jpg = saveProfileForm(TA_ID, "E2E TA", "20231234", "Computer Science",
                    "13800138000", "resume.jpg", "fake image bytes".getBytes(StandardCharsets.UTF_8));
            if (jpg.status >= 400 && jpg.body.contains("Unsupported")) {
                pass("Phase 2.A2: Unsupported file type rejected");
            } else {
                fail("Phase 2.A2", "Unsupported .jpg should be rejected, got " + jpg);
            }
        } catch (Exception e) {
            fail("Phase 2.A2", "Unsupported upload request failed: " + e.getMessage());
        }

        try {
            byte[] scannedPdf = "%PDF-1.4\n1 0 obj << /Type /Catalog >> endobj\n%%EOF"
                    .getBytes(StandardCharsets.ISO_8859_1);
            Response scanned = saveProfileForm(TA_ID, "E2E TA", "20231234", "Computer Science",
                    "13800138000", "scan.pdf", scannedPdf);
            if (scanned.status >= 400 && (scanned.body.contains("DOCX") || scanned.body.contains("TXT"))) {
                pass("Phase 2.A3: Scanned PDF warning returned");
            } else {
                fail("Phase 2.A3", "Scanned/image-only PDF should warn to convert to DOCX/TXT, got " + scanned);
            }
        } catch (IOException e) {
            System.out.println("❌ FAIL Phase 2.X: Backend crashed (Connection dropped) during PDF upload.");
            failCount++;
        } catch (Exception e) {
            fail("Phase 2.A3", "Scanned PDF upload request failed: " + e.getMessage());
        }

        try {
            String txtResume = "Readable TXT resume. TA can teach Java and support labs.";
            Response txt = saveProfileForm(TA_ID, "E2E TA", "20231234", "Computer Science",
                    "13800138000", "resume.txt", txtResume.getBytes(StandardCharsets.UTF_8));
            expectOk("Phase 2.4A: Valid TXT resume upload", txt);
        } catch (Exception e) {
            fail("Phase 2.4A", "TXT resume upload failed: " + e.getMessage());
        }

        phase2BrowserStylePdfCrashUpload();

        try {
            byte[] actualPdf = loadRealPdfPayload();
            Response pdfProfile = saveProfileForm(TA_ID, "E2E TA", "20231234", "Computer Science",
                    "13800138000", "resume.pdf", actualPdf);
            if (pdfProfile.status == 200 && pdfProfile.body.contains("\"ok\":true")) {
                pass("Phase 2.4B: PDF profile data prepared for MO review");
            } else {
                fail("Phase 2.4B", "Could not prepare PDF resume profile for MO review: " + pdfProfile);
            }
        } catch (IOException e) {
            System.out.println("❌ FAIL Phase 2.X: Backend crashed (Connection dropped) during PDF upload.");
            failCount++;
        } catch (Exception e) {
            fail("Phase 2.4B", "PDF profile preparation failed: " + e.getMessage());
        }

        try {
            if (job1Id == null || job2Id == null) {
                fail("Phase 2.5", "Skipping applications because jobs were not created");
                return;
            }
            taJob1AppId = apply(TA_ID, job1Id);
            taJob2AppId = apply(TA_ID, job2Id);

            Response apps = get("/api/ta/applications?taId=" + enc(TA_ID));
            String status1 = statusOfApplication(apps.body, taJob1AppId);
            String status2 = statusOfApplication(apps.body, taJob2AppId);
            if ("Pending".equals(status1) && "Pending".equals(status2)) {
                pass("Phase 2.5: TA applied to Job 1 and Job 2 with Pending status");
            } else {
                fail("Phase 2.5", "Expected both applications Pending, got app1=" + status1 + ", app2=" + status2);
            }
        } catch (Exception e) {
            fail("Phase 2.5", "TA application workflow failed: " + e.getMessage());
        }
    }

    private static void phase2BrowserStylePdfCrashUpload() {
        try {
            String boundary = "---Boundary12345";
            byte[] pdfBytes = buildCorruptedPdfBytes(1024 * 1024);

            ByteArrayOutputStream body = new ByteArrayOutputStream();
            body.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
            body.write(("Content-Disposition: form-data; name=\"resume\"; filename=\"test.pdf\"\r\n")
                    .getBytes(StandardCharsets.ISO_8859_1));
            body.write(("Content-Type: application/pdf\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1));
            body.write(pdfBytes);
            body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.ISO_8859_1));

            HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/api/ta/profile"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                    .build();

            HttpResponse<String> response = CLIENT.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );

            if (response.statusCode() >= 500) {
                System.out.println("❌ FAIL Phase 2.X: Backend crashed (Connection dropped) during PDF upload.");
                failCount++;
            } else {
                pass("Phase 2.X: Browser-style PDF multipart upload returned HTTP " + response.statusCode());
            }
        } catch (IOException e) {
            System.out.println("❌ FAIL Phase 2.X: Backend crashed (Connection dropped) during PDF upload.");
            failCount++;
        } catch (Exception e) {
            fail("Phase 2.X", "Browser-style PDF upload simulation failed unexpectedly: " + e.getMessage());
        }
    }

    private static void phase3MoReviewGarbledTextSortingAndReject() {
        System.out.println();
        System.out.println("---- Phase 3: MO Review, Garbled Text Trigger, Sorting & Reject Trigger ----");

        try {
            Response login = postForm("/api/login", Map.of("email", "mo@bupt.edu", "password", "mo123"));
            expectOk("Phase 3.0: MO login again", login);
        } catch (Exception e) {
            fail("Phase 3.0", "MO login failed: " + e.getMessage());
        }

        try {
            Response applicants = get("/api/mo/applicants?moId=" + enc(MO_ID) + "&jobId=" + enc(job1Id));
            expectOk("Phase 3.1: MO fetches applicant list", applicants);
            String responseBody = applicants.body;
            if (containsGarbledPdfText(responseBody)) {
                System.out.println("❌ FAIL Phase 3.X: Parsed PDF resume contains garbled text.");
                failCount++;
            } else {
                pass("Phase 3.X: Parsed PDF resume text is readable");
            }
        } catch (Exception e) {
            fail("Phase 3.X", "MO applicant fetch for garbled-text check failed: " + e.getMessage());
        }

        try {
            if (taJob1AppId != null) {
                Response hire = postForm("/api/mo/status", Map.of(
                        "moId", MO_ID,
                        "appId", taJob1AppId,
                        "status", "Hired",
                        "rejectReason", ""
                ));
                expectOk("Phase 3.2: MO updates Job 1 application to Hired", hire);
            }
        } catch (Exception e) {
            fail("Phase 3.2", "MO hire status update failed: " + e.getMessage());
        }

        try {
            Response reject = postForm("/api/mo/status", Map.of(
                    "moId", MO_ID,
                    "appId", taJob2AppId,
                    "status", "Rejected",
                    "rejectReason", "Schedule conflict"
            ));
            if (reject.status == 200) {
                pass("Phase 3.3A: Reject request returned 200 OK");
            } else {
                fail("Phase 3.3A", "Reject request should return 200 OK, got " + reject);
            }

            Response appsAfterReject = get("/api/ta/applications?taId=" + enc(TA_ID));
            String status = statusOfApplication(appsAfterReject.body, taJob2AppId);
            if (status.equals("Pending")) {
                System.out.println("❌ FAIL Phase 3.X: Reject action had no effect. Status remained Pending.");
                failCount++;
            } else if (status.equals("Rejected")) {
                pass("Phase 3.X: Reject action persisted Rejected status");
            } else {
                fail("Phase 3.X", "Reject action persisted unexpected status: " + status);
            }
        } catch (Exception e) {
            fail("Phase 3.X", "Reject action request or verification failed: " + e.getMessage());
        }

        try {
            setupSecondTaForSorting();
            Response job2Applicants = get("/api/mo/applicants?moId=" + enc(MO_ID) + "&jobId=" + enc(job2Id));
            expectOk("Phase 3.B: MO fetches Job 2 applicants", job2Applicants);
            int ta2N = activeTaskCountOfApplicant(job2Applicants.body, TA2_ID);
            int ta1N = activeTaskCountOfApplicant(job2Applicants.body, TA_ID);
            int ta2Position = applicantPosition(job2Applicants.body, TA2_ID);
            int ta1Position = applicantPosition(job2Applicants.body, TA_ID);
            if (ta2N == 0 && ta1N >= 1 && ta2Position >= 0 && ta1Position >= 0 && ta2Position < ta1Position) {
                pass("Phase 3.B: Sorting puts lower workload before higher workload");
            } else {
                fail("Phase 3.B", "Expected N=0 before N=1+, got body: " + job2Applicants.body);
            }
        } catch (Exception e) {
            fail("Phase 3.B", "Sorting check failed: " + e.getMessage());
        }
    }

    private static void phase4AdminMetrics() {
        System.out.println();
        System.out.println("---- Phase 4: Admin Global Metrics ----");

        try {
            Response login = postForm("/api/login", Map.of("email", "admin@bupt.edu", "password", "admin123"));
            expectOk("Phase 4.1: Admin login", login);
            expectContains("Phase 4.1: Admin role validation", login.body, "\"role\":\"Admin\"");
        } catch (Exception e) {
            fail("Phase 4.1", "Admin login failed: " + e.getMessage());
        }

        try {
            Response metrics = get("/api/admin/metrics");
            expectOk("Phase 4.2: Admin metrics fetched", metrics);
            if (metrics.body.contains("\"Hired\":1")) {
                pass("Phase 4.2: Admin metrics reflect +1 active workload via Hired status");
            } else {
                fail("Phase 4.2", "Expected metrics to include \"Hired\":1, got " + metrics.body);
            }
        } catch (Exception e) {
            fail("Phase 4.2", "Admin metrics request failed: " + e.getMessage());
        }
    }

    private static String createJob(String title, String requirements) throws IOException, InterruptedException {
        Response response = postForm("/api/mo/job", Map.of(
                "moId", MO_ID,
                "title", title,
                "requirements", requirements,
                "deadline", LocalDate.now().plusDays(30).toString()
        ));
        if (response.status == 200 && response.body.contains("\"ok\":true")) {
            return valueOf(response.body, "jobId");
        }
        throw new IllegalStateException("Create job failed: " + response);
    }

    private static String apply(String taId, String jobId) throws IOException, InterruptedException {
        Response response = postForm("/api/ta/apply", Map.of("taId", taId, "jobId", jobId));
        if (response.status == 200 && response.body.contains("\"ok\":true")) {
            return valueOf(response.body, "appId");
        }
        throw new IllegalStateException("Apply failed: " + response);
    }

    private static void setupSecondTaForSorting() throws IOException, InterruptedException {
        Response profile = saveProfileForm(TA2_ID, "Low Workload TA", "20235678", "Software Engineering",
                "13900139000", "ta2.txt", "Low workload resume".getBytes(StandardCharsets.UTF_8));
        if (!(profile.status == 200 && profile.body.contains("\"ok\":true"))) {
            throw new IllegalStateException("Could not save second TA profile: " + profile);
        }
        Response apply = postForm("/api/ta/apply", Map.of("taId", TA2_ID, "jobId", job2Id));
        if (!(apply.status == 200 && apply.body.contains("\"ok\":true"))
                && !apply.body.contains("Already applied")) {
            throw new IllegalStateException("Could not apply second TA to Job 2: " + apply);
        }
    }

    private static Response saveProfileForm(String taId, String name, String studentId, String major,
                                            String phone, String fileName, byte[] fileBytes)
            throws IOException, InterruptedException {
        return postForm("/api/ta/profile", Map.of(
                "taId", taId,
                "name", name,
                "studentId", studentId,
                "major", major,
                "phone", phone,
                "resumeText", "",
                "resumeFileName", fileName,
                "resumeFileBase64", Base64.getEncoder().encodeToString(fileBytes)
        ));
    }

    private static byte[] buildCorruptedPdfBytes(int size) {
        byte[] pdfBytes = new byte[size];
        byte[] pdfHeader = "%PDF-1.4\n".getBytes(StandardCharsets.ISO_8859_1);
        new Random(42).nextBytes(pdfBytes);
        System.arraycopy(pdfHeader, 0, pdfBytes, 0, pdfHeader.length);
        return pdfBytes;
    }

    private static byte[] loadRealPdfPayload() throws IOException {
        Path localPdf = Path.of("test_cv.pdf");
        if (Files.exists(localPdf)) {
            return Files.readAllBytes(localPdf);
        }
        return minimalTextPdf("Readable PDF resume for E2E test. Java TA applicant.");
    }

    private static byte[] minimalTextPdf(String text) {
        String escaped = text.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
        String pdf = "%PDF-1.4\n"
                + "1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n"
                + "2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj\n"
                + "3 0 obj << /Type /Page /Parent 2 0 R /Contents 4 0 R >> endobj\n"
                + "4 0 obj << /Length 80 >> stream\n"
                + "BT /F1 12 Tf 72 720 Td (" + escaped + ") Tj ET\n"
                + "endstream endobj\n"
                + "%%EOF";
        return pdf.getBytes(StandardCharsets.ISO_8859_1);
    }

    private static Response get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + path))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new Response(response.statusCode(), response.body());
    }

    private static Response postForm(String path, Map<String, String> form) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + path))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(formEncode(form), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new Response(response.statusCode(), response.body());
    }

    private static String formEncode(Map<String, String> form) {
        StringBuilder body = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : form.entrySet()) {
            if (!first) {
                body.append('&');
            }
            first = false;
            body.append(enc(entry.getKey())).append('=').append(enc(entry.getValue()));
        }
        return body.toString();
    }

    private static String enc(String value) {
        return URLEncoder.encode(Objects.toString(value, ""), StandardCharsets.UTF_8);
    }

    private static void expectOk(String name, Response response) {
        if (response.status == 200 && response.body.contains("\"ok\":true")) {
            pass(name);
        } else {
            fail(name, "Expected HTTP 200 ok:true, got " + response);
        }
    }

    private static void expectContains(String name, String actual, String expected) {
        if (actual != null && actual.contains(expected)) {
            pass(name);
        } else {
            fail(name, "Expected response to contain " + expected + ", got " + actual);
        }
    }

    private static boolean containsGarbledPdfText(String text) {
        if (text == null) {
            return false;
        }
        return text.contains("\uFFFD")
                || text.contains("å")
                || text.contains("æ")
                || text.contains("Ã")
                || text.contains("Â")
                || text.contains("锛")
                || text.contains("鏂")
                || text.contains("寤鸿");
    }

    private static String statusOfApplication(String json, String appId) {
        String object = objectContaining(json, "appId", appId);
        return valueOf(object, "status");
    }

    private static int activeTaskCountOfApplicant(String json, String taId) {
        String object = objectContaining(json, "taId", taId);
        return intValueOf(object, "activeTaskCount");
    }

    private static int applicantPosition(String json, String taId) {
        Matcher matcher = Pattern.compile("\\{([^{}]*\"appId\"[^{}]*)\\}").matcher(json);
        int index = 0;
        while (matcher.find()) {
            String object = "{" + matcher.group(1) + "}";
            if (taId.equals(valueOf(object, "taId"))) {
                return index;
            }
            index++;
        }
        return -1;
    }

    private static String objectContaining(String json, String key, String value) {
        Matcher matcher = Pattern.compile("\\{([^{}]*\"" + Pattern.quote(key) + "\"\\s*:\\s*\""
                + Pattern.quote(value) + "\"[^{}]*)\\}").matcher(json);
        if (!matcher.find()) {
            throw new IllegalStateException("Could not find object with " + key + "=" + value + " in " + json);
        }
        return "{" + matcher.group(1) + "}";
    }

    private static String valueOf(String json, String key) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"")
                .matcher(json);
        if (!matcher.find()) {
            throw new IllegalStateException("Could not find string field " + key + " in " + json);
        }
        return unescapeJson(matcher.group(1));
    }

    private static int intValueOf(String json, String key) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(\\d+)").matcher(json);
        if (!matcher.find()) {
            throw new IllegalStateException("Could not find integer field " + key + " in " + json);
        }
        return Integer.parseInt(matcher.group(1));
    }

    private static String unescapeJson(String text) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c != '\\' || i + 1 >= text.length()) {
                out.append(c);
                continue;
            }
            char next = text.charAt(++i);
            switch (next) {
                case '"': out.append('"'); break;
                case '\\': out.append('\\'); break;
                case '/': out.append('/'); break;
                case 'b': out.append('\b'); break;
                case 'f': out.append('\f'); break;
                case 'n': out.append('\n'); break;
                case 'r': out.append('\r'); break;
                case 't': out.append('\t'); break;
                default: out.append(next); break;
            }
        }
        return out.toString();
    }

    private static void backupDataFiles() {
        for (String file : DATA_FILES) {
            Path path = Path.of(file);
            try {
                DATA_BACKUP.put(file, Files.exists(path) ? Files.readAllBytes(path) : null);
            } catch (IOException e) {
                System.out.println("WARN: Could not back up " + file + ": " + e.getMessage());
            }
        }
    }

    private static void restoreDataFiles() {
        for (Map.Entry<String, byte[]> entry : DATA_BACKUP.entrySet()) {
            Path path = Path.of(entry.getKey());
            try {
                if (entry.getValue() == null) {
                    Files.deleteIfExists(path);
                } else {
                    Files.createDirectories(path.getParent());
                    Files.write(path, entry.getValue());
                }
            } catch (IOException e) {
                System.out.println("WARN: Could not restore " + entry.getKey() + ": " + e.getMessage());
            }
        }
    }

    private static void pass(String name) {
        passCount++;
        System.out.println("✅ PASS " + name);
    }

    private static void fail(String phase, String detail) {
        failCount++;
        System.out.println("❌ FAIL " + phase + ": " + detail);
    }

    private record Response(int status, String body) {
        @Override
        public String toString() {
            return "HTTP " + status + " body=" + body;
        }
    }
}
