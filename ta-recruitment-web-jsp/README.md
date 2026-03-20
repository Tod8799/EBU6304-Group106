# TA Recruitment（Servlet/JSP 真落地原型说明）

这是一个轻量的 Java Servlet/JSP 风格 Web 原型：页面由 JSP 承载，并在服务端使用 Java 逻辑完成读写与业务流程演示。

## 实现了哪些功能

- TA：创建申请人资料（保存到文本文件）
- TA：上传简历 CV（仅保存：上传文件名 + 备注，不保存全文内容到数据库）
- TA：查找可用岗位（搜索）
- TA：申请岗位（技能匹配 + 缺失技能解释；申请记录写入文本文件）
- TA：查看申请状态，并推进模拟流程
- MO：发布岗位（把岗位写入文本存储）
- MO：选择申请人（保证“每个岗位只有一个 Selected”）
- Admin：工作量汇总（包含“工作量风险”的可解释规则）

## 数据存储方式（满足强制要求：不使用数据库）

所有数据均以纯文本文件形式持久化到：

`%USER_HOME%/ta-recruitment-data`

整个项目不使用数据库。

## 在 Tomcat 部署与运行

1. 安装并启动 Tomcat（Servlet 容器）
2. 在 `webapps` 下建立一个新的应用目录，例如：`webapps/ta-recruitment`
3. 将 `ta-recruitment-web-jsp/webapp` 目录下的全部内容复制到上述目录中
4. 重启 Tomcat 让应用生效

然后打开：

`http://localhost:8080/<your-context>/index.jsp`

其中 `<your-context>` 就是你部署时 webapps 目录名（如 `ta-recruitment`）。

## 关于 CV 上传的说明

表单上传只会保存“上传文件名 + 备注”，不会保存完整文件内容到服务端数据库；因此适合用于原型/作业演示与后续对接文本数据存储要求。

