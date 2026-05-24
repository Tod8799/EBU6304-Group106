# User Manual – TA Recruitment System

This manual guides you through installing, running, and using the Teaching Assistant Recruitment System.
Each section includes a description of the main screens you will encounter.

---

## 1. Installation & First Run

### 1.1 Requirements
- **Java 11** or later (17+ recommended)
- A modern web browser (Chrome, Edge, Firefox)
- No additional libraries – the application is self‑contained.

### 1.2 Start the Server
1. Open a terminal / PowerShell in the project root folder.
2. Compile the code (only once):
   ```bash
   javac -encoding UTF-8 -d out src/model/*.java src/dao/*.java src/Main.java src/WebServer.java
   ```
3. Start the web server:
   ```bash
   java -cp out WebServer
   ```
4. Open your browser and go to **http://localhost:8080** .

![alt text](image.png)

---

## 2. Login Screen

The login page is the first screen you see in the browser.
- Enter your **email** and **password**.

![alt text](image-1.png)

---

## 3. TA (Teaching Assistant) Panel

After logging in as a TA, you enter the **TA Dashboard**. It has several tabs:

### 3.1 My Profile
- Fill in your **name**, **student ID** (8 digits), **major**, and **phone** (11 digits).
- Upload your **CV (PDF only)** by selecting a file. The maximum size is 5 MB.
- Click **Save**.

![alt text](image-2.png)

### 3.2 Job Applications
- Click **“Job Applications”** tab to see all open jobs.
- Each job shows its title, requirements, and deadline.
- Click **“Apply”** next to a suitable job. A success message confirms the application.

![alt text](image-3.png)

### 3.3 My Applications
- Click **“My Applications”** tab to see the status of every job you have applied for.
- Statuses can be **Pending, Shortlisted, Interview, Hired,** or **Rejected**. If rejected, the reason is also shown.

![alt text](image-4.png)

---

## 4. MO (Module Organiser) Panel

After logging in as MO, you see the **MO Dashboard**.

### 4.1 Post a Job
- Go to **“Post a Job”**.
- Enter the job **title**, **requirements**, and **deadline** (YYYY‑MM‑DD). The deadline must be in the future.
- Click **Post Job**.

![alt text](image-5.png)

### 4.2 My Jobs
- Switch to **“My Jobs”** to view all the jobs you have posted.
- Click **“View Applicants”** on any job to see who has applied.

![alt text](image-6.png)

### 4.3 Applicant Review
- The applicant list is sorted by **active workload** – TAs with fewer current tasks appear first.
- For each applicant you can see their profile details, download their **resume PDF**, and change their application status.
- Available statuses: **Shortlisted → Interview → Hired**.
- If you choose **Rejected**, you must provide a reason.

![alt text](image-7.png)

---

## 5. Admin Panel

After logging in as Admin, you see the **Admin Dashboard**.

### 5.1 Global Metrics
- Click **“Global Metrics”** to see system‑wide statistics:
  - Total jobs / open jobs
  - Total applications
  - Completion rate (percentage of applications no longer pending)
  - Status distribution (how many are Pending, Shortlisted, etc.)

![alt text](image-8.png)

### 5.2 Workload Overview
- Choose **“Workloads”** to see a detailed breakdown for every TA.
- The table shows each TA’s name, student ID, major, active workload count, and detailed status counts (Pending, Shortlisted, Interview, Hired, Rejected).
- TAs are sorted by active workload (highest first), making it easy to spot overloaded TAs.

![alt text](image-9.png)

### 5.3 Operation Logs
- Click **“Operation Logs”** to view a full audit trail.
- Every important action (login, job post, status change, registration) is recorded with timestamp, user ID, role, and detail.

![alt text](image-10.png)

---


## 6. Shutting Down

- In the terminal where the server is running, press `Ctrl + C` to stop the server.
- All data is automatically saved in the `data/` folder and will be available the next time you start the server.

---

## 7. Troubleshooting Quick Reference

| Problem | Solution |
|---------|----------|
| Browser shows “Unable to connect” | Make sure the terminal says “Web UI started”. If not, follow the start steps again. |
| Port 8080 is already in use | Use `netstat -ano | findstr :8080` (Windows) or `lsof -ti:8080` (Mac/Linux) to find and stop the process. |
| CV upload fails | Ensure the file is a **PDF** and smaller than 5 MB. |
| Cannot apply for a job twice | The system prevents duplicate applications – this is normal. Check your existing applications in “My Applications”. |

---

**End of User Manual**
