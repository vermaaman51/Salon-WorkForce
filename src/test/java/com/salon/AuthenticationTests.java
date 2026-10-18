package com.salon;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;

@SpringBootTest @AutoConfigureMockMvc
@Disabled("Replaced workforce schema; retained as legacy behavior reference")
class AuthenticationTests {
 @Autowired MockMvc mvc; @Autowired JdbcTemplate db; @Autowired PasswordEncoder encoder;
 @BeforeEach void reset(){db.update("DELETE FROM staff_accounts WHERE username='new.staff'");}
 String registration(){return "{\"name\":\"New Staff\",\"username\":\"new.staff\",\"password\":\"a-strong-staff-password\"}";}
 @Test void separatePagesAndProtectedDashboard() throws Exception {
  mvc.perform(get("/")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
  mvc.perform(get("/login")).andExpect(status().isOk()).andExpect(content().string(containsString("Welcome back.")));
  mvc.perform(get("/signup")).andExpect(status().isOk()).andExpect(content().string(containsString("Create your account")));
  mvc.perform(get("/dashboard")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("http://localhost/login"));
  mvc.perform(get("/dashboard").with(user("admin").roles("ADMIN"))).andExpect(status().isOk()).andExpect(content().string(containsString("Your salon at a glance")));
  mvc.perform(get("/auth/csrf")).andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty());
 }
 @Test void signupPersistsHashAndLogsInAsReceptionist() throws Exception {
  mvc.perform(post("/signup").with(csrf()).contentType("application/json").content(registration())).andExpect(status().isCreated());
  var account=db.queryForMap("SELECT * FROM staff_accounts WHERE username='new.staff'");
  assertThat(account.get("role")).isEqualTo("RECEPTION");
  assertThat(encoder.matches("a-strong-staff-password",account.get("password_hash").toString())).isTrue();
  var result=mvc.perform(post("/login").with(csrf()).param("username","new.staff").param("password","a-strong-staff-password")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/dashboard")).andReturn();
  var session=(MockHttpSession)result.getRequest().getSession(false);
  mvc.perform(get("/api/session").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.admin").value(false));
  mvc.perform(post("/api/employees").session(session).with(csrf()).contentType("application/json").content("{}")).andExpect(status().isForbidden());
  mvc.perform(post("/signup").with(csrf()).contentType("application/json").content(registration())).andExpect(status().isConflict());
 }
 @Test void signupRequiresValidationAndCsrf() throws Exception {
  mvc.perform(post("/signup").contentType("application/json").content(registration())).andExpect(status().isForbidden());
  mvc.perform(post("/signup").with(csrf()).contentType("application/json").content(registration().replace("a-strong-staff-password","short"))).andExpect(status().isBadRequest());
  assertThat(db.queryForObject("SELECT count(*) FROM staff_accounts WHERE username='new.staff'",Long.class)).isZero();
 }
 @Test void wrongPasswordShowsLoginError() throws Exception {
  mvc.perform(post("/login").with(csrf()).param("username","admin").param("password","incorrect")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login?error"));
 }
}


