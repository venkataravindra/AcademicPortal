package com.academicportal.web.model;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class Student{

	private int id;
	private String name;
	private String email;
	private int age ;
	private String text="hello";
	
	public Student() {
		super();
	}
	public Student(int id, String name, String email, int age, String text) {
		super();
		this.id = id;
		this.name = name;
		this.email = email;
		this.age = age;
		this.text = text;
	}
	
	
	public Student(int id, String name, String email, int age) {
		super();
		this.id = id;
		this.name = name;
		this.email = email;
		this.age = age;
	}
	
	public Student(String name, String email, int age, String text) {
		super();
		this.name = name;
		this.email = email;
		this.age = age;
		this.text = text;
	}
	public int getId() {
		return id;
	}
	public void setId(int id) {
		this.id = id;
	}
	
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getEmail() {
		return email;
	}
	public void setEmail(String email) {
		this.email = email;
	}
	public int getAge() {
		return age;
	}
	public void setAge(int age) {
		this.age = age;
	}
	public String getText() {
		return text;
	}
	public void setText(String text) {
		this.text = text;
	}
	public void speak() {
		// TODO Auto-generated method stub
		System.out.println("Student id:"+id+":student says "+text);
		
	}
	@Override
	public String toString() {
		return "Student [ id=" + id + ", name=" + name + ", email=" + email
				+ ", age=" + age + ", text=" + text + "]";
	}
	

	
	 
}
