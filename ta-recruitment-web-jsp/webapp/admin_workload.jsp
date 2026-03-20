<%@ page contentType="text/html; charset=UTF-8" %>
<%@ include file="/WEB-INF/ta/helpers.jspf" %>
<!doctype html>
<html>
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <title>Admin - Workload Summary</title>
    <link rel="stylesheet" href="<%=request.getContextPath()%>/assets/style.css" />
  </head>
  <body>
    <div class="wrap">
      <aside class="nav">
        <%@ include file="/WEB-INF/ta/nav.jspf" %>
      </aside>
      <main class="content">
        <div class="card">
          <h2 style="margin-top:0;">Admin - Workload Summary</h2>
          <div class="muted" style="line-height:1.6;">Compute workload from MO-selected applications (no database).</div>
          <div class="hr"></div>

          <%
            java.util.List<Application> apps = loadApplications();
            java.util.List<WorkloadRow> rows = computeWorkload(apps);
          %>

          <div class="grid2">
            <div>
              <table>
                <thead>
                  <tr>
                    <th>Applicant</th>
                    <th>Selected Jobs</th>
                    <th>Total Hours / week</th>
                    <th>Risk Flag</th>
                  </tr>
                </thead>
                <tbody>
                  <%
                    if (rows == null || rows.isEmpty()) {
                  %>
                    <tr><td colspan="4" class="muted">No selected applications yet. Select applicants first.</td></tr>
                  <%
                    } else {
                      for (WorkloadRow r : rows) {
                        String label = workloadRiskLabel(r.hours);
                        String cls = workloadRiskClass(r.hours);
                  %>
                      <tr>
                        <td><%=r.applicantName%></td>
                        <td><%=r.selectedJobs%></td>
                        <td><%=r.hours%></td>
                        <td><span class="tag <%=cls%>"><%=label%></span></td>
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
                <h3 style="margin-top:0;">Explainable Output</h3>
                <div class="muted" style="line-height:1.7;">
                  Risk Flag rule:
                  <div>Hours >= 10 => High workload</div>
                  <div style="margin-top:6px;">Hours >= 7 => Medium workload</div>
                  <div style="margin-top:6px;">Else => OK</div>
                </div>
                <div class="hr"></div>
                <div class="muted" style="line-height:1.7;">
                  This demonstrates explainability for any AI-assisted features later.
                </div>
              </div>
            </div>
          </div>

        </div>
      </main>
    </div>
  </body>
</html>

