<%@ page contentType="text/html; charset=UTF-8" %>
<%@ include file="/WEB-INF/ta/helpers.jspf" %>
<!doctype html>
<html>
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <title>TA Recruitment - Home</title>
    <link rel="stylesheet" href="<%=request.getContextPath()%>/assets/style.css" />
  </head>
  <body>
    <div class="wrap">
      <aside class="nav">
        <%@ include file="/WEB-INF/ta/nav.jspf" %>
      </aside>
      <main class="content">
        <div class="card">
          <h2 style="margin-top:0;">Prototype Core Flow</h2>
          <div class="muted" style="line-height:1.7;">
            TA: Profile -> Upload CV -> Find Jobs -> Apply -> Status<br/>
            MO: Post Jobs -> Select Applicants<br/>
            Admin: Workload Summary
          </div>

          <div class="hr"></div>
          <%
            Profile profile = loadProfile();
            CvInfo cv = loadCv();
            java.util.List<Application> apps = loadApplications();
            String stateLabel;
            if (profile.name == null || profile.name.trim().isEmpty() || profile.email == null || profile.email.trim().isEmpty()) stateLabel = "Need Profile";
            else if (!cv.saved) stateLabel = "Need CV";
            else if (apps == null || apps.size() == 0) stateLabel = "Ready";
            else stateLabel = "In Progress";
          %>
          <div class="row">
            <div class="tag warn">Current state: <strong style="color:#e8eeff;"><%=stateLabel%></strong></div>
            <div class="tag">Applications: <strong style="color:#e8eeff;"><%=apps==null?0:apps.size()%></strong></div>
            <div class="tag good">Selected: <strong style="color:#e8eeff;"><% 
              int selectedCount = 0;
              if (apps != null) { for (Application a : apps) if (a.selected) selectedCount++; }
              out.print(selectedCount);
            %></strong></div>
          </div>

          <div class="hr"></div>
          <form method="post" action="<%=request.getContextPath()%>/ta-actions/reset_state.jsp">
            <button class="btn btnDanger" type="submit">Reset Mock State</button>
          </form>

          <div class="hr"></div>
          <div class="muted" style="line-height:1.7;">
            Tip: start from <strong>TA: Create Applicant Profile</strong> and complete the flow.
          </div>
        </div>
      </main>
    </div>
  </body>
</html>

