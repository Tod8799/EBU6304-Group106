<%@ page contentType="text/html; charset=UTF-8" %>
<%@ include file="/WEB-INF/ta/helpers.jspf" %>
<%
  try {
    resetAll();
  } catch (Exception ignore) {
  }
  response.sendRedirect(request.getContextPath() + "/index.jsp");
%>

