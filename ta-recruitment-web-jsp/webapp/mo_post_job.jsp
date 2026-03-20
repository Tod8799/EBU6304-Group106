<%@ page contentType="text/html; charset=UTF-8" %>
<%@ include file="/WEB-INF/ta/helpers.jspf" %>
<!doctype html>
<html>
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <title>MO - Post Jobs</title>
    <link rel="stylesheet" href="<%=request.getContextPath()%>/assets/style.css" />
  </head>
  <body>
    <div class="wrap">
      <aside class="nav">
        <%@ include file="/WEB-INF/ta/nav.jspf" %>
      </aside>
      <main class="content">
        <div class="card">
          <h2 style="margin-top:0;">MO - Post a Job</h2>
          <div class="muted" style="line-height:1.6;">Create a new job record (stored in plain text). Then MO can select applicants.</div>
          <div class="hr"></div>

          <%
            java.util.List<Job> jobs = loadJobs();
          %>
          <div class="muted" style="margin-bottom:10px;">Current jobs: <strong style="color:#e8eeff;"><%=jobs.size()%></strong></div>

          <form method="post" action="<%=request.getContextPath()%>/ta-actions/post_job.jsp">
            <div class="grid2">
              <div>
                <label>Job ID</label>
                <input name="jobId" type="text" placeholder="e.g., J-010" />
              </div>
              <div>
                <label>Hours / week</label>
                <input name="hoursPerWeek" type="number" min="1" placeholder="e.g., 6" />
              </div>
            </div>

            <label>Job Title</label>
            <input name="title" type="text" placeholder="e.g., Invigilation - Midterm" />

            <div class="grid2">
              <div>
                <label>Module / Activity</label>
                <input name="module" type="text" placeholder="e.g., EBU6304" />
              </div>
              <div>
                <label>Location (mock)</label>
                <input name="location" type="text" placeholder="e.g., Main Campus" />
              </div>
            </div>

            <label>Required Skills (comma separated)</label>
            <input name="requiredSkills" type="text" placeholder="e.g., Invigilation, Communication" />

            <div style="margin-top:12px;" class="row">
              <button class="btn btnPrimary" type="submit">Post Job</button>
              <a class="btn" href="<%=request.getContextPath()%>/mo_select.jsp">Next: Select Applicants</a>
            </div>
          </form>

        </div>
      </main>
    </div>
  </body>
</html>

