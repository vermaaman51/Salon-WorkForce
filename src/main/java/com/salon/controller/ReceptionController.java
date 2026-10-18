package com.salon.controller;

import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.salon.dto.ReceptionRequests.*;
import com.salon.service.ReceptionService;

@RestController
@RequestMapping("/api/reception")
public class ReceptionController {
    private final ReceptionService service;
    public ReceptionController(ReceptionService service) {
        this.service=service;
    }
    @GetMapping("/session")
    public Object session(Authentication auth) {
        return service.session(auth);
    }
    @GetMapping("/experts")
    public Object experts(Authentication auth,@RequestParam UUID storeId) {
        return service.experts(auth, storeId);
    }
    @GetMapping("/orders")
    public Object orders(Authentication auth,@RequestParam UUID storeId) {
        return service.orders(auth, storeId);
    }
    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public Object create(Authentication auth,@RequestParam UUID storeId,@Valid @RequestBody NewOrder input) {
        return service.create(auth, storeId, input);
    }
    @PatchMapping("/orders/{id}/assign")
    public void assign(Authentication auth,@PathVariable UUID id,@RequestParam UUID storeId,@Valid @RequestBody Assignment input) {
        service.assign(auth, id, storeId, input);
    }
    @PatchMapping("/orders/{id}/checkout")
    public void checkout(Authentication auth,@PathVariable UUID id,@RequestParam UUID storeId,@Valid @RequestBody Checkout input) {
        service.checkout(auth, id, storeId, input);
    }
    @PatchMapping("/orders/{id}/payment")
    public void paid(Authentication auth,@PathVariable UUID id,@RequestParam UUID storeId,@Valid @RequestBody Payment input) {
        service.paid(auth, id, storeId,input);
    }
}
