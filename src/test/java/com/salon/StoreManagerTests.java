package com.salon;

import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest @AutoConfigureMockMvc @Transactional
class StoreManagerTests {
 @Autowired MockMvc mvc; @Autowired JdbcTemplate db; @Autowired PasswordEncoder encoder;
 UUID tenant,manager,store,otherStore,foreignStore,managerRole;
 @BeforeEach void setup(){
  tenant=UUID.randomUUID();manager=UUID.randomUUID();store=UUID.randomUUID();otherStore=UUID.randomUUID();foreignStore=UUID.randomUUID();managerRole=UUID.randomUUID();UUID foreignTenant=UUID.randomUUID();
  db.update("INSERT INTO tenant(id,tenant_name) VALUES (?,'Our tenant'),(?,'Foreign tenant')",tenant,foreignTenant);
  db.update("INSERT INTO store(id,name,tenant_id) VALUES (?,'Assigned store',?),(?,'Unassigned store',?),(?,'Foreign store',?)",store,tenant,otherStore,tenant,foreignStore,foreignTenant);
  db.update("INSERT INTO users(id,name,email,mobileno,password_hash) VALUES (?,'Manager','manager-test@example.test','9000000051',?)",manager,encoder.encode("manager-test-password"));
  db.update("INSERT INTO role(id,roletype) VALUES (?,'STORE_MANAGER')",managerRole);
  db.update("INSERT INTO user_role(id,user_id,role_id,store_id,tenant_id) VALUES (?,?,?,?,?)",UUID.randomUUID(),manager,managerRole,store,tenant);
 }
 String body(String category){return "{\"name\":\"Staff One\",\"email\":\""+category+"@example.test\",\"mobileNo\":\""+(category.equals("experts")?"9000000052":"9000000053")+"\",\"address\":\"Noida\",\"password\":\"staff-test-password\"}";}
 String path(String category,UUID selected){return "/api/manager/"+category+"?storeId="+selected;}
 UUID assignment(String category){return db.queryForObject("SELECT ur.id FROM user_role ur JOIN users u ON u.id=ur.user_id WHERE u.email=?",UUID.class,category+"@example.test");}
 @Test void expertCanBeCreatedAndEditedWithoutPasswordButReceptionistRequiresOne() throws Exception {
  var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
  var expert=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree(body("experts"));expert.remove("password");
  mvc.perform(post(path("experts",store)).with(user(manager.toString()).roles("STORE_MANAGER")).with(csrf()).contentType("application/json").content(expert.toString())).andExpect(status().isCreated());
  UUID id=assignment("experts");
  assertThat(db.queryForObject("SELECT password_hash FROM users WHERE email='experts@example.test'",String.class)).isNull();
  expert.put("name","Expert Updated");
  mvc.perform(put("/api/manager/experts/"+id+"?storeId="+store).with(user(manager.toString()).roles("STORE_MANAGER")).with(csrf()).contentType("application/json").content(expert.toString())).andExpect(status().isOk());
  mvc.perform(post("/login").with(csrf()).param("mobileNo","9000000052").param("password","any-password")).andExpect(redirectedUrl("/login?error"));
  var receptionist=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree(body("receptionists"));receptionist.remove("password");
  mvc.perform(post(path("receptionists",store)).with(user(manager.toString()).roles("STORE_MANAGER")).with(csrf()).contentType("application/json").content(receptionist.toString())).andExpect(status().isBadRequest());
 }
 @Test void finalExpertRoleSupportsLoginAssignmentsAndOrderHistory() throws Exception {
  UUID userId=UUID.randomUUID(),roleId=UUID.randomUUID(),assignmentId=UUID.randomUUID(),orderId=UUID.randomUUID();
  String hash=encoder.encode("expert-test-password");
  db.update("INSERT INTO users(id,name,mobileno,password_hash) VALUES (?,'Existing expert','9000000097',?)",userId,hash);
  db.update("INSERT INTO role(id,roletype) VALUES (?,'EXPERT')",roleId);
  db.update("INSERT INTO user_role(id,user_id,role_id,store_id,tenant_id) VALUES (?,?,?,?,?)",assignmentId,userId,roleId,store,tenant);
  db.update("INSERT INTO orders(id,tenant_id,store_id,assignee,order_type,order_status,customer_name,customer_mobile,details,service,amount,appointment_at) VALUES (?,?,?,?,'APPOINTMENT','ASSIGNED','Customer','9000000091','','Hair',100,CURRENT_TIMESTAMP)",orderId,tenant,store,assignmentId);
  assertThat(db.queryForObject("SELECT roletype FROM role WHERE id=?",String.class,roleId)).isEqualTo("EXPERT");
  assertThat(db.queryForObject("SELECT role_id FROM user_role WHERE id=?",UUID.class,assignmentId)).isEqualTo(roleId);
  assertThat(db.queryForObject("SELECT password_hash FROM users WHERE id=?",String.class,userId)).isEqualTo(hash);
  assertThat(db.queryForObject("SELECT assignee FROM orders WHERE id=?",UUID.class,orderId)).isEqualTo(assignmentId);
  assertThat(db.queryForObject("SELECT assignee FROM order_history WHERE order_id=?",UUID.class,orderId)).isEqualTo(assignmentId);
  var login=mvc.perform(post("/login").with(csrf()).param("mobileNo","9000000097").param("password","expert-test-password")).andExpect(redirectedUrl("/workspace")).andReturn();
  var context=(org.springframework.security.core.context.SecurityContext)login.getRequest().getSession(false).getAttribute("SPRING_SECURITY_CONTEXT");
  assertThat(context.getAuthentication().getAuthorities().stream().map(a->a.getAuthority())).contains("ROLE_EXPERT").doesNotContain("ROLE_WORKER");
  mvc.perform(get(path("experts",store)).with(user(manager.toString()).roles("STORE_MANAGER"))).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(assignmentId.toString()));
 }
 String appointmentBody="{\"orderType\":\"APPOINTMENT\",\"customerName\":\"Customer\",\"customerMobile\":\"9000000091\",\"service\":\"Hair, Nail\",\"amount\":500,\"appointmentAt\":\"2026-10-18T12:00\"}";
 String appointmentPath(UUID id,String action){return "/api/manager/appointments/orders/"+id+"/"+action+"?storeId="+store;}
 @Test void managerCanCreateAssignCheckoutAndMarkAppointmentPaid() throws Exception {
  mvc.perform(get("/manager/appointments").with(user(manager.toString()).roles("STORE_MANAGER"))).andExpect(status().isOk()).andExpect(content().string(containsString("data-workflow=\"manager\"")));
  mvc.perform(get("/api/manager/appointments/session").with(user(manager.toString()).roles("STORE_MANAGER"))).andExpect(jsonPath("$.stores",hasSize(1)));
  mvc.perform(post(path("experts",store)).with(user(manager.toString()).roles("STORE_MANAGER")).with(csrf()).contentType("application/json").content(body("experts"))).andExpect(status().isCreated());
  UUID expert=assignment("experts");
  mvc.perform(get("/api/manager/appointments/experts?storeId="+store).with(user(manager.toString()).roles("STORE_MANAGER"))).andExpect(jsonPath("$[0].id").value(expert.toString()));
  mvc.perform(post("/api/manager/appointments/orders?storeId="+store).with(user(manager.toString()).roles("STORE_MANAGER")).with(csrf()).contentType("application/json").content(appointmentBody)).andExpect(status().isCreated());
  UUID id=db.queryForObject("SELECT id FROM orders WHERE store_id=?",UUID.class,store);
  mvc.perform(get("/api/manager/appointments/orders?storeId="+store).with(user(manager.toString()).roles("STORE_MANAGER"))).andExpect(jsonPath("$.items",hasSize(1)));
  mvc.perform(patch(appointmentPath(id,"checkout")).with(user(manager.toString()).roles("STORE_MANAGER")).with(csrf()).contentType("application/json").content("{\"paymentStatus\":\"PENDING\"}")).andExpect(status().isConflict());
  mvc.perform(patch(appointmentPath(id,"assign")).with(user(manager.toString()).roles("STORE_MANAGER")).with(csrf()).contentType("application/json").content("{\"assignee\":\""+expert+"\"}")).andExpect(status().isOk());
  mvc.perform(patch(appointmentPath(id,"checkout")).with(user(manager.toString()).roles("STORE_MANAGER")).with(csrf()).contentType("application/json").content("{\"paymentStatus\":\"COMPLETED\"}")).andExpect(status().isBadRequest());
  mvc.perform(patch(appointmentPath(id,"checkout")).with(user(manager.toString()).roles("STORE_MANAGER")).with(csrf()).contentType("application/json").content("{\"paymentStatus\":\"PENDING\"}")).andExpect(status().isOk());
  mvc.perform(patch(appointmentPath(id,"payment")).with(user(manager.toString()).roles("STORE_MANAGER")).with(csrf()).contentType("application/json").content("{\"paymentMode\":\"UPI\"}")).andExpect(status().isOk());
  assertThat(db.queryForMap("SELECT payment_status,payment_mode FROM orders WHERE id=?",id)).containsEntry("payment_status","COMPLETED").containsEntry("payment_mode","UPI");
  assertThat(db.queryForObject("SELECT count(*) FROM order_history WHERE order_id=?",Long.class,id)).isEqualTo(4);
  mvc.perform(patch(appointmentPath(id,"payment")).with(user(manager.toString()).roles("STORE_MANAGER")).with(csrf()).contentType("application/json").content("{\"paymentMode\":\"CASH\"}")).andExpect(status().isConflict());
 }
 @Test void managerAppointmentScopeRejectsOtherStoresQueriesWrongRolesAndMissingCsrf() throws Exception {
  for(UUID target:new UUID[]{otherStore,foreignStore}) {
   mvc.perform(get("/api/manager/appointments/orders?storeId="+target).with(user(manager.toString()).roles("STORE_MANAGER"))).andExpect(status().isNotFound());
   mvc.perform(post("/api/manager/appointments/orders?storeId="+target).with(user(manager.toString()).roles("STORE_MANAGER")).with(csrf()).contentType("application/json").content(appointmentBody)).andExpect(status().isNotFound());
  }
  UUID query=UUID.randomUUID();db.update("INSERT INTO orders(id,tenant_id,store_id,order_type,customer_name,customer_mobile,details) VALUES (?,?,?,'QUERY','Customer','9000000091','Question')",query,tenant,store);
  mvc.perform(get("/api/manager/appointments/orders?storeId="+store).with(user(manager.toString()).roles("STORE_MANAGER"))).andExpect(jsonPath("$.items",hasSize(0)));
  mvc.perform(patch(appointmentPath(query,"checkout")).with(user(manager.toString()).roles("STORE_MANAGER")).with(csrf()).contentType("application/json").content("{\"paymentStatus\":\"PENDING\"}")).andExpect(status().isNotFound());
  mvc.perform(post("/api/manager/appointments/orders?storeId="+store).with(user(manager.toString()).roles("STORE_MANAGER")).with(csrf()).contentType("application/json").content(appointmentBody.replace("APPOINTMENT","QUERY"))).andExpect(status().isBadRequest());
  mvc.perform(get("/api/manager/appointments/session")).andExpect(status().isUnauthorized());
  for(String role:new String[]{"ADMIN","RECEPTIONIST","EXPERT"})mvc.perform(get("/api/manager/appointments/session").with(user(manager.toString()).roles(role))).andExpect(status().isForbidden());
  mvc.perform(post("/api/manager/appointments/orders?storeId="+store).with(user(manager.toString()).roles("STORE_MANAGER")).contentType("application/json").content(appointmentBody)).andExpect(status().isForbidden());
  db.update("DELETE FROM user_role WHERE user_id=?",manager);
  mvc.perform(get("/api/manager/appointments/orders?storeId="+store).with(user(manager.toString()).roles("STORE_MANAGER"))).andExpect(status().isNotFound());
 }
 @Test void staffFieldsRequiredAndMobileExactlyTenDigits() throws Exception {
  for(String category:new String[]{"receptionists","experts"}) {
   for(String invalid:new String[]{"","123456789","12345678901","+919000000052","90000abc52"}) {
    var json=(com.fasterxml.jackson.databind.node.ObjectNode)new com.fasterxml.jackson.databind.ObjectMapper().readTree(body(category));json.put("mobileNo",invalid);
    mvc.perform(post(path(category,store)).with(user(manager.toString()).roles("STORE_MANAGER")).with(csrf()).contentType("application/json").content(json.toString())).andExpect(status().isBadRequest());
   }
   for(String field:category.equals("experts")?new String[]{"name","email","mobileNo","address"}:new String[]{"name","email","mobileNo","address","password"}) {
    var json=(com.fasterxml.jackson.databind.node.ObjectNode)new com.fasterxml.jackson.databind.ObjectMapper().readTree(body(category));json.remove(field);
    mvc.perform(post(path(category,store)).with(user(manager.toString()).roles("STORE_MANAGER")).with(csrf()).contentType("application/json").content(json.toString())).andExpect(status().isBadRequest());
   }
  }
 }
 @Test void dashboardOrdersAreStoreScopedAndShowSalesDetails() throws Exception {
  mvc.perform(get(path("orders",store)).with(user(manager.toString()).roles("STORE_MANAGER"))).andExpect(status().isOk()).andExpect(jsonPath("$.items",hasSize(0)));
  mvc.perform(post(path("experts",store)).with(user(manager.toString()).roles("STORE_MANAGER")).with(csrf()).contentType("application/json").content(body("experts"))).andExpect(status().isCreated());
  UUID expert=assignment("experts");
  for(String state:new String[]{"PENDING","ASSIGNED","COMPLETED"}) {
   db.update("INSERT INTO orders(id,tenant_id,store_id,assignee,order_type,order_status,payment_status,customer_name,customer_mobile,details,amount,appointment_at) VALUES (?,?,?,?, 'APPOINTMENT',?,?,?,'9000000060','Haircut',125.50,CURRENT_TIMESTAMP)",UUID.randomUUID(),tenant,store,state.equals("PENDING")?null:expert,state,state.equals("COMPLETED")?"COMPLETED":"PENDING","Customer "+state);
  }
  db.update("INSERT INTO orders(id,tenant_id,store_id,order_type,customer_name,customer_mobile,details,amount,appointment_at) VALUES (?,?,?,'APPOINTMENT','Other store','9000000061','Private service',999,CURRENT_TIMESTAMP)",UUID.randomUUID(),tenant,otherStore);
  db.update("INSERT INTO orders(id,tenant_id,store_id,order_type,customer_name,customer_mobile,details) VALUES (?,?,?,'QUERY','Query customer','9000000062','Question')",UUID.randomUUID(),tenant,store);
  mvc.perform(get(path("orders",store)).with(user(manager.toString()).roles("STORE_MANAGER"))).andExpect(status().isOk()).andExpect(jsonPath("$.items",hasSize(3))).andExpect(jsonPath("$.items[*].service_name",everyItem(nullValue()))).andExpect(jsonPath("$.items[*].amount",everyItem(is(125.50)))).andExpect(jsonPath("$.items[*].order_status",containsInAnyOrder("PENDING","ASSIGNED","COMPLETED"))).andExpect(jsonPath("$.items[*].customer_name",containsInAnyOrder("Customer PENDING","Customer ASSIGNED","Customer COMPLETED")));
  for(UUID target:new UUID[]{otherStore,foreignStore}) mvc.perform(get(path("orders",target)).with(user(manager.toString()).roles("STORE_MANAGER"))).andExpect(status().isNotFound());
  mvc.perform(get(path("orders",store))).andExpect(status().isUnauthorized());
  for(String role:new String[]{"ADMIN","RECEPTIONIST","EXPERT"}) mvc.perform(get(path("orders",store)).with(user(manager.toString()).roles(role))).andExpect(status().isForbidden());
  db.update("DELETE FROM user_role WHERE user_id=?",manager);
  mvc.perform(get(path("orders",store)).with(user(manager.toString()).roles("STORE_MANAGER"))).andExpect(status().isNotFound());
 }
 @Test void managerLandsOnOwnDashboardAndSessionListsOnlyAssignedStores() throws Exception {
  mvc.perform(post("/login").with(csrf()).param("mobileNo","9000000051").param("password","manager-test-password")).andExpect(redirectedUrl("/workspace"));
  mvc.perform(get("/workspace").with(user(manager.toString()).roles("STORE_MANAGER"))).andExpect(redirectedUrl("/manager/dashboard"));
  mvc.perform(get("/manager/dashboard").with(user(manager.toString()).roles("STORE_MANAGER"))).andExpect(status().isOk()).andExpect(content().string(containsString("Manage Receptionist"))).andExpect(content().string(containsString("Manage Expert")));
  mvc.perform(get("/api/manager/session").with(user(manager.toString()).roles("STORE_MANAGER"))).andExpect(jsonPath("$.stores",hasSize(1))).andExpect(jsonPath("$.stores[0].id").value(store.toString()));
 }
 @Test void bothStaffTypesSupportCrudAndMobileLogin() throws Exception {
  for(String category:new String[]{"receptionists","experts"}) {
   mvc.perform(post(path(category,store)).with(user(manager.toString()).roles("STORE_MANAGER")).with(csrf()).contentType("application/json").content(body(category))).andExpect(status().isCreated());
   UUID id=assignment(category);String hash=db.queryForObject("SELECT password_hash FROM users WHERE email=?",String.class,category+"@example.test");assertThat(encoder.matches("staff-test-password",hash)).isTrue();
   mvc.perform(get(path(category,store)).with(user(manager.toString()).roles("STORE_MANAGER"))).andExpect(jsonPath("$",hasSize(1))).andExpect(jsonPath("$[0].name").value("Staff One"));
   mvc.perform(put("/api/manager/"+category+"/"+id+"?storeId="+store).with(user(manager.toString()).roles("STORE_MANAGER")).with(csrf()).contentType("application/json").content(body(category).replace("Staff One","Staff Updated"))).andExpect(status().isOk());
   assertThat(encoder.matches("staff-test-password",db.queryForObject("SELECT password_hash FROM users WHERE email=?",String.class,category+"@example.test"))).isTrue();
   mvc.perform(post("/login").with(csrf()).param("mobileNo",category.equals("experts")?"9000000052":"9000000053").param("password","staff-test-password")).andExpect(redirectedUrl("/workspace"));
   mvc.perform(delete("/api/manager/"+category+"/"+id+"?storeId="+store).with(user(manager.toString()).roles("STORE_MANAGER")).with(csrf())).andExpect(status().isOk());
   assertThat(db.queryForObject("SELECT count(*) FROM user_role WHERE id=?",Long.class,id)).isZero();
  }
 }
 @Test void rejectsCrossStoreCrossTenantAndWrongCategoryIds() throws Exception {
  for(UUID target:new UUID[]{otherStore,foreignStore}) {
   mvc.perform(get(path("experts",target)).with(user(manager.toString()).roles("STORE_MANAGER"))).andExpect(status().isNotFound());
   mvc.perform(post(path("experts",target)).with(user(manager.toString()).roles("STORE_MANAGER")).with(csrf()).contentType("application/json").content(body("experts"))).andExpect(status().isNotFound());
  }
  mvc.perform(post(path("experts",store)).with(user(manager.toString()).roles("STORE_MANAGER")).with(csrf()).contentType("application/json").content(body("experts"))).andExpect(status().isCreated());UUID id=assignment("experts");
  mvc.perform(delete("/api/manager/receptionists/"+id+"?storeId="+store).with(user(manager.toString()).roles("STORE_MANAGER")).with(csrf())).andExpect(status().isNotFound());
  UUID otherManager=UUID.randomUUID();db.update("INSERT INTO users(id,name) VALUES (?,'Other manager')",otherManager);db.update("INSERT INTO user_role(id,user_id,role_id,store_id,tenant_id) VALUES (?,?,?,?,?)",UUID.randomUUID(),otherManager,managerRole,otherStore,tenant);
  mvc.perform(put("/api/manager/experts/"+id+"?storeId="+otherStore).with(user(otherManager.toString()).roles("STORE_MANAGER")).with(csrf()).contentType("application/json").content(body("experts"))).andExpect(status().isNotFound());
  mvc.perform(delete("/api/manager/experts/"+id+"?storeId="+otherStore).with(user(otherManager.toString()).roles("STORE_MANAGER")).with(csrf())).andExpect(status().isNotFound());
 }
 @Test void rejectsWrongRolesMissingCsrfAndDuplicateMobile() throws Exception {
  mvc.perform(get(path("experts",store))).andExpect(status().isUnauthorized());
  for(String role:new String[]{"ADMIN","RECEPTIONIST","EXPERT"}) {
   mvc.perform(get("/manager/dashboard").with(user(manager.toString()).roles(role))).andExpect(status().isForbidden());
   mvc.perform(post(path("experts",store)).with(user(manager.toString()).roles(role)).with(csrf()).contentType("application/json").content(body("experts"))).andExpect(status().isForbidden());
  }
  mvc.perform(post(path("experts",store)).with(user(manager.toString()).roles("STORE_MANAGER")).contentType("application/json").content(body("experts"))).andExpect(status().isForbidden());
  mvc.perform(post(path("experts",store)).with(user(manager.toString()).roles("STORE_MANAGER")).with(csrf()).contentType("application/json").content(body("experts").replace("9000000052","9000000051"))).andExpect(status().isConflict());
  mvc.perform(post(path("experts",store)).with(user(manager.toString()).roles("STORE_MANAGER")).with(csrf()).contentType("application/json").content(body("experts").replace("staff-test-password","short"))).andExpect(status().isBadRequest());
  db.update("DELETE FROM user_role WHERE user_id=?",manager);
  mvc.perform(get(path("experts",store)).with(user(manager.toString()).roles("STORE_MANAGER"))).andExpect(status().isNotFound());
 }
}
