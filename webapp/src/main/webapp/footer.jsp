<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib prefix="fn"  uri="http://java.sun.com/jsp/jstl/functions" %>

<!-- footer.jsp -->
<br/>
<br/>

<div class="body" align="center" style="clear:both">
    <!-- contact -->
    <c:if test="${pageName != 'contact'}">
        <div id="contactFormDivButton">
            <div class="contactButton">
              <b>Questions? Comments? Email us at <c:out value="${WEB_PROPERTIES['feedback.destination.public']}"/></b>
            </div>
        </div>
    </c:if>
    <br/>

    <!-- funding -->
    <div id="funding-footer">
        <fmt:message key="funding" />
        <br/>
        <br/>

        <!-- powered -->
        <p>Powered by</p>
        <a target="new" href="http://intermine.org" title="InterMine">
            <img src="images/icons/intermine-footer-logo.png" alt="InterMine logo" />
        </a><br/><br/>
    </div>
</div>

<!-- /footer.jsp -->
