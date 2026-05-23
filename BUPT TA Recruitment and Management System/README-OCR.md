# Portable OCR Setup (Near Zero Manual Steps)

This project uses a **visual OCR pipeline** for PDF resumes:

1. **PDFBox** renders each PDF page to an image.
2. **Tesseract OCR** extracts text from those images.

This means scanned/image-based PDFs are supported and the old raw PDF token parsing path is no longer used.

## First Run on a New Machine

Install **JDK 17+** first, then run from the project root:

```powershell
powershell -ExecutionPolicy Bypass -File .\run.ps1
```

`run.ps1` will automatically:

- download Java dependencies into `lib/` (PDFBox, OCR-related JARs, etc.),
- prepare OCR runtime under `vendor/tesseract/`,
- attempt a one-time `winget` install of Tesseract if it is missing,
- compile source files and start `WebServer`.

You do **not** need to manually install Poppler or set OCR environment variables.

## Optional: Setup OCR Dependencies Only

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\setup-portable-ocr.ps1
```

## Directory Overview

```text
lib/                    # Downloaded Java dependency JARs
vendor/tesseract/       # Portable tesseract.exe + tessdata/
src/ResumePdfOcr.java   # OCR implementation entry
```

## Notes

- OCR scans up to the first **5 pages**.
- Render DPI is tuned for OCR quality (currently **300 DPI**).
- If both language packs exist, OCR uses `chi_sim+eng`; otherwise it falls back to available packs.
- For scanned/corrupted PDFs that still cannot be recognized, the backend returns a conversion hint to upload DOCX/TXT.
