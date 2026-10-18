package com.salon.legacy.controller;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.http.*;
import org.springframework.core.io.*;
@Controller
@Profile("legacy")
public class PageController {
 private boolean signedIn(Authentication auth){return auth!=null&&auth.isAuthenticated()&&!(auth instanceof AnonymousAuthenticationToken);}
 @GetMapping({"/","/index.html"}) public String home(Authentication auth){return "redirect:"+(signedIn(auth)?"/dashboard":"/login");}
 @GetMapping("/login") @ResponseBody public ResponseEntity<?> login(Authentication auth){return signedIn(auth)?ResponseEntity.status(302).location(java.net.URI.create("/dashboard")).build():html("login.html");}
 @GetMapping("/signup") @ResponseBody public ResponseEntity<Resource> signup(){return html("signup.html");}
 @GetMapping("/dashboard") @ResponseBody public ResponseEntity<Resource> dashboard(){return html("dashboard.html");}
 private ResponseEntity<Resource> html(String file){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.TEXT_HTML).body(new ClassPathResource("static/"+file));}
}

