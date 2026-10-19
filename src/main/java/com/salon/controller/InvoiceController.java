package com.salon.controller;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import com.salon.service.InvoiceService;
@RestController
public class InvoiceController {
 private final InvoiceService service;
 public InvoiceController(InvoiceService service){this.service=service;}
 @GetMapping("/api/reception/orders/{id}/invoice") public Object reception(Authentication auth,@PathVariable UUID id,@RequestParam UUID storeId){return service.invoice(auth,storeId,id,"RECEPTIONIST");}
 @GetMapping("/api/manager/appointments/orders/{id}/invoice") public Object manager(Authentication auth,@PathVariable UUID id,@RequestParam UUID storeId){return service.invoice(auth,storeId,id,"STORE_MANAGER");}
}
