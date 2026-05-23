| GitHub Username | Student Name | QMID |
| --- | --- | --- |
|Tod8799  | Xuanchen Liu | 231223450 |
|Monicatwentytwo  | Jing Luo | 231223438 |
|liujy921  | Jiayi Liu | 231223276 |
|TXP  | Xiaopeng Tan | 231223379| 
|Nyog39  | Yankai Wang | 231223737| 
|Duck0114  | JianCheng Dong | 231223357 |


# TA Recruitment System

A lightweight role-based Teaching Assistant recruitment web application for the EBU6304 Software Engineering group project. Built with Java's built-in `HttpServer`, static HTML/CSS/JavaScript frontend, and CSV file persistence. **No Spring Boot, no external database.**

---

## Table of Contents
- [Features](#features)
- [Project Structure](#project-structure)
- [Prerequisites](#prerequisites)
- [Quick Start](#quick-start)
- [Demo Accounts](#demo-accounts)
- [API Endpoints](#api-endpoints)
- [Usage Guide](#usage-guide)
- [Application Status Lifecycle](#application-status-lifecycle)
- [Data Persistence](#data-persistence)
- [Troubleshooting](#troubleshooting)
- [Contributors](#contributors)

---

## Features

### Admin
- System-wide recruitment metrics (total users, jobs, applications, status breakdown)
- Full audit log with timestamp, user ID, role, action, and detail
- Reset all system data (testing only)

### Module Organiser (MO)
- Post job openings with title, requirements, and deadline
- View all self-posted jobs and applicants per job
- View applicant profiles and uploaded CVs (PDF, DOCX, TXT) – PDFs use embedded text extraction with OCR fallback
- Update application status through the full lifecycle: Shortlist, Interview, Hire, or Reject (with reason)

### TA / Applicant
- Create/update personal profile (name, student ID, major, phone)
- Upload CV in PDF, DOCX, or TXT format (max 5 MB)
- Browse open job postings and apply
- Track application statuses in real time

---

## Project Structure
```
ta-recruitment-system/
├── data/                  # CSV files (auto-created on first run)
│   ├── users.csv
│   ├── profiles.csv
│   ├── jobs.csv
│   ├── applications.csv
│   └── logs.csv
├── src/
│   ├── WebServer.java     # Web entry point
│   ├── Main.java          # Console entry point
│   ├── model/             # Domain classes
│   └── dao/               # Data access layer
├── web/                   # Frontend (static)
│   ├── index.html
│   ├── app.js
│   ├── styles.css
│   └── favicon.ico
├── scripts/               # OCR setup helper
│   └── setup-portable-ocr.ps1
└── run.ps1                # One-click start script (Windows PowerShell)
```

---

## Prerequisites
- **JDK 11 or higher** (JDK 17+ recommended; JDK 21/23 also work)
- A modern browser (Chrome, Edge, Firefox)
- **Internet access on first run** – required only to download OCR dependencies for PDF processing

---

## Quick Start

### 1. Compile (all platforms)
From the project root:
```bash
mkdir -p out
javac -encoding UTF-8 -d out src/model/*.java src/dao/*.java src/*.java
```

### 2. Run Web Server
```bash
java -cp out WebServer
```
Then open **http://localhost:8080** in your browser.

**Windows users** can alternatively use the provided PowerShell script (handles OCR setup + compilation + launch):
```powershell
powershell -ExecutionPolicy Bypass -File .\run.ps1
```

### 3. Run Console Mode (optional)
```bash
java -cp out Main
```
*Note: The console interface follows the same business logic but has a text-based menu.*

---

## Demo Accounts
| Role  | Email              | Password  |
|-------|--------------------|-----------|
| Admin | admin@bupt.edu     | admin123  |
| MO    | mo@bupt.edu        | mo123     |
| TA    | ta@bupt.edu        | ta123     |

These accounts are hardcoded in `data/users.csv` and are for **demonstration only**. Passwords are stored in plain text (not intended for production).

---

## API Endpoints
| Method | Endpoint             | Description                     | Role  |
|--------|----------------------|---------------------------------|-------|
| POST   | `/api/login`         | User login                      | All   |
| GET    | `/api/ta/profile`    | Get TA profile                  | TA    |
| POST   | `/api/ta/profile`    | Create or update TA profile     | TA    |
| POST   | `/api/ta/resume`     | Upload CV/resume (multipart)     | TA    |
| GET    | `/api/ta/jobs-open`  | Get all open job listings       | TA    |
| POST   | `/api/ta/apply`      | Submit a job application        | TA    |
| GET    | `/api/ta/applications` | Get own application statuses | TA    |
| POST   | `/api/mo/job`        | Post a new job                  | MO    |
| GET    | `/api/mo/jobs`       | Get MO's posted jobs            | MO    |
| GET    | `/api/mo/applicants` | Get applicants for a job        | MO    |
| POST   | `/api/mo/status`     | Update application status       | MO    |
| GET    | `/api/admin/metrics` | Get recruitment metrics         | Admin |
| GET    | `/api/admin/logs`    | Get audit logs                  | Admin |
| POST   | `/api/reset`         | Reset all data (testing)        | Admin |

---

## Usage Guide

### TA Workflow
1. Login with TA credentials.
2. Go to **My Profile** → fill in required fields → upload CV (PDF/DOCX/TXT).
3. Browse open jobs under **Job Applications** and **Apply**.
4. Track application status in **My Applications**.

### MO Workflow
1. Login with MO credentials.
2. **Post a Job** with title, requirements, and a valid future deadline.
3. View your jobs in **My Jobs** → click **View Applicants**.
4. Review applicant profile and CV → update status (Shortlist / Interview / Hire / Reject).

### Admin Workflow
1. Login with Admin credentials.
2. Open **Global Metrics** to see system-wide statistics.
3. Open **Operation Logs** to inspect all recorded actions.

---

## Application Status Lifecycle
```
          Pending
             |
        Shortlisted
             |
         Interview
             |
          Hired
```
At any stage, an application may be **Rejected** (with a required reason).

---

## Data Persistence
All data is stored in plain CSV files under `data/`. The directory and initial files are created automatically.
Resumes are Base64-encoded and saved directly in `profiles.csv`; no external upload folder is used.

---

## Troubleshooting

### Port 8080 already in use
**Windows:** `netstat -ano | findstr :8080` → `taskkill /PID <PID> /F`
**macOS/Linux:** `lsof -ti:8080 | xargs kill -9`

### "Failed to fetch" or backend errors
- Ensure `WebServer` is still running (check terminal for stack traces).
- Hard‑refresh the browser (`Ctrl+F5` / `Cmd+Shift+R`).

### PDF upload returns 400
- Only `.txt`, `.pdf`, `.docx` are accepted (max 5 MB).
- Scanned or corrupted PDFs that OCR cannot read will prompt re‑upload as DOCX/TXT.

### OCR dependencies not found
Run the setup script manually:
```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\setup-portable-ocr.ps1
```

---
