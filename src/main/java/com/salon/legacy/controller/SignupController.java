package com.salon.legacy.controller;
import org.springframework.context.annotation.Profile;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
@RestController
@Profile("legacy")
public class SignupController {
 private final JdbcTemplate db; private final PasswordEncoder encoder;
 public SignupController(JdbcTemplate db,PasswordEncoder encoder){this.db=db;this.encoder=encoder;}
 public record Signup(@NotBlank @Size(max=100) String name,@Pattern(regexp="[a-z0-9][a-z0-9._-]{2,63}") @NotNull String username,@NotNull @Size(min=12,max=72) String password){}
 @GetMapping("/auth/csrf") ResponseEntity<?> csrf(CsrfToken csrf){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("token",csrf.getToken(),"header",csrf.getHeaderName()));}
 @PostMapping("/signup") @ResponseStatus(HttpStatus.CREATED) void signup(@Valid @RequestBody Signup s){
  if(s.password.getBytes(StandardCharsets.UTF_8).length>72)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Password must be at most 72 UTF-8 bytes.");
  try{db.update("INSERT INTO staff_accounts(username,display_name,password_hash,role) VALUES (?,?,?,'RECEPTION')",s.username,s.name.trim(),encoder.encode(s.password));}catch(org.springframework.dao.DuplicateKeyException e){throw new ResponseStatusException(HttpStatus.CONFLICT,"That username is unavailable. Choose another.");}
 }
}


