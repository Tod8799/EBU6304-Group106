<%@ page contentType="text/html; charset=UTF-8" %>
<%@ include file="/WEB-INF/ta/helpers.jspf" %>
<!doctype html>
<html>
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <title>TA - Create Applicant Profile</title>
    <link rel="stylesheet" href="<%=request.getContextPath()%>/assets/style.css" />
  </head>
  <body>
    <div class="wrap">
      <aside class="nav">
        <%@ include file="/WEB-INF/ta/nav.jspf" %>
      </aside>
      <main class="content">
        <div class="card">
          <h2 style="margin-top:0;">TA - Create Applicant Profile</h2>
          <div class="muted" style="line-height:1.7;">
            Fill your profile and skills (comma separated). Data is persisted into a plain text file on server.
          </div>
          <div class="hr"></div>

          <%
            Profile profile = loadProfile();
          %>
          <form method="post" action="<%=request.getContextPath()%>/ta-actions/save_profile.jsp">
            <label>Full Name</label>
            <input name="name" type="text" value="<%=profile.name%>" required />

            <label>Email</label>
            <input name="email" type="email" value="<%=profile.email%>" required />

            <div class="grid2">
              <div>
                <label>Major</label>
                <input name="major" type="text" value="<%=profile.major%>" />
              </div>
              <div>
                <label>Skills (comma separated)</label>
                <input name="skills" type="text" value="<%=profile.skills%>" placeholder="Teaching, Python, Data Structures" />
              </div>
            </div>

            <label>Short Bio</label>
            <textarea name="bio" placeholder="1-3 sentences."><%=profile.bio%></textarea>

            <div style="margin-top:12px;" class="row">
              <button class="btn btnPrimary" type="submit">Save Profile</button>
              <a class="btn" href="<%=request.getContextPath()%>/ta_upload_cv.jsp">Next: Upload CV</a>
            </div>
          </form>
        </div>
      </main>
    </div>
  </body>
</html>

