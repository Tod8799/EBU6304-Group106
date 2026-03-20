<%@ page contentType="text/html; charset=UTF-8" %>
<%@ include file="/WEB-INF/ta/helpers.jspf" %>
<!doctype html>
<html>
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <title>Find Available Jobs</title>
    <link rel="stylesheet" href="<%=request.getContextPath()%>/assets/style.css" />
  </head>
  <body>
    <div class="wrap">
      <aside class="nav">
        <%@ include file="/WEB-INF/ta/nav.jspf" %>
      </aside>
      <main class="content">
        <div class="card">
          <h2 style="margin-top:0;">TA - Find Available Jobs</h2>
          <div class="muted" style="line-height:1.6;">
            Search and then apply for a job. Matching is computed on the Apply page.
          </div>
          <div class="hr"></div>

          <%
            String q = sanitize(request.getParameter("q"));
            java.util.List<Job> jobs = loadJobs();
            java.util.List<Job> filtered = new java.util.ArrayList<Job>();
            String qLower = q.toLowerCase();
            for (Job j : jobs) {
              if (qLower.isEmpty()) {
                filtered.add(j);
              } else {
                String hay = (j.title + " " + j.module + " " + j.requiredSkills.toString()).toLowerCase();
                if (hay.contains(qLower)) filtered.add(j);
              }
            }
          %>

          <form method="get" action="<%=request.getContextPath()%>/jobs.jsp" class="row">
            <div style="flex:1; min-width:220px;">
              <label style="margin-top:0;">Search (title / module / skills)</label>
              <input name="q" type="text" value="<%=q%>" placeholder="e.g., Invigilation, Python" />
            </div>
            <div style="margin-top:24px;">
              <button class="btn btnPrimary" type="submit">Search</button>
            </div>
          </form>

          <div class="hr"></div>
          <div class="muted" style="margin-bottom:10px;">Total jobs: <strong style="color:#e8eeff;"><%=filtered.size()%></strong></div>

          <%
            Profile profile = loadProfile();
          %>
          <div class="muted" style="line-height:1.6; margin-bottom:14px;">
            Your TA profile: <strong style="color:#e8eeff;"><%=profile.name%></strong> | Skills: <strong style="color:#e8eeff;"><%=profile.skills%></strong>
          </div>

          <div>
            <%
              if (filtered.isEmpty()) {
            %>
              <div class="muted">No jobs matched your search.</div>
            <%
              } else {
                for (Job j : filtered) {
            %>
              <div class="card" style="margin-bottom:12px; padding:14px;">
                <div style="display:flex; justify-content:space-between; gap:12px; flex-wrap:wrap;">
                  <div>
                    <div style="font-weight:800; letter-spacing:.2px;"><%=j.title%></div>
                    <div class="muted" style="font-size:12px; margin-top:4px;">
                      Module: <%=j.module%> | Location: <%=j.location%>
                    </div>
                  </div>
                  <div style="text-align:right;">
                    <div class="tag warn">Job ID: <%=j.id%></div>
                    <div class="muted" style="font-size:12px; margin-top:6px;"><%=j.hoursPerWeek%> hours/week</div>
                  </div>
                </div>
                <div class="muted" style="margin-top:10px; line-height:1.6;">
                  Required skills: <%=skillsToCsv(j.requiredSkills)%>
                </div>
                <div style="margin-top:12px;">
                  <a class="btn btnPrimary" href="<%=request.getContextPath()%>/apply.jsp?jobId=<%=j.id%>">Apply</a>
                </div>
              </div>
            <%
                }
              }
            %>
          </div>
        </div>
      </main>
    </div>
  </body>
</html>

