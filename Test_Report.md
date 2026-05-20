# Software System Test Report

**Tester:** [Jiayi Liu / 231223276]  
**Date:** 202-05-16  

## 1. Test Environment
- **OS:** Windows 11
- **Runtime:** JDK 17
- **Browser:** Latest version of Chrome / Edge
- **Target Version:** Web UI version (`localhost:8080`)

## 2. Core Functional Testing (Happy Path) 🟢
I conducted an end-to-end (E2E) workflow test simulating different user roles strictly following the specifications in the README. All functionalities operated smoothly and as expected:

- [x] **MO (Module Organizer) Test:** Successfully logged in, properly filled out the form, and posted a new job (`Java TA - Spring 2026`).
- [x] **TA (Teaching Assistant) Test:** Successfully logged in, updated personal Profile, viewed the newly posted job, and submitted an `Apply` request.
- [x] **MO Review Test:** The MO was able to view the pending application and execute the `Approve` operation successfully. The status was updated accordingly.
- [x] **Admin Test:** The `Global Metrics` dashboard displayed accurate statistics, and all preceding operations were properly recorded in the `Operation Logs`.

## 3. Boundary & Exception Testing (Edge Cases) 🔴
To verify the system's robustness, I conducted several edge-case tests attempting to break the system. The system demonstrated excellent error handling and interception capabilities. **No fatal bugs, crashes, or data corruption were found.**

| Test Case | Operation Description | System Response | Status |
| :--- | :--- | :--- | :--- |
| **Empty Input Injection** | MO attempts to submit the 'Post Job' form with all mandatory fields left empty. | The system successfully intercepted the action; no invalid empty records were generated. | ✅ Pass |
| **Format Disruption** | Entered an invalid date format in the `Deadline` field (e.g., `2026-05-30` instead of `yyyy/MM/dd`). | The system gracefully handled the exception without causing backend crashes or service interruption. | ✅ Pass |
| **Authorization Boundaries** | Logged in as a TA and attempted to access unauthorized resources. | The UI properly isolated permissions; MO/Admin exclusive features remained inaccessible. | ✅ Pass |

## 4. Conclusion
After comprehensive testing, the current version demonstrates rigorous front-end and back-end logic, accurate data persistence, and robust exception-handling mechanisms. **The system is stable and meets release standards.**

*(Note: All interactive data generated during this test run has been successfully persisted into the `.csv` files under the `data/` directory and pushed along with this report as proof of testing.)*