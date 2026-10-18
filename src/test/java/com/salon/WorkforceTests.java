package com.salon;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import java.time.*;
@SpringBootTest @AutoConfigureMockMvc
@Disabled("Replaced workforce schema; retained as legacy behavior reference")
class WorkforceTests {
 @Autowired MockMvc mvc; @Autowired JdbcTemplate db;
 long id; String day;
 @BeforeEach void setup(){db.update("DELETE FROM appointments");db.update("DELETE FROM leaves");db.update("DELETE FROM queries");db.update("DELETE FROM employees");db.update("INSERT INTO employees(name,specialty,phone,shift_start,shift_end,working_days,active) VALUES ('Maya','Hair','123','09:00','18:00','1,2,3,4,5,6,7',true)");id=db.queryForObject("SELECT id FROM employees",Long.class);day=LocalDate.now().plusDays(2).toString();}
 String booking(String start,String end){return "{\"employeeId\":"+id+",\"customer\":\"Alex\",\"phone\":\"123\",\"service\":\"Haircut\",\"startsAt\":\""+day+"T"+start+"\",\"endsAt\":\""+day+"T"+end+"\",\"notes\":\"\"}";}
 @Test void bookingConflictsAndAdjacentSlots() throws Exception {
  mvc.perform(post("/api/appointments").with(user("reception").roles("RECEPTION")).with(csrf()).contentType("application/json").content(booking("10:00","11:00"))).andExpect(status().isOk());
  mvc.perform(post("/api/appointments").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content(booking("10:30","11:30"))).andExpect(status().isConflict());
  mvc.perform(post("/api/appointments").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content(booking("11:00","12:00"))).andExpect(status().isOk());
 }
 @Test void leaveAndShiftPreventBooking() throws Exception {
  db.update("INSERT INTO leaves(employee_id,starts_on,ends_on,reason) VALUES (?,?,?,?)",id,LocalDate.parse(day),LocalDate.parse(day),"Off");
  mvc.perform(post("/api/appointments").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content(booking("10:00","11:00"))).andExpect(status().isConflict());
  db.update("DELETE FROM leaves");
  mvc.perform(post("/api/appointments").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content(booking("08:00","09:00"))).andExpect(status().isConflict());
 }
 @Test void authorizationAndCsrf() throws Exception {
  mvc.perform(post("/api/employees").with(user("reception").roles("RECEPTION")).with(csrf()).contentType("application/json").content("{}")).andExpect(status().isForbidden());
  mvc.perform(post("/api/queries").with(user("admin").roles("ADMIN")).contentType("application/json").content("{}")).andExpect(status().isForbidden());
  mvc.perform(get("/api/employees")).andExpect(status().is3xxRedirection());
 }
 @Test void queriesAndValidation() throws Exception {
  mvc.perform(post("/api/queries").with(user("reception").roles("RECEPTION")).with(csrf()).contentType("application/json").content("{\"customer\":\"Alex\",\"phone\":\"123\",\"message\":\"Call me\"}")).andExpect(status().isOk());
  mvc.perform(get("/api/queries").with(user("admin").roles("ADMIN"))).andExpect(jsonPath("$[0].message").value("Call me"));
  mvc.perform(post("/api/appointments").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content("{}")).andExpect(status().isBadRequest());
 }
 @Test void leaveCannotDisplaceBooking() throws Exception {
  mvc.perform(post("/api/appointments").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content(booking("10:00","11:00"))).andExpect(status().isOk());
  mvc.perform(post("/api/leaves").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content("{\"employeeId\":"+id+",\"startsOn\":\""+day+"\",\"endsOn\":\""+day+"\",\"reason\":\"Holiday\"}")).andExpect(status().isConflict());
 }
 @Test void canonicalRoutes() throws Exception {
  mvc.perform(get("/").with(user("admin").roles("ADMIN"))).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/dashboard"));
  mvc.perform(get("/index.html?_ijt=preview&_ij_reload=RELOAD_ON_SAVE").with(user("admin").roles("ADMIN"))).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/dashboard"));
  mvc.perform(get("/css/style.css")).andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("text/css"));
  mvc.perform(get("/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
 }}

