<%@ page language="java" contentType="text/html; charset=ISO-8859-1" pageEncoding="ISO-8859-1"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/sql" prefix="sql" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
<title>Student Registration</title>
<style type="text/css">
table{
margin-top: 120px;
}
</style>
</head>
<body>
<center>
<form method="post" action="${pageContext.request.contextPath}/docreate">
<table>
<tr>
		<td>Name:</td>
		<td><input type="text" name ="name"></td>
</tr>
<tr>
	<td>Email:</td>
	<td><input type="text" name ="email"></td>
</tr>
<tr>
		<td>Age:</td>
		<td><input type="number" name ="email"></td>
</tr>
<tr>
		<td>text:</td>
		<td><input type="text" name ="text"></td>
</tr>
</tr>
		<td  ><input type="submit" value ="CreateStudent"></td>
</tr>

</table>
</form>
</center>
</body>
</html>