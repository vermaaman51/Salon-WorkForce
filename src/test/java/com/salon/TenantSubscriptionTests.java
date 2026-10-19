package com.salon;
import java.util.*;
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
@SpringBootTest @AutoConfigureMockMvc @Transactional
class TenantSubscriptionTests {
 @Autowired MockMvc mvc; @Autowired JdbcTemplate db; @Autowired com.salon.service.TenantSubscriptions service;
 UUID root,user,tenant;
 @BeforeEach void setup(){root=UUID.randomUUID();user=UUID.randomUUID();tenant=UUID.randomUUID();UUID role=UUID.randomUUID();db.update("INSERT INTO users(id) VALUES (?)",root);db.update("INSERT INTO users(id) VALUES (?)",user);db.update("INSERT INTO platform_root VALUES (1,?)",root);db.update("INSERT INTO tenant(id,tenant_name) VALUES (?,'Client')",tenant);db.update("INSERT INTO role(id,roletype) VALUES (?,'ADMIN')",role);db.update("INSERT INTO user_role(id,user_id,role_id,tenant_id) VALUES (?,?,?,?)",UUID.randomUUID(),user,role,tenant);}
 @Test void rootIsSingletonAndManagementIsProtected() throws Exception {assertThatThrownBy(()->db.update("INSERT INTO platform_root VALUES (2,?)",user)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);}
 @Test void onlyRootCanManageAndWritesRequireCsrf() throws Exception {mvc.perform(get("/api/root/tenants").with(user(user.toString()).roles("ADMIN"))).andExpect(status().isForbidden());mvc.perform(get("/api/root/tenants").with(user(root.toString()).roles("ROOT"))).andExpect(status().isOk());mvc.perform(post("/api/root/tenants").with(user(root.toString()).roles("ROOT")).contentType("application/json").content("{}")).andExpect(status().isForbidden());}
 @Test void disabledAndExpiredTenantsBlockExistingSessionsButRootWorks() throws Exception {assertThat(service.allowed(user)).isTrue();db.update("UPDATE tenant SET _is_active=FALSE WHERE id=?",tenant);mvc.perform(get("/api/admin/session").with(user(user.toString()).roles("ADMIN"))).andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value("Your subscription has expired. Contact your administrator."));assertThat(service.allowed(root)).isTrue();db.update("UPDATE tenant SET _is_active=TRUE,subscription_expires_at=DATEADD('HOUR',-1,CURRENT_TIMESTAMP) WHERE id=?",tenant);assertThat(service.allowed(user)).isFalse();db.update("UPDATE tenant SET subscription_expires_at=DATEADD('HOUR',12,CURRENT_TIMESTAMP) WHERE id=?",tenant);mvc.perform(get("/api/subscription").with(user(user.toString()).roles("ADMIN"))).andExpect(status().isOk()).andExpect(jsonPath("$[0].segment_type").value("BASE"));}
 @Test void createTenantAndAdminThenExpireLogin() throws Exception {UUID t=service.save(null,new com.salon.service.TenantSubscriptions.TenantInput("Pro client","India",true,"PRO",java.time.Instant.now().plusSeconds(3600)));service.admin(t,null,new com.salon.service.TenantSubscriptions.AdminInput("Client admin","client@example.test","9123456789","Delhi","strong-client-password"));mvc.perform(post("/login").with(csrf()).param("mobileNo","9123456789").param("password","strong-client-password")).andExpect(redirectedUrl("/workspace"));db.update("UPDATE tenant SET _is_active=FALSE WHERE id=?",t);mvc.perform(post("/login").with(csrf()).param("mobileNo","9123456789").param("password","strong-client-password")).andExpect(redirectedUrl("/login?expired"));assertThat(service.admins(t)).hasSize(1);UUID admin=(UUID)service.admins(t).get(0).get("id");service.removeAdmin(t,admin);assertThat(service.admins(t)).isEmpty();} @Test void tenantUsernameAndGlobalAdminListAreAvailableToRoot() throws Exception {
  UUID t=service.save(null,new com.salon.service.TenantSubscriptions.TenantInput("New client","India",true,"PRO",null,"client.username"));
  assertThat(db.queryForObject("SELECT username FROM tenant WHERE id=?",String.class,t)).isEqualTo("client.username");
  service.save(t,new com.salon.service.TenantSubscriptions.TenantInput("Updated client","UK",false,"PRO_PLUS",null,"updated.username"));
  assertThat(db.queryForObject("SELECT username FROM tenant WHERE id=?",String.class,t)).isEqualTo("updated.username");
  mvc.perform(get("/api/root/admins").with(user(root.toString()).roles("ROOT"))).andExpect(status().isOk()).andExpect(jsonPath("$[0].tenant_name").value("Client"));
  mvc.perform(get("/api/root/admins").with(user(user.toString()).roles("ADMIN"))).andExpect(status().isForbidden());
 }
 @Test void modifyAdminCanKeepPasswordAndChangeTenant() {
  UUID t=service.save(null,new com.salon.service.TenantSubscriptions.TenantInput("Destination","India",true,"BASE",null));
  db.update("UPDATE users SET password_hash='existing-hash' WHERE id=?",user);
  service.admin(tenant,user,new com.salon.service.TenantSubscriptions.AdminInput("Updated owner","updated@example.test","9234567890","New address","",t));
  assertThat(service.admins(tenant)).isEmpty();assertThat(service.admins(t)).hasSize(1);
  assertThat(db.queryForObject("SELECT password_hash FROM users WHERE id=?",String.class,user)).isEqualTo("existing-hash");
  assertThat(db.queryForObject("SELECT name FROM users WHERE id=?",String.class,user)).isEqualTo("Updated owner");
 }
 @Test void cannotMoveAdminWithStoreAssignments() {
  UUID t=service.save(null,new com.salon.service.TenantSubscriptions.TenantInput("Destination","India",true,"BASE",null));
  UUID store=UUID.randomUUID(),role=UUID.randomUUID();
  db.update("INSERT INTO store(id,tenant_id) VALUES (?,?)",store,tenant);
  db.update("INSERT INTO role(id,roletype) VALUES (?,'STORE_MANAGER')",role);
  db.update("INSERT INTO user_role(id,user_id,role_id,tenant_id,store_id) VALUES (?,?,?,?,?)",UUID.randomUUID(),user,role,tenant,store);
  assertThatThrownBy(()->service.admin(tenant,user,new com.salon.service.TenantSubscriptions.AdminInput("Updated owner","updated@example.test","9234567890","New address","",t))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  assertThat(service.admins(tenant)).hasSize(1);assertThat(service.admins(t)).isEmpty();
 }
 @Test void subscriptionReminderIsAdminOnly() throws Exception {
  mvc.perform(get("/api/subscription").with(user(user.toString()).roles("ADMIN"))).andExpect(status().isOk());
  for(String role:new String[]{"STORE_MANAGER","RECEPTIONIST","EXPERT","ROOT"}) {
   for(String path:new String[]{"/api/subscription","/js/subscription.js"})
    mvc.perform(get(path).with(user(user.toString()).roles(role))).andExpect(status().isForbidden());
  }
  String adminPage=new String(new org.springframework.core.io.ClassPathResource("static/admin.html").getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
  assertThat(adminPage).contains("/js/subscription.js");
  for(String page:new String[]{"manager.html","manager-appointments.html","reception.html"}) {
   String html=new String(new org.springframework.core.io.ClassPathResource("static/"+page).getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
   assertThat(html).doesNotContain("/js/subscription.js");
  }
 }
 @Test void expiryDashboardScriptIsAvailableOnlyToRoot() throws Exception {
  mvc.perform(get("/js/tenant-expiry.js").with(user(root.toString()).roles("ROOT"))).andExpect(status().isOk());
  mvc.perform(get("/js/tenant-expiry.js").with(user(user.toString()).roles("ADMIN"))).andExpect(status().isForbidden());
 }
}
