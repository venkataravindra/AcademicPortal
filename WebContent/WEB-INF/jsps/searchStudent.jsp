<%@ page language="java" contentType="text/html; charset=ISO-8859-1" pageEncoding="ISO-8859-1"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/sql" prefix="sql" %>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
<meta name="viewport" content="width=device-width, initial-scale=1">
  <link rel="stylesheet" href="https://maxcdn.bootstrapcdn.com/bootstrap/3.4.0/css/bootstrap.min.css">
  <script src="https://ajax.googleapis.com/ajax/libs/jquery/3.3.1/jquery.min.js"></script>
  <script src="https://maxcdn.bootstrapcdn.com/bootstrap/3.4.0/js/bootstrap.min.js"></script>
<style type="text/css">
table{
margin-top: 80px;

}
</style>
<title>Insert title here</title>
</head>
<body>
<f:view>
<div class="container">
<center>
<form action="${pageContext.request.contextPath}/searchStudent/${id}">
<fieldset>
<table class="table table-bordered table-striped">
<tr>
		<td>Enter Student Id</td>
		<td><input class="form-control" type="number" name ="id" required></td>
		<td ><input class="btn btn-info" type="submit" value ="searchStudent"></td>
</tr>
<tr>
</fieldset>
</table>
<table class="table table-bordered table-striped">
<c:forEach var="row${id}" items="${students}">
     
     <tr>
     <td>${row.id}</td>
     <td>${row.name}</td>
     <td>${row.email}</td>
     <td>${row.text}</td>
    </tr>
</c:forEach>
</table>
</form>
</center>
</div>
</f:view>
</body>
</html>