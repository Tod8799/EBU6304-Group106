<%@ page contentType="text/html; charset=UTF-8" %>
<%@ include file="/WEB-INF/ta/helpers.jspf" %>
<%
  String jobId = sanitize(request.getParameter("jobId"));
  String hoursRaw = sanitize(request.getParameter("hoursPerWeek"));
  String title = sanitize(request.getParameter("title"));
  String module = sanitize(request.getParameter("module"));
  String location = sanitize(request.getParameter("location"));
  String requiredSkillsRaw = sanitize(request.getParameter("requiredSkills"));

  int hours = 0;
  try {
    hours = Integer.parseInt(hoursRaw);
  } catch (Exception ignore) {}

  if (jobId == null || jobId.trim().isEmpty()) {
    jobId = "J-" + (100 + (int)(Math.random() * 900));
  }
  if (hours <= 0 || title == null || title.isEmpty() || module == null || module.isEmpty()) {
    response.sendRedirect(request.getContextPath() + "/mo_post_job.jsp");
    return;
  }
  if (location == null || location.isEmpty()) location = "TBD";

  java.util.List<String> required = parseSkillsCsv(requiredSkillsRaw);
  if (required.isEmpty()) {
    required.add("general");
  }

  java.util.List<Job> jobs = loadJobs();
  boolean exists = false;
  for (Job j : jobs) {
    if (jobId.equals(j.id)) { exists = true; break; }
  }
  if (exists) {
    // Keep it simple: redirect back.
    response.sendRedirect(request.getContextPath() + "/mo_post_job.jsp");
    return;
  }

  Job j = new Job();
  j.id = jobId;
  j.title = title;
  j.module = module;
  j.hoursPerWeek = hours;
  j.location = location;
  j.requiredSkills = required;
  jobs.add(j);
  saveJobs(jobs);

  response.sendRedirect(request.getContextPath() + "/mo_select.jsp");
%>

