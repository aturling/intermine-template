<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>

<!-- homologueDisplayer.jsp -->

<div id="homologue-displayer" class="collection-table">
<h3>Homologues</h3>

<c:choose>
<c:when test="${homologues != null && !empty homologues}">
<table class="tiny-font">
  <tbody>
    <c:forEach items="${homologues}" var="entry">
    <tr><th><c:out value="${entry.key}"/></th>
      <c:set var="genes" value="${entry.value}"/>
      <c:choose>
        <c:when test="${empty genes}">
          <td></td>
        </c:when>
        <c:otherwise>
          <td>
            <c:forEach items="${genes}" var="resultElement">
            <!-- only links to ath (aip request, MINE-918) -->
            <c:choose>
             <c:when test="${entry.key eq 'A. thaliana'}">
              <a href="report.do?id=${resultElement.id}">${resultElement.field}</a>
             </c:when>
             <c:otherwise>
              <a href="report.do?id=${resultElement.id}">${resultElement.field}</a>
             </c:otherwise>
            </c:choose>
            </c:forEach>
          </td>
        </c:otherwise>
      </c:choose>
    </c:forEach>
    </tr>
  </tbody>
</table>
</c:when>
<c:when test="${homologues != null && empty homologues}">
  <p style="font-style:italic;">No data found</p>
</c:when>
<c:otherwise>
  <p>There was a problem rendering the displayer.</p>
  <script type="text/javascript">
    jQuery('#homologue-displayer').addClass('warning');
  </script>
</c:otherwise>
</c:choose>
</div>

<!-- /homologueDisplayer.jsp -->
