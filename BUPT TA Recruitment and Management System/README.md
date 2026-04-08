# BUPT TA Recruitment and Management System

## Issue #1 - Wangyankai

### What I implemented
- **User domain model**: added a `User` entity with `id`, `email`, `password`, and `role` fields. The role is represented as a string and is intended to distinguish user types such as `TA`, `MO`, and `Admin`.
- **CSV-based persistence**: added a `UserDAO` that persists users in a simple CSV format (one record per line, 4 columns: `id,email,password,role`).
- **Auto-initialization**: when the backing CSV file does not exist, the DAO creates it and seeds **three default accounts** (Admin / MO / TA) so the system has usable credentials from a clean state.
- **Authentication helper**: provided `authenticate(email, password)` which looks up all stored users and returns the matching `User` when credentials are valid; otherwise it returns `null`.

### Command-line example (PowerShell)

```powershell
# Example output for Issue #1 (CSV seed / storage format)
@(
  "A001,admin@bupt.edu,admin123,Admin",
  "M001,mo@bupt.edu,mo123,MO",
  "T001,ta@bupt.edu,ta123,TA"
)
```

### Expected output (example)

```text
A001,admin@bupt.edu,admin123,Admin
M001,mo@bupt.edu,mo123,MO
T001,ta@bupt.edu,ta123,TA
```

