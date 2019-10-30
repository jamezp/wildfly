<%@ page import="org.wildfly.test.integration.jsp.*" %>
<jsp:useBean id="person" class="org.wildfly.test.integration.jsp.Person" scope="request"/>
<html>
   <body>
      Boolean.TRUE: --- ${Boolean.TRUE} ---<br/>
      Integer.MAX_VALUE: --- ${Integer.MAX_VALUE} ---<br/>
      DummyConstants.FOO: --- ${DummyConstants.FOO} ---<br/>
      DummyEnum.VALUE: --- ${DummyEnum.VALUE} ---<br/>
      person.name: --- ${person.name} ---<br/>
   </body>
</html>