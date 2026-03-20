<%@ page contentType="text/html; charset=UTF-8" %>
<%@ include file="/WEB-INF/ta/helpers.jspf" %>
<!doctype html>
<html>
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <title>MO - Select Applicants</title>
    <link rel="stylesheet" href="<%=request.getContextPath()%>/assets/style.css" />
  </head>
  <body>
    <div class="wrap">
      <aside class="nav">
        <%@ include file="/WEB-INF/ta/nav.jspf" %>
      </aside>
      <main class="content">
        <div class="card">
          <h2 style="margin-top:0;">MO - Select Applicants</h2>
          <div class="muted" style="line-height:1.6;">
            Select an applicant for a job. Rule: per job, only one application can be marked as <strong>Selected</strong>.
          </div>
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
                    <th>Applicant</th>
                    <th>CV</th>
                    <th>Status</th>
                    <th>Select</th>
                  </tr>
                </thead>
                <tbody>
                  <%
                    if (apps == null || apps.isEmpty()) {
                  %>
                    <tr><td colspan="5" class="muted">No applications yet. TA should apply first.</td></tr>
                  <%
                    } else {
                      for (Application a : apps) {
                  %>
                    <tr>
                      <td><%=a.jobId%></td>
                      <td><%=a.applicantName%></td>
                      <td><%=a.cvSaved ? "Uploaded" : "Missing"%></td>
                      <%
                        String cssClass = a.selected
                          ? "good"
                          : ("Submitted".equals(a.status) ? "warn" : "good");
                      %>
                      <td><span class="tag <%=cssClass%>"><%=statusTextForRow(a)%></span></td>
                      <td>
                        <form method="post" action="<%=request.getContextPath()%>/ta-actions/select_applicant.jsp">
                          <input type="hidden" name="jobId" value="<%=a.jobId%>" />
                          <input type="hidden" name="applicantName" value="<%=a.applicantName%>" />
                          <button class="btn btnGreen" type="submit" <%=a.selected ? "disabled" : ""%> >
                            <%=a.selected ? "Selected" : "Select"%>
                          </button>
                        </form>
                      </td>
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
                <h3 style="margin-top:0;">After Selecting</h3>
                <div class="muted" style="line-height:1.7;">
                  Selected applications will affect the Admin workload summary.
                </div>
                <div class="hr"></div>
                <a class="btn btnPrimary" href="<%=request.getContextPath()%>/admin_workload.jsp" style="display:block; text-align:center;">Go to Admin Workload</a>
              </div>
            </div>
          </div>

        </div>
      </main>
    </div>
  </body>
</html>

