<%@ page contentType="text/html; charset=UTF-8" %>
<%@ include file="/WEB-INF/ta/helpers.jspf" %>
<!doctype html>
<html>
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <title>Check Application Status</title>
    <link rel="stylesheet" href="<%=request.getContextPath()%>/assets/style.css" />
  </head>
  <body>
    <div class="wrap">
      <aside class="nav">
        <%@ include file="/WEB-INF/ta/nav.jspf" %>
      </aside>
      <main class="content">
        <div class="card">
          <h2 style="margin-top:0;">TA - Check Application Status</h2>
          <div class="muted" style="line-height:1.6;">Advance statuses in mock steps and observe changes. Selected applications are controlled by MO.</div>
          <div class="hr"></div>

          <%
            java.util.List<Application> apps = loadApplications();
          %>
          <div class="grid2">
            <div>
              <table>
                <thead>
                  <tr>
                    <th>Job ID</th>
                    <th>Job Title</th>
                    <th>Applicant</th>
                    <th>Status</th>
                    <th>CV</th>
                  </tr>
                </thead>
                <tbody>
                  <%
                    if (apps == null || apps.isEmpty()) {
                  %>
                    <tr><td colspan="5" class="muted">No applications yet. Apply to a job first.</td></tr>
                  <%
                    } else {
                      for (Application a : apps) {
                  %>
                    <tr>
                      <td><%=a.jobId%></td>
                      <td><%=a.jobTitle%></td>
                      <td><%=a.applicantName%></td>
                      <%
                        String cssClass = a.selected
                          ? "good"
                          : ( "Submitted".equals(a.status) ? "warn" : ("Approved".equals(a.status) ? "good" : "warn") );
                      %>
                      <td><span class="tag <%=cssClass%>"><%=statusTextForRow(a)%></span></td>
                      <td><%=a.cvSaved ? "Uploaded" : "Missing"%></td>
                    </tr>
                  <%
                      }
                    }
                  %>
                </tbody>
              </table>
            </div>

            <div>
              <div class="card" style="padding:14px;">
                <h3 style="margin-top:0;">Simulate Updates</h3>
                <div class="muted" style="line-height:1.7; margin-bottom:12px;">
                  Advance steps (mock):
                  <div>Submitted -> In Review -> Approved -> Completed</div>
                  <div style="margin-top:6px;">If an application is MO-selected, it stays selected.</div>
                </div>

                <form method="post" action="<%=request.getContextPath()%>/ta-actions/advance_status.jsp">
                  <button class="btn btnGreen" type="submit" style="width:100%;">Advance Status (Mock)</button>
                </form>

                <form method="post" action="<%=request.getContextPath()%>/ta-actions/reset_state.jsp" style="margin-top:10px;">
                  <button class="btn btnDanger" type="submit" style="width:100%;">Reset Mock State</button>
                </form>

                <div class="hr"></div>
                <a class="btn btnPrimary" href="<%=request.getContextPath()%>/mo_select.jsp" style="display:block; text-align:center;">Go to MO: Select Applicants</a>
              </div>
            </div>
          </div>

        </div>
      </main>
    </div>
  </body>
</html>

