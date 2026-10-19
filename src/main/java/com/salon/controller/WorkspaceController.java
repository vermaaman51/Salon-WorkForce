package com.salon.controller;

import com.salon.constant.Roles;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.core.io.*;
import org.springframework.http.*;

@Controller
@Profile("!legacy")
public class WorkspaceController {
    @GetMapping( {
        "/","/index.html"
    }) String home() {
        return "redirect:/workspace";
    }
    @GetMapping("/workspace") Object workspace(Authentication auth) {
        if(auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_ROOT"))) return "redirect:/root";
        if(auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_"+Roles.ADMIN))) return "redirect:/dashboard";
        if(auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_"+Roles.STORE_MANAGER))) return "redirect:/manager/dashboard";
        if(auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_"+Roles.RECEPTIONIST))) return "redirect:/reception/dashboard";
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body("<h1>Signed in</h1><p>Your account does not have ADMIN access. Contact your administrator.</p><a href='/login'>Sign in</a>");
    }
    @GetMapping("/login")
    @ResponseBody ResponseEntity<Resource> login(Authentication auth) {
        if(auth!=null && auth.isAuthenticated() && !(auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken))
            return ResponseEntity.status(HttpStatus.FOUND).location(java.net.URI.create("/workspace")).cacheControl(CacheControl.noStore()).build();
        return html("login.html");
    }
    @GetMapping("/root") @ResponseBody ResponseEntity<Resource> root() { return html("root.html"); }
    @GetMapping("/dashboard")
    @ResponseBody ResponseEntity<Resource> dashboard() {
        return html("admin.html");
    }
    @GetMapping("/manager/dashboard")
    @ResponseBody ResponseEntity<Resource> managerDashboard() {
        return html("manager.html");
    }
    @GetMapping("/manager/appointments")
    @ResponseBody ResponseEntity<Resource> managerAppointments() { return html("manager.html"); }
    @GetMapping("/reception/dashboard")
    @ResponseBody ResponseEntity<Resource> receptionDashboard() {
        return html("reception.html");
    }
    @GetMapping("/auth/csrf")
    @ResponseBody ResponseEntity<?> csrf(CsrfToken token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("token",token.getToken(),"header",token.getHeaderName()));
    }
    private ResponseEntity<Resource> html(String file) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.TEXT_HTML).body(new ClassPathResource("static/"+file));
    }
}
