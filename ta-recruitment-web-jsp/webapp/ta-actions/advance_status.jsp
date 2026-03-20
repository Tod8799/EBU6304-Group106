<%@ page contentType="text/html; charset=UTF-8" %>
<%@ include file="/WEB-INF/ta/helpers.jspf" %>
<%
  java.util.List<Application> apps = loadApplications();
  if (apps == null || apps.isEmpty()) {
    response.sendRedirect(request.getContextPath() + "/jobs.jsp");
    return;
  }
  advanceStatuses(apps);
  saveApplications(apps);
  response.sendRedirect(request.getContextPath() + "/status.jsp");
%>

