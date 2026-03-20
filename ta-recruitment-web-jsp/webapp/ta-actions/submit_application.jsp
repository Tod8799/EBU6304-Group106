<%@ page contentType="text/html; charset=UTF-8" %>
<%@ include file="/WEB-INF/ta/helpers.jspf" %>
<%
  String jobId = sanitize(request.getParameter("jobId"));
  String message = sanitize(request.getParameter("message"));

  Profile profile = loadProfile();
  CvInfo cv = loadCv();

  // Validate prerequisites
  if (profile.name == null || profile.name.trim().isEmpty() || profile.email == null || profile.email.trim().isEmpty()) {
    response.sendRedirect(request.getContextPath() + "/ta_profile.jsp");
    return;
  }
  if (!cv.saved) {
    response.sendRedirect(request.getContextPath() + "/ta_upload_cv.jsp");
    return;
  }

  java.util.List<Job> jobs = loadJobs();
  Job job = findJob(jobs, jobId);
  if (job == null) {
    response.sendRedirect(request.getContextPath() + "/jobs.jsp");
    return;
  }

  java.util.List<Application> apps = loadApplications();
  Application existing = null;
  for (Application a : apps) {
    if (jobId.equals(a.jobId) && profile.name.equals(a.applicantName)) {
      existing = a;
      break;
    }
  }

  if (existing == null) {
    existing = new Application();
    existing.jobId = job.id;
    existing.applicantName = profile.name;
    existing.jobTitle = job.title;
    existing.hoursPerWeek = job.hoursPerWeek;
    existing.cvSaved = cv.saved;
    existing.message = message;
    existing.status = "Submitted";
    existing.selected = false;
    apps.add(existing);
  } else {
    existing.jobTitle = job.title;
    existing.hoursPerWeek = job.hoursPerWeek;
    existing.cvSaved = cv.saved;
    existing.message = message;
    // Keep current status if it was selected/advanced; for mock just leave as is.
    // If it was selected=true, keep selected and status 'Approved' already set.
  }

  saveApplications(apps);
  response.sendRedirect(request.getContextPath() + "/status.jsp");
%>

