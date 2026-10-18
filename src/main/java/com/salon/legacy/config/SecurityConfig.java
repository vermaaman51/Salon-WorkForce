package com.salon.legacy.config;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
@Configuration
@Profile("legacy")
public class SecurityConfig {
 @Bean PasswordEncoder passwords(){return new BCryptPasswordEncoder();}
 @Bean UserDetailsService users(JdbcTemplate db){return username -> {
  var rows=db.queryForList("SELECT * FROM staff_accounts WHERE username=?",username);
  if(rows.isEmpty())throw new UsernameNotFoundException("Account not found");
  var row=rows.get(0);
  return User.withUsername(row.get("username").toString()).password(row.get("password_hash").toString()).roles(row.get("role").toString()).build();
 };}
 @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
  return http.authorizeHttpRequests(a->a
   .requestMatchers(org.springframework.http.HttpMethod.GET,"/health","/css/**","/js/auth.js","/login","/signup","/auth/csrf","/","/index.html").permitAll()
   .requestMatchers(org.springframework.http.HttpMethod.POST,"/signup").permitAll()
   .requestMatchers(org.springframework.http.HttpMethod.GET,"/api/**").authenticated()
   .requestMatchers("/api/employees/**","/api/leaves/**").hasRole("ADMIN")
   .anyRequest().authenticated())
   .formLogin(f->f.loginPage("/login").loginProcessingUrl("/login").failureUrl("/login?error").defaultSuccessUrl("/dashboard",true).permitAll())
   .logout(l->l.logoutSuccessUrl("/login?logout")).build();
 }
}

