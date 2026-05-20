const state = {
  id: "",
  role: "",
};

// Step 6-3: 统一前端状态管理（当前用户、角色、缓存、选中项）。

const loginView = document.getElementById("loginView");
const taView = document.getElementById("taView");
const moView = document.getElementById("moView");
const adminView = document.getElementById("adminView");
const toastEl = document.getElementById("toast");
let _toastTimer = null;
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
const moApplicantIndex = new Map();
const moApplicantsByJob = new Map();
let moJobsCache = [];
let selectedApplicant = null;
let taOpenJobsCache = [];
let selectedTaJob = null;
let taResumeText = "";
let taResumeFileName = "";
let taResumeFileBase64 = "";

function clearProfileForm() {
  const form = document.getElementById("profileForm");
  if (!form) return;
  form.reset();
}

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
      clearProfileForm();
      taResumeText = "";
      taResumeFileName = "";
      taResumeFileBase64 = "";
      refreshResumeUploadBtn();
    } else {
      notice.classList.add("hidden");
      const form = document.getElementById("profileForm");
      form.elements["name"].value = p.profile.name || "";
      form.elements["studentId"].value = p.profile.studentId || "";
      form.elements["major"].value = p.profile.major || "";
      form.elements["phone"].value = p.profile.phone || "";
      taResumeText = p.profile.resumeText || "";
      taResumeFileName = p.profile.resumeUploaded ? "resume_saved" : "";
      taResumeFileBase64 = "";
      refreshResumeUploadBtn();
    }
  } catch (_) {
    notice.classList.remove("hidden");
    clearProfileForm();
    taResumeText = "";
    taResumeFileName = "";
    taResumeFileBase64 = "";
    refreshResumeUploadBtn();
  }
}

async function loadOpenJobs() {
  const el = document.getElementById("openJobs");
  const reqView = document.getElementById("taJobRequirementView");
  try {
    const json = await api("/api/ta/jobs-open");
    taOpenJobsCache = json.jobs || [];
    selectedTaJob = null;
    reqView.classList.add("hidden");
    el.classList.remove("hidden");

    if (!taOpenJobsCache.length) {
      el.innerHTML = "<p class=\"empty-hint\">No open jobs</p>";
      return;
    }

    el.innerHTML = taOpenJobsCache.map((j) => `
      <div class="job-card">
        <div class="job-card-header">
          <span class="job-title">${escHtml(j.title)}</span>
          <span class="job-id-badge">${escHtml(j.jobId)}</span>
        </div>
        <div class="job-meta">Deadline: ${escHtml(j.deadline)} &nbsp;·&nbsp; Posted by: ${escHtml(j.moId)}</div>
        <button class="btn-apply" data-view-job="${escHtml(j.jobId)}" aria-label="View requirements for ${escHtml(j.jobId)}">View Requirements</button>
      </div>`).join("");
  } catch (err) {
    showMessage(err.message);
  }
}

function showTaRequirementView(job) {
  const listEl = document.getElementById("openJobs");
  const reqView = document.getElementById("taJobRequirementView");
  const titleEl = document.getElementById("taReqJobTitle");
  const metaEl = document.getElementById("taReqJobMeta");
  const contentEl = document.getElementById("taReqJobContent");

  selectedTaJob = job;
  titleEl.textContent = `Job Requirements - ${job.title}`;
  metaEl.textContent = `${job.jobId} | Deadline: ${job.deadline} | Posted by: ${job.moId}`;
  contentEl.innerHTML = `<p>${escHtml(job.requirements || "None")}</p>`;

  listEl.classList.add("hidden");
  reqView.classList.remove("hidden");
}

function returnToTaJobList() {
  selectedTaJob = null;
  document.getElementById("taJobRequirementView").classList.add("hidden");
  document.getElementById("openJobs").classList.remove("hidden");
}

async function applyForJob(jobId) {
  try {
    await api("/api/ta/apply", "POST", { taId: state.id, jobId });
    showMessage("Application submitted", true);
    await loadOpenJobs();
  } catch (err) {
    showMessage(err.message);
  }
}

async function loadMyApps() {
  const el = document.getElementById("myApps");
  try {
    const json = await api(`/api/ta/applications?taId=${encodeURIComponent(state.id)}`);
    if (!json.applications.length) {
      el.innerHTML = "<p class=\"empty-hint\">No application records</p>";
      return;
    }
    el.innerHTML = json.applications.map((a) => `
      <div class="app-card">
        <div class="app-card-header">
          <span class="status-badge status-${escHtml(a.status.toLowerCase())}">${escHtml(a.status)}</span>
        </div>
        <div class="app-meta">Job: ${escHtml(a.jobTitle || a.jobId)}</div>
        ${a.status === "Rejected" ? `<div class="app-meta">Rejection reason: ${escHtml(a.rejectReason || "No rejection reason provided")}</div>` : ""}
        <div class="app-meta">Applied at: ${escHtml(a.appliedAt)}</div>
      </div>`).join("");
  } catch (err) {
    showMessage(err.message);
  }
}

function escHtml(str) {
  return String(str)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

function refreshResumeUploadBtn() {
  const btn = document.getElementById("uploadResumeBtn");
  const meta = document.getElementById("resumeMeta");
  if (!btn) return;
  if (taResumeText) {
    btn.textContent = `Replace Resume (.txt/.pdf/.docx, ${taResumeText.length} chars)`;
    if (meta) {
      const fileLabel = taResumeFileName || "saved_resume";
      meta.textContent = `Resume: ${fileLabel} | Parsed length: ${taResumeText.length} chars`;
    }
  } else {
    btn.textContent = "Upload Resume (.txt/.pdf/.docx)";
    if (meta) {
      meta.textContent = "Resume not uploaded yet";
    }
  }
}

function getFileExtension(fileName) {
  const idx = fileName.lastIndexOf(".");
  if (idx < 0 || idx === fileName.length - 1) return "";
  return fileName.substring(idx + 1).toLowerCase();
}

function arrayBufferToBase64(buffer) {
  const bytes = new Uint8Array(buffer);
  const chunkSize = 0x8000;
  let binary = "";
  for (let i = 0; i < bytes.length; i += chunkSize) {
    const slice = bytes.subarray(i, i + chunkSize);
    binary += String.fromCharCode(...slice);
  }
  return btoa(binary);
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
  if (tabName === "review") loadMoReviewPanel();
}

function renderMoApplicantList() {
  const listEl = document.getElementById("applicants");
  moApplicantIndex.clear();
  listEl.innerHTML = moJobsCache
    .map((job) => {
      const applicants = moApplicantsByJob.get(job.jobId) || [];
      const applicantsHtml = applicants.length
        ? applicants.map((a) => {
            moApplicantIndex.set(a.appId, { ...a, jobId: job.jobId, jobTitle: job.title });
            return `
            <button class="applicant-item" type="button" data-app-id="${escHtml(a.appId)}">
              <span class="applicant-item-name">${escHtml(a.name || "(Profile not completed)")}</span>
              <span class="applicant-item-meta">${escHtml(a.taId)}</span>
              <span class="applicant-item-meta">Active tasks: ${escHtml(a.activeTaskCount || 0)}</span>
              <span class="status-badge status-${escHtml(a.status.toLowerCase())}">${escHtml(a.status)}</span>
            </button>
          `;
          }).join("")
        : "<p class=\"empty-hint\">No applications for this job</p>";

      return `
        <details class="job-fold" id="job-fold-${escHtml(job.jobId)}">
          <summary>
            <span class="job-fold-title">${escHtml(job.jobId)} - ${escHtml(job.title)}</span>
            <span class="job-fold-count">${applicants.length} applicants</span>
          </summary>
          <div class="job-fold-content">${applicantsHtml}</div>
        </details>
      `;
    })
    .join("");
}

async function loadMoReviewPanel() {
  const selectEl = document.getElementById("reviewJobSelect");
  const detailPanel = document.getElementById("reviewDetailPanel");
  const metaEl = document.getElementById("selectedApplicantMeta");
  moApplicantIndex.clear();
  moApplicantsByJob.clear();
  moJobsCache = [];
  selectedApplicant = null;
  detailPanel.classList.add("hidden");
  metaEl.textContent = "Select an applicant on the left first";
  try {
    const jobsResp = await api(`/api/mo/jobs?moId=${encodeURIComponent(state.id)}`);
    const jobs = jobsResp.jobs || [];
    moJobsCache = jobs;

    if (!jobs.length) {
      selectEl.innerHTML = "<option value=''>No jobs posted</option>";
      document.getElementById("applicants").innerHTML = "<p class=\"empty-hint\">No jobs available, cannot view applicants</p>";
      return;
    }

    selectEl.innerHTML = jobs
      .map((job) => `<option value="${escHtml(job.jobId)}">${escHtml(job.jobId)} - ${escHtml(job.title)}</option>`)
      .join("");

    await Promise.all(
      jobs.map(async (job) => {
        try {
          const resp = await api(`/api/mo/applicants?moId=${encodeURIComponent(state.id)}&jobId=${encodeURIComponent(job.jobId)}`);
          moApplicantsByJob.set(job.jobId, resp.applicants || []);
        } catch (_) {
          moApplicantsByJob.set(job.jobId, []);
        }
      })
    );

    renderMoApplicantList();
    showMessage("Application review list refreshed", true);
  } catch (err) {
    showMessage(err.message);
  }
}

async function refreshCurrentJobApplicants() {
  const selectEl = document.getElementById("reviewJobSelect");
  const currentJobId = selectEl.value;
  if (!currentJobId) return;
  const prevAppId = selectedApplicant ? selectedApplicant.appId : null;
  try {
    const resp = await api(`/api/mo/applicants?moId=${encodeURIComponent(state.id)}&jobId=${encodeURIComponent(currentJobId)}`);
    moApplicantsByJob.set(currentJobId, resp.applicants || []);
    renderMoApplicantList();
    const fold = document.getElementById(`job-fold-${currentJobId}`);
    if (fold) fold.open = true;
    if (prevAppId && moApplicantIndex.has(prevAppId)) {
      selectedApplicant = moApplicantIndex.get(prevAppId);
      renderSelectedApplicant();
      document.querySelectorAll(".applicant-item").forEach((node) => {
        node.classList.toggle("active", node.dataset.appId === prevAppId);
      });
    } else {
      selectedApplicant = null;
      renderSelectedApplicant();
    }
  } catch (err) {
    showMessage(err.message);
  }
}

function renderSelectedApplicant() {
  const detailPanel = document.getElementById("reviewDetailPanel");
  const metaEl = document.getElementById("selectedApplicantMeta");
  const resumeEl = document.getElementById("selectedApplicantResume");
  if (!selectedApplicant) {
    detailPanel.classList.add("hidden");
    metaEl.textContent = "Select an applicant on the left first";
    resumeEl.textContent = "No resume uploaded yet.";
    return;
  }

  detailPanel.classList.remove("hidden");
  metaEl.innerHTML = `
    <div><strong>Job:</strong> ${escHtml(selectedApplicant.jobId)} - ${escHtml(selectedApplicant.jobTitle || "")}</div>
    <div><strong>Applicant:</strong> ${escHtml(selectedApplicant.name || "(Profile not completed)")} (${escHtml(selectedApplicant.taId)})</div>
    <div><strong>Student ID:</strong> ${escHtml(selectedApplicant.studentId || "-")} &nbsp; <strong>Major:</strong> ${escHtml(selectedApplicant.major || "-")}</div>
    <div><strong>Phone:</strong> ${escHtml(selectedApplicant.phone || "-")} &nbsp; <strong>Status:</strong> ${escHtml(selectedApplicant.status)}</div>
    <div><strong>Current active tasks:</strong> ${escHtml(selectedApplicant.activeTaskCount || 0)}</div>
  `;
  resumeEl.textContent = selectedApplicant.resumeText || "No resume uploaded yet.";
}

async function updateSelectedApplicantStatus(newStatus, rejectReason = "") {
  if (!selectedApplicant) {
    showMessage("Please select an applicant first");
    return;
  }
  try {
    await api("/api/mo/status", "POST", {
      moId: state.id,
      appId: selectedApplicant.appId,
      status: newStatus,
      rejectReason,
    });
    showMessage(`Status updated to ${newStatus}`, true);
    await refreshCurrentJobApplicants();
  } catch (err) {
    showMessage(err.message);
  }
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
    profileEl.textContent = p.exists ? "Completed" : "Not set";
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
  toastEl.className = "toast " + (ok ? "toast-ok" : "toast-err");
  toastEl.textContent = text;
  if (_toastTimer) clearTimeout(_toastTimer);
  _toastTimer = setTimeout(() => {
    toastEl.classList.add("toast-hide");
    _toastTimer = setTimeout(() => { toastEl.className = "toast hidden"; }, 400);
  }, 3000);
}

// Step 6-4: 统一接口调用与数据提交协议（表单编码 + 统一错误处理）。
function toFormBody(obj) {
  return new URLSearchParams(obj).toString();
}

async function api(path, method = "GET", data = null) {
  const opt = { method, headers: {} };
  if (data) {
    opt.headers["Content-Type"] = "application/x-www-form-urlencoded;charset=UTF-8";
    opt.body = toFormBody(data);
  }
  let res;
  try {
    res = await fetch(path, opt);
  } catch (_) {
    throw new Error("Cannot connect to backend server. Please make sure WebServer is running and check terminal logs.");
  }
  let json;
  try {
    json = await res.json();
  } catch (_) {
    throw new Error(`Server returned non-JSON response (HTTP ${res.status}). Check backend terminal for exception details.`);
  }
  if (!res.ok || !json.ok) {
    throw new Error(json.error || "Request failed");
  }
  return json;
}

function switchRoleView() {
  // Step 6-1: 角色化页面入口与视图切换。
  loginView.classList.add("hidden");
  taView.classList.add("hidden");
  moView.classList.add("hidden");
  adminView.classList.add("hidden");

  if (!state.role) {
    loginView.classList.remove("hidden");
    sessionInfo.textContent = "Not logged in";
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
  clearProfileForm();
  taResumeText = "";
  taResumeFileName = "";
  taResumeFileBase64 = "";
  refreshResumeUploadBtn();
  switchRoleView();
  showMessage("Logged out", true);
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
    showMessage("Login successful", true);
    e.target.reset();
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
      resumeText: taResumeText,
      resumeFileName: taResumeFileName,
      resumeFileBase64: taResumeFileBase64,
    });
    taResumeFileBase64 = "";
    await loadAndPopulateProfile();
    showMessage("Profile saved", true);
  } catch (err) {
    showMessage(err.message);
  }
});

document.getElementById("uploadResumeBtn").addEventListener("click", () => {
  const picker = document.createElement("input");
  picker.type = "file";
  picker.accept = ".txt,.pdf,.docx,text/plain,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document";
  picker.addEventListener("change", async () => {
    const file = picker.files && picker.files[0];
    if (!file) return;
    const ext = getFileExtension(file.name);
    if (!["txt", "pdf", "docx"].includes(ext)) {
      showMessage("Unsupported file type. Please upload .txt, .pdf, or .docx");
      return;
    }
    if (file.size > 512 * 1024) {
      showMessage("Resume file is too large (max 512KB)");
      return;
    }
    try {
      const arrayBuffer = await file.arrayBuffer();
      taResumeFileBase64 = arrayBufferToBase64(arrayBuffer);
      taResumeFileName = file.name;
      if (ext === "txt") {
        taResumeText = new TextDecoder("utf-8").decode(arrayBuffer).trim();
      } else {
        taResumeText = `[${file.name}] uploaded, text will be extracted on save`;
      }
      refreshResumeUploadBtn();
      if (!taResumeText) {
        showMessage("Uploaded file is empty");
        return;
      }
      if (ext === "pdf" || ext === "docx") {
        showMessage(`Resume loaded: ${taResumeFileName}. Click Save Profile to parse and store`, true);
      } else {
        showMessage(`Resume loaded: ${taResumeFileName}`, true);
      }
    } catch (_) {
      showMessage("Failed to read resume file");
    }
  });
  picker.click();
});

document.getElementById("loadOpenJobsBtn").addEventListener("click", async () => {
  await loadOpenJobs();
  showMessage("Open jobs refreshed", true);
});

document.getElementById("taReqApplyBtn").addEventListener("click", async () => {
  if (!selectedTaJob) {
    showMessage("Please select a job first");
    return;
  }
  await applyForJob(selectedTaJob.jobId);
  returnToTaJobList();
});

document.getElementById("taReqIgnoreBtn").addEventListener("click", () => {
  if (!selectedTaJob) {
    showMessage("Please select a job first");
    return;
  }
  showMessage(`Ignored job: ${selectedTaJob.jobId}`, true);
  returnToTaJobList();
});

document.getElementById("taReqBackBtn").addEventListener("click", () => {
  returnToTaJobList();
});

document.getElementById("loadMyAppsBtn").addEventListener("click", async () => {
  await loadMyApps();
  showMessage("Applications refreshed", true);
});

document.getElementById("jobForm").addEventListener("submit", async (e) => {
  // Step 6-6: 前端校验 + 即时反馈，减少无效请求。
  e.preventDefault();
  const fd = new FormData(e.target);
  const englishLevel = (fd.get("englishLevel") || "").toString().trim();
  const workDuration = (fd.get("workDuration") || "").toString().trim();
  const weekendAvailable = (fd.get("weekendAvailable") || "").toString().trim();
  const customRequirements = (fd.get("customRequirements") || "").toString().trim();
  const deadlineRaw = (fd.get("deadline") || "").toString().trim();
  if (!englishLevel || !workDuration || !weekendAvailable) {
    showMessage("Please complete all three requirement fields");
    return;
  }
  if (!/^\d{4}\/\d{2}\/\d{2}$/.test(deadlineRaw)) {
    showMessage("Deadline format must be xxxx/xx/xx (e.g. 2026/04/12)");
    return;
  }
  let requirements = `English Level:${englishLevel}; Work Duration:${workDuration}; Weekend Availability:${weekendAvailable}`;
  if (customRequirements) {
    requirements += `; Additional Requirements:${customRequirements}`;
  }
  const deadline = deadlineRaw.replace(/\//g, "-");
  try {
    const json = await api("/api/mo/job", "POST", {
      moId: state.id,
      title: fd.get("title"),
      requirements,
      deadline,
    });
    showMessage(`Job posted: ${json.jobId}`, true);
    e.target.reset();
    e.target.querySelectorAll(".req-item").forEach((item) => {
      item.open = false;
    });
    await loadMyJobs();
  } catch (err) {
    showMessage(err.message);
  }
});

async function loadMyJobs() {
  const el = document.getElementById("myJobs");
  const json = await api(`/api/mo/jobs?moId=${encodeURIComponent(state.id)}`);
  if (!json.jobs.length) {
    el.innerHTML = "<p class=\"empty-hint\">No posted jobs</p>";
    return;
  }
  el.innerHTML = json.jobs.map((j) => `
    <div class="job-card">
      <div class="job-card-header">
        <span class="job-title">${escHtml(j.title)}</span>
        <span class="job-id-badge">${escHtml(j.jobId)}</span>
      </div>
      <div class="job-meta">Deadline: ${escHtml(j.deadline)}</div>
      <div class="job-req">Requirements: ${escHtml(j.requirements)}</div>
    </div>`).join("");
}

document.getElementById("loadMyJobsBtn").addEventListener("click", async () => {
  try {
    await loadMyJobs();
    showMessage("My jobs refreshed", true);
  } catch (err) {
    showMessage(err.message);
  }
});

document.getElementById("refreshReviewJobsBtn").addEventListener("click", async () => {
  await loadMoReviewPanel();
});

document.getElementById("reviewJobSelect").addEventListener("change", (e) => {
  const jobId = e.target.value;
  if (!jobId) return;
  document.querySelectorAll(".job-fold").forEach((fold) => {
    fold.open = false;
  });
  const target = document.getElementById(`job-fold-${jobId}`);
  if (target) {
    target.open = true;
    target.scrollIntoView({ behavior: "smooth", block: "nearest" });
  }
});

document.getElementById("applicants").addEventListener("click", (e) => {
  const btn = e.target.closest(".applicant-item");
  if (!btn) return;
  const appId = btn.dataset.appId;
  if (!appId || !moApplicantIndex.has(appId)) return;
  selectedApplicant = moApplicantIndex.get(appId);
  renderSelectedApplicant();
  document.querySelectorAll(".applicant-item").forEach((node) => {
    node.classList.toggle("active", node.dataset.appId === appId);
  });
});

document.getElementById("approveApplicantBtn").addEventListener("click", async () => {
  await updateSelectedApplicantStatus("Shortlisted");
});

document.getElementById("rejectApplicantBtn").addEventListener("click", async () => {
  if (!selectedApplicant) {
    showMessage("Please select an applicant first");
    return;
  }
  const reason = prompt("Enter rejection reason:", "");
  if (reason === null) {
    return;
  }
  const trimmed = reason.trim();
  if (!trimmed) {
    showMessage("Rejection reason cannot be empty");
    return;
  }
  await updateSelectedApplicantStatus("Rejected", trimmed);
});

document.getElementById("backToApplicantsBtn").addEventListener("click", () => {
  selectedApplicant = null;
  renderSelectedApplicant();
  document.querySelectorAll(".applicant-item").forEach((node) => {
    node.classList.remove("active");
  });
});

document.getElementById("loadMetricsBtn").addEventListener("click", async () => {
  const el = document.getElementById("metrics");
  try {
    const m = await api("/api/admin/metrics");
    const dist = Object.keys(m.statusDistribution || {}).length
      ? Object.entries(m.statusDistribution).map(([k, v]) => `${k}: ${v}`).join("\n")
      : "(empty)";
    el.textContent = `Total Jobs: ${m.totalJobs}\nOpen Jobs: ${m.openJobs}\nTotal Applications: ${m.totalApplications}\nCompletion: ${m.completionRate}%\n\nStatus Distribution\n${dist}`;
    showMessage("Metrics refreshed", true);
  } catch (err) {
    showMessage(err.message);
  }
});

document.getElementById("loadLogsBtn").addEventListener("click", async () => {
  const el = document.getElementById("logs");
  try {
    const json = await api("/api/admin/logs");
    if (!json.logs.length) {
      el.textContent = "No logs";
      return;
    }
    el.textContent = json.logs
      .map((l) => `${l.timestamp} | ${l.userId} | ${l.role} | ${l.action} | ${l.detail}`)
      .join("\n");
    showMessage("Logs refreshed", true);
  } catch (err) {
    showMessage(err.message);
  }
});

document.getElementById("openJobs").addEventListener("click", (e) => {
  const btn = e.target.closest("[data-view-job]");
  if (!btn) return;
  const job = taOpenJobsCache.find((j) => j.jobId === btn.dataset.viewJob);
  if (!job) return;
  showTaRequirementView(job);
});

document.querySelectorAll("[data-stat-nav]").forEach((card) => {
  // Step 6-5: 事件驱动交互组织（操作 -> 接口 -> 视图更新）。
  card.addEventListener("click", () => {
    const nav = card.dataset.statNav;
    if (nav === "ta-profile") switchTaTab("profile");
    else if (nav === "ta-history") switchTaTab("history");
    else if (nav === "mo-jobs") switchMoTab("jobs");
    else if (nav === "mo-review") switchMoTab("review");
    else if (nav === "admin-metrics") switchAdminTab("metrics");
    else if (nav === "admin-logs") switchAdminTab("logs");
  });
});

// Step 6-7: 启动后进入可演示闭环（登录->发岗->投递->审核->统计日志）。
switchRoleView();
refreshResumeUploadBtn();
