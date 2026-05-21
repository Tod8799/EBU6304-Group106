# TA Recruitment System (Console + Web)

This project is a Java-based Teaching Assistant recruitment and management system.
It provides:

- a console entry point: `Main`
- a web entry point: `WebServer`
- CSV file persistence under `data/`
- a browser UI under `web/`

## 1) Requirements

- JDK 17+ (JDK 21/23 also works)
- Windows PowerShell (commands below are PowerShell style)
- Internet access on first run (to download OCR dependencies)

Check Java:

```powershell
java -version
javac -version
```

## 2) Repository Layout

```text
BUPT TA Recruitment and Management System---version 1/
  src/
    Main.java
    WebServer.java
    ResumePdfOcr.java
    dao/
    model/
  data/
    users.csv
    profiles.csv
    jobs.csv
    applications.csv
    logs.csv
  web/
    index.html
    app.js
    styles.css
    favicon.ico
  scripts/
    setup-portable-ocr.ps1
  run.ps1
```

## 3) Start the Web App (Recommended)

From project root:

```powershell
cd ".\BUPT TA Recruitment and Management System---version 1"
powershell -ExecutionPolicy Bypass -File .\run.ps1
```

`run.ps1` will:

1. set up OCR runtime and Java dependencies (first run only),
2. compile sources,
3. start `WebServer` on `http://localhost:8080`.

Open:

```text
http://localhost:8080
```

## 4) OCR Behavior for PDF Resumes

PDF processing uses a hybrid strategy:

1. try embedded text extraction first (best for digital PDFs),
2. fall back to OCR for scanned/image PDFs.

If OCR cannot read a scanned/corrupted PDF, the API returns a clear guidance message:
`Please convert it to DOCX/TXT and upload again.`

## 5) Run Console Mode

From project root:

```powershell
cd ".\BUPT TA Recruitment and Management System---version 1"
javac -encoding UTF-8 -d out src\model\*.java src\dao\*.java src\Main.java
java -cp out Main
```

## 6) Default Accounts

- Admin: `admin@bupt.edu / admin123`
- MO: `mo@bupt.edu / mo123`
- TA: `ta@bupt.edu / ta123`

## 7) Quick Functional Walkthrough (Web)

### Step 0: Reset demo data (optional)

```powershell
'' | Set-Content .\data\profiles.csv
'' | Set-Content .\data\jobs.csv
'' | Set-Content .\data\applications.csv
'' | Set-Content .\data\logs.csv
```

### Step 1: MO posts jobs

1. Login as MO.
2. Go to `Job Management`.
3. Fill all fields and post a job.

Validation notes:

- empty/whitespace-only fields are rejected,
- invalid date is rejected,
- past deadline is rejected.

### Step 2: TA creates profile and uploads resume

1. Login as TA.
2. Fill profile fields (`Student ID` 8 digits, `Phone` 11 digits).
3. Upload `txt/pdf/docx` resume and click save.

### Step 3: TA applies for jobs

1. Open `Job Applications`.
2. Refresh open jobs.
3. View requirements and apply.

### Step 4: MO reviews applicants

1. Login as MO.
2. Go to `Application Review`.
3. Refresh jobs and applicants.
4. Approve or reject (reject requires reason).

### Step 5: Admin checks system status

1. Login as Admin.
2. Open `Global Metrics` and `Operation Logs`.

## 8) Troubleshooting

### 8.1 Port 8080 is already in use

```powershell
netstat -ano | Select-String ":8080"
taskkill /PID <PID> /F
```

### 8.2 `Failed to fetch` on frontend

- Ensure `run.ps1` is still running.
- Check terminal output for backend exception stack traces.
- Hard refresh browser (`Ctrl+F5`).

### 8.3 PDF upload returns 400

Common causes:

- unsupported extension (only `txt/pdf/docx`),
- file too large (`max 5MB`),
- scanned/corrupted PDF that OCR cannot read.

For unreadable scanned PDFs, convert to DOCX/TXT and upload again.

### 8.4 OCR setup issues on new computers

Run:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\setup-portable-ocr.ps1
```

Then rerun:

```powershell
powershell -ExecutionPolicy Bypass -File .\run.ps1
```

## 9) E2E Script (Optional)

If you use the external script `E2EBusinessLogicTest.java`, compile and run from project root:

```powershell
javac -encoding UTF-8 -d . "d:\Download\E2EBusinessLogicTest.java"
java E2EBusinessLogicTest
```

Note: the script expects a valid `test_cv.pdf` under the project directory for one PDF test phase.