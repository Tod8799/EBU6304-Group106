# TA Recruitment System (Console + Web)

本项目是一个基于 Java 的 TA 招聘管理系统，包含：
- 控制台版本入口：`Main`
- Web 版本入口：`WebServer`
- 数据持久化：`data/*.csv`
- 前端页面：`web/index.html`

## 1. 环境要求

- JDK 17 或更高版本
- Windows PowerShell（本文命令按 PowerShell 编写）

验证 Java 版本：

```powershell
java -version
javac -version
```

## 2. 项目结构

```text
src/
  Main.java
  WebServer.java
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
```

## 3. 一键编译命令

在项目根目录执行：

```powershell
javac -encoding UTF-8 -d out src\model\*.java src\dao\*.java src\Main.java src\WebServer.java
```

## 4. 运行命令

### 4.1 运行 Web 版（推荐演示）

```powershell
java -cp out WebServer
```

启动成功后访问：

```text
http://localhost:8080
```

### 4.2 运行控制台版

```powershell
java -cp out Main
```

## 5. 默认账号

- Admin：`admin@bupt.edu / admin123`
- MO：`mo@bupt.edu / mo123`
- TA：`ta@bupt.edu / ta123`

## 6. 完整功能演示样例（Web）

下面是一套可以覆盖主要功能的演示脚本，按顺序执行即可。

### Step 0：重置演示数据（可选，但推荐）

执行一行命令清空业务数据（保留用户账号）：

```powershell
'' | Set-Content .\data\profiles.csv; '' | Set-Content .\data\jobs.csv; '' | Set-Content .\data\applications.csv; '' | Set-Content .\data\logs.csv
```

### Step 1：MO 发布岗位

1. 使用 `mo@bupt.edu / mo123` 登录。
2. 进入 `Job Management`。
3. 在 `Post Job` 填写：
   - Job Title：`Java TA - Spring 2026`
   - English Level：`CET-6`
   - Work Duration：`Within one semester`
   - Weekend Availability：`Yes`
   - Custom Requirements：`Need Java OOP and basic SQL; can hold weekly office hours`
   - Deadline：填未来日期，例如 `2026/05/30`
4. 点击 `Post Job`。
5. 在右侧 `My Job List` 点击 `Refresh My Jobs`，应看到新发布岗位。

预期结果：
- 岗位创建成功并生成 `JOBxxx`。
- `data/jobs.csv` 新增一条记录。
- `requirements` 字段同时包含三段下拉要求 + 自由输入要求。

### Step 2：TA 完善资料并投递

1. 退出登录，使用 `ta@bupt.edu / ta123` 登录。
2. 进入 `Profile`，填写：
   - Name：`Alice`
   - Student ID：`20231234`
   - Major：`Computer Science`
   - Phone：`13800138000`
3. 点击 `Save Profile`。
4. 进入 `Job Applications`，点击 `Refresh Open Jobs`。
5. 选择 MO 刚发布岗位，点击 `View Requirements`。
6. 在要求详情里点击 `Apply`。
7. 进入 `My Applications`，点击 `Refresh Applications`。

预期结果：
- 成功投递，状态为 `Pending`。
- `data/profiles.csv`、`data/applications.csv` 有新增记录。

### Step 3：MO 审核申请

1. 退出登录，再次使用 `mo@bupt.edu / mo123` 登录。
2. 进入 `Application Review`。
3. 点击 `Refresh Jobs & Applicants`。
4. 在左侧选择对应 Job，点击申请人卡片。
5. 点击 `Approve`（或 `Reject` 并输入拒绝原因）。

预期结果：
- 申请状态变更为 `Shortlisted` 或 `Rejected`。
- 若拒绝，拒绝理由会写入申请记录。

### Step 4：TA 查看审核结果

1. 退出登录，使用 `ta@bupt.edu / ta123` 登录。
2. 进入 `My Applications`，点击 `Refresh Applications`。

预期结果：
- 能看到最新状态（`Shortlisted` 或 `Rejected`）及拒绝原因（如有）。

### Step 5：Admin 查看全局指标和日志

1. 退出登录，使用 `admin@bupt.edu / admin123` 登录。
2. 进入 `Global Metrics`，点击 `Refresh Metrics`。
3. 进入 `Operation Logs`，点击 `Refresh Logs`。

预期结果：
- 指标统计包含岗位数、开放岗位数、申请数、完成率。
- 日志包含登录、发岗位、保存资料、投递、审核等行为。

## 7. 常见问题

### Q1：编译报错“找不到符号”

先确认在项目根目录执行，且使用了完整编译命令（包含 `src/model` 与 `src/dao`）。

### Q2：访问不了网页

- 确认已执行 `java -cp out WebServer`
- 确认端口 `8080` 未被占用
- 访问地址必须是 `http://localhost:8080`

### Q3：Deadline 格式不通过

前端要求输入：`yyyy/MM/dd`（例如 `2026/05/30`），提交后后端会转为 `yyyy-MM-dd`。

## 8. 快速复现命令清单

```powershell
# 1) 编译
javac -encoding UTF-8 -d out src\model\*.java src\dao\*.java src\Main.java src\WebServer.java

# 2) 运行 Web
java -cp out WebServer

# 3) (新终端) 清空业务数据
'' | Set-Content .\data\profiles.csv; '' | Set-Content .\data\jobs.csv; '' | Set-Content .\data\applications.csv; '' | Set-Content .\data\logs.csv
```

至此即可完成项目完整功能演示。