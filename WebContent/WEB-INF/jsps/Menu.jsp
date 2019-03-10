<%@ page language="java" contentType="text/html; charset=ISO-8859-1" pageEncoding="ISO-8859-1"%>
<html>
<head>

<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
 <meta name="viewport" content="width=device-width, initial-scale=1">
  <link rel="stylesheet" href="https://maxcdn.bootstrapcdn.com/bootstrap/3.4.0/css/bootstrap.min.css">
  <script src="https://ajax.googleapis.com/ajax/libs/jquery/3.3.1/jquery.min.js"></script>
  <script src="https://maxcdn.bootstrapcdn.com/bootstrap/3.4.0/js/bootstrap.min.js"></script>
<title>Menu</title>
 <link rel="stylesheet" href="/WEB-INF/jspsmenu.css">
 </style>
</head>
<body>
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
      <li><a href="${pageContext.request.contextPath}/RegisteredCourses">Registered Courses</a></li>
		<li><a href="${pageContext.request.contextPath}/marks">Marks</a></li>
      <li><a href="${pageContext.request.contextPath}/timetable">Time Table</a></li>
      <li><a href="${pageContext.request.contextPath}/attendence">Attendence</a></li>
      <li><a href="#">Logout</a></li>
  
  </nav>
<div id="side-menu" class="side-nav">
    <a href="#" class="btn-close" onclick="closeSlideMenu()">&times;</a>
      <a href="${pageContext.request.contextPath}/RegisteredCourses">Registered Courses</a>>
	  <a href="${pageContext.request.contextPath}/marks">Marks</a>
      <a href="${pageContext.request.contextPath}/timetable">Time Table</a>
      <a href="${pageContext.request.contextPath}/attendence">Attendence</a>
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
  </form>
</body>
</html>