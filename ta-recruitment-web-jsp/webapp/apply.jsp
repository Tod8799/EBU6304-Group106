<%@ page contentType="text/html; charset=UTF-8" %>
<%@ include file="/WEB-INF/ta/helpers.jspf" %>
<!doctype html>
<html>
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <title>TA - Apply for a Job</title>
    <link rel="stylesheet" href="<%=request.getContextPath()%>/assets/style.css" />
  </head>
  <body>
    <div class="wrap">
      <aside class="nav">
        <%@ include file="/WEB-INF/ta/nav.jspf" %>
      </aside>
      <main class="content">
        <div class="card">
          <h2 style="margin-top:0;">TA - Apply for a Job</h2>
          <div class="muted" style="line-height:1.6;">Submit a mock application. Skill match is computed and explained here.</div>
          <div class="hr"></div>

          <%
            String jobId = sanitize(request.getParameter("jobId"));
            java.util.List<Job> jobs = loadJobs();
            Job job = findJob(jobs, jobId);
            Profile profile = loadProfile();
            CvInfo cv = loadCv();
            java.util.List<Application> apps = loadApplications();
            Application existing = null;
            if (job != null) {
              for (Application a : apps) {
                if (jobId.equals(a.jobId) && profile.name != null && profile.name.equals(a.applicantName)) {
                  existing = a;
                  break;
                }
              }
            }
            SkillMatch match = computeSkillMatch(job, profile);
          %>

          <%
            if (job == null) {
          %>
            <div class="muted">Invalid jobId. Please go back to <a href="<%=request.getContextPath()%>/jobs.jsp">Jobs</a>.</div>
          <%
            } else {
          %>
            <div class="grid2">
              <div>
                <form method="post" action="<%=request.getContextPath()%>/ta-actions/submit_application.jsp">
                  <input type="hidden" name="jobId" value="<%=job.id%>" />

                  <label>Job</label>
                  <input type="text" value="<%=job.title%> (ID: <%=job.id%>)" readonly />

                  <label>Expected hours / week</label>
                  <input type="text" value="<%=job.hoursPerWeek%>" readonly />

                  <label>Motivation / Message</label>
                  <textarea name="message" placeholder="Why you are a good match..."><%=
                    (existing != null && existing.message != null) ? existing.message : ""
                  %></textarea>

                  <label>Availability (mock)</label>
                  <select name="availability">
                    <option value="Flexible">Flexible</option>
                    <option value="Weekdays">Weekdays</option>
                    <option value="Weekends">Weekends</option>
                    <option value="Evenings">Evenings</option>
                  </select>

                  <div style="margin-top:12px;" class="row">
                    <button class="btn btnPrimary" type="submit">Submit Application</button>
                    <a class="btn" href="<%=request.getContextPath()%>/status.jsp">Next: Status</a>
                  </div>
                </form>
              </div>

              <div>
                <div class="card" style="padding:14px;">
                  <h3 style="margin-top:0;">AI (Mock) Skill Match</h3>
                  <div class="row" style="margin-bottom:8px;">
                    <span class="tag warn">Score: <strong style="color:#e8eeff;"><%=match.score%>%</strong></span>
                  </div>
                  <div class="muted" style="line-height:1.7;">
                    <div><strong style="color:#e8eeff;">Matched:</strong> <%=skillsToCsv(match.matched)%></div>
                    <div><strong style="color:#e8eeff;">Missing:</strong> <%=skillsToCsv(match.missing)%></div>
                    <div class="hr"></div>
                    <div>
                      Explainable rule:
                      compare TA skills with job required skills by simple intersection
                      (comma-separated skills).
                    </div>
                  </div>

                  <div class="hr"></div>
                  <div class="muted" style="line-height:1.7;">
                    Existing application status:
                    <strong style="color:#e8eeff;">
                      <%= existing == null ? "Draft" : statusTextForRow(existing) %>
                    </strong>
                  </div>
                  <div class="hr"></div>
                  <div class="muted" style="line-height:1.7;">
                    CV saved: <strong style="color:#e8eeff;"><%=cv.saved%></strong>
                    <div style="margin-top:6px;">
                      Profile completeness:
                      <strong style="color:#e8eeff;">
                        <%=
                          (profile.name == null || profile.name.trim().isEmpty() || profile.email == null || profile.email.trim().isEmpty())
                          ? "Need Profile"
                          : (!cv.saved ? "Need CV" : "OK")
                        %>
                      </strong>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          <%
            }
          %>
        </div>
      </main>
    </div>
  </body>
</html>

