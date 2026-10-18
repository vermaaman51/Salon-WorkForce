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
class AdminManagementTests {
 @Autowired MockMvc mvc; @Autowired JdbcTemplate db; @Autowired PasswordEncoder encoder;
 UUID tenant,otherTenant,admin,store,foreignStore;
 @BeforeEach void setup(){
  tenant=UUID.randomUUID();otherTenant=UUID.randomUUID();admin=UUID.randomUUID();store=UUID.randomUUID();foreignStore=UUID.randomUUID();UUID role=UUID.randomUUID();
  db.update("INSERT INTO tenant(id,tenant_name,username) VALUES (?,'Own organization','admin.test')",tenant);
  db.update("INSERT INTO tenant(id,tenant_name,username) VALUES (?,'Other organization','other.test')",otherTenant);
  db.update("INSERT INTO users(id,name,email,mobileno,password_hash) VALUES (?,'Admin owner','admin@example.test','9000000001',?)",admin,encoder.encode("admin-test-password"));
  db.update("INSERT INTO role(id,roletype) VALUES (?,'ADMIN')",role);
  db.update("INSERT INTO user_role(id,user_id,role_id,tenant_id) VALUES (?,?,?,?)",UUID.randomUUID(),admin,role,tenant);
  db.update("INSERT INTO store(id,name,city,address,tenant_id) VALUES (?,'Own store','Noida','Own address',?)",store,tenant);
  db.update("INSERT INTO store(id,name,city,address,tenant_id) VALUES (?,'Foreign store','Delhi','Other address',?)",foreignStore,otherTenant);
 }
 String storeBody="{\"name\":\"Updated store\",\"city\":\"Noida\",\"address\":\"Sector 104\"}";
 @Test void finalAdminRolePreservesLoginAndRejectsOldAuthority() throws Exception {
  UUID legacyRole=UUID.randomUUID(),legacyUser=UUID.randomUUID(),assignment=UUID.randomUUID();
  String hash=encoder.encode("migrated-admin-password");
  db.update("INSERT INTO users(id,name,mobileno,password_hash) VALUES (?,'Existing owner','9000000098',?)",legacyUser,hash);
  db.update("INSERT INTO role(id,roletype) VALUES (?,'ADMIN')",legacyRole);
  db.update("INSERT INTO user_role(id,user_id,role_id,tenant_id) VALUES (?,?,?,?)",assignment,legacyUser,legacyRole,tenant);
  assertThat(db.queryForObject("SELECT roletype FROM role WHERE id=?",String.class,legacyRole)).isEqualTo("ADMIN");
  assertThat(db.queryForObject("SELECT role_id FROM user_role WHERE id=?",UUID.class,assignment)).isEqualTo(legacyRole);
  assertThat(db.queryForObject("SELECT password_hash FROM users WHERE id=?",String.class,legacyUser)).isEqualTo(hash);
  var login=mvc.perform(post("/login").with(csrf()).param("mobileNo","9000000098").param("password","migrated-admin-password")).andExpect(redirectedUrl("/workspace")).andReturn();
  var session=(org.springframework.mock.web.MockHttpSession)login.getRequest().getSession(false);
  mvc.perform(get("/api/admin/session").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.tenant_id").value(tenant.toString()));
  mvc.perform(get("/dashboard").session(session)).andExpect(status().isOk()).andExpect(content().string(containsString("Admin workspace"))).andExpect(content().string(not(containsString("ROOT"))));
  mvc.perform(get("/api/admin/session").with(user(legacyUser.toString()).roles("ROOT"))).andExpect(status().isForbidden());
  mvc.perform(get("/api/root/session").with(user(legacyUser.toString()).roles("ADMIN"))).andExpect(status().isForbidden());
 }
 @Test void salesReportSupportsThirtyDayRangeAndTenantScopedStoreFilter() throws Exception {
  var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
  var initial=mapper.readTree(mvc.perform(get("/api/admin/sales").with(user(admin.toString()).roles("ADMIN"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
  var today=java.time.LocalDate.parse(initial.get("today").asText());
  assertThat(initial.get("date").asText()).isEqualTo(today.toString());
  assertThat(initial.get("from").asText()).isEqualTo(today.toString());
  assertThat(initial.get("to").asText()).isEqualTo(today.toString());
  assertThat(initial.get("minDate").asText()).isEqualTo(today.minusMonths(1).toString());
  UUID secondStore=UUID.randomUUID();db.update("INSERT INTO store(id,name,tenant_id) VALUES (?,'Second store',?)",secondStore,tenant);
  for(UUID selected:new UUID[]{store,secondStore})for(var date:new java.time.LocalDate[]{today,today.minusDays(1),today.minusDays(29)}) {
   db.update("INSERT INTO orders(id,tenant_id,store_id,order_type,customer_name,customer_mobile,details,service,amount,appointment_at) VALUES (?,?,?,'APPOINTMENT',?,'9000000077','Details','Hair',100,?)",UUID.randomUUID(),tenant,selected,date.toString(),date.atTime(12,0));
  }
  db.update("INSERT INTO orders(id,tenant_id,store_id,order_type,customer_name,customer_mobile,details,amount,appointment_at) VALUES (?,?,?,'APPOINTMENT','Private customer','9000000078','Private',999,?)",UUID.randomUUID(),otherTenant,foreignStore,today.minusDays(1).atTime(12,0));
  mvc.perform(get("/api/admin/sales").with(user(admin.toString()).roles("ADMIN"))).andExpect(jsonPath("$.orders",hasSize(2))).andExpect(jsonPath("$.date").value(today.toString()));
  for(var date:new java.time.LocalDate[]{today.minusDays(1),today.minusDays(29)}) {
   mvc.perform(get("/api/admin/sales?date="+date).with(user(admin.toString()).roles("ADMIN"))).andExpect(status().isOk()).andExpect(jsonPath("$.orders",hasSize(2))).andExpect(jsonPath("$.date").value(date.toString()));
   mvc.perform(get("/api/admin/sales?date="+date+"&storeId="+secondStore).with(user(admin.toString()).roles("ADMIN"))).andExpect(status().isOk()).andExpect(jsonPath("$.orders",hasSize(1))).andExpect(jsonPath("$.orders[0].store_id").value(secondStore.toString()));
  }
  for(String date:new String[]{today.plusDays(1).toString(),"invalid","2026-02-30"}) mvc.perform(get("/api/admin/sales?date="+date).with(user(admin.toString()).roles("ADMIN"))).andExpect(status().isBadRequest());
  mvc.perform(get("/api/admin/sales?from="+today.minusDays(29)+"&to="+today).with(user(admin.toString()).roles("ADMIN"))).andExpect(status().isOk()).andExpect(jsonPath("$.orders",hasSize(6)));
  mvc.perform(get("/api/admin/sales?from="+today.minusDays(29)+"&to="+today+"&storeId="+secondStore).with(user(admin.toString()).roles("ADMIN"))).andExpect(status().isOk()).andExpect(jsonPath("$.orders",hasSize(3)));
  mvc.perform(get("/api/admin/sales?from="+today.minusDays(90)+"&to="+today.minusDays(61)).with(user(admin.toString()).roles("ADMIN"))).andExpect(status().isBadRequest());
  mvc.perform(get("/api/admin/sales?from="+today.minusMonths(1)+"&to="+today.minusMonths(1)).with(user(admin.toString()).roles("ADMIN"))).andExpect(status().isOk());
  mvc.perform(get("/api/admin/sales?from="+today.minusMonths(1).minusDays(1)+"&to="+today.minusMonths(1).minusDays(1)).with(user(admin.toString()).roles("ADMIN"))).andExpect(status().isBadRequest());
  mvc.perform(get("/api/admin/sales?date="+today.minusMonths(1).minusDays(1)).with(user(admin.toString()).roles("ADMIN"))).andExpect(status().isBadRequest());
  for(String query:new String[]{"from="+today.minusDays(30)+"&to="+today,"from="+today+"&to="+today.minusDays(1),"from="+today,"to="+today,"from="+today+"&to="+today.plusDays(1),"from=invalid&to="+today,"date="+today+"&from="+today+"&to="+today})mvc.perform(get("/api/admin/sales?"+query).with(user(admin.toString()).roles("ADMIN"))).andExpect(status().isBadRequest());
  for(UUID invalid:new UUID[]{foreignStore,UUID.randomUUID()}) mvc.perform(get("/api/admin/sales?storeId="+invalid).with(user(admin.toString()).roles("ADMIN"))).andExpect(status().isNotFound());
  mvc.perform(get("/api/admin/sales?storeId=invalid").with(user(admin.toString()).roles("ADMIN"))).andExpect(status().isBadRequest());
 }
 @Test void managerFieldsRequiredAndMobileExactlyTenDigits() throws Exception {
  for(String invalid:new String[]{"","123456789","12345678901","+919616543935","96165abc35"}) mvc.perform(post("/api/admin/managers").with(user(admin.toString()).roles("ADMIN")).with(csrf()).contentType("application/json").content(managerBody(store).replace("9616543935",invalid))).andExpect(status().isBadRequest());
  for(String field:new String[]{"name","email","mobileNo","address","storeId","password"}) {
   var json=new com.fasterxml.jackson.databind.ObjectMapper().readTree(managerBody(store));((com.fasterxml.jackson.databind.node.ObjectNode)json).remove(field);
   mvc.perform(post("/api/admin/managers").with(user(admin.toString()).roles("ADMIN")).with(csrf()).contentType("application/json").content(json.toString())).andExpect(status().isBadRequest());
  }
  mvc.perform(post("/api/admin/managers").with(user(admin.toString()).roles("ADMIN")).with(csrf()).contentType("application/json").content(managerBody(store))).andExpect(status().isCreated());
  UUID id=db.queryForObject("SELECT id FROM user_role WHERE user_id=(SELECT id FROM users WHERE email='manager@example.test')",UUID.class);
  mvc.perform(put("/api/admin/managers/"+id).with(user(admin.toString()).roles("ADMIN")).with(csrf()).contentType("application/json").content(managerBody(store).replace("manager-test-password",""))).andExpect(status().isBadRequest());
 }
 @Test void dailySalesUsesAppointmentDayAndTenantScopeWithoutExpertDetails() throws Exception {
  var response=mvc.perform(get("/api/admin/sales").with(user(admin.toString()).roles("ADMIN"))).andExpect(status().isOk()).andExpect(jsonPath("$.orders",hasSize(0))).andReturn().getResponse().getContentAsString();
  var date=java.time.LocalDate.parse(new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).get("date").asText());
  UUID expertRole=UUID.randomUUID(),expert=UUID.randomUUID();
  db.update("INSERT INTO role(id,roletype) VALUES (?,'EXPERT')",expertRole);
  db.update("INSERT INTO user_role(id,user_id,role_id,store_id,tenant_id) VALUES (?,?,?,?,?)",expert,admin,expertRole,store,tenant);
  db.update("INSERT INTO orders(id,tenant_id,store_id,assignee,order_type,order_status,payment_status,customer_name,customer_mobile,details,amount,appointment_at) VALUES (?,?,?,?,'APPOINTMENT','COMPLETED','COMPLETED','Paid customer','9000000070','Haircut',250.50,?)",UUID.randomUUID(),tenant,store,expert,date.atStartOfDay());
  for(var time:new java.time.LocalDateTime[]{date.atStartOfDay().minusNanos(1000),date.plusDays(1).atStartOfDay(),date.atTime(23,59,59)}) {
   db.update("INSERT INTO orders(id,tenant_id,store_id,order_type,customer_name,customer_mobile,details,amount,appointment_at) VALUES (?,?,?,'APPOINTMENT','Pending customer','9000000071','Colour',400,?)",UUID.randomUUID(),tenant,store,time);
  }
  db.update("UPDATE orders SET service='Hair, Nail' WHERE tenant_id=? AND customer_name='Paid customer'",tenant);
  db.update("INSERT INTO orders(id,tenant_id,store_id,order_type,customer_name,customer_mobile,details,amount,appointment_at) VALUES (?,?,?,'APPOINTMENT','Foreign customer','9000000072','Private',999,?)",UUID.randomUUID(),otherTenant,foreignStore,date.atTime(12,0));
  db.update("INSERT INTO orders(id,tenant_id,store_id,order_type,customer_name,customer_mobile,details,created_at) VALUES (?,?,?,'QUERY','Query','9000000073','Question',?)",UUID.randomUUID(),tenant,store,date.atTime(12,0));
  mvc.perform(get("/api/admin/sales").with(user(admin.toString()).roles("ADMIN"))).andExpect(status().isOk()).andExpect(jsonPath("$.orders",hasSize(2))).andExpect(jsonPath("$.orders[*].customer_name",containsInAnyOrder("Paid customer","Pending customer"))).andExpect(jsonPath("$.orders[0].service_name").value(nullValue())).andExpect(jsonPath("$.orders[1].service_name").value("Hair, Nail")).andExpect(jsonPath("$.orders[*].amount",containsInAnyOrder(250.50,400.0))).andExpect(jsonPath("$.orders[0].assignee").doesNotExist()).andExpect(jsonPath("$.orders[0].expert_name").doesNotExist());
  mvc.perform(get("/api/admin/sales")).andExpect(status().isUnauthorized());
  for(String role:new String[]{"STORE_MANAGER","RECEPTIONIST","EXPERT"}) mvc.perform(get("/api/admin/sales").with(user(admin.toString()).roles(role))).andExpect(status().isForbidden());
  db.update("DELETE FROM user_role WHERE user_id=? AND store_id IS NULL",admin);
  mvc.perform(get("/api/admin/sales").with(user(admin.toString()).roles("ADMIN"))).andExpect(status().isForbidden());
 }
 String managerBody(UUID store){return "{\"name\":\"Manager One\",\"email\":\"manager@example.test\",\"mobileNo\":\"9616543935\",\"address\":\"Noida\",\"storeId\":\""+store+"\",\"password\":\"manager-test-password\"}";}
 @Test void rootLoginAndLandingPage() throws Exception {
  mvc.perform(post("/login").with(csrf()).param("mobileNo","9000000001").param("password","admin-test-password")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/workspace"));
  mvc.perform(get("/workspace").with(user(admin.toString()).roles("ADMIN"))).andExpect(redirectedUrl("/dashboard"));
  mvc.perform(get("/dashboard").with(user(admin.toString()).roles("ADMIN"))).andExpect(status().isOk()).andExpect(content().string(containsString("Manage Store Manager")));
 }
 @Test void emailAndTenantUsernameCannotLogin() throws Exception {
  for(String identifier:new String[]{"admin.test","admin@example.test"}) {
   mvc.perform(post("/login").with(csrf()).param("mobileNo",identifier).param("password","admin-test-password")).andExpect(redirectedUrl("/login?error"));
   mvc.perform(post("/login").with(csrf()).param("username",identifier).param("password","admin-test-password")).andExpect(redirectedUrl("/login?error"));
  }
 }
 @Test void storeCrudIsTenantScopedAndRequiresCsrf() throws Exception {
  mvc.perform(get("/api/admin/stores").with(user(admin.toString()).roles("ADMIN"))).andExpect(status().isOk()).andExpect(jsonPath("$",hasSize(1))).andExpect(jsonPath("$[0].name").value("Own store"));
  mvc.perform(post("/api/admin/stores").with(user(admin.toString()).roles("ADMIN")).contentType("application/json").content(storeBody)).andExpect(status().isForbidden());
  mvc.perform(post("/api/admin/stores").with(user(admin.toString()).roles("ADMIN")).with(csrf()).contentType("application/json").content(storeBody)).andExpect(status().isCreated());
  mvc.perform(put("/api/admin/stores/"+store).with(user(admin.toString()).roles("ADMIN")).with(csrf()).contentType("application/json").content(storeBody)).andExpect(status().isOk());
  mvc.perform(put("/api/admin/stores/"+foreignStore).with(user(admin.toString()).roles("ADMIN")).with(csrf()).contentType("application/json").content(storeBody)).andExpect(status().isNotFound());
  mvc.perform(delete("/api/admin/stores/"+foreignStore).with(user(admin.toString()).roles("ADMIN")).with(csrf())).andExpect(status().isNotFound());
  mvc.perform(delete("/api/admin/stores/"+store).with(user(admin.toString()).roles("ADMIN")).with(csrf())).andExpect(status().isOk());
 }
 @Test void managerCrudAndStoreDeletionGuard() throws Exception {
  mvc.perform(post("/api/admin/managers").with(user(admin.toString()).roles("ADMIN")).with(csrf()).contentType("application/json").content(managerBody(foreignStore))).andExpect(status().isNotFound());
  mvc.perform(post("/api/admin/managers").with(user(admin.toString()).roles("ADMIN")).with(csrf()).contentType("application/json").content(managerBody(store))).andExpect(status().isCreated());
  mvc.perform(post("/login").with(csrf()).param("mobileNo","9616543935").param("password","manager-test-password")).andExpect(redirectedUrl("/workspace"));
  mvc.perform(post("/api/admin/managers").with(user(admin.toString()).roles("ADMIN")).with(csrf()).contentType("application/json").content(managerBody(store).replace("manager@example.test","duplicate@example.test"))).andExpect(status().isConflict());
  UUID id=db.queryForObject("SELECT ur.id FROM user_role ur JOIN users u ON u.id=ur.user_id WHERE u.email='manager@example.test'",UUID.class);
  var hash=db.queryForObject("SELECT password_hash FROM users WHERE email='manager@example.test'",String.class);
  assertThat(encoder.matches("manager-test-password",hash)).isTrue();
  mvc.perform(get("/api/admin/managers").with(user(admin.toString()).roles("ADMIN"))).andExpect(jsonPath("$",hasSize(1))).andExpect(jsonPath("$[0].store_name").value("Own store"));
  mvc.perform(put("/api/admin/managers/"+id).with(user(admin.toString()).roles("ADMIN")).with(csrf()).contentType("application/json").content(managerBody(store).replace("Manager One","Manager Updated"))).andExpect(status().isOk());
  assertThat(encoder.matches("manager-test-password",db.queryForObject("SELECT password_hash FROM users WHERE email='manager@example.test'",String.class))).isTrue();
  mvc.perform(delete("/api/admin/stores/"+store).with(user(admin.toString()).roles("ADMIN")).with(csrf())).andExpect(status().isConflict());
  UUID otherAdmin=UUID.randomUUID();db.update("INSERT INTO users(id,name,email) VALUES (?,'Other admin','other@example.test')",otherAdmin);db.update("INSERT INTO user_role(id,user_id,role_id,tenant_id) SELECT ?,?,role_id,? FROM user_role WHERE user_id=?",UUID.randomUUID(),otherAdmin,otherTenant,admin);
  mvc.perform(get("/api/admin/managers").with(user(otherAdmin.toString()).roles("ADMIN"))).andExpect(jsonPath("$",hasSize(0)));
  mvc.perform(delete("/api/admin/managers/"+id).with(user(otherAdmin.toString()).roles("ADMIN")).with(csrf())).andExpect(status().isNotFound());
  mvc.perform(delete("/api/admin/managers/"+id).with(user(admin.toString()).roles("ADMIN")).with(csrf())).andExpect(status().isOk());
  mvc.perform(delete("/api/admin/stores/"+store).with(user(admin.toString()).roles("ADMIN")).with(csrf())).andExpect(status().isOk());
 }
 @Test void nonAdminCannotAccessAdminFeatures() throws Exception {
  mvc.perform(get("/api/admin/stores")).andExpect(status().isUnauthorized());
  for(String path:new String[]{"/dashboard","/api/admin/session","/api/admin/stores","/api/admin/managers"}) mvc.perform(get(path).with(user(UUID.randomUUID().toString()).roles("STORE_MANAGER"))).andExpect(status().isForbidden());
  mvc.perform(post("/api/admin/stores").with(user(UUID.randomUUID().toString()).roles("STORE_MANAGER")).with(csrf()).contentType("application/json").content(storeBody)).andExpect(status().isForbidden());
  mvc.perform(delete("/api/admin/managers/"+UUID.randomUUID()).with(user(UUID.randomUUID().toString()).roles("STORE_MANAGER")).with(csrf())).andExpect(status().isForbidden());
 }
}

