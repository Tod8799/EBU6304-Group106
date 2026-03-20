<%@ page contentType="text/html; charset=UTF-8" %>
<%@ include file="/WEB-INF/ta/helpers.jspf" %>
<%
  String name = sanitize(request.getParameter("name"));
  String email = sanitize(request.getParameter("email"));
  String major = sanitize(request.getParameter("major"));
  String skills = sanitize(request.getParameter("skills"));
  String bio = sanitize(request.getParameter("bio"));

  Profile p = new Profile();
  p.name = name;
  p.email = email;
  p.major = major;
  p.skills = skills;
  p.bio = bio;
  saveProfile(p);

  response.sendRedirect(request.getContextPath() + "/ta_profile.jsp");
%>

