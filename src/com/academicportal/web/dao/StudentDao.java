package com.academicportal.web.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.RowMapperResultSetExtractor;
import org.springframework.jdbc.core.namedparam.BeanPropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

import com.academicportal.web.model.Student;


@Component("studentDao")
public class StudentDao {
	private NamedParameterJdbcTemplate jdbc;
	
	private final String INSERT_SQL = "INSERT INTO student(id,name,email,age,text) values(:id,:name,:email,:age,:text)";
	
	@Autowired
	public void setDataSource(DataSource jdbc) {
        this.jdbc = new NamedParameterJdbcTemplate(jdbc);
	
	}
 
	public List<Student> getStudents()
	{
	   return jdbc.query("select *from student", new RowMapper<Student>() {

		@Override
		public Student mapRow(ResultSet rs, int rowNum) throws SQLException {
			Student student = new Student();
			student.setId(rs.getInt("id"));
			student.setName(rs.getString("name"));
			student.setEmail(rs.getString("email"));
			student.setAge(rs.getInt("age"));
			student.setText(rs.getString("text"));
			
			
			return student;
		}
	});
	}
	

	public Student getStudent(int id)
	{
		MapSqlParameterSource params = new MapSqlParameterSource();
		params.addValue("id", id);
	   return jdbc.queryForObject("select *from student where id=:id", params, new RowMapper<Student>() {

		@Override
		public Student mapRow(ResultSet rs, int rowNum) throws SQLException {
			Student student = new Student();
			student.setId(rs.getInt("id"));
			student.setName(rs.getString("name"));
			student.setEmail(rs.getString("email"));
			student.setAge(rs.getInt("age"));
			student.setText(rs.getString("text"));
			
			
			return student;
		}
	});
			   
	
	}
	public int createStudent(Student student) {
		String sql ="INSERT INTO student(name,email,age,text) values(:name,:email,:age,:text)";
		/*Map<String, Object> paramMap = new HashMap<String, Object>();
		paramMap.put("id", student.getId());
		paramMap.put("name", student.getName());
		paramMap.put("email", student.getEmail());
		paramMap.put("age", student.getAge());
		paramMap.put("text", student.getText());
         return jdbc.update("INSERT INTO student(id,name,email,age,text) values(:id,:name,:email,:age,:text)", paramMap); 
		*/
         BeanPropertySqlParameterSource params = new BeanPropertySqlParameterSource(student);
         
         return jdbc.update(INSERT_SQL, params);
         
		/*SqlParameterSource parameters = new MapSqlParameterSource()
				.addValue("id", student.getId())
				.addValue("name", student.getName())
				.addValue("email", student.getEmail())
				.addValue("age", student.getAge())
				.addValue("text", student.getText());
		namedParameterJdbcTemplate.update(INSERT_SQL, parameters);*/
		
		
	
		
		
	}
	public int studentCreation(Student student) {
		String sql ="INSERT INTO student(name,email,age,text) values(:name,:email,:age,:text)";
		 BeanPropertySqlParameterSource params = new BeanPropertySqlParameterSource(student);
         return jdbc.update(INSERT_SQL, params);
        }
	
	
	public int updateStudent(Student student) {
		String sql ="update student set name=:name,age=:age,email=:email where id=:id";
		 BeanPropertySqlParameterSource params = new BeanPropertySqlParameterSource(student);
         
        return jdbc.update(sql, params);
	}
	
	public int deleteStudent(int id) {
		String sql ="delete from student where id=:id";
		MapSqlParameterSource params = new MapSqlParameterSource();
		params.addValue("id", id);
        return jdbc.update(sql, params);
	}
	


}
