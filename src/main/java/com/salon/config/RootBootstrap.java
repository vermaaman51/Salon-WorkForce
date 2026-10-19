package com.salon.config;
import java.util.*;
import org.springframework.context.annotation.*;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.salon.utility.PasswordPolicy;
@Configuration
@Profile("!legacy")
public class RootBootstrap {
 @Bean ApplicationRunner rootAccount(JdbcTemplate db,PasswordEncoder encoder,@Value("${ROOT_MOBILE:}") String mobile,@Value("${ROOT_PASSWORD:}") String password){return args->{if(db.queryForObject("SELECT count(*) FROM platform_root",Long.class)>0||mobile.isBlank())return;if(!mobile.matches("[0-9]{10}")||!PasswordPolicy.isValid(password))throw new IllegalArgumentException("ROOT_MOBILE requires 10 digits; ROOT_PASSWORD requires 12+ characters, max 72 bytes");UUID id=UUID.randomUUID();db.update("INSERT INTO users(id,name,mobileno,password_hash) VALUES (?,'Platform owner',?,?)",id,mobile,encoder.encode(password));db.update("INSERT INTO platform_root(singleton,user_id) VALUES (1,?)",id);};}
}
