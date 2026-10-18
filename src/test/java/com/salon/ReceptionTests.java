package com.salon;

import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest @AutoConfigureMockMvc @Transactional
class ReceptionTests {
 @Autowired MockMvc mvc; @Autowired JdbcTemplate db;
 UUID tenant,store,otherStore,foreignStore,reception,expert,foreignExpert,wrongRole;
 @BeforeEach void setup(){
  tenant=UUID.randomUUID();store=UUID.randomUUID();otherStore=UUID.randomUUID();foreignStore=UUID.randomUUID();reception=UUID.randomUUID();expert=UUID.randomUUID();foreignExpert=UUID.randomUUID();wrongRole=UUID.randomUUID();UUID otherTenant=UUID.randomUUID();UUID receptionRole=UUID.randomUUID(),expertRole=UUID.randomUUID();
  db.update("INSERT INTO tenant(id,tenant_name) VALUES (?,'Own tenant'),(?,'Other tenant')",tenant,otherTenant);
  db.update("INSERT INTO store(id,name,tenant_id) VALUES (?,'Own store',?),(?,'Other store',?),(?,'Foreign store',?)",store,tenant,otherStore,tenant,foreignStore,otherTenant);
  db.update("INSERT INTO users(id,name) VALUES (?,'Reception'),(?,'Expert'),(?,'Other expert')",reception,UUID.randomUUID(),UUID.randomUUID());
  UUID expertUser=db.queryForObject("SELECT id FROM users WHERE name='Expert'",UUID.class);UUID otherExpertUser=db.queryForObject("SELECT id FROM users WHERE name='Other expert'",UUID.class);
  db.update("INSERT INTO role(id,roletype) VALUES (?,'RECEPTIONIST'),(?,'EXPERT')",receptionRole,expertRole);
  db.update("INSERT INTO user_role(id,user_id,role_id,store_id,tenant_id) VALUES (?,?,?,?,?),(?,?,?,?,?),(?,?,?,?,?),(?,?,?,?,?)",wrongRole,reception,receptionRole,store,tenant,expert,expertUser,expertRole,store,tenant,foreignExpert,otherExpertUser,expertRole,otherStore,tenant,UUID.randomUUID(),reception,receptionRole,otherStore,tenant);
 }
 String input(String type){return "{\"orderType\":\""+type+"\",\"customerName\":\"Customer One\",\"customerMobile\":\"9000000088\",\"details\":\"Haircut\""+(type.equals("APPOINTMENT")?",\"service\":\"Hair, Nail\",\"amount\":1250.50,\"appointmentAt\":\"2026-10-20T11:00\"":"")+"}";}
 String path(UUID id,String action){return "/api/reception/orders/"+id+"/"+action+"?storeId="+store;}
 @Test void pendingCheckoutAllowsOmittedModeButPaymentCompletionRequiresMode() throws Exception {
  UUID id=create("APPOINTMENT");
  mvc.perform(patch(path(id,"assign")).with(user(reception.toString()).roles("RECEPTIONIST")).with(csrf()).contentType("application/json").content("{\"assignee\":\""+expert+"\"}")).andExpect(status().isOk());
  mvc.perform(patch(path(id,"checkout")).with(user(reception.toString()).roles("RECEPTIONIST")).with(csrf()).contentType("application/json").content("{\"paymentStatus\":\"PENDING\"}")).andExpect(status().isOk());
  assertThat(db.queryForObject("SELECT payment_mode FROM orders WHERE id=?",String.class,id)).isNull();
  mvc.perform(patch(path(id,"payment")).with(user(reception.toString()).roles("RECEPTIONIST")).with(csrf()).contentType("application/json").content("{}")).andExpect(status().isBadRequest());
  mvc.perform(patch(path(id,"payment")).with(user(reception.toString()).roles("RECEPTIONIST")).with(csrf()).contentType("application/json").content("{\"paymentMode\":\"CASH\"}")).andExpect(status().isOk());
  assertThat(db.queryForObject("SELECT payment_mode FROM orders WHERE id=?",String.class,id)).isEqualTo("CASH");
 }
 @Test void checkoutAndPaymentModeAreValidatedStoredAndSnapshotted() throws Exception {
  UUID id=create("APPOINTMENT");
  mvc.perform(patch(path(id,"assign")).with(user(reception.toString()).roles("RECEPTIONIST")).with(csrf()).contentType("application/json").content("{\"assignee\":\""+expert+"\"}")).andExpect(status().isOk());
  for(String payload:new String[]{"{\"paymentStatus\":\"COMPLETED\"}","{\"paymentStatus\":\"COMPLETED\",\"paymentMode\":\"CARD\"}"})mvc.perform(patch(path(id,"checkout")).with(user(reception.toString()).roles("RECEPTIONIST")).with(csrf()).contentType("application/json").content(payload)).andExpect(status().isBadRequest());
  mvc.perform(patch(path(id,"checkout")).with(user(reception.toString()).roles("RECEPTIONIST")).with(csrf()).contentType("application/json").content("{\"paymentStatus\":\"PENDING\",\"paymentMode\":\"CASH\"}")).andExpect(status().isOk());
  assertThat(db.queryForObject("SELECT payment_mode FROM orders WHERE id=?",String.class,id)).isEqualTo("CASH");
  mvc.perform(patch(path(id,"payment")).with(user(reception.toString()).roles("RECEPTIONIST")).with(csrf()).contentType("application/json").content("{}")).andExpect(status().isBadRequest());
  mvc.perform(patch(path(id,"payment")).with(user(reception.toString()).roles("RECEPTIONIST")).with(csrf()).contentType("application/json").content("{\"paymentMode\":\"UPI\"}")).andExpect(status().isOk());
  assertThat(db.queryForObject("SELECT payment_mode FROM orders WHERE id=?",String.class,id)).isEqualTo("UPI");
  assertThat(db.queryForList("SELECT payment_mode FROM order_history WHERE order_id=? AND payment_mode IS NOT NULL ORDER BY history_sequence",String.class,id)).containsExactly("CASH","UPI");
  mvc.perform(patch(path(id,"payment")).with(user(reception.toString()).roles("RECEPTIONIST")).with(csrf()).contentType("application/json").content("{\"paymentMode\":\"CASH\"}")).andExpect(status().isConflict());
 }
 @Test void appointmentDetailsOptionalButQueryDetailsRequired() throws Exception {
  for(String value:new String[]{null,"","   ","Additional notes"}) {
   var json=(com.fasterxml.jackson.databind.node.ObjectNode)new com.fasterxml.jackson.databind.ObjectMapper().readTree(input("APPOINTMENT"));
   if(value==null)json.remove("details");else json.put("details",value);
   mvc.perform(post("/api/reception/orders?storeId="+store).with(user(reception.toString()).roles("RECEPTIONIST")).with(csrf()).contentType("application/json").content(json.toString())).andExpect(status().isCreated());
  }
  assertThat(db.queryForObject("SELECT count(*) FROM orders WHERE store_id=? AND details=''",Long.class,store)).isEqualTo(3);
  for(String value:new String[]{null,"","   "}) {
   var json=(com.fasterxml.jackson.databind.node.ObjectNode)new com.fasterxml.jackson.databind.ObjectMapper().readTree(input("QUERY"));
   if(value==null)json.remove("details");else json.put("details",value);
   mvc.perform(post("/api/reception/orders?storeId="+store).with(user(reception.toString()).roles("RECEPTIONIST")).with(csrf()).contentType("application/json").content(json.toString())).andExpect(status().isBadRequest());
  }
 }
 @Test void orderFieldsRequiredAndMobileExactlyTenDigits() throws Exception {
  for(String type:new String[]{"QUERY","APPOINTMENT"}) {
   for(String invalid:new String[]{"","123456789","12345678901","+919000000088","90000abc88"}) mvc.perform(post("/api/reception/orders?storeId="+store).with(user(reception.toString()).roles("RECEPTIONIST")).with(csrf()).contentType("application/json").content(input(type).replace("9000000088",invalid))).andExpect(status().isBadRequest());
   var fields=new java.util.ArrayList<>(java.util.List.of("orderType","customerName","customerMobile"));
   if(type.equals("APPOINTMENT"))fields.addAll(java.util.List.of("service","amount","appointmentAt"));else fields.add("details");
   for(String field:fields) {
    var json=(com.fasterxml.jackson.databind.node.ObjectNode)new com.fasterxml.jackson.databind.ObjectMapper().readTree(input(type));json.remove(field);
    mvc.perform(post("/api/reception/orders?storeId="+store).with(user(reception.toString()).roles("RECEPTIONIST")).with(csrf()).contentType("application/json").content(json.toString())).andExpect(status().isBadRequest());
   }
  }
 }
 @Test void appointmentServicesAreSeparateFromDetailsAndValidated() throws Exception {
  String payload=input("APPOINTMENT").replace("Hair, Nail"," Hair, Nail, Hair ").replace("Haircut","Customer prefers a short cut");
  mvc.perform(post("/api/reception/orders?storeId="+store).with(user(reception.toString()).roles("RECEPTIONIST")).with(csrf()).contentType("application/json").content(payload)).andExpect(status().isCreated());
  var row=db.queryForMap("SELECT service,details,appointment_at FROM orders WHERE store_id=?",store);
  assertThat(row.get("service")).isEqualTo("Hair, Nail");
  assertThat(row.get("details")).isEqualTo("Customer prefers a short cut");
  assertThat(db.queryForObject("SELECT appointment_at FROM orders WHERE store_id=?",java.time.LocalDateTime.class,store)).isEqualTo(java.time.LocalDateTime.of(2026,10,20,11,0));
  mvc.perform(get("/api/reception/orders?storeId="+store).with(user(reception.toString()).roles("RECEPTIONIST"))).andExpect(jsonPath("$[0].service").value("Hair, Nail")).andExpect(jsonPath("$[0].details").value("Customer prefers a short cut"));
  for(String invalid:new String[]{"","Unknown","Hair,","Hair, Unknown"}) mvc.perform(post("/api/reception/orders?storeId="+store).with(user(reception.toString()).roles("RECEPTIONIST")).with(csrf()).contentType("application/json").content(input("APPOINTMENT").replace("Hair, Nail",invalid))).andExpect(status().isBadRequest());
  mvc.perform(post("/api/reception/orders?storeId="+store).with(user(reception.toString()).roles("RECEPTIONIST")).with(csrf()).contentType("application/json").content(input("APPOINTMENT").replace("\"service\":\"Hair, Nail\",",""))).andExpect(status().isBadRequest());
 }
 UUID create(String type) throws Exception{mvc.perform(post("/api/reception/orders?storeId="+store).with(user(reception.toString()).roles("RECEPTIONIST")).with(csrf()).contentType("application/json").content(input(type))).andExpect(status().isCreated());return db.queryForObject("SELECT id FROM orders WHERE order_type=? AND store_id=?",UUID.class,type,store);}
 @Test void appointmentAssignmentCheckoutAndOutstandingPayment() throws Exception {
  UUID id=create("APPOINTMENT");
  mvc.perform(patch(path(id,"checkout")).with(user(reception.toString()).roles("RECEPTIONIST")).with(csrf()).contentType("application/json").content("{\"paymentStatus\":\"PENDING\",\"paymentMode\":\"CASH\"}")).andExpect(status().isConflict());
  mvc.perform(patch(path(id,"assign")).with(user(reception.toString()).roles("RECEPTIONIST")).with(csrf()).contentType("application/json").content("{\"assignee\":\""+expert+"\"}")).andExpect(status().isOk());
  mvc.perform(patch(path(id,"payment")).with(user(reception.toString()).roles("RECEPTIONIST")).with(csrf()).contentType("application/json").content("{\"paymentMode\":\"UPI\"}")).andExpect(status().isConflict());
  mvc.perform(patch(path(id,"checkout")).with(user(reception.toString()).roles("RECEPTIONIST")).with(csrf()).contentType("application/json").content("{\"paymentStatus\":\"PENDING\",\"paymentMode\":\"CASH\"}")).andExpect(status().isOk());
  assertThat(db.queryForObject("SELECT payment_status FROM orders WHERE id=?",String.class,id)).isEqualTo("PENDING");
  mvc.perform(patch(path(id,"assign")).with(user(reception.toString()).roles("RECEPTIONIST")).with(csrf()).contentType("application/json").content("{\"assignee\":\""+expert+"\"}")).andExpect(status().isConflict());
  mvc.perform(patch(path(id,"payment")).with(user(reception.toString()).roles("RECEPTIONIST")).with(csrf()).contentType("application/json").content("{\"paymentMode\":\"UPI\"}")).andExpect(status().isOk());
  assertThat(db.queryForObject("SELECT order_status FROM orders WHERE id=?",String.class,id)).isEqualTo("COMPLETED");assertThat(db.queryForObject("SELECT payment_status FROM orders WHERE id=?",String.class,id)).isEqualTo("COMPLETED");
  mvc.perform(get("/api/reception/orders?storeId="+store).with(user(reception.toString()).roles("RECEPTIONIST"))).andExpect(jsonPath("$[0].assignee").value(expert.toString())).andExpect(jsonPath("$[0].amount").value(1250.50));
 }
 @Test void queriesResolveWithoutExpertAndPaidCheckoutWorks() throws Exception {
  UUID query=create("QUERY");
  mvc.perform(patch(path(query,"assign")).with(user(reception.toString()).roles("RECEPTIONIST")).with(csrf()).contentType("application/json").content("{\"assignee\":\""+expert+"\"}")).andExpect(status().isConflict());
  mvc.perform(patch(path(query,"checkout")).with(user(reception.toString()).roles("RECEPTIONIST")).with(csrf()).contentType("application/json").content("{\"paymentStatus\":\"COMPLETED\",\"paymentMode\":\"UPI\"}")).andExpect(status().isConflict());
  mvc.perform(patch(path(query,"checkout")).with(user(reception.toString()).roles("RECEPTIONIST")).with(csrf()).contentType("application/json").content("{\"paymentStatus\":\"PENDING\",\"paymentMode\":\"CASH\"}")).andExpect(status().isOk());
  UUID appointment=create("APPOINTMENT");mvc.perform(patch(path(appointment,"assign")).with(user(reception.toString()).roles("RECEPTIONIST")).with(csrf()).contentType("application/json").content("{\"assignee\":\""+expert+"\"}")).andExpect(status().isOk());mvc.perform(patch(path(appointment,"checkout")).with(user(reception.toString()).roles("RECEPTIONIST")).with(csrf()).contentType("application/json").content("{\"paymentStatus\":\"COMPLETED\",\"paymentMode\":\"UPI\"}")).andExpect(status().isOk());
  assertThat(db.queryForObject("SELECT payment_status FROM orders WHERE id=?",String.class,appointment)).isEqualTo("COMPLETED");
 }
 @Test void enforcesStoreScopeExpertRoleAndValidInput() throws Exception {
  UUID id=create("APPOINTMENT");
  for(UUID invalid:new UUID[]{foreignExpert,wrongRole})mvc.perform(patch(path(id,"assign")).with(user(reception.toString()).roles("RECEPTIONIST")).with(csrf()).contentType("application/json").content("{\"assignee\":\""+invalid+"\"}")).andExpect(status().isNotFound());
  mvc.perform(patch("/api/reception/orders/"+id+"/checkout?storeId="+otherStore).with(user(reception.toString()).roles("RECEPTIONIST")).with(csrf()).contentType("application/json").content("{\"paymentStatus\":\"PENDING\",\"paymentMode\":\"CASH\"}")).andExpect(status().isNotFound());
  mvc.perform(get("/api/reception/orders?storeId="+foreignStore).with(user(reception.toString()).roles("RECEPTIONIST"))).andExpect(status().isNotFound());
  mvc.perform(get("/api/reception/experts?storeId="+store).with(user(reception.toString()).roles("RECEPTIONIST"))).andExpect(jsonPath("$",hasSize(1))).andExpect(jsonPath("$[0].id").value(expert.toString()));
  mvc.perform(post("/api/reception/orders?storeId="+store).with(user(reception.toString()).roles("RECEPTIONIST")).with(csrf()).contentType("application/json").content(input("APPOINTMENT").replace("1250.50","-1"))).andExpect(status().isBadRequest());
  mvc.perform(post("/api/reception/orders?storeId="+store).with(user(reception.toString()).roles("RECEPTIONIST")).with(csrf()).contentType("application/json").content(input("APPOINTMENT").replace("1250.50","null"))).andExpect(status().isBadRequest());
 }
 @Test void routesReceptionAndRejectsOtherRolesAndMissingCsrf() throws Exception {
  mvc.perform(get("/workspace").with(user(reception.toString()).roles("RECEPTIONIST"))).andExpect(redirectedUrl("/reception/dashboard"));
  mvc.perform(get("/reception/dashboard").with(user(reception.toString()).roles("RECEPTIONIST"))).andExpect(status().isOk()).andExpect(content().string(containsString("Checkout &amp; payments")));
  mvc.perform(get("/api/reception/orders?storeId="+store)).andExpect(status().isUnauthorized());
  for(String role:new String[]{"ADMIN","STORE_MANAGER","EXPERT"})mvc.perform(post("/api/reception/orders?storeId="+store).with(user(reception.toString()).roles(role)).with(csrf()).contentType("application/json").content(input("QUERY"))).andExpect(status().isForbidden());
  mvc.perform(post("/api/reception/orders?storeId="+store).with(user(reception.toString()).roles("RECEPTIONIST")).contentType("application/json").content(input("QUERY"))).andExpect(status().isForbidden());
 }
}
