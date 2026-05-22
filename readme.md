# Group 106 TA Recruitment System

This is a lightweight Java web application for the EBU6304 Software Engineering 
group project. It supports role-based TA recruitment for Teaching Assistants, 
Module Organisers, and Admin users.

The project follows the coursework restriction: it uses a Java `HttpServer` 
backend, static HTML/CSS/JavaScript frontend, and simple CSV files under 
`data/` for persistence. It does **not** use Spring Boot or a database.

## Current Version

This package contains the completed final implementation. It supports 
TA profile management, CV upload and retrieval, job posting, application 
management with a full status lifecycle, and an admin monitoring panel 
with audit logging.

The login system uses fixed demo accounts for prototype purposes. It validates 
email and password against `data/users.csv`. It does not implement production 
password hashing or server sessions.

## Implemented Features

### Admin
- View system-wide recruitment metrics:
  - total users, TAs, and Module Organisers;
  - total jobs posted;
  - total applications submitted;
  - application breakdown by status.
- View full system audit logs with timestamp, user ID, role, action, and detail.
- Reset all system data (for testing purposes only).

### Module Organiser
- Post new job openings with title, requirements, and application deadline.
- View all self-posted jobs.
- View all applicants for a specific job, including profile and contact details.
- View applicant uploaded CV/resume.
- Update application status through the full recruitment lifecycle:
  - Shortlist, Interview, Hire, or Reject with a reason.

### TA / Applicant
- Create and update personal profile including name, student ID, major, 
  and phone number.
- Upload CV in PDF, DOCX, or TXT format.
- Browse all open job postings with title, requirements, and deadline.
- Submit applications for available positions.
- Track all application statuses in real time.

## Project Structure

```text
ta-recruitment-system/
├── README.md
├── data/
│   ├── users.csv
│   ├── profiles.csv
│   ├── jobs.csv
│   ├── applications.csv
│   └── logs.csv
├── src/
│   ├── WebServer.java
│   ├── Main.java
│   ├── model/
│   │   ├── User.java
│   │   ├── Profile.java
│   │   ├── Job.java
│   │   ├── ApplicationRecord.java
│   │   └── AuditLog.java
│   └── dao/
│       ├── UserDao.java
│       ├── ProfileDao.java
│       ├── JobDao.java
│       ├── ApplicationDao.java
│       └── LogDao.java
└── web/
    ├── index.html
    ├── app.js
    ├── styles.css
    └── favicon.ico
```
## Prerequisites

Java 11 or above.

A modern browser such as Chrome, Edge, or Firefox.

## How to Compile and Run

From the project root directory:
```bash
mkdir out
javac -encoding UTF-8 -d out src/model/*.java src/dao/*.java src/*.java
java -cp out WebServer
```
Then open the following address in a browser:
```text
http://localhost:8080
```
## Demo Accounts

The data/ folder includes pre-loaded demo users for all three roles.

Role	Email	Password
Admin	admin@bupt.edu
	admin123
MO	mo@bupt.edu
	mo123
TA	ta@bupt.edu
	ta123

These are fixed prototype accounts and do not correspond to real email
addresses. They are intended for demonstration and testing only.

## Main Workflows
TA Workflow

Log in using the TA demo account.

Go to My Profile and fill in name, student ID, major, and phone number.

Upload a CV in PDF, DOCX, or TXT format.

Browse available job postings.

Apply for a suitable position.

Track application status under My Applications.

Module Organiser Workflow

Log in using the MO demo account.

Go to Post a Job and fill in title, requirements, and deadline.

View posted jobs under My Jobs.

Click View Applicants to see all applicants for a job.

Review applicant profiles and uploaded CVs.

Update application status to Shortlist, Interview, Hire, or Reject.

Admin Workflow

Log in using the Admin demo account.

View recruitment metrics including application counts and status breakdown.

View the full audit log to monitor all system activity.

Use Reset System to clear all data during testing.

## Application Status Lifecycle
```text
           ┌─────────────┐
           │   Pending   │
           └──────┬──────┘
                  │
      ┌───────────▼───────────┐
      │      Shortlisted      │
      └───────────┬───────────┘
                  │
      ┌───────────▼───────────┐
      │       Interview       │
      └───────────┬───────────┘
                  │
      ┌───────────▼───────────┐
      │         Hired         │
      └───────────────────────┘

  At any stage → Rejected (with reason provided)
```
## API Endpoints
Method	Endpoint	Description	Role
POST	/api/login	User login	All
GET	/api/ta/profile	Get TA profile	TA
POST	/api/ta/profile	Create or update TA profile	TA
POST	/api/ta/resume	Upload CV/resume file	TA
GET	/api/ta/jobs-open	Get all open job listings	TA
POST	/api/ta/apply	Submit a job application	TA
GET	/api/ta/applications	Get own application statuses	TA
POST	/api/mo/job	Post a new job	MO
GET	/api/mo/jobs	Get MO's posted jobs	MO
GET	/api/mo/applicants	Get applicants for a job	MO
POST	/api/mo/status	Update application status	MO
GET	/api/admin/metrics	Get system recruitment metrics	Admin
GET	/api/admin/logs	Get system audit logs	Admin
POST	/api/reset	Reset all system data	Admin
## Data File Formats

All data is stored as plain CSV files in the data/ directory.
The directory and all files are created automatically on first run.

File	Fields
users.csv	id, email, password, role
profiles.csv	taId, name, studentId, major, phone, resumeText(Base64), resumeFileName(Base64)
jobs.csv	jobId, moId, title, requirements, deadline, createdAt
applications.csv	appId, jobId, taId, status, appliedAt, rejectReason
logs.csv	timestamp, userId, role, action, detail

Resume files are Base64-encoded and stored directly in profiles.csv.

No separate upload directory is used.

## Notes for Final Demonstration

Demonstrate role login for all three roles: TA, MO, and Admin.

Demonstrate the complete TA workflow: profile creation, CV upload,
job browsing, application submission, and status tracking.

Demonstrate the complete MO workflow: job posting, applicant review,
CV viewing, and status update through the full lifecycle.

Demonstrate the Admin panel: recruitment metrics and audit log viewing.

Demonstrate at least one error-handling case such as applying for a job
twice, submitting an incomplete profile, or uploading an unsupported file format.

Note that the login system is a lightweight prototype. User IDs are validated
against data/users.csv with plain-text passwords for demonstration purposes only.