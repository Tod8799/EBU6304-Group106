<%@ page contentType="text/html; charset=UTF-8" %>
<%@ include file="/WEB-INF/ta/helpers.jspf" %>
<!doctype html>
<html>
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <title>TA - Upload CV</title>
    <link rel="stylesheet" href="<%=request.getContextPath()%>/assets/style.css" />
  </head>
  <body>
    <div class="wrap">
      <aside class="nav">
        <%@ include file="/WEB-INF/ta/nav.jspf" %>
      </aside>
      <main class="content">
        <div class="card">
          <h2 style="margin-top:0;">TA - Upload CV (File Upload)</h2>
          <div class="muted" style="line-height:1.7;">
            For this prototype, we only store the uploaded filename and a note (no database).
          </div>
          <div class="hr"></div>
          <%
            CvInfo cv = loadCv();
          %>
          <form method="post" enctype="multipart/form-data" action="<%=request.getContextPath()%>/ta-actions/save_cv.jsp">
            <label>Choose a file</label>
            <input name="cvFile" type="file" accept=".pdf,.doc,.docx,.txt" />

            <label>Note (optional)</label>
            <textarea name="cvNote" placeholder="Any additional information..."><%=cv.note%></textarea>

            <div style="margin-top:12px;" class="row">
              <button class="btn btnPrimary" type="submit">Save CV</button>
              <a class="btn" href="<%=request.getContextPath()%>/jobs.jsp">Next: Find Jobs</a>
            </div>
          </form>

          <div class="hr"></div>
          <div class="muted" style="line-height:1.7;">
            <div>Saved: <strong style="color:#e8eeff;"><%=cv.saved%></strong></div>
            <div>Filename: <strong style="color:#e8eeff;"><%=cv.fileName%></strong></div>
          </div>
        </div>
      </main>
    </div>
  </body>
</html>

