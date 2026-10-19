package com.salon;
import java.util.*;
import java.time.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest @AutoConfigureMockMvc @Transactional
class OrderPaginationTests {
 @Autowired MockMvc mvc;@Autowired JdbcTemplate db;@Autowired PasswordEncoder passwords;
 UUID tenant,store,admin,manager,reception;
 @BeforeEach void setup(){tenant=UUID.randomUUID();store=UUID.randomUUID();db.update("INSERT INTO tenant(id,tenant_name) VALUES (?,'Pagination tenant')",tenant);db.update("INSERT INTO store(id,name,tenant_id) VALUES (?,'Store',?)",store,tenant);admin=staff("ADMIN",false);manager=staff("STORE_MANAGER",true);reception=staff("RECEPTIONIST",true);}
 UUID staff(String type,boolean assigned){UUID user=UUID.randomUUID(),role=UUID.randomUUID();db.update("INSERT INTO users(id,name) VALUES (?,'User')",user);db.update("INSERT INTO role(id,roletype) VALUES (?,?)",role,type);db.update("INSERT INTO user_role(id,user_id,role_id,store_id,tenant_id) VALUES (?,?,?,?,?)",UUID.randomUUID(),user,role,assigned?store:null,tenant);return user;}
 UUID order(String name,String type){UUID id=UUID.randomUUID();db.update("INSERT INTO orders(id,tenant_id,store_id,order_type,customer_name,customer_mobile,details,amount,appointment_at) VALUES (?,?,?,?,?,'9876543210','Details',?,?)",id,tenant,store,type,name,type.equals("QUERY")?0:10,type.equals("QUERY")?null:LocalDate.now(ZoneId.of("Asia/Kolkata")).atTime(12,0));return id;}
 @Test void paymentCompletionTimestampIsCapturedAndPreservedInHistory(){
  UUID id=order("Payment audit","APPOINTMENT");
  assertThat(db.queryForObject("SELECT payment_completed_at FROM orders WHERE id=?",java.time.OffsetDateTime.class,id)).isNull();
  var before=java.time.OffsetDateTime.now().minusSeconds(1);
  UUID expert=staff("EXPERT",true);UUID assignment=db.queryForObject("SELECT id FROM user_role WHERE user_id=?",UUID.class,expert);
  db.update("UPDATE orders SET assignee=?,order_status='COMPLETED',payment_status='COMPLETED',payment_mode='CASH' WHERE id=?",assignment,id);
  var paid=db.queryForObject("SELECT payment_completed_at FROM orders WHERE id=?",java.time.OffsetDateTime.class,id);
  assertThat(paid).isAfter(before);
  assertThat(db.queryForObject("SELECT count(*) FROM order_history WHERE order_id=? AND payment_completed_at=?",Long.class,id,paid)).isEqualTo(1);
  db.update("UPDATE orders SET details='Corrected details' WHERE id=?",id);
  assertThat(db.queryForObject("SELECT payment_completed_at FROM orders WHERE id=?",java.time.OffsetDateTime.class,id)).isEqualTo(paid);
  assertThat(db.queryForObject("SELECT count(*) FROM order_history WHERE order_id=? AND payment_completed_at=?",Long.class,id,paid)).isEqualTo(2);
 }
 String managerUrl(){return "/api/manager/orders?storeId="+store;}
 @Test void managerSalesDefaultToTodayAndSupportValidatedRanges()throws Exception{
  LocalDate today=LocalDate.now(ZoneId.of("Asia/Kolkata"));order("Today","APPOINTMENT");UUID yesterday=order("Yesterday","APPOINTMENT");db.update("UPDATE orders SET appointment_at=? WHERE id=?",today.minusDays(1).atTime(12,0),yesterday);
  String url="/api/manager/sales?storeId="+store;
  mvc.perform(get(url).with(user(manager.toString()).roles("STORE_MANAGER"))).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
  mvc.perform(get(url+"&from="+today.minusDays(29)+"&to="+today).with(user(manager.toString()).roles("STORE_MANAGER"))).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2));
  mvc.perform(get(url+"&from="+today.minusDays(30)+"&to="+today).with(user(manager.toString()).roles("STORE_MANAGER"))).andExpect(status().isBadRequest());
  mvc.perform(get("/api/manager/sales?storeId="+UUID.randomUUID()).with(user(manager.toString()).roles("STORE_MANAGER"))).andExpect(status().isNotFound());
 }
 @Test void signedInUsersVisitingLoginReturnToTheirWorkspace()throws Exception{
  mvc.perform(get("/login")).andExpect(status().isOk());
  for(String role:new String[]{"ADMIN","STORE_MANAGER","RECEPTIONIST","ROOT"})mvc.perform(get("/login").with(user(manager.toString()).roles(role))).andExpect(redirectedUrl("/workspace"));
  mvc.perform(get("/workspace").with(user(manager.toString()).roles("STORE_MANAGER"))).andExpect(redirectedUrl("/manager/dashboard"));
 }
 @Test void pagesContainAtMost20AndSummaryCoversAllMatches()throws Exception{for(int i=0;i<25;i++)order("A"+i,"APPOINTMENT");for(int i=0;i<5;i++)order("Q"+i,"QUERY");
  mvc.perform(get(managerUrl()).with(user(manager.toString()).roles("STORE_MANAGER"))).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(20)).andExpect(jsonPath("$.total").value(25)).andExpect(jsonPath("$.pageSize").value(20)).andExpect(jsonPath("$.summary.total").value(25)).andExpect(jsonPath("$.summary.unpaid").value(250));
  mvc.perform(get(managerUrl()+"&page=1&size=100000").with(user(manager.toString()).roles("STORE_MANAGER"))).andExpect(jsonPath("$.items.length()").value(5)).andExpect(jsonPath("$.totalPages").value(2)).andExpect(jsonPath("$.pageSize").value(20));
  mvc.perform(get("/api/reception/orders?storeId="+store+"&type=QUERY").with(user(reception.toString()).roles("RECEPTIONIST"))).andExpect(jsonPath("$.items.length()").value(5)).andExpect(jsonPath("$.total").value(5));
 }
 @Test void modificationMovesOrderToFirstPageAndIsAudited()throws Exception{UUID oldest=order("Old","APPOINTMENT");for(int i=0;i<22;i++)order("New"+i,"APPOINTMENT");var before=db.queryForObject("SELECT modified_at FROM orders WHERE id=?",java.time.OffsetDateTime.class,oldest);db.update("UPDATE orders SET details='Updated' WHERE id=?",oldest);
  mvc.perform(get(managerUrl()).with(user(manager.toString()).roles("STORE_MANAGER"))).andExpect(jsonPath("$.items[0].id").value(oldest.toString()));assertThat(db.queryForObject("SELECT modified_at FROM orders WHERE id=?",java.time.OffsetDateTime.class,oldest)).isAfter(before);assertThat(db.queryForObject("SELECT count(*) FROM order_history WHERE order_id=? AND modified_at IS NOT NULL",Long.class,oldest)).isEqualTo(2);
  assertThat(db.queryForObject("SELECT count(*) FROM information_schema.indexes WHERE index_name='orders_created_at_idx'",Long.class)).isEqualTo(1);
 }
 @Test void filteringHappensBeforePagingAndInvalidFiltersFail()throws Exception{for(int i=0;i<24;i++)order("A"+i,"APPOINTMENT");UUID expert=staff("EXPERT",true),assignment=db.queryForObject("SELECT id FROM user_role WHERE user_id=?",UUID.class,expert);UUID paid=order("Paid","APPOINTMENT");db.update("UPDATE orders SET assignee=?,order_status='COMPLETED',payment_status='COMPLETED' WHERE id=?",assignment,paid);
  mvc.perform(get(managerUrl()+"&view=sales&expert="+assignment+"&status=COMPLETED&payment=COMPLETED").with(user(manager.toString()).roles("STORE_MANAGER"))).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.summary.sales").value(10)).andExpect(jsonPath("$.items[0].id").value(paid.toString()));
  for(String bad:new String[]{"&page=-1","&status=bad","&view=bad","&expert=bad"})mvc.perform(get(managerUrl()+bad).with(user(manager.toString()).roles("STORE_MANAGER"))).andExpect(status().isBadRequest());
  mvc.perform(get("/api/manager/orders?storeId="+UUID.randomUUID()).with(user(manager.toString()).roles("STORE_MANAGER"))).andExpect(status().isNotFound());
 }
 @Test void adminSalesArePagedWithFullReportTotals()throws Exception{for(int i=0;i<25;i++)order("A"+i,"APPOINTMENT");mvc.perform(get("/api/admin/sales?page=1").with(user(admin.toString()).roles("ADMIN"))).andExpect(status().isOk()).andExpect(jsonPath("$.orders.length()").value(5)).andExpect(jsonPath("$.total").value(25)).andExpect(jsonPath("$.summary.total").value(25));}
 @Test void subscriptionIsCapturedAtLoginAndReturnedInSessionWithoutRefreshingIt()throws Exception{db.update("UPDATE users SET mobileno='9787654320',password_hash=? WHERE id=?",passwords.encode("subscription-login-password"),admin);db.update("UPDATE tenant SET subscription_expires_at=DATEADD('HOUR',12,CURRENT_TIMESTAMP) WHERE id=?",tenant);var result=mvc.perform(post("/login").with(csrf()).param("mobileNo","9787654320").param("password","subscription-login-password")).andExpect(redirectedUrl("/workspace")).andReturn();var session=(MockHttpSession)result.getRequest().getSession(false);Object snapshot=session.getAttribute("subscriptionSnapshot");assertThat(snapshot).isNotNull();db.update("UPDATE tenant SET subscription_expires_at=DATEADD('DAY',2,CURRENT_TIMESTAMP) WHERE id=?",tenant);mvc.perform(get("/api/admin/session").session(session)).andExpect(jsonPath("$.subscriptions[0].tenant_name").value("Pagination tenant"));assertThat(session.getAttribute("subscriptionSnapshot")).isSameAs(snapshot);}
}
