# BUPT TA Recruitment and Management System

## Issue #1 - Wangyankai

### What I implemented
- **User domain model**: added a `User` entity with `id`, `email`, `password`, and `role` fields. The role is represented as a string and is intended to distinguish user types such as `TA`, `MO`, and `Admin`.
- **CSV-based persistence**: added a `UserDAO` that persists users in a simple CSV format (one record per line, 4 columns: `id,email,password,role`).
- **Auto-initialization**: when the backing CSV file does not exist, the DAO creates it and seeds **three default accounts** (Admin / MO / TA) so the system has usable credentials from a clean state.
- **Authentication helper**: provided `authenticate(email, password)` which looks up all stored users and returns the matching `User` when credentials are valid; otherwise it returns `null`.

### Run example (PowerShell)

```powershell
@(
  "A001,admin@bupt.edu,admin123,Admin"
  "M001,mo@bupt.edu,mo123,MO"
  "T001,ta@bupt.edu,ta123,TA"
) | ForEach-Object { $_ }
```

### Output

```text
A001,admin@bupt.edu,admin123,Admin
M001,mo@bupt.edu,mo123,MO
T001,ta@bupt.edu,ta123,TA
```

## Issue #2 - Jiayi Liu

## 1. Code Location

- `src/dao/ProfileDAO.java`
- `src/model/Profile.java`
- `src/demo/ProfileDAODemo.java`

## 2. What This Part Implements

`ProfileDAO` is responsible for local CSV-based persistence of TA profile information.  
The data file used by this module is `data/profiles.csv`.

This part of the code provides the following functionality:

- Automatically checks whether `profiles.csv` exists and creates it if needed
- Reads all TA profile records from `profiles.csv`
- Searches for a specific profile by `taId`
- Updates an existing profile if the same `taId` already exists
- Adds a new profile if the `taId` does not exist
- Rewrites all profile data back to the CSV file after changes

## 3. Data Fields

Each profile record contains 5 fields in the following order:

1. `taId`: TA identifier
2. `name`: name
3. `studentId`: student number
4. `major`: major
5. `phone`: phone number

Example CSV record:

```text
T001,Alice,20231234,ComputerScience,13800138000
```

## 4. Core Methods

- `getAllProfiles()`: reads and returns all TA profiles
- `getByTaId(String taId)`: finds a profile by TA ID
- `saveOrUpdate(Profile profile)`: inserts a new profile or updates an existing one

## 5. Run Example

Run the following commands inside the `2` project folder:

```bash
javac -d out src/model/Profile.java src/dao/ProfileDAO.java src/demo/ProfileDAODemo.java
java -cp out demo.ProfileDAODemo
```

Notes:

- The first command compiles `Profile`, `ProfileDAO`, and the demo class
- The second command runs the demo program
- The demo temporarily writes a sample record and then restores the original `profiles.csv`, so your existing data is not permanently changed

## 6. Example Output

```text
=== ProfileDAO Demo ===
[1] Existing profile count: 1
[2] Saved profile: T002,Bob,20239876,SoftwareEngineering,13900139000
[3] Query by TA ID (T002): T002,Bob,20239876,SoftwareEngineering,13900139000
[4] Profile count after saveOrUpdate: 2
```

## 7. Function Demonstration

The output above shows that this code successfully:

- Reads the existing TA profile data
- Saves a new profile record
- Retrieves the newly saved profile by `taId`
- Confirms that the total number of profiles increases after `saveOrUpdate`

This demonstrates that `ProfileDAO` already supports the basic persistence, query, and update operations required for the TA profile management module.
