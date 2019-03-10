package com.academicportal.web.controller;

import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import com.academicportal.web.dao.StudentDao;
import com.academicportal.web.model.Student;
import com.academicportal.web.service.AcademicService;

@Controller
public class AcademicController {
	private AcademicService academicService;
	private StudentDao studentDao;
	
	
	@Autowired
	public void setAcademicService(AcademicService academicService) {
		this.academicService = academicService;
	}
	
    @Autowired
	public void setStudentDao(StudentDao studentDao) {
		this.studentDao = studentDao;
	}


	@RequestMapping(value="/test",method=RequestMethod.GET)
	public String test(Model model,@RequestParam("id")String id)
	{
		System.out.println("ID is:" +id);
			return "home";
	}
	@RequestMapping("/students")
	public String showStudents(Model model)
	{
		List<Student> students = academicService.getCurrent();
         model.addAttribute("students",students);
       
			return "students";
	}
	@RequestMapping("/createStudent")
	public String createStudent()
	{
		
			return "createStudent";
	}
	@RequestMapping(value="/docreate",method=RequestMethod.POST)
	public String doCreate(Model model,Student student)
	{
		    studentDao.createStudent(student);
			return "studentcreated";
	}
	@RequestMapping(value="/registeredcourses",method=RequestMethod.POST)
	public String registeredCourses()
	{
	  return "RegisteredCourses";	
	}
}
