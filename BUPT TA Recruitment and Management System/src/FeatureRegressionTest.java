import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * A simple regression test suite that performs quick assertions against several
 * primary endpoints.
 */
public class FeatureRegressionTest {
    private static final String BASE_URL = "http://localhost:8080";
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final List<String> DATA_FILES = List.of(
            "data/profiles.csv",
            "data/jobs.csv",
            "data/applications.csv",
            "data/logs.csv"
    );

    private final List<String> failures = new ArrayList<>();
    private final List<String> passes = new ArrayList<>();
    private final Map<String, byte[]> backup = new LinkedHashMap<>();
    private Process serverProcess;

    public static void main(String[] args) throws Exception {
        FeatureRegressionTest runner = new FeatureRegressionTest();
        int exitCode = runner.run();
        System.exit(exitCode);
    }

    private int run() throws Exception {
        boolean startedByTest = false;
        try {
            backupDataFiles();
            if (!isServerReachable()) {
                startedByTest = startServer();
            }
            test("A0 login roles", this::testLoginRoles);
            test("A1 txt resume upload and profile query", this::testTxtResumeUpload);
            test("A1 boundary resume text length 20000 chars", this::testResumeTextBoundary);
            test("A1 pdf resume upload and parse", this::testPdfResumeUpload);
            test("A1 docx resume upload and parse", this::testDocxResumeUpload);
            test("A3 oversized resume is rejected", this::testOversizedResume);
            test("A3 unsupported resume type is rejected", this::testUnsupportedResumeType);
            test("A3 image-only pdf gives conversion advice", this::testImageOnlyPdfAdvice);
            test("profile invalid student id and phone", this::testInvalidProfileFields);
            test("B application workflow, active-task sort, TA history, admin metrics", this::testApplicationSortingAndAdmin);
        } finally {
            restoreDataFiles();
            if (startedByTest && serverProcess != null) {
                serverProcess.destroy();
                serverProcess.waitFor(3, TimeUnit.SECONDS);
                if (serverProcess.isAlive()) {
                    serverProcess.destroyForcibly();
                }
            }
        }

        System.out.println();
        System.out.println("========== Test Summary ==========");
        for (String pass : passes) {
            System.out.println("[PASS] " + pass);
        }
        for (String failure : failures) {
            System.out.println("[FAIL] " + failure);
        }
        System.out.println("Passed: " + passes.size() + ", Failed: " + failures.size());
        if (!failures.isEmpty()) {
            System.out.println();
            System.out.println("First failure is the most useful starting point:");
            System.out.println(failures.get(0));
            return 1;
        }
        return 0;
    }

    private void test(String name, ThrowingRunnable body) {
        try {
            resetBusinessData();
            body.run();
            passes.add(name);
        } catch (Throwable t) {
            failures.add(name + " -> " + rootMessage(t));
        }
    }

    private void testLoginRoles() throws Exception {
        expect(login("admin@bupt.edu", "admin123").contains("\"role\":\"Admin\""), "Admin login did not return role Admin");
        expect(login("mo@bupt.edu", "mo123").contains("\"role\":\"MO\""), "MO login did not return role MO");
        expect(login("ta@bupt.edu", "ta123").contains("\"role\":\"TA\""), "TA login did not return role TA");
        Response bad = post("/api/login", Map.of("email", "ta@bupt.edu", "password", "wrong"));
        expectStatus(bad, 401, "invalid password");
        expectContains(errorOf(bad.body), "Invalid email or password", "invalid password error message");
    }

    private void testTxtResumeUpload() throws Exception {
        String resume = "Alice TA resume. Java, SQL, office hours.";
        Response save = saveProfile("T001", "Alice", "20231234", "CS", "13800138000", "alice.txt", resume.getBytes(StandardCharsets.UTF_8));
        expectOk(save, "save txt resume");

        Response profile = get("/api/ta/profile?taId=T001");
        expectOk(profile, "query profile after txt upload");
        expectContains(valueOf(profile.body, "resumeFileName"), "alice.txt", "stored resume file name");
        expectContains(valueOf(profile.body, "resumeText"), "Alice TA resume", "parsed txt resume text");
    }

    private void testResumeTextBoundary() throws Exception {
        String text = "x".repeat(20_000);
        Response ok = post("/api/ta/profile", Map.of(
                "taId", "T001",
                "name", "Alice",
                "studentId", "20231234",
                "major", "CS",
                "phone", "13800138000",
                "resumeText", text,
                "resumeFileName", "",
                "resumeFileBase64", ""
        ));
        expectOk(ok, "resume text exactly 20000 chars");

        Response tooLong = post("/api/ta/profile", Map.of(
                "taId", "T001",
                "name", "Alice",
                "studentId", "20231234",
                "major", "CS",
                "phone", "13800138000",
                "resumeText", text + "x",
                "resumeFileName", "",
                "resumeFileBase64", ""
        ));
        expectStatus(tooLong, 400, "resume text 20001 chars");
        expectContains(errorOf(tooLong.body), "too long", "resume text over-limit error");
    }

    private void testPdfResumeUpload() throws Exception {
        byte[] pdf = minimalTextPdf("PDF Resume Text Java TA");
        Response save = saveProfile("T001", "Alice", "20231234", "CS", "13800138000", "alice.pdf", pdf);
        expectOk(save, "save pdf resume");
        Response profile = get("/api/ta/profile?taId=T001");
        expectContains(valueOf(profile.body, "resumeText"), "PDF Resume Text", "parsed pdf resume text after save");
    }

    private void testDocxResumeUpload() throws Exception {
        byte[] docx = minimalDocx("DOCX Resume Text Database TA");
        Response save = saveProfile("T001", "Alice", "20231234", "CS", "13800138000", "alice.docx", docx);
        expectOk(save, "save docx resume");
        Response profile = get("/api/ta/profile?taId=T001");
        expectContains(valueOf(profile.body, "resumeText"), "DOCX Resume Text", "parsed docx resume text after save");
    }

    private void testOversizedResume() throws Exception {
        byte[] huge = new byte[512 * 1024 + 1];
        Response response = saveProfile("T001", "Alice", "20231234", "CS", "13800138000", "huge.txt", huge);
        expectStatus(response, 400, "oversized resume upload");
        expectContains(errorOf(response.body), "too large", "oversized resume error");
    }

    private void testUnsupportedResumeType() throws Exception {
        Response response = saveProfile("T001", "Alice", "20231234", "CS", "13800138000", "resume.png", "fake".getBytes(StandardCharsets.UTF_8));
        expectStatus(response, 400, "unsupported resume type");
        expectContains(errorOf(response.body), "Unsupported", "unsupported resume error");
    }

    private void testImageOnlyPdfAdvice() throws Exception {
        byte[] imageOnlyPdf = "%PDF-1.4\n1 0 obj << /Type /Catalog >> endobj\n%%EOF".getBytes(StandardCharsets.ISO_8859_1);
        Response response = saveProfile("T001", "Alice", "20231234", "CS", "13800138000", "scan.pdf", imageOnlyPdf);
        expectStatus(response, 400, "image-only pdf should be rejected");
        String error = errorOf(response.body);
        expect(error.contains("DOCX") || error.contains("TXT") || error.contains("convert"),
                "image-only pdf error should advise converting to DOCX/TXT, actual response: HTTP "
                        + response.status + " " + response.body);
    }

    private void testInvalidProfileFields() throws Exception {
        Response badStudentId = post("/api/ta/profile", baseProfile("T001", "1234567", "13800138000"));
        expectStatus(badStudentId, 400, "student id with 7 digits");
        expectContains(errorOf(badStudentId.body), "Student ID", "student id validation message");

        Response badPhone = post("/api/ta/profile", baseProfile("T001", "20231234", "1380013800"));
        expectStatus(badPhone, 400, "phone with 10 digits");
        expectContains(errorOf(badPhone.body), "Phone", "phone validation message");
    }

    private void testApplicationSortingAndAdmin() throws Exception {
        saveSimpleProfile("T001", "BusyTA");
        saveSimpleProfile("T002", "FreeTA");
        saveSimpleProfile("T003", "MiddleTA");

        String targetJob = postJob("Target Job");
        String loadJob1 = postJob("Load Job 1");
        String loadJob2 = postJob("Load Job 2");

        String busyTarget = apply("T001", targetJob);
        String freeTarget = apply("T002", targetJob);
        String middleTarget = apply("T003", targetJob);
        String busyLoad1 = apply("T001", loadJob1);
        String busyLoad2 = apply("T001", loadJob2);
        String middleLoad1 = apply("T003", loadJob1);

        updateStatus(busyLoad1, "Shortlisted");
        updateStatus(busyLoad2, "Hired");
        updateStatus(middleLoad1, "Interview");

        Response applicants = get("/api/mo/applicants?moId=M001&jobId=" + enc(targetJob));
        expectOk(applicants, "MO applicant list");
        List<Applicant> list = parseApplicants(applicants.body);
        expect(list.size() == 3, "target job should have 3 applicants, actual " + list.size() + ": " + applicants.body);
        expectOrder(list, List.of("T002", "T003", "T001"), "active-task sorting on target job");
        expect(countFor(list, "T002") == 0, "T002 should have 0 active tasks");
        expect(countFor(list, "T003") == 1, "T003 should have 1 active task");
        expect(countFor(list, "T001") == 2, "T001 should have 2 active tasks");
        expect(isNonDecreasing(list), "applicants are not sorted by activeTaskCount: " + list);

        Response duplicate = post("/api/ta/apply", Map.of("taId", "T002", "jobId", targetJob));
        expectStatus(duplicate, 400, "duplicate application");
        expectContains(errorOf(duplicate.body), "Already applied", "duplicate application error");

        Response taHistory = get("/api/ta/applications?taId=T001");
        expectOk(taHistory, "TA application history");
        expectContains(taHistory.body, busyTarget, "TA history contains target application");

        Response metrics = get("/api/admin/metrics");
        expectOk(metrics, "Admin metrics");
        expectContains(metrics.body, "\"totalJobs\":3", "admin total jobs");
        expectContains(metrics.body, "\"totalApplications\":6", "admin total applications");

        Response logs = get("/api/admin/logs");
        expectOk(logs, "Admin logs");
        expectContains(logs.body, "MO_POST_JOB", "admin logs contain job posting");
        expectContains(logs.body, "TA_APPLY", "admin logs contain TA application");

        Response invalidStatus = post("/api/mo/status", Map.of("moId", "M001", "appId", freeTarget, "status", "Done", "rejectReason", ""));
        expectStatus(invalidStatus, 400, "invalid MO status");
        expectContains(errorOf(invalidStatus.body), "Invalid status", "invalid status error");

        Response missingRejectReason = post("/api/mo/status", Map.of("moId", "M001", "appId", middleTarget, "status", "Rejected", "rejectReason", ""));
        expectStatus(missingRejectReason, 400, "missing reject reason");
        expectContains(errorOf(missingRejectReason.body), "Reject reason", "missing reject reason error");
    }

    private void backupDataFiles() throws IOException {
        for (String file : DATA_FILES) {
            Path path = Path.of(file);
            backup.put(file, Files.exists(path) ? Files.readAllBytes(path) : null);
        }
    }

    private void restoreDataFiles() {
        for (Map.Entry<String, byte[]> entry : backup.entrySet()) {
            try {
                Path path = Path.of(entry.getKey());
                Files.createDirectories(path.getParent());
                if (entry.getValue() == null) {
                    Files.deleteIfExists(path);
                } else {
                    Files.write(path, entry.getValue());
                }
            } catch (IOException e) {
                System.out.println("[WARN] Failed to restore " + entry.getKey() + ": " + e.getMessage());
            }
        }
    }

    private boolean isServerReachable() {
        try {
            Response response = get("/api/admin/metrics");
            return response.status < 500;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean startServer() throws Exception {
        System.out.println("[INFO] WebServer is not running. Starting it for the test...");
        serverProcess = new ProcessBuilder("java", "-cp", "out", "WebServer")
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start();
        long deadline = System.currentTimeMillis() + 8000;
        while (System.currentTimeMillis() < deadline) {
            if (isServerReachable()) {
                return true;
            }
            Thread.sleep(250);
        }
        throw new AssertionError("Cannot start WebServer on " + BASE_URL + ". Check whether port 8080 is occupied.");
    }

    private void resetBusinessData() throws Exception {
        Response response = post("/api/reset", Map.of());
        expectOk(response, "reset test data");
    }

    private String login(String email, String password) throws Exception {
        Response response = post("/api/login", Map.of("email", email, "password", password));
        expectOk(response, "login " + email);
        return response.body;
    }

    private Response saveProfile(String taId, String name, String studentId, String major, String phone, String fileName, byte[] bytes) throws Exception {
        return post("/api/ta/profile", Map.of(
                "taId", taId,
                "name", name,
                "studentId", studentId,
                "major", major,
                "phone", phone,
                "resumeText", "",
                "resumeFileName", fileName,
                "resumeFileBase64", Base64.getEncoder().encodeToString(bytes)
        ));
    }

    private void saveSimpleProfile(String taId, String name) throws Exception {
        Response response = saveProfile(taId, name, "20231234", "CS", "13800138000",
                taId.toLowerCase() + ".txt", (name + " resume").getBytes(StandardCharsets.UTF_8));
        expectOk(response, "save simple profile for " + taId);
    }

    private Map<String, String> baseProfile(String taId, String studentId, String phone) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("taId", taId);
        data.put("name", "Alice");
        data.put("studentId", studentId);
        data.put("major", "CS");
        data.put("phone", phone);
        data.put("resumeText", "resume");
        data.put("resumeFileName", "");
        data.put("resumeFileBase64", "");
        return data;
    }

    private String postJob(String title) throws Exception {
        Response response = post("/api/mo/job", Map.of(
                "moId", "M001",
                "title", title,
                "requirements", "English Level:CET-6; Work Duration:One semester; Weekend Availability:Yes",
                "deadline", LocalDate.now().plusDays(30).toString()
        ));
        expectOk(response, "post job " + title);
        return valueOf(response.body, "jobId");
    }

    private String apply(String taId, String jobId) throws Exception {
        Response response = post("/api/ta/apply", Map.of("taId", taId, "jobId", jobId));
        expectOk(response, "apply " + taId + " to " + jobId);
        return valueOf(response.body, "appId");
    }

    private void updateStatus(String appId, String status) throws Exception {
        Response response = post("/api/mo/status", Map.of("moId", "M001", "appId", appId, "status", status, "rejectReason", ""));
        expectOk(response, "update " + appId + " to " + status);
    }

    private Response get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + path)).GET().build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new Response(response.statusCode(), response.body());
    }

    private Response post(String path, Map<String, String> form) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + path))
                .header("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(formEncode(form), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new Response(response.statusCode(), response.body());
    }

    private String formEncode(Map<String, String> form) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : form.entrySet()) {
            if (!first) {
                sb.append('&');
            }
            first = false;
            sb.append(enc(entry.getKey())).append('=').append(enc(entry.getValue()));
        }
        return sb.toString();
    }

    private static String enc(String value) {
        return URLEncoder.encode(Objects.toString(value, ""), StandardCharsets.UTF_8);
    }

    private void expectOk(Response response, String step) {
        expect(response.status == 200 && response.body.contains("\"ok\":true"),
                step + " expected HTTP 200 ok:true, actual HTTP " + response.status + " body: " + response.body);
    }

    private void expectStatus(Response response, int status, String step) {
        expect(response.status == status,
                step + " expected HTTP " + status + ", actual HTTP " + response.status + " body: " + response.body);
    }

    private void expectContains(String actual, String expectedPart, String step) {
        expect(actual != null && actual.contains(expectedPart),
                step + " expected to contain [" + expectedPart + "], actual: " + actual);
    }

    private void expectOrder(List<Applicant> actual, List<String> expectedTaIds, String step) {
        List<String> actualTaIds = actual.stream().map(a -> a.taId).toList();
        expect(actualTaIds.equals(expectedTaIds),
                step + " expected TA order " + expectedTaIds + ", actual " + actualTaIds + " with applicants " + actual);
    }

    private void expect(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private String valueOf(String json, String key) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"").matcher(json);
        if (!matcher.find()) {
            throw new AssertionError("Cannot find JSON string field [" + key + "] in: " + json);
        }
        return unescapeJson(matcher.group(1));
    }

    private String errorOf(String json) {
        try {
            return valueOf(json, "error");
        } catch (AssertionError e) {
            return json;
        }
    }

    private List<Applicant> parseApplicants(String json) {
        List<Applicant> result = new ArrayList<>();
        Matcher objectMatcher = Pattern.compile("\\{([^{}]*\"appId\"[^{}]*)\\}").matcher(json);
        while (objectMatcher.find()) {
            String object = objectMatcher.group(1);
            String taId = valueOf("{" + object + "}", "taId");
            int activeTaskCount = intValueOf("{" + object + "}", "activeTaskCount");
            result.add(new Applicant(taId, activeTaskCount));
        }
        return result;
    }

    private int intValueOf(String json, String key) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(\\d+)").matcher(json);
        if (!matcher.find()) {
            throw new AssertionError("Cannot find JSON number field [" + key + "] in: " + json);
        }
        return Integer.parseInt(matcher.group(1));
    }

    private int countFor(List<Applicant> list, String taId) {
        return list.stream()
                .filter(a -> a.taId.equals(taId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Cannot find applicant " + taId + " in " + list))
                .activeTaskCount;
    }

    private boolean isNonDecreasing(List<Applicant> list) {
        return list.equals(list.stream().sorted(Comparator.comparingInt(a -> a.activeTaskCount)).toList());
    }

    private String unescapeJson(String text) {
        return text.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private byte[] minimalTextPdf(String text) {
        String escaped = text.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
        return ("%PDF-1.4\n1 0 obj\n<<>>\nstream\nBT (" + escaped + ") Tj ET\nendstream\nendobj\n%%EOF")
                .getBytes(StandardCharsets.ISO_8859_1);
    }

    private byte[] minimalDocx(String text) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
                    + "<w:body><w:p><w:r><w:t>" + xmlEscape(text) + "</w:t></w:r></w:p></w:body></w:document>";
            zip.write(xml.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return out.toByteArray();
    }

    private String xmlEscape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private String rootMessage(Throwable t) {
        Throwable current = t;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getClass().getSimpleName() + ": " + current.getMessage();
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private record Response(int status, String body) {}

    private record Applicant(String taId, int activeTaskCount) {}
}
