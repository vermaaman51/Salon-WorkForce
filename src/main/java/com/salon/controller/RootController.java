package com.salon.controller;
import com.salon.service.TenantSubscriptions;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import com.salon.utility.CurrentUser;
@RestController
public class RootController {
 private final TenantSubscriptions service;
 public RootController(TenantSubscriptions service){this.service=service;}
 @PreAuthorize("hasRole('ADMIN')") @GetMapping("/api/subscription") public Object subscription(Authentication a){return service.subscriptions(CurrentUser.id(a));}
 @PreAuthorize("hasRole('ROOT')") @GetMapping("/api/root/admins") public Object allAdmins(){return service.allAdmins();}
 @PreAuthorize("hasRole('ROOT')") @GetMapping("/api/root/tenants") public Object tenants(){return service.tenants();}
 @PreAuthorize("hasRole('ROOT')") @PostMapping("/api/root/tenants") public Object create(@RequestBody TenantSubscriptions.TenantInput v){return Map.of("id",service.save(null,v));}
 @PreAuthorize("hasRole('ROOT')") @PutMapping("/api/root/tenants/{id}") public Object update(@PathVariable UUID id,@RequestBody TenantSubscriptions.TenantInput v){return Map.of("id",service.save(id,v));}
 @PreAuthorize("hasRole('ROOT')") @GetMapping("/api/root/tenants/{id}/admins") public Object admins(@PathVariable UUID id){return service.admins(id);}
 @PreAuthorize("hasRole('ROOT')") @PostMapping("/api/root/tenants/{id}/admins") public void createAdmin(@PathVariable UUID id,@RequestBody TenantSubscriptions.AdminInput v){service.admin(id,null,v);}
 @PreAuthorize("hasRole('ROOT')") @PutMapping("/api/root/tenants/{id}/admins/{user}") public void editAdmin(@PathVariable UUID id,@PathVariable UUID user,@RequestBody TenantSubscriptions.AdminInput v){service.admin(id,user,v);}
 @PreAuthorize("hasRole('ROOT')") @DeleteMapping("/api/root/tenants/{id}/admins/{user}") public void removeAdmin(@PathVariable UUID id,@PathVariable UUID user){service.removeAdmin(id,user);}
}
