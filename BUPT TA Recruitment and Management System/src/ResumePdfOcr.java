import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Visual OCR pipeline for PDF resumes:
 * PDF bytes -> page images (PDFBox) -> Tesseract CLI OCR.
 */
public final class ResumePdfOcr {
    private static final int MAX_PAGES = 5;
    private static final int RENDER_DPI = 300;
    private static final int OCR_TIMEOUT_SECONDS = 120;

    private ResumePdfOcr() {
    }

    public static String extractText(byte[] pdfBytes) throws IOException {
        if (pdfBytes == null || pdfBytes.length == 0) {
            return "";
        }
        Path tesseractExe = resolveTesseractExe();
        Path tessRoot = resolveTesseractRoot();
        if (tesseractExe == null || tessRoot == null) {
            throw new IOException(
                    "OCR runtime is missing. Run: powershell -ExecutionPolicy Bypass -File scripts/setup-portable-ocr.ps1");
        }

        String language = resolveLanguagesOrThrow(tessRoot.resolve("tessdata"));
        Path workDir = Files.createTempDirectory("resume-ocr-");
        try {
            StringBuilder merged = new StringBuilder();
            try (PDDocument document = Loader.loadPDF(pdfBytes)) {
                String embeddedText = extractEmbeddedText(document);
                if (!embeddedText.isBlank()) {
                    return embeddedText;
                }
                PDFRenderer renderer = new PDFRenderer(document);
                int pageCount = Math.min(document.getNumberOfPages(), MAX_PAGES);
                for (int page = 0; page < pageCount; page++) {
                    BufferedImage image = renderer.renderImageWithDPI(page, RENDER_DPI);
                    Path imagePath = workDir.resolve("page-" + page + ".png");
                    ImageIO.write(image, "png", imagePath.toFile());

                    String pageText = runTesseractCli(tesseractExe, tessRoot, imagePath, language, "6");
                    if (pageText.isBlank()) {
                        // Retry with auto page segmentation for complex layouts.
                        pageText = runTesseractCli(tesseractExe, tessRoot, imagePath, language, "3");
                    }
                    if (pageText != null && !pageText.isBlank()) {
                        if (merged.length() > 0) {
                            merged.append('\n');
                        }
                        merged.append(pageText.trim());
                    }
                }
            }
            return merged.toString();
        } finally {
            deleteRecursively(workDir);
        }
    }

    private static String runTesseractCli(Path tesseractExe, Path tessRoot, Path imagePath, String language, String psm)
            throws IOException {
        List<String> command = new ArrayList<>();
        command.add(tesseractExe.toAbsolutePath().toString());
        command.add(imagePath.toAbsolutePath().toString());
        command.add("stdout");
        command.add("-l");
        command.add(language);
        command.add("--psm");
        command.add(psm);
        command.add("--dpi");
        command.add(String.valueOf(RENDER_DPI));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        pb.environment().put("TESSDATA_PREFIX", tessRoot.toAbsolutePath().toString());

        Process process = pb.start();
        String output;
        try (InputStream in = process.getInputStream()) {
            output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        try {
            if (!process.waitFor(OCR_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("OCR timed out while reading PDF");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IOException("OCR interrupted", e);
        }
        if (process.exitValue() != 0) {
            throw new IOException("OCR failed: " + output.trim());
        }
        return output;
    }

    private static String extractEmbeddedText(PDDocument document) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setSortByPosition(true);
        stripper.setStartPage(1);
        stripper.setEndPage(Math.min(document.getNumberOfPages(), MAX_PAGES));
        String text = stripper.getText(document);
        return text == null ? "" : text.trim();
    }

    private static String resolveLanguagesOrThrow(Path tessDataDir) throws IOException {
        boolean hasChi = Files.exists(tessDataDir.resolve("chi_sim.traineddata"));
        boolean hasEng = Files.exists(tessDataDir.resolve("eng.traineddata"));
        if (hasChi && hasEng) {
            return "chi_sim+eng";
        }
        if (hasChi) {
            return "chi_sim";
        }
        if (hasEng) {
            return "eng";
        }
        throw new IOException("No tessdata language files found under " + tessDataDir);
    }

    private static Path resolveTesseractExe() {
        Path root = resolveTesseractRoot();
        if (root == null) {
            return null;
        }
        Path exe = root.resolve("tesseract.exe");
        return Files.isRegularFile(exe) ? exe : null;
    }

    private static Path resolveTesseractRoot() {
        Path projectRoot = resolveProjectRoot();
        List<Path> candidates = List.of(
                projectRoot.resolve("vendor/tesseract"),
                Path.of("C:/Program Files/Tesseract-OCR")
        );
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate.resolve("tesseract.exe"))
                    && Files.isDirectory(candidate.resolve("tessdata"))) {
                return candidate.toAbsolutePath();
            }
        }
        return null;
    }

    static Path resolveProjectRoot() {
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path current = cwd;
        for (int i = 0; i < 6; i++) {
            if (Files.isDirectory(current.resolve("data"))
                    && Files.isDirectory(current.resolve("src"))) {
                return current;
            }
            Path parent = current.getParent();
            if (parent == null) {
                break;
            }
            current = parent;
        }
        return cwd;
    }

    private static void deleteRecursively(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }
}
