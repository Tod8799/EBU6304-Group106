| GitHub Username | Student Name | QMID |
| --- | --- | --- |
|Tod8799  | Xuanchen Liu | 231223450 |
|Monicatwentytwo  | Jing Luo | 231223438 |
|liujy921  | Jiayi Liu | 231223276 |
|TXP  | Xiaopeng Tan | 231223379| 
|Nyog39  | Yankai Wang | 231223737| 
|Duck0114  | JianCheng Dong | 231223357 |

# TA Recruitment System

A lightweight role‑based Teaching Assistant recruitment web application for the EBU6304 Software Engineering group project. Built with Java's built‑in `HttpServer`, static HTML/CSS/JavaScript frontend, and CSV file persistence. **No Spring Boot, no external database.**

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
- [Documentation](#documentation)

---

## Features

### Admin
- System‑wide recruitment metrics (total jobs, open jobs, applications, completion rate)
- Detailed workload breakdown per TA, with status counts and active positions
- Full audit log with timestamp, user ID, role, action, and detail
- Reset all system data for testing

### Module Organiser (MO)
- Post job openings with title, requirements, and deadline (strict validation)
- View all self‑posted jobs and applicants per job, sorted by workload
- View applicant profiles and uploaded CVs (PDF only)
- Download or view applicant resume PDF directly
- Update application status through the full lifecycle: Shortlist → Interview → Hired; reject with mandatory reason

### TA / Applicant
- Self‑registration (with email/password validation)
- Create/update personal profile (name, student ID, major, phone)
- Upload CV as **PDF** (max 5 MB) – stored securely on disk
- Browse open job postings and apply (duplicate and expiry checks)
- Track application statuses in real time

---

## Project Structure
```
ta-recruitment-system/
├── data/
│   ├── users.csv
│   ├── profiles.csv
│   ├── jobs.csv
│   ├── applications.csv
│   ├── logs.csv
│   └── resumes/               # uploaded PDF resumes
├── docs/                      # Javadoc documentation
├── src/
│   ├── Main.java              # Console entry point
│   ├── WebServer.java         # Web server entry point
│   ├── FeatureRegressionTest.java   # Automated test suite
│   ├── E2EBusinessLogicTest.java    # End‑to‑end business flow test
│   ├── model/                 # Domain classes
│   │   ├── User.java
│   │   ├── Profile.java
│   │   ├── Job.java
│   │   ├── ApplicationRecord.java
│   │   └── AuditLog.java
│   └── dao/                   # Data access layer
│       ├── UserDAO.java
│       ├── ProfileDAO.java
│       ├── JobDAO.java
│       ├── ApplicationDAO.java
│       └── AuditLogDAO.java
├── web/
│   ├── index.html
│   ├── app.js
│   ├── styles.css
│   └── favicon.ico
├── run.ps1                    # One‑click start script (Windows)
└── README.md
```

---

## Prerequisites
- **JDK 11 or higher** (JDK 17+ recommended)
- A modern browser (Chrome, Edge, Firefox)
- The system **does not require any external libraries** – all PDF parsing is done with built‑in Java code.

---

## Quick Start

### 1. Compile
From the project root directory:

```bash
javac -encoding UTF-8 -d out src/model/*.java src/dao/*.java src/Main.java src/WebServer.java src/FeatureRegressionTest.java src/E2EBusinessLogicTest.java
```

This command compiles all necessary source files into the `out` folder.

### 2. Run Web Server
```bash
java -cp out WebServer
```
Then open **http://localhost:8080** in your browser.

**Windows users** can use the provided PowerShell script:
```powershell
powershell -ExecutionPolicy Bypass -File .\run.ps1
```
It handles compilation and starts the server automatically.

### 3. Run Console Mode (optional)
```bash
java -cp out Main
```
Provides a text‑based interface with the same business logic.

### 4. Run Tests
After starting the server (in another terminal), execute:
```bash
java -cp out FeatureRegressionTest
```
```bash
java -cp out E2EBusinessLogicTest
```

---

## Demo Accounts
| Role  | Email              | Password |
|-------|--------------------|----------|
| Admin | admin@bupt.edu     | admin123 |
| MO    | mo@bupt.edu        | mo123    |
| TA    | ta@bupt.edu        | ta123    |

These accounts are seeded in `data/users.csv`. Passwords are plain text for demonstration only.
New TAs can also create their own account via the registration page.

---

## API Endpoints
| Method | Endpoint              | Description                             | Role  |
|--------|------------------------|-----------------------------------------|-------|
| POST   | `/api/login`          | User login                              | All   |
| POST   | `/api/register`       | Register a new TA account               | All   |
| GET    | `/api/ta/profile`     | Get TA profile                          | TA    |
| POST   | `/api/ta/profile`     | Create/update profile and upload resume | TA    |
| GET    | `/api/ta/jobs-open`   | Get all open job listings               | TA    |
| POST   | `/api/ta/apply`       | Submit a job application                | TA    |
| GET    | `/api/ta/applications`| Get own application statuses            | TA    |
| POST   | `/api/mo/job`         | Post a new job                          | MO    |
| GET    | `/api/mo/jobs`        | Get MO's posted jobs                    | MO    |
| GET    | `/api/mo/applicants`  | Get applicants for a job (sorted)       | MO    |
| POST   | `/api/mo/status`      | Update application status               | MO    |
| GET    | `/api/mo/resume`      | View applicant's resume PDF             | MO    |
| GET    | `/api/admin/metrics`  | Get global recruitment metrics          | Admin |
| GET    | `/api/admin/workloads`| Get per‑TA workload statistics          | Admin |
| GET    | `/api/admin/logs`     | Get system audit logs                   | Admin |
| POST   | `/api/reset`          | Reset all data (testing only)           | Admin |

---

## Usage Guide

### TA Workflow
1. Login or register a new TA account.
2. Go to **My Profile** → fill in personal details → upload CV (PDF only).
3. Browse open jobs under **Job Applications** → click **Apply**.
4. Track application status in **My Applications**.

### MO Workflow
1. Login with MO credentials.
2. **Post a Job** with title, requirements, and a valid future deadline.
3. View your jobs in **My Jobs** → click **View Applicants**.
4. Review applicant profiles and download PDF resumes.
5. Update status: Shortlist → Interview → Hired, or **Reject** (reason required).

### Admin Workflow
1. Login with Admin credentials.
2. Open **Global Metrics** for system‑wide statistics.
3. Open **Workloads** to see each TA's active tasks and status breakdown.
4. Open **Operation Logs** to view all recorded actions.

---

## Application Status Lifecycle
```
          Pending
             │
        Shortlisted
             │
         Interview
             │
          Hired
```
At any stage → **Rejected** (with mandatory reason).

---

## Data Persistence
- All data is stored in **plain CSV files** under `data/`.
- Uploaded **PDF resumes** are saved to `data/resumes/` and referenced by path in `profiles.csv`.
- The initial CSV files are created automatically on first run.

---

## Troubleshooting

### Port 8080 already in use
**Windows:** `netstat -ano | findstr :8080` → `taskkill /PID <PID> /F`
**macOS/Linux:** `lsof -ti:8080 | xargs kill -9`

### “Failed to fetch” or backend errors
- Ensure `WebServer` is running (check terminal for errors).
- Hard‑refresh browser (`Ctrl+F5` / `Cmd+Shift+R`).

### Resume upload returns 400
- Only **PDF** files are accepted (max 5 MB).
- The file must be a valid PDF; corrupted or image‑only PDFs are rejected with guidance.

### Tests fail to connect
- Make sure the web server is started first in a separate terminal.
- Confirm that `localhost:8080` is accessible.

---

## Documentation
Full Javadoc documentation is available in the `docs/` folder.
To regenerate after code changes, run:
```bash
javadoc -d docs -encoding UTF-8 -sourcepath src -subpackages model:dao src/Main.java src/WebServer.java
```

---

*For further details, refer to the project report or source code comments.*
```
