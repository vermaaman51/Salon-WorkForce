package com.salon;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;
import com.salon.service.AdminService;
import com.salon.service.StoreManagerService;
import com.salon.service.ReceptionService;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @AutoConfigureMockMvc @Transactional
class ApiAuthenticationTests {
 @Autowired MockMvc mvc;
 @Autowired AdminService admin;
 @Autowired StoreManagerService manager;
 @Autowired ReceptionService reception;
 @Autowired JdbcTemplate db;
 @Autowired PasswordEncoder passwords;

 @Test void anonymousCannotReadOrMutateAnyWorkflowEvenWithCsrf() throws Exception {
  String id=UUID.randomUUID().toString();
  for(String path:new String[]{"/api/admin/session","/api/admin/stores","/api/admin/managers","/api/manager/session","/api/manager/experts?storeId="+id,"/api/manager/receptionists?storeId="+id,"/api/reception/session","/api/reception/orders?storeId="+id,"/api/reception/experts?storeId="+id})
   mvc.perform(get(path)).andExpect(status().isUnauthorized()).andExpect(content().contentTypeCompatibleWith("application/json")).andExpect(jsonPath("$.status").value(401));
  for(String path:new String[]{"/api/admin/stores","/api/admin/managers","/api/manager/experts?storeId="+id,"/api/manager/receptionists?storeId="+id,"/api/reception/orders?storeId="+id}) {
   mvc.perform(post(path).with(csrf()).contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
   mvc.perform(post(path).contentType("application/json").content("{}")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
  }
  for(String path:new String[]{"/api/admin/stores/"+id,"/api/admin/managers/"+id,"/api/manager/experts/"+id+"?storeId="+id,"/api/manager/receptionists/"+id+"?storeId="+id}) {
   mvc.perform(put(path).with(csrf()).contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
   mvc.perform(delete(path).with(csrf())).andExpect(status().isUnauthorized());
  }
  for(String action:new String[]{"assign","checkout","payment"})
   mvc.perform(patch("/api/reception/orders/"+id+"/"+action+"?storeId="+id).with(csrf()).contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
 }

 @Test void authorizationAndCsrfFailuresReturnJson() throws Exception {
  mvc.perform(get("/api/admin/stores").with(user("expert").roles("EXPERT")))
   .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
  mvc.perform(post("/api/admin/stores").with(user("admin").roles("ADMIN")).contentType("application/json").content("{}"))
   .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
 }

 @Test void servicesRejectAnonymousCallsWithoutController() {
  assertThatThrownBy(()->admin.stores(null)).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
  assertThatThrownBy(()->manager.session(null)).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
  assertThatThrownBy(()->reception.session(null)).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
 }

 @Test @WithMockUser(roles="EXPERT") void servicesRejectWrongRoleWithoutController() {
  var auth=SecurityContextHolder.getContext().getAuthentication();
  assertThatThrownBy(()->admin.stores(auth)).isInstanceOf(AccessDeniedException.class);
  assertThatThrownBy(()->manager.session(auth)).isInstanceOf(AccessDeniedException.class);
  assertThatThrownBy(()->reception.session(auth)).isInstanceOf(AccessDeniedException.class);
 }

 @Test void loginRotatesSessionAndLogoutInvalidatesIt() throws Exception {
  UUID tenant=UUID.randomUUID(),userId=UUID.randomUUID(),role=UUID.randomUUID();
  db.update("INSERT INTO tenant(id,tenant_name) VALUES (?,'Session test')",tenant);
  db.update("INSERT INTO users(id,name,mobileno,password_hash) VALUES (?,'Session test','9000000061',?)",userId,passwords.encode("session-test-password"));
  db.update("INSERT INTO role(id,roletype) VALUES (?,'ADMIN')",role);
  db.update("INSERT INTO user_role(id,user_id,role_id,tenant_id) VALUES (?,?,?,?)",UUID.randomUUID(),userId,role,tenant);
  MockHttpSession before=(MockHttpSession)mvc.perform(get("/auth/csrf")).andReturn().getRequest().getSession(false);
  String oldId=before.getId();
  var result=mvc.perform(post("/login").session(before).with(csrf()).param("mobileNo","9000000061").param("password","session-test-password"))
   .andExpect(redirectedUrl("/workspace")).andReturn();
  MockHttpSession signedIn=(MockHttpSession)result.getRequest().getSession(false);
  assertThat(signedIn.getId()).isNotEqualTo(oldId);
  mvc.perform(get("/api/admin/session").session(signedIn)).andExpect(status().isOk());
  mvc.perform(post("/logout").session(signedIn).with(csrf())).andExpect(redirectedUrl("/login?logout"));
  assertThat(signedIn.isInvalid()).isTrue();
  mvc.perform(get("/api/admin/session")).andExpect(status().isUnauthorized());
 }
}
