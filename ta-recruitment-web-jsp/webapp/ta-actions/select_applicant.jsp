<%@ page contentType="text/html; charset=UTF-8" %>
<%@ include file="/WEB-INF/ta/helpers.jspf" %>
<%
  String jobId = sanitize(request.getParameter("jobId"));
  String applicantName = sanitize(request.getParameter("applicantName"));
  if (jobId == null || jobId.isEmpty() || applicantName == null || applicantName.isEmpty()) {
    response.sendRedirect(request.getContextPath() + "/mo_select.jsp");
    return;
  }
  java.util.List<Application> apps = loadApplications();
  selectApplicant(apps, jobId, applicantName);
  saveApplications(apps);
  response.sendRedirect(request.getContextPath() + "/mo_select.jsp");
%>

