package com.academicportal.web.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.academicportal.web.dao.StudentDao;
import com.academicportal.web.model.Student;

@Service("studentService")
public class AcademicService {
	private StudentDao studentDao;
	
	
	
	@Autowired
	public void setStudentDao(StudentDao studentDao) {
		this.studentDao = studentDao;
	}
	public List<Student> getCurrent() 
	{
		return studentDao.getStudents();
	}
	public Student getStudent(int id)
	{
		return studentDao.getStudent(id);
	}
    
}
