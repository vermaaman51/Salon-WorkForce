package com.salon;

import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import com.salon.service.TenantPlans.Plan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest @AutoConfigureMockMvc @Transactional
class PlanFeatureTests {
 @Autowired MockMvc mvc;@Autowired JdbcTemplate db;
 UUID tenant,admin;
 @BeforeEach void setup(){tenant=UUID.randomUUID();admin=UUID.randomUUID();db.update("INSERT INTO tenant(id,tenant_name) VALUES (?,'Plan client')",tenant);db.update("INSERT INTO users(id,name) VALUES (?,'Owner')",admin);assign(admin,"ADMIN",null);}
 void plan(Plan p){db.update("UPDATE tenant SET segment_type=? WHERE id=?",p.name(),tenant);}
 UUID store(){UUID id=UUID.randomUUID();db.update("INSERT INTO store(id,name,tenant_id) VALUES (?,'Test store',?)",id,tenant);return id;}
 void assign(UUID user,String type,UUID store){UUID role=UUID.randomUUID();db.update("INSERT INTO role(id,roletype) VALUES (?,?)",role,type);db.update("INSERT INTO user_role(id,user_id,role_id,tenant_id,store_id) VALUES (?,?,?,?,?)",UUID.randomUUID(),user,role,tenant,store);}
 UUID staff(String type,UUID store){UUID user=UUID.randomUUID();db.update("INSERT INTO users(id) VALUES (?)",user);assign(user,type,store);return user;}
 String staffBody(UUID store){return "{\"name\":\"New staff\",\"email\":\"staff@plan.test\",\"mobileNo\":\"9787654321\",\"address\":\"Delhi\",\"storeId\":\""+store+"\",\"password\":\"strong-staff-password\"}";}
 @ParameterizedTest @EnumSource(Plan.class) void storeLimitIsEnforced(Plan p)throws Exception{plan(p);for(int i=0;i<p.stores-1;i++)store();String body="{\"name\":\"New store\",\"city\":\"Delhi\",\"address\":\"Sector 1\"}";
 mvc.perform(post("/api/admin/stores").with(user(admin.toString()).roles("ADMIN")).with(csrf()).contentType("application/json").content(body)).andExpect(status().isCreated());
 mvc.perform(post("/api/admin/stores").with(user(admin.toString()).roles("ADMIN")).with(csrf()).contentType("application/json").content(body)).andExpect(status().isConflict());assertThat(db.queryForObject("SELECT count(*) FROM store WHERE tenant_id=?",Long.class,tenant)).isEqualTo(p.stores);
 }
 @ParameterizedTest @EnumSource(Plan.class) void managerLimitIsTenantWide(Plan p)throws Exception{plan(p);UUID s=store();for(int i=0;i<p.managers-1;i++)staff("STORE_MANAGER",s);
 mvc.perform(post("/api/admin/managers").with(user(admin.toString()).roles("ADMIN")).with(csrf()).contentType("application/json").content(staffBody(s))).andExpect(status().isCreated());
 mvc.perform(post("/api/admin/managers").with(user(admin.toString()).roles("ADMIN")).with(csrf()).contentType("application/json").content(staffBody(s).replace("9787654321","9787654322").replace("staff@plan.test","other@plan.test"))).andExpect(status().isConflict());
 assertThat(db.queryForObject("SELECT count(*) FROM users WHERE mobileno='9787654322'",Long.class)).isZero();
 }
 @ParameterizedTest @EnumSource(Plan.class) void receptionistLimitSharedByAdminAndManager(Plan p)throws Exception{plan(p);UUID s=store(),manager=staff("STORE_MANAGER",s);for(int i=0;i<p.receptionists-1;i++)staff("RECEPTIONIST",s);
 mvc.perform(post("/api/admin/receptionists").with(user(admin.toString()).roles("ADMIN")).with(csrf()).contentType("application/json").content(staffBody(s))).andExpect(status().isCreated());
 mvc.perform(post("/api/manager/receptionists?storeId="+s).with(user(manager.toString()).roles("STORE_MANAGER")).with(csrf()).contentType("application/json").content(staffBody(s).replace("9787654321","9787654322").replace("staff@plan.test","other@plan.test"))).andExpect(status().isConflict());
 mvc.perform(get("/api/admin/receptionists").with(user(admin.toString()).roles("ADMIN"))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(p.receptionists));
 }
 @ParameterizedTest @EnumSource(Plan.class) void invoiceEntitlementAndScope(Plan p)throws Exception{plan(p);UUID s=store(),manager=staff("STORE_MANAGER",s),reception=staff("RECEPTIONIST",s),id=UUID.randomUUID();
 UUID expert=staff("EXPERT",s);UUID assignee=db.queryForObject("SELECT id FROM user_role WHERE user_id=?",UUID.class,expert);
 db.update("INSERT INTO orders(id,tenant_id,store_id,assignee,order_type,order_status,customer_name,customer_mobile,details,service,amount,appointment_at,payment_status) VALUES (?,?,?,?,'APPOINTMENT','COMPLETED','Customer','9876543210','','Hair',250,CURRENT_TIMESTAMP,'PENDING')",id,tenant,s,assignee);
 for(String role:new String[]{"STORE_MANAGER","RECEPTIONIST"}){UUID who=role.equals("STORE_MANAGER")?manager:reception;String prefix=role.equals("STORE_MANAGER")?"/api/manager/appointments":"/api/reception";
 mvc.perform(get(prefix+"/orders/"+id+"/invoice?storeId="+s).with(user(who.toString()).roles(role))).andExpect(status().is(p.invoices?200:403));
 mvc.perform(get(prefix+"/orders/"+id+"/invoice?storeId="+UUID.randomUUID()).with(user(who.toString()).roles(role))).andExpect(status().isNotFound());}
 if(p.invoices){db.update("UPDATE orders SET order_status='PENDING' WHERE id=?",id);mvc.perform(get("/api/reception/orders/"+id+"/invoice?storeId="+s).with(user(reception.toString()).roles("RECEPTIONIST"))).andExpect(status().isNotFound());}
 mvc.perform(get("/api/admin/session").with(user(admin.toString()).roles("ADMIN"))).andExpect(jsonPath("$.plan.segmentType").value(p.name())).andExpect(jsonPath("$.plan.csv").value(p.csv));
 }
 @Test void adminCannotManageForeignTenantReceptionist()throws Exception{UUID foreign=UUID.randomUUID(),s=UUID.randomUUID();db.update("INSERT INTO tenant(id) VALUES (?)",foreign);db.update("INSERT INTO store(id,tenant_id) VALUES (?,?)",s,foreign);mvc.perform(post("/api/admin/receptionists").with(user(admin.toString()).roles("ADMIN")).with(csrf()).contentType("application/json").content(staffBody(s))).andExpect(status().isNotFound());}
 @Test void tenantGstIsOptionalAndStoresInheritOverrideOrClearIt()throws Exception{
  String tenantBody="{\"tenantName\":\"GST client\",\"username\":\"gst.client\",\"country\":\"India\",\"active\":true,\"segmentType\":\"PRO\",\"gstNumber\":\"09abcde1234f1z5\"}";
  mvc.perform(put("/api/root/tenants/"+tenant).with(user(admin.toString()).roles("ROOT")).with(csrf()).contentType("application/json").content(tenantBody)).andExpect(status().isOk());
  mvc.perform(get("/api/admin/session").with(user(admin.toString()).roles("ADMIN"))).andExpect(jsonPath("$.gst_number").value("09ABCDE1234F1Z5"));
  String body="{\"name\":\"GST store\",\"city\":\"Lucknow\",\"address\":\"Address\"}";
  mvc.perform(post("/api/admin/stores").with(user(admin.toString()).roles("ADMIN")).with(csrf()).contentType("application/json").content(body)).andExpect(status().isCreated());
  UUID id=db.queryForObject("SELECT id FROM store WHERE tenant_id=?",UUID.class,tenant);
  assertThat(db.queryForObject("SELECT gst_number FROM store WHERE id=?",String.class,id)).isEqualTo("09ABCDE1234F1Z5");
  mvc.perform(put("/api/admin/stores/"+id).with(user(admin.toString()).roles("ADMIN")).with(csrf()).contentType("application/json").content(body.replace("\"GST store\"","\"GST store\",\"gstNumber\":\"27ABCDE1234F1Z5\""))).andExpect(status().isOk());
  assertThat(db.queryForObject("SELECT gst_number FROM store WHERE id=?",String.class,id)).isEqualTo("27ABCDE1234F1Z5");
  mvc.perform(put("/api/admin/stores/"+id).with(user(admin.toString()).roles("ADMIN")).with(csrf()).contentType("application/json").content(body)).andExpect(status().isOk());
  assertThat(db.queryForObject("SELECT gst_number FROM store WHERE id=?",String.class,id)).isEqualTo("27ABCDE1234F1Z5");
  mvc.perform(put("/api/admin/stores/"+id).with(user(admin.toString()).roles("ADMIN")).with(csrf()).contentType("application/json").content(body.replace("\"GST store\"","\"GST store\",\"gstNumber\":\"\""))).andExpect(status().isOk());
  assertThat(db.queryForObject("SELECT gst_number FROM store WHERE id=?",String.class,id)).isNull();
  mvc.perform(put("/api/root/tenants/"+tenant).with(user(admin.toString()).roles("ROOT")).with(csrf()).contentType("application/json").content(tenantBody.replace("09abcde1234f1z5","bad"))).andExpect(status().isBadRequest());
 }
 @Test void invoiceNumbersAreShortStableAndUseStoreGst()throws Exception{
  plan(Plan.PRO);UUID s=store(),reception=staff("RECEPTIONIST",s),expert=staff("EXPERT",s),order=UUID.randomUUID();UUID assignee=db.queryForObject("SELECT id FROM user_role WHERE user_id=?",UUID.class,expert);
  db.update("UPDATE store SET gst_number='27ABCDE1234F1Z5' WHERE id=?",s);
  db.update("INSERT INTO orders(id,tenant_id,store_id,assignee,order_type,order_status,customer_name,customer_mobile,details,service,amount,appointment_at) VALUES (?,?,?,?,'APPOINTMENT','COMPLETED','Customer','9876543210','','Hair',250,CURRENT_TIMESTAMP)",order,tenant,s,assignee);
  String url="/api/reception/orders/"+order+"/invoice?storeId="+s;
  String first=mvc.perform(get(url).with(user(reception.toString()).roles("RECEPTIONIST"))).andExpect(status().isOk()).andExpect(jsonPath("$.gst_number").value("27ABCDE1234F1Z5")).andReturn().getResponse().getContentAsString();
  String second=mvc.perform(get(url).with(user(reception.toString()).roles("RECEPTIONIST"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  var mapper=new com.fasterxml.jackson.databind.ObjectMapper();String number=mapper.readTree(first).get("invoice_no").asText();assertThat(number).matches("INV-[0-9]{6,}");assertThat(mapper.readTree(second).get("invoice_no").asText()).isEqualTo(number);
  assertThat(db.queryForObject("SELECT count(*) FROM order_invoice WHERE order_id=?",Long.class,order)).isEqualTo(1);
 }}
