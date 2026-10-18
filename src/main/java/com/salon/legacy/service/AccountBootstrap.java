package com.salon.legacy.service;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
@Component
@Profile("legacy")
public class AccountBootstrap implements ApplicationRunner {
 private final JdbcTemplate db; private final PasswordEncoder encoder; private final String admin,reception;
 public AccountBootstrap(JdbcTemplate db,PasswordEncoder encoder,@Value("${ADMIN_PASSWORD}") String admin,@Value("${RECEPTION_PASSWORD}") String reception){this.db=db;this.encoder=encoder;this.admin=admin;this.reception=reception;}
 public void run(ApplicationArguments args){create("admin","Salon administrator","ADMIN",admin);create("reception","Reception desk","RECEPTION",reception);}
 private void create(String username,String name,String role,String password){
  if(db.queryForObject("SELECT count(*) FROM staff_accounts WHERE username=?",Long.class,username)>0)return;
  if(password.length()<12||password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)throw new IllegalArgumentException("Bootstrap passwords must contain 12+ characters and at most 72 UTF-8 bytes");
  try{db.update("INSERT INTO staff_accounts(username,display_name,password_hash,role) VALUES (?,?,?,?)",username,name,encoder.encode(password),role);}catch(org.springframework.dao.DuplicateKeyException ignored){}
 }
}

