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

### What I implemented

- CSV-based persistence layer: implemented `ProfileDAO` to manage TA profile data stored in `data/profiles.csv`.
- Automatic file handling: added logic to check and create `profiles.csv` if it does not exist.
- Data retrieval: implemented `getAllProfiles()` to read all records and `getByTaId(String taId)` to query a specific profile.
- Insert or update logic: implemented `saveOrUpdate(Profile profile)` to either update an existing record (by taId) or insert a new one.
- Data consistency: ensured all profile data is rewritten to the CSV file after any modification.
- Data model definition: created `Profile` class with fields `taId`, `name`, `studentId`, `major`, and `phone`.
- Demo verification: provided `ProfileDAODemo` to demonstrate read, write, query, and update functionality without permanently altering existing data.

powershell
javac -d out src/model/Profile.java src/dao/ProfileDAO.java src/demo/ProfileDAODemo.java && java -cp out demo.ProfileDAODemo

## Issue #3 - Tan Xiaopeng

### What I implemented

- **IntelliJ IDEA module configuration**: provided a `java2.iml` file that defines a Java module structure for the project.
- **Source folder setup**: configured `src` as the source root directory (`isTestSource="false"`) so the IDE recognizes it for compilation.
- **JDK configuration**: set to use the project-level inherited JDK (`inheritedJdk`), avoiding hardcoded local paths.
- **Output control**: excluded default compiler output paths (`exclude-output`), delegating build output management to the IDE.
powershell
# Navigate to the module root directory (where java2.iml is located)
cd path\to\java2

# Create a simple Java source file inside the src folder
echo 'public class Main { public static void main(String[] args) { System.out.println("Hello from Tan Xiaopeng, Issue #3!"); } }' > src\Main.java

# Compile the Java file (javac expects source files under src)
javac -d . src\Main.java

# Run the compiled class
java Main
## Issue #4 - [Jing Luo]

### What I implemented

- **Audit log model**: added an `AuditLog` class with fields `timestamp`, `userId`, `role`, `action`, and `detail` to represent operation records in the system.
- **CSV log output**: implemented `toCsvLine()` so each audit log can be written in CSV format for local persistence.
- **Main program workflow**: completed the main interactive program in `Main.java`, including login, dashboard routing, and menu handling.
- **Role-based access control**: supported different menus and permissions for `TA`, `MO`, and `Admin`.
- **TA functions integration**: connected profile creation, profile viewing, job browsing, job application, and application history display.
- **MO functions integration**: connected job posting, job viewing, applicant viewing, and applicant status updating.
- **Admin functions integration**: connected recruitment metrics display and read-only audit log viewing.
- **Operation logging**: added log writing for key actions such as login, logout, profile save, job posting, application submission, and status update.
- **Input validation**: added validation for student ID, phone number, status values, and deadline date format.
- **Sequence initialization**: implemented automatic initialization for job IDs and application IDs based on existing stored records.

powershell
# Navigate to the project root directory
cd path\to\java2

# Compile all Java source files
javac -d out src\model\*.java src\dao\*.java src\*.java

# Run the main program
java -cp out Main

## Issue #5 - [JianCheng  Dong]


javac -d bin src/**/*.java
java -cp bin Main

Backend Implementation and Interface Extension Guide 
This project utilizes the `com.sun.net.httpserver` from the Java standard library to implement a lightweight built-in server, which can run without the need to install external containers such as Tomcat. The following are its core logic and extension methods: 
#### 1. Core Architecture Logic
* **Service Startup**: When the system starts, an HTTP service instance is created and bound to the specified port (default 8080).
* **Routing Distribution**: Utilizes a "path mapping" mechanism to distribute different URLs (such as `/api/login`) to specific handler classes for processing.
* **Concurrent Processing**: Built-in thread pool management is available, enabling the simultaneous handling of multiple requests from clients. 
#### 2. How to Add Backend Interfaces
Without complex configuration, the development of new functions can be completed in just three steps:
* **Step 1: Write Business Logic**
Create a logic processing class to handle requests. In this class, determine the request type (such as GET or POST), perform the corresponding business operations, and call the system's built-in methods to return the processing results.
* **Step 2: Register Access Paths**
In the server initialization configuration, bind the newly written logic processing class to a custom URL path.
* **Step 3: Connect to Data Persistence Layer**
If the function involves data storage, add the reading and writing logic for CSV files in the corresponding data access object (DAO) to ensure that the data can be persistently saved. 


#### 3. Static Resource Integration
The system has an automatic hosting feature. When the received request does not belong to an API interface, the server will automatically search for the corresponding HTML, CSS, or JavaScript files from the `web/` directory. This means that the front-end page and the back-end service can share the same port, enabling seamless interaction.