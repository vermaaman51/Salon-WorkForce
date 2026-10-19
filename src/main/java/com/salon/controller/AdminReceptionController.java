package com.salon.controller;
import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import com.salon.service.AdminReceptionService;
import com.salon.dto.AdminRequests.ManagerInput;
@RestController @RequestMapping("/api/admin/receptionists")
public class AdminReceptionController {
 private final AdminReceptionService service;
 public AdminReceptionController(AdminReceptionService service){this.service=service;}
 @GetMapping public Object list(Authentication auth){return service.list(auth);}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) public void create(Authentication auth,@Valid @RequestBody ManagerInput input){service.save(auth,null,input);}
 @PutMapping("/{id}") public void update(Authentication auth,@PathVariable UUID id,@Valid @RequestBody ManagerInput input){service.save(auth,id,input);}
 @DeleteMapping("/{id}") public void remove(Authentication auth,@PathVariable UUID id){service.remove(auth,id);}
}
