package com.salon.controller;

import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.salon.dto.StoreManagerRequests.*;
import com.salon.service.StoreManagerService;

@RestController
@RequestMapping("/api/manager")
public class StoreManagerController {
    private final StoreManagerService service;
    public StoreManagerController(StoreManagerService service) {
        this.service=service;
    }
    @GetMapping("/session")
    public Object session(Authentication auth) {
        return service.session(auth);
    }
    @GetMapping("/orders")
    public Object orders(Authentication auth,@RequestParam UUID storeId,@ModelAttribute com.salon.repository.OrderPages.Query query) {
        return service.orders(auth, storeId,query);
    }
    @GetMapping("/sales")
    public Object sales(Authentication auth,@RequestParam UUID storeId,@ModelAttribute com.salon.repository.OrderPages.Query query,
        @RequestParam(required=false) @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate from,
        @RequestParam(required=false) @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate to) {
        return service.sales(auth,storeId,query,from,to);
    }
    @GetMapping("/{category:receptionists|experts}")
    public Object list(Authentication auth,@PathVariable String category,@RequestParam UUID storeId) {
        return service.list(auth, category, storeId);
    }
    @PostMapping("/{category:receptionists|experts}")
    @ResponseStatus(HttpStatus.CREATED)
    public void create(Authentication auth,@PathVariable String category,@RequestParam UUID storeId,@Valid @RequestBody StaffInput input) {
        service.create(auth, category, storeId, input);
    }
    @PutMapping("/{category:receptionists|experts}/{id}")
    public void update(Authentication auth,@PathVariable String category,@PathVariable UUID id,@RequestParam UUID storeId,@Valid @RequestBody StaffInput input) {
        service.update(auth, category, id, storeId, input);
    }
    @DeleteMapping("/{category:receptionists|experts}/{id}")
    public void remove(Authentication auth,@PathVariable String category,@PathVariable UUID id,@RequestParam UUID storeId) {
        service.remove(auth, category, id, storeId);
    }
}
