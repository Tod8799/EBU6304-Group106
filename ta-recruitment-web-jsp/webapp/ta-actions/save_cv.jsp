<%@ page contentType="text/html; charset=UTF-8" %>
<%@ include file="/WEB-INF/ta/helpers.jspf" %>
<%
  // Extract uploaded CV filename and optional note.
  String note = sanitize(request.getParameter("cvNote"));
  String fileName = "";
  boolean saved = false;

  try {
    jakarta.servlet.http.Part part = request.getPart("cvFile");
    if (part != null && part.getSize() > 0) {
      String cd = part.getHeader("content-disposition");
      // Example: form-data; name="cvFile"; filename="xxx.pdf"
      if (cd != null) {
        for (String token : cd.split(";")) {
          String t = token.trim();
          if (t.startsWith("filename=")) {
            String fn = t.substring("filename=".length()).trim();
            if (fn.startsWith("\"") && fn.endsWith("\"")) fn = fn.substring(1, fn.length()-1);
            fileName = fn;
            break;
          }
        }
      }
      saved = !fileName.isEmpty();
    }
  } catch (Exception ignore) {
    // multipart might not exist or no file chosen; keep defaults.
  }

  CvInfo cv = new CvInfo();
  cv.saved = saved;
  cv.fileName = fileName;
  cv.note = note;
  saveCv(cv);

  response.sendRedirect(request.getContextPath() + "/ta_upload_cv.jsp");
%>

