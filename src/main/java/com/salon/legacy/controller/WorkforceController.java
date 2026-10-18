package com.salon.legacy.controller;
import org.springframework.context.annotation.Profile;
import java.time.*;
import java.util.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/api")
@Profile("legacy")
public class WorkforceController {
 private final JdbcTemplate db;
 public WorkforceController(JdbcTemplate db){this.db=db;}
 public record Employee(@NotBlank @Size(max=100) String name,@NotBlank @Size(max=100) String specialty,@NotBlank @Size(max=30) String phone,@NotNull LocalTime shiftStart,@NotNull LocalTime shiftEnd,@NotEmpty Set<@Min(1) @Max(7) Integer> workingDays,boolean active){}
 public record Booking(@NotNull Long employeeId,@NotBlank @Size(max=100) String customer,@NotBlank @Size(max=30) String phone,@NotBlank @Size(max=100) String service,@NotNull LocalDateTime startsAt,@NotNull LocalDateTime endsAt,@NotNull @Size(max=1000) String notes){}
 public record Leave(@NotNull Long employeeId,@NotNull LocalDate startsOn,@NotNull LocalDate endsOn,@NotBlank @Size(max=500) String reason){}
 public record Query(@NotBlank @Size(max=100) String customer,@NotBlank @Size(max=30) String phone,@NotBlank @Size(max=2000) String message){}
 public record Status(@NotBlank String status){}
 private void check(boolean ok,String message){if(!ok)throw new ResponseStatusException(HttpStatus.CONFLICT,message);}
 private Map<String,Object> employee(long id,boolean lock){var rows=db.queryForList("SELECT * FROM employees WHERE id=?"+(lock?" FOR UPDATE":""),id);if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Employee not found");return rows.get(0);}
 @GetMapping("/session") Object session(Authentication auth,CsrfToken csrf){return Map.of("username",auth.getName(),"admin",auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_ADMIN")),"csrf",csrf.getToken(),"csrfHeader",csrf.getHeaderName());}
 @GetMapping("/employees") Object employees(){return db.queryForList("SELECT * FROM employees ORDER BY name");}
 @PostMapping("/employees") void createEmployee(@Valid @RequestBody Employee e){validateShift(e);db.update("INSERT INTO employees(name,specialty,phone,shift_start,shift_end,working_days,active) VALUES (?,?,?,?,?,?,?)",e.name,e.specialty,e.phone,e.shiftStart,e.shiftEnd,days(e),e.active);}
 private String days(Employee e){return String.join(",",e.workingDays.stream().sorted().map(String::valueOf).toList());}
 private void validateShift(Employee e){check(e.shiftEnd.isAfter(e.shiftStart),"Shift must end after it starts");}
 @PutMapping("/employees/{id}") @Transactional void updateEmployee(@PathVariable long id,@Valid @RequestBody Employee e){employee(id,true);validateShift(e);var bookings=db.queryForList("SELECT starts_at,ends_at FROM appointments WHERE employee_id=? AND status='BOOKED' AND ends_at>?",id,LocalDateTime.now());for(var b:bookings){var s=((java.sql.Timestamp)b.get("starts_at")).toLocalDateTime();var end=((java.sql.Timestamp)b.get("ends_at")).toLocalDateTime();check(e.active&&e.workingDays.contains(s.getDayOfWeek().getValue())&&!s.toLocalTime().isBefore(e.shiftStart)&&!end.toLocalTime().isAfter(e.shiftEnd),"Cancel future bookings before changing this roster");}db.update("UPDATE employees SET name=?,specialty=?,phone=?,shift_start=?,shift_end=?,working_days=?,active=? WHERE id=?",e.name,e.specialty,e.phone,e.shiftStart,e.shiftEnd,days(e),e.active,id);}
 @GetMapping("/appointments") Object appointments(@RequestParam LocalDate date){return db.queryForList("SELECT a.*, e.name AS employee_name FROM appointments a JOIN employees e ON e.id=a.employee_id WHERE starts_at>=? AND starts_at<? ORDER BY starts_at",date.atStartOfDay(),date.plusDays(1).atStartOfDay());}
 private boolean available(Map<String,Object> e,LocalDateTime start,LocalDateTime end){long id=((Number)e.get("id")).longValue();return Boolean.TRUE.equals(e.get("active"))&&start.toLocalDate().equals(end.toLocalDate())&&Arrays.asList(e.get("working_days").toString().split(",")).contains(""+start.getDayOfWeek().getValue())&&!start.toLocalTime().isBefore(((java.sql.Time)e.get("shift_start")).toLocalTime())&&!end.toLocalTime().isAfter(((java.sql.Time)e.get("shift_end")).toLocalTime())&&db.queryForObject("SELECT count(*) FROM leaves WHERE employee_id=? AND starts_on<=? AND ends_on>=?",Long.class,id,start.toLocalDate(),start.toLocalDate())==0&&db.queryForObject("SELECT count(*) FROM appointments WHERE employee_id=? AND status <> 'CANCELLED' AND starts_at<? AND ends_at>?",Long.class,id,end,start)==0;}
 @GetMapping("/availability") Object availability(@RequestParam LocalDateTime start,@RequestParam LocalDateTime end){check(end.isAfter(start),"End must follow start");return db.queryForList("SELECT * FROM employees ORDER BY name").stream().map(e->{e.put("available",available(e,start,end));return e;}).toList();}
 @PostMapping("/appointments") @Transactional void book(@Valid @RequestBody Booking b){check(b.endsAt.isAfter(b.startsAt)&&b.startsAt.isAfter(LocalDateTime.now()),"Choose a future time with a later end");var e=employee(b.employeeId,true);check(available(e,b.startsAt,b.endsAt),"Employee is unavailable: check shift, working days, leave or another booking");db.update("INSERT INTO appointments(employee_id,customer,phone,service,starts_at,ends_at,notes) VALUES (?,?,?,?,?,?,?)",b.employeeId,b.customer,b.phone,b.service,b.startsAt,b.endsAt,b.notes);}
 @PatchMapping("/appointments/{id}") @Transactional void appointmentStatus(@PathVariable long id,@Valid @RequestBody Status s){check(Set.of("COMPLETED","CANCELLED").contains(s.status),"Invalid appointment status");var rows=db.queryForList("SELECT employee_id FROM appointments WHERE id=?",id);if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND);employee(((Number)rows.get(0).get("employee_id")).longValue(),true);check(db.update("UPDATE appointments SET status=? WHERE id=? AND status='BOOKED'",s.status,id)==1,"Appointment is already closed");}
 @GetMapping("/leaves") Object leaves(){return db.queryForList("SELECT l.*,e.name AS employee_name FROM leaves l JOIN employees e ON e.id=l.employee_id ORDER BY starts_on DESC");}
 @PostMapping("/leaves") @Transactional void leave(@Valid @RequestBody Leave l){employee(l.employeeId,true);check(!l.endsOn.isBefore(l.startsOn),"Invalid leave dates");check(db.queryForObject("SELECT count(*) FROM appointments WHERE employee_id=? AND status='BOOKED' AND starts_at<? AND ends_at>?",Long.class,l.employeeId,l.endsOn.plusDays(1).atStartOfDay(),l.startsOn.atStartOfDay())==0,"Cancel booked appointments before adding leave");check(db.queryForObject("SELECT count(*) FROM leaves WHERE employee_id=? AND starts_on<=? AND ends_on>=?",Long.class,l.employeeId,l.endsOn,l.startsOn)==0,"Leave overlaps existing leave");db.update("INSERT INTO leaves(employee_id,starts_on,ends_on,reason) VALUES (?,?,?,?)",l.employeeId,l.startsOn,l.endsOn,l.reason);}
 @DeleteMapping("/leaves/{id}") void deleteLeave(@PathVariable long id){if(db.update("DELETE FROM leaves WHERE id=?",id)==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND);}
 @GetMapping("/queries") Object queries(){return db.queryForList("SELECT * FROM queries ORDER BY created_at DESC");}
 @PostMapping("/queries") void query(@Valid @RequestBody Query q){db.update("INSERT INTO queries(customer,phone,message) VALUES (?,?,?)",q.customer,q.phone,q.message);}
 @PatchMapping("/queries/{id}") void queryStatus(@PathVariable long id,@Valid @RequestBody Status s){check(Set.of("OPEN","IN_PROGRESS","RESOLVED").contains(s.status),"Invalid query status");if(db.update("UPDATE queries SET status=? WHERE id=?",s.status,id)==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND);}
}

