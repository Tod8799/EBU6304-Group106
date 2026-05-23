---

# 📚 User Manual

```markdown
# TA Recruitment System — User Manual

**Version:** 1.0  
**Application:** BUPT International School TA Recruitment System  
**Supported Roles:** Teaching Assistant (TA) · Module Organiser (MO) · Administrator

---

## Table of Contents

1. [Introduction](#1-introduction)
2. [System Requirements](#2-system-requirements)
3. [Getting Started — Login](#3-getting-started--login)
4. [TA Guide](#4-ta-guide)
5. [MO Guide](#5-mo-guide)
6. [Admin Guide](#6-admin-guide)
7. [FAQ & Troubleshooting](#7-faq--troubleshooting)
8. [Glossary](#8-glossary)

---

## 1. Introduction

The **TA Recruitment System** is a web-based application designed for  
BUPT International School to manage the Teaching Assistant recruitment process.

It replaces paper forms and Excel spreadsheets with a centralised system  
that allows:
- **TAs** to apply for positions online
- **Module Organisers** to post jobs and manage applicants
- **Administrators** to monitor the entire recruitment process

### Who should use this manual?
This manual is intended for all three types of users:

| Role | Description |
|------|-------------|
| **TA** | Students applying for Teaching Assistant positions |
| **MO** | Academic staff posting and managing TA job openings |
| **Admin** | System administrators monitoring and managing the platform |

---

## 2. System Requirements

| Requirement | Detail |
|-------------|--------|
| Operating System | Windows / macOS / Linux |
| Browser | Chrome / Firefox / Edge (latest version recommended) |
| Network | Local network access to the server |
| Server | Java JDK 11+ must be running on the host machine |

> ℹ️ No software installation is needed on the user's machine.  
> Simply open a browser and navigate to the system URL.

---

## 3. Getting Started — Login

### Step 1 — Open the Application
Open your web browser and go to:

http://localhost:8080

> If accessing from another machine on the same network, replace  
> `localhost` with the server's IP address.

### Step 2 — Enter Your Credentials

| Field    | Description |
|----------|-------------|
| Email    | Your registered email address |
| Password | Your account password |

### Step 3 — Click "Login"

You will be redirected to your role-specific dashboard automatically.

### Login Errors

| Error Message | Cause | Solution |
|---------------|-------|----------|
| "Invalid credentials" | Wrong email or password | Check your email and password |
| "User not found" | Account does not exist | Contact your administrator |

---

## 4. TA Guide

### 4.1 Dashboard Overview

After login, the TA dashboard shows:
- Your profile completion status
- Available job listings
- Your recent application status

---

### 4.2 Create / Update Your Profile

Before applying for jobs, you must complete your profile.

**Steps:**
1. Click **"My Profile"** in the navigation menu
2. Fill in the following fields:

| Field | Required | Description |
|-------|----------|-------------|
| Full Name | ✅ | Your full legal name |
| Student ID | ✅ | Your BUPT student ID number |
| Major | ✅ | Your academic major / programme |
| Phone | ✅ | Your contact phone number |

3. Click **"Save Profile"**

> ✅ A success message will confirm your profile has been saved.

---

### 4.3 Upload Your CV / Resume

**Steps:**
1. Go to **"My Profile"**
2. Click **"Upload Resume"**
3. Select your CV file

**Supported formats:**
| Format | Extension |
|--------|-----------|
| PDF Document | `.pdf` |
| Word Document | `.docx` |
| Plain Text | `.txt` |

4. Click **"Upload"**

> ⚠️ Only one resume can be stored at a time.  
> Uploading a new file will replace the previous one.

---

### 4.4 Browse Available Jobs

**Steps:**
1. Click **"Browse Jobs"** in the navigation menu
2. A list of open positions will be displayed

Each job listing shows:
| Field | Description |
|-------|-------------|
| Job Title | Name of the position |
| Requirements | Skills and qualifications needed |
| Deadline | Application closing date |
| Posted By | Module Organiser name |

---

### 4.5 Apply for a Job

**Steps:**
1. Click **"Browse Jobs"**
2. Find a suitable position
3. Click **"Apply"** on the job listing
4. Confirm your application in the dialog box

> ✅ A success message confirms your application has been submitted.

**Important notes:**
- You can only apply for each job **once**
- You must have a completed profile before applying
- Applications cannot be withdrawn after submission

---

### 4.6 Check Application Status

**Steps:**
1. Click **"My Applications"** in the navigation menu
2. All your applications are listed with their current status

**Application Statuses Explained:**

| Status | Meaning |
|--------|---------|
| 🟡 **Pending** | Your application has been received and is awaiting review |
| 🔵 **Shortlisted** | You have been shortlisted for further consideration |
| 🟠 **Interview** | You have been invited for an interview |
| 🟢 **Hired** | Congratulations! You have been selected for the position |
| 🔴 **Rejected** | Your application was not successful (a reason may be provided) |

---

## 5. MO Guide

### 5.1 Dashboard Overview

After login, the MO dashboard shows:
- Your posted jobs summary
- Recent applicant activity

---

### 5.2 Post a New Job

**Steps:**
1. Click **"Post a Job"** in the navigation menu
2. Fill in the following fields:

| Field | Required | Description |
|-------|----------|-------------|
| Job Title | ✅ | Title of the TA position |
| Requirements | ✅ | Skills, qualifications, and responsibilities |
| Application Deadline | ✅ | Last date for applications (YYYY-MM-DD format) |

3. Click **"Post Job"**

> ✅ The job will immediately appear in the TA job listings.

---

### 5.3 View Your Posted Jobs

**Steps:**
1. Click **"My Jobs"** in the navigation menu
2. All jobs you have posted are listed

Each entry shows:
- Job title
- Application deadline
- Number of applicants
- Date posted

---

### 5.4 Review Applicants

**Steps:**
1. Click **"My Jobs"**
2. Click **"View Applicants"** next to a job
3. A list of all applicants for that job is displayed

Each applicant entry shows:
| Field | Description |
|-------|-------------|
| Name | Applicant's full name |
| Student ID | Applicant's student ID |
| Major | Applicant's academic major |
| Phone | Applicant's contact number |
| Applied At | Date and time of application |
| Current Status | Current status in the pipeline |

4. Click **"View Resume"** to read the applicant's uploaded CV

---

### 5.5 Update Application Status

**Steps:**
1. In the applicant list, find the applicant you want to update
2. Click **"Update Status"**
3. Select the new status:

| Action | Result | When to Use |
|--------|--------|-------------|
| Shortlist | Status → Shortlisted | Candidate meets initial criteria |
| Interview | Status → Interview | Candidate invited for interview |
| Hire | Status → Hired | Candidate selected for the role |
| Reject | Status → Rejected | Candidate not suitable |

4. If selecting **"Reject"**, enter a brief reason in the text box
5. Click **"Confirm"**

> ✅ The applicant will see the updated status immediately in their dashboard.

---

## 6. Admin Guide

### 6.1 Dashboard Overview

After login, the Admin dashboard provides a system-wide overview of  
all recruitment activity.

---

### 6.2 View Recruitment Metrics

**Steps:**
1. Click **"Metrics"** in the navigation menu
2. The metrics dashboard displays key statistics

**Available Metrics:**
| Metric | Description |
|--------|-------------|
| Total Users | Total number of registered users |
| Total TAs | Number of registered Teaching Assistants |
| Total MOs | Number of registered Module Organisers |
| Total Jobs | Total number of job postings |
| Total Applications | Total number of applications submitted |
| Applications by Status | Breakdown by Pending / Shortlisted / Interview / Hired / Rejected |

---

### 6.3 View Audit Logs

The audit log records all significant system actions for accountability  
and transparency.

**Steps:**
1. Click **"Audit Logs"** in the navigation menu
2. All system events are listed in chronological order

**Log Entry Fields:**

| Field | Description |
|-------|-------------|
| Timestamp | Date and time of the action |
| User ID | ID of the user who performed the action |
| Role | Role of the user (TA / MO / Admin) |
| Action | Type of action performed |
| Detail | Additional details about the action |

**Common Logged Actions:**
- User login
- Profile creation / update
- Resume upload
- Job posting
- Application submission
- Application status update

---

### 6.4 Reset System Data

> ⚠️ **WARNING: This action is irreversible.**  
> All data will be permanently deleted.

**Steps:**
1. Click **"Reset System"** in the navigation menu
2. Read the warning message carefully
3. Click **"Confirm Reset"**

> This feature is intended for **testing purposes only**.  
> Do not use in a live production environment.

---

## 7. FAQ & Troubleshooting

### General

**Q: I cannot access the system in my browser.**  
A: Ensure the server is running. Check with your administrator that  
`WebServer.java` has been started and is listening on port 8080.

**Q: My login is not working.**  
A: Double-check your email address and password. Passwords are  
case-sensitive. Contact your administrator if the problem persists.

---

### TA Issues

**Q: I cannot apply for a job.**  
A: Ensure your profile is fully completed before applying.  
All required fields (Name, Student ID, Major, Phone) must be filled in.

**Q: I applied for a job but do not see it in "My Applications".**  
A: Refresh the page. If still missing, contact your administrator.

**Q: My resume upload is failing.**  
A: Check that your file is in PDF, DOCX, or TXT format.  
Try a smaller file if the upload continues to fail.

---

### MO Issues

**Q: My posted job is not visible to TAs.**  
A: Verify that the application deadline has not already passed.  
Only jobs with future deadlines appear in the TA job listings.

**Q: I cannot see applicants for my job.**  
A: No TAs have applied yet. The list will populate once applications  
are received.

---

### Admin Issues

**Q: The audit log is empty.**  
A: No actions have been recorded yet, or the log file has been reset.

**Q: Metrics are showing 0 for everything.**  
A: The data files may be empty or missing. Check the `data/` directory  
on the server.

---

## 8. Glossary

| Term | Definition |
|------|------------|
| **TA** | Teaching Assistant — a student who supports academic modules |
| **MO** | Module Organiser — academic staff responsible for a course module |
| **Admin** | System Administrator — manages the platform |
| **Profile** | A TA's personal information page including contact details and resume |
| **CV / Resume** | A document summarising a TA's qualifications and experience |
| **Application** | A TA's formal request to be considered for a job posting |
| **Shortlisted** | A TA whose application has passed the initial review stage |
| **Audit Log** | A chronological record of all system actions for accountability |
| **Metrics** | System-wide statistics summarising recruitment activity |
| **Base64** | Encoding method used to store resume files safely in CSV format |