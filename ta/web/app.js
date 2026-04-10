const state = {
  id: "",
  role: "",
};

const loginView = document.getElementById("loginView");
const taView = document.getElementById("taView");
const moView = document.getElementById("moView");
const adminView = document.getElementById("adminView");
const messageEl = document.getElementById("message");
const sessionInfo = document.getElementById("sessionInfo");
const taSections = {
  home: document.getElementById("taHomeSection"),
  profile: document.getElementById("taProfileSection"),
  apply: document.getElementById("taApplySection"),
  history: document.getElementById("taHistorySection"),
};
const moSections = {
  home: document.getElementById("moHomeSection"),
  jobs: document.getElementById("moJobsSection"),
  review: document.getElementById("moReviewSection"),
};
const adminSections = {
  home: document.getElementById("adminHomeSection"),
  metrics: document.getElementById("adminMetricsSection"),
  logs: document.getElementById("adminLogsSection"),
};

function switchTaTab(tabName) {
  Object.values(taSections).forEach((section) => section.classList.add("hidden"));
  if (taSections[tabName]) {
    taSections[tabName].classList.remove("hidden");
  }
  document.querySelectorAll("[data-ta-tab]").forEach((btn) => {
    btn.classList.toggle("active", btn.dataset.taTab === tabName);
  });
  if (tabName === "home") loadTaHomeSummary();
  if (tabName === "profile") loadAndPopulateProfile();
  if (tabName === "history") loadMyApps();
  if (tabName === "apply") loadOpenJobs();
}

async function loadAndPopulateProfile() {
  const notice = document.getElementById("profileNotice");
  try {
    const p = await api(`/api/ta/profile?taId=${encodeURIComponent(state.id)}`);
    if (!p.exists) {
      notice.classList.remove("hidden");
    } else {
      notice.classList.add("hidden");
      const form = document.getElementById("profileForm");
      form.elements["name"].value = p.profile.name || "";
      form.elements["studentId"].value = p.profile.studentId || "";
      form.elements["major"].value = p.profile.major || "";
      form.elements["phone"].value = p.profile.phone || "";
    }
  } catch (_) {
    notice.classList.remove("hidden");
  }
}

async function loadOpenJobs() {
  const el = document.getElementById("openJobs");
  try {
    const json = await api("/api/ta/jobs-open");
    if (!json.jobs.length) {
      el.innerHTML = "<p class=\"empty-hint\">暂无开放岗位</p>";
      return;
    }
    el.innerHTML = json.jobs.map((j) => `
      <div class="job-card">
        <div class="job-card-header">
          <span class="job-title">${escHtml(j.title)}</span>
          <span class="job-id-badge">${escHtml(j.jobId)}</span>
        </div>
        <div class="job-meta">截止日期：${escHtml(j.deadline)} &nbsp;·&nbsp; 发布人：${escHtml(j.moId)}</div>
        <div class="job-req">要求：${escHtml(j.requirements)}</div>
        <button class="btn-apply" onclick="fillJobId('${escHtml(j.jobId)}')" aria-label="申请${escHtml(j.jobId)}">申请此岗位</button>
      </div>`).join("");
  } catch (err) {
    showMessage(err.message);
  }
}

async function loadMyApps() {
  const el = document.getElementById("myApps");
  try {
    const json = await api(`/api/ta/applications?taId=${encodeURIComponent(state.id)}`);
    if (!json.applications.length) {
      el.innerHTML = "<p class=\"empty-hint\">暂无申请记录</p>";
      return;
    }
    el.innerHTML = json.applications.map((a) => `
      <div class="app-card">
        <div class="app-card-header">
          <span class="status-badge status-${escHtml(a.status.toLowerCase())}">${escHtml(a.status)}</span>
          <span class="app-id-text">${escHtml(a.appId)}</span>
        </div>
        <div class="app-meta">岗位 ID：${escHtml(a.jobId)}</div>
        <div class="app-meta">申请时间：${escHtml(a.appliedAt)}</div>
      </div>`).join("");
  } catch (err) {
    showMessage(err.message);
  }
}

function fillJobId(jobId) {
  switchTaTab("apply");
  document.getElementById("applyJobId").value = jobId;
}

function escHtml(str) {
  return String(str)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

function switchMoTab(tabName) {
  Object.values(moSections).forEach((section) => section.classList.add("hidden"));
  if (moSections[tabName]) {
    moSections[tabName].classList.remove("hidden");
  }
  document.querySelectorAll("[data-mo-tab]").forEach((btn) => {
    btn.classList.toggle("active", btn.dataset.moTab === tabName);
  });
  if (tabName === "home") loadMoHomeSummary();
}

function switchAdminTab(tabName) {
  Object.values(adminSections).forEach((section) => section.classList.add("hidden"));
  if (adminSections[tabName]) {
    adminSections[tabName].classList.remove("hidden");
  }
  document.querySelectorAll("[data-admin-tab]").forEach((btn) => {
    btn.classList.toggle("active", btn.dataset.adminTab === tabName);
  });
  if (tabName === "home") loadAdminHomeSummary();
}

async function loadTaHomeSummary() {
  const profileEl = document.getElementById("taProfileStatus");
  const appsEl = document.getElementById("taAppsCount");
  try {
    const p = await api(`/api/ta/profile?taId=${encodeURIComponent(state.id)}`);
    profileEl.textContent = p.exists ? "已完善" : "未填写";
    profileEl.style.color = p.exists ? "var(--accent)" : "#a23d17";
  } catch (_) { profileEl.textContent = "—"; }
  try {
    const a = await api(`/api/ta/applications?taId=${encodeURIComponent(state.id)}`);
    appsEl.textContent = a.applications.length;
  } catch (_) { appsEl.textContent = "—"; }
}

async function loadMoHomeSummary() {
  const jobsEl = document.getElementById("moJobsCount");
  const pendingEl = document.getElementById("moPendingCount");
  try {
    const j = await api(`/api/mo/jobs?moId=${encodeURIComponent(state.id)}`);
    jobsEl.textContent = j.jobs.length;
    let pending = 0;
    for (const job of j.jobs) {
      try {
        const r = await api(`/api/mo/applicants?moId=${encodeURIComponent(state.id)}&jobId=${encodeURIComponent(job.jobId)}`);
        pending += r.applicants.filter((a) => a.status === "Pending").length;
      } catch (_) {}
    }
    pendingEl.textContent = pending;
  } catch (_) { jobsEl.textContent = "—"; pendingEl.textContent = "—"; }
}

async function loadAdminHomeSummary() {
  try {
    const m = await api("/api/admin/metrics");
    document.getElementById("adminTotalJobs").textContent = m.totalJobs;
    document.getElementById("adminOpenJobs").textContent = m.openJobs;
    document.getElementById("adminTotalApps").textContent = m.totalApplications;
    document.getElementById("adminCompleteRate").textContent = m.completionRate + "%";
  } catch (_) {}
}

function showMessage(text, ok = false) {
  messageEl.style.color = ok ? "#0b6e4f" : "#a23d17";
  messageEl.textContent = text;
}

function toFormBody(obj) {
  return new URLSearchParams(obj).toString();
}

async function api(path, method = "GET", data = null) {
  const opt = { method, headers: {} };
  if (data) {
    opt.headers["Content-Type"] = "application/x-www-form-urlencoded;charset=UTF-8";
    opt.body = toFormBody(data);
  }
  const res = await fetch(path, opt);
  const json = await res.json();
  if (!res.ok || !json.ok) {
    throw new Error(json.error || "Request failed");
  }
  return json;
}

function switchRoleView() {
  loginView.classList.add("hidden");
  taView.classList.add("hidden");
  moView.classList.add("hidden");
  adminView.classList.add("hidden");

  if (!state.role) {
    loginView.classList.remove("hidden");
    sessionInfo.textContent = "未登录";
    return;
  }

  sessionInfo.textContent = `${state.id} (${state.role})`;
  if (state.role === "TA") {
    taView.classList.remove("hidden");
    switchTaTab("home");
  }
  if (state.role === "MO") {
    moView.classList.remove("hidden");
    switchMoTab("home");
  }
  if (state.role === "Admin") {
    adminView.classList.remove("hidden");
    switchAdminTab("home");
  }
}

function logout() {
  state.id = "";
  state.role = "";
  switchRoleView();
  showMessage("已退出登录", true);
}

document.querySelectorAll("[data-action='logout']").forEach((btn) => {
  btn.addEventListener("click", logout);
});

document.querySelectorAll("[data-mo-tab]").forEach((btn) => {
  btn.addEventListener("click", () => switchMoTab(btn.dataset.moTab));
});

document.querySelectorAll("[data-ta-tab]").forEach((btn) => {
  btn.addEventListener("click", () => switchTaTab(btn.dataset.taTab));
});

document.querySelectorAll("[data-admin-tab]").forEach((btn) => {
  btn.addEventListener("click", () => switchAdminTab(btn.dataset.adminTab));
});

document.getElementById("loginForm").addEventListener("submit", async (e) => {
  e.preventDefault();
  const fd = new FormData(e.target);
  try {
    const json = await api("/api/login", "POST", {
      email: fd.get("email"),
      password: fd.get("password"),
    });
    state.id = json.id;
    state.role = json.role;
    switchRoleView();
    showMessage("登录成功", true);
    e.target.reset();
  } catch (err) {
    showMessage(err.message);
  }
});

document.getElementById("resetBtn").addEventListener("click", async () => {
  if (!confirm("确认清空 profiles/jobs/applications/logs 吗？")) return;
  try {
    await api("/api/reset", "POST", {});
    showMessage("演示数据已重置", true);
  } catch (err) {
    showMessage(err.message);
  }
});

document.getElementById("profileForm").addEventListener("submit", async (e) => {
  e.preventDefault();
  const fd = new FormData(e.target);
  try {
    await api("/api/ta/profile", "POST", {
      taId: state.id,
      name: fd.get("name"),
      studentId: fd.get("studentId"),
      major: fd.get("major"),
      phone: fd.get("phone"),
    });
    showMessage("档案保存成功", true);
  } catch (err) {
    showMessage(err.message);
  }
});

document.getElementById("loadOpenJobsBtn").addEventListener("click", async () => {
  await loadOpenJobs();
  showMessage("岗位列表已刷新", true);
});

document.getElementById("applyForm").addEventListener("submit", async (e) => {
  e.preventDefault();
  const fd = new FormData(e.target);
  try {
    const json = await api("/api/ta/apply", "POST", {
      taId: state.id,
      jobId: fd.get("jobId"),
    });
    showMessage(`申请成功: ${json.appId}`, true);
    e.target.reset();
  } catch (err) {
    showMessage(err.message);
  }
});

document.getElementById("loadMyAppsBtn").addEventListener("click", async () => {
  await loadMyApps();
  showMessage("申请记录已刷新", true);
});

document.getElementById("jobForm").addEventListener("submit", async (e) => {
  e.preventDefault();
  const fd = new FormData(e.target);
  try {
    const json = await api("/api/mo/job", "POST", {
      moId: state.id,
      title: fd.get("title"),
      requirements: fd.get("requirements"),
      deadline: fd.get("deadline"),
    });
    showMessage(`岗位发布成功: ${json.jobId}`, true);
  } catch (err) {
    showMessage(err.message);
  }
});

document.getElementById("loadMyJobsBtn").addEventListener("click", async () => {
  const el = document.getElementById("myJobs");
  try {
    const json = await api(`/api/mo/jobs?moId=${encodeURIComponent(state.id)}`);
    if (!json.jobs.length) {
      el.innerHTML = "<p class=\"empty-hint\">当前没有发布岗位</p>";
      return;
    }
    el.innerHTML = json.jobs.map((j) => `
      <div class="job-card">
        <div class="job-card-header">
          <span class="job-title">${escHtml(j.title)}</span>
          <span class="job-id-badge">${escHtml(j.jobId)}</span>
        </div>
        <div class="job-meta">截止日期：${escHtml(j.deadline)}</div>
        <div class="job-req">要求：${escHtml(j.requirements)}</div>
      </div>`).join("");
    showMessage("岗位列表已刷新", true);
  } catch (err) {
    showMessage(err.message);
  }
});

document.getElementById("loadApplicantsForm").addEventListener("submit", async (e) => {
  e.preventDefault();
  const fd = new FormData(e.target);
  const el = document.getElementById("applicants");
  try {
    const jobId = fd.get("jobId");
    const json = await api(`/api/mo/applicants?moId=${encodeURIComponent(state.id)}&jobId=${encodeURIComponent(jobId)}`);
    if (!json.applicants.length) {
      el.innerHTML = "<p class=\"empty-hint\">该岗位暂无申请</p>";
      return;
    }
    el.innerHTML = json.applicants.map((a) => `
      <div class="applicant-card">
        <div class="applicant-card-header">
          <span class="applicant-name">${escHtml(a.name || "（未填写档案）")}</span>
          <span class="ta-id-badge">${escHtml(a.taId)}</span>
          <span class="status-badge status-${escHtml(a.status.toLowerCase())}">${escHtml(a.status)}</span>
        </div>
        <div class="applicant-meta">学号：${escHtml(a.studentId || "—")} &nbsp;·&nbsp; 专业：${escHtml(a.major || "—")} &nbsp;·&nbsp; 电话：${escHtml(a.phone || "—")}</div>
        <div class="applicant-meta app-id-text">App ID: ${escHtml(a.appId)}</div>
      </div>`).join("");
    showMessage("申请人列表已刷新", true);
  } catch (err) {
    showMessage(err.message);
  }
});

document.getElementById("statusForm").addEventListener("submit", async (e) => {
  e.preventDefault();
  const fd = new FormData(e.target);
  try {
    await api("/api/mo/status", "POST", {
      moId: state.id,
      appId: fd.get("appId"),
      status: fd.get("status"),
    });
    showMessage("状态更新成功", true);
  } catch (err) {
    showMessage(err.message);
  }
});

document.getElementById("loadMetricsBtn").addEventListener("click", async () => {
  const el = document.getElementById("metrics");
  try {
    const m = await api("/api/admin/metrics");
    const dist = Object.keys(m.statusDistribution || {}).length
      ? Object.entries(m.statusDistribution).map(([k, v]) => `${k}: ${v}`).join("\n")
      : "(empty)";
    el.textContent = `Total Jobs: ${m.totalJobs}\nOpen Jobs: ${m.openJobs}\nTotal Applications: ${m.totalApplications}\nCompletion: ${m.completionRate}%\n\nStatus Distribution\n${dist}`;
    showMessage("指标已刷新", true);
  } catch (err) {
    showMessage(err.message);
  }
});

document.getElementById("loadLogsBtn").addEventListener("click", async () => {
  const el = document.getElementById("logs");
  try {
    const json = await api("/api/admin/logs");
    if (!json.logs.length) {
      el.textContent = "暂无日志";
      return;
    }
    el.textContent = json.logs
      .map((l) => `${l.timestamp} | ${l.userId} | ${l.role} | ${l.action} | ${l.detail}`)
      .join("\n");
    showMessage("日志已刷新", true);
  } catch (err) {
    showMessage(err.message);
  }
});

switchRoleView();
