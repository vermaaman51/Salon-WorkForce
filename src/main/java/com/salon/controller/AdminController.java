package com.salon.controller;

import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.salon.dto.AdminRequests.*;
import com.salon.service.AdminService;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminService service;
    public AdminController(AdminService service) {
        this.service=service;
    }
    @GetMapping("/session")
    public Object session(Authentication auth,jakarta.servlet.http.HttpSession session) {
        var result=service.session(auth);result.put("subscriptions",session.getAttribute("subscriptionSnapshot")==null?java.util.List.of():session.getAttribute("subscriptionSnapshot"));return result;
    }
    @GetMapping("/stores")
    public Object stores(Authentication auth) {
        return service.stores(auth);
    }
    @GetMapping("/sales")
    public Object sales(Authentication auth,
        @RequestParam(required=false) @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate date,
        @RequestParam(name="from",required=false) @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate from,
        @RequestParam(name="to",required=false) @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate to,
        @RequestParam(required=false) UUID storeId,@RequestParam(defaultValue="0") int page) {
        return service.sales(auth,date,from,to,storeId,page);
    }
    @PostMapping("/stores")
    @ResponseStatus(HttpStatus.CREATED)
    public void addStore(Authentication auth,@Valid @RequestBody StoreInput input) {
        service.addStore(auth, input);
    }
    @PutMapping("/stores/{id}")
    public void editStore(Authentication auth,@PathVariable UUID id,@Valid @RequestBody StoreInput input) {
        service.editStore(auth, id, input);
    }
    @DeleteMapping("/stores/{id}")
    public void deleteStore(Authentication auth,@PathVariable UUID id) {
        service.deleteStore(auth, id);
    }
    @GetMapping("/managers")
    public Object managers(Authentication auth) {
        return service.managers(auth);
    }
    @PostMapping("/managers")
    @ResponseStatus(HttpStatus.CREATED)
    public void addManager(Authentication auth,@Valid @RequestBody ManagerInput input) {
        service.addManager(auth, input);
    }
    @PutMapping("/managers/{id}")
    public void editManager(Authentication auth,@PathVariable UUID id,@Valid @RequestBody ManagerInput input) {
        service.editManager(auth, id, input);
    }
    @DeleteMapping("/managers/{id}")
    public void deleteManager(Authentication auth,@PathVariable UUID id) {
        service.deleteManager(auth, id);
    }
}
