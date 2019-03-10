<%@ page language="java" contentType="text/html; charset=ISO-8859-1" pageEncoding="ISO-8859-1"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/sql" prefix="sql" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
 <meta name="viewport" content="width=device-width, initial-scale=1">
  <link rel="stylesheet" href="https://maxcdn.bootstrapcdn.com/bootstrap/3.4.0/css/bootstrap.min.css">
  <script src="https://ajax.googleapis.com/ajax/libs/jquery/3.3.1/jquery.min.js"></script>
  <script src="https://maxcdn.bootstrapcdn.com/bootstrap/3.4.0/js/bootstrap.min.js"></script>

<style type="text/css">
body{
  font-family:"Arial", Serif;
  background-color:#f4f4f4;
  overflow-x:hidden;
 
}

table{
margin-top: 120px;

}

.navbar{
  background-color:#3b5998;
  overflow:hidden;
  height:63px;
}

.navbar a{
  float:left;
  display:block;
  color:#f2f2f2;
  text-align:center;
  padding:14px 16px;
  text-decoration:none;
  font-size:17px;
}

.navbar ul{
  margin:8px 0 0 0;
  list-style:none;
}

.navbar a:hover{
  background-color:#ddd;
  color:#000;
}

.navbar-nav input[type="submit"]{
	
	align:right;
	display:block;
	color:#f2f2f2;
	text-align:center;
	
}
.navbar-nav input[type="submit"] : hover{
  background-color:#fff;
  color:#000;

}

.side-nav{
  height:100%;
  width:0;
  position:fixed;
  z-index:1;
  top:0;
  left:0;
  background-color:#111;
  opacity:0.9;
  overflow-x:hidden;
  padding-top:60px;
  transition:0.5s;
}

.side-nav a{
  padding:10px 10px 10px 30px;
  text-decoration:none;
  font-size:22px;
  color:#ccc;
  display:block;
  transition:0.3s;
}

.side-nav a:hover{
  color:#fff;
}

.side-nav .btn-close{
  position:absolute;
  top:0;
  right:22px;
  font-size:36px;
  margin-left:50px;
}

#main{
  transition:margin-left 0.5s;
  padding:20px;
  overflow:hidden;
  width:100%;
}

@media(max-width:568px){
  .navbar-nav{display:none}
}

@media(min-width:568px){
  /*.open-slide{display:none}*/
}

</style>
<title>Consolidated List</title>
</head>
<body>
<f:view>
<div class="container">

  <nav class="navbar">
    <span class="open-slide">
      <a href="#" onclick="openSlideMenu()">
        <svg width="30" height="30">
            <path d="M0,5 30,5" stroke="#fff" stroke-width="5"/>
            <path d="M0,14 30,14" stroke="#fff" stroke-width="5"/>
            <path d="M0,23 30,23" stroke="#fff" stroke-width="5"/>
        </svg>
      </a>
    </span>

<ul class="navbar-nav">
      <li><a href="#">Registered Courses</a></li>
		<li><a href="#">Marks</a></li>
      <li><a href="#">Daily Schedule</a></li>
      <li><a href="#">Attendence</a></li>
      <li><a href="#">Logout</a></li>
  
  </nav>
<div id="side-menu" class="side-nav">
    <a href="#" class="btn-close" onclick="closeSlideMenu()">&times;</a>
      <a href=#">Registered Courses</a>>
	  <a href="#">Marks</a>
      <a href="#">Daily Schedule</a>
      <a href="#">Attendence</a>
      <a href="#">Logout</a>
  </div>
 <script>
    function openSlideMenu(){
      document.getElementById('side-menu').style.width = '250px';
      document.getElementById('main').style.marginLeft = '250px';
    }

    function closeSlideMenu(){
      document.getElementById('side-menu').style.width = '0';
      document.getElementById('main').style.marginLeft = '0';
    }
  </script>
  
<center>
<table border="5" class="table table-bordered">
<tr>
	<th>ID</th>
	<th>Name</th>
	<th>E-mail</th>
	<th>Text</th>
</tr>
<c:forEach var="row" items="${students}">
     
     <tr>
     <td>${row.id}</td>
     <td>${row.name}</td>
     <td>${row.email}</td>
     <td>${row.text}</td>
    </tr>
</c:forEach>
</table>
</center>
</f:view>
</div>
</body>
</html>