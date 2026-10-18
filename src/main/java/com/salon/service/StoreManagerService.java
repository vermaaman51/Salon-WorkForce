package com.salon.service;

import com.salon.repository.StoreManagerRepository;
import com.salon.dto.StoreManagerRequests.*;
import com.salon.constant.*;
import com.salon.utility.CurrentUser;
import com.salon.utility.PasswordPolicy;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
@PreAuthorize("hasRole('STORE_MANAGER')")
public class StoreManagerService {
    private final StoreManagerRepository repository;
    private final PasswordEncoder passwords;
    public StoreManagerService(StoreManagerRepository repository, PasswordEncoder passwords) {
        this.repository=repository;
        this.passwords=passwords;
    }
    private String role(String category) {
        return switch(category) {
            case "receptionists" -> Roles.RECEPTIONIST;
            case "experts" -> Roles.EXPERT;
            default -> throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Staff category not found.");
        };
    }
    private UUID scope(Authentication auth, UUID storeId) {
        var tenants=repository.findManagerTenants(CurrentUser.id(auth), storeId);
        if(tenants.size()!=1) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Assigned store not found.");
        return tenants.get(0);
    }
    private UUID staff(UUID assignmentId, UUID storeId, UUID tenantId, String role) {
        var rows=repository.lockStaffUser(assignmentId, storeId, tenantId, role);
        if(rows.size()!=1) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Staff member not found.");
        return rows.get(0);
    }
    private void validate(StaffInput input, UUID user,String type) {
        String value=input.password();
        if(!Roles.EXPERT.equals(type)&&(value==null||value.isBlank())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"A login password is required.");
        if(value!=null&&!value.isBlank()&&(!PasswordPolicy.isValid(value))) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Password must be at least 12 characters and at most 72 UTF-8 bytes.");
        var duplicates=user==null
        ? repository.findDuplicateIdentity(input.mobileNo(), input.email().trim().toLowerCase(Locale.ROOT))
        : repository.findOtherDuplicateIdentity(input.mobileNo(), input.email().trim().toLowerCase(Locale.ROOT), user);
        if(!duplicates.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT,"That mobile number or email already belongs to an account.");
    }
    public Object session(Authentication auth) {
        UUID user=CurrentUser.id(auth);
        var result=new LinkedHashMap<String,Object>(repository.findUser(user));
        result.put("stores",repository.findAssignedStores(user));
        return result;
    }
    public Object list(Authentication auth,String category,UUID storeId) {
        UUID tenant=scope(auth,storeId);
        return repository.findStaff(storeId, tenant, role(category));
    }
    public Object orders(Authentication auth,UUID storeId) {
        UUID tenant=scope(auth,storeId);
        return repository.findOrders(storeId, tenant);
    }
    @Transactional
    public void create(Authentication auth,String category,UUID storeId,StaffInput input) {
        UUID tenant=scope(auth,storeId);
        String type=role(category);
        validate(input,null,type);
        UUID user=UUID.randomUUID();
        repository.insertUser(user, input.name().trim(), input.email().trim().toLowerCase(Locale.ROOT), input.mobileNo(), input.address().trim(), input.password()==null||input.password().isBlank()?null:passwords.encode(input.password()));
        var roles=repository.findRoles(type);
        UUID roleId;
        if(roles.isEmpty()) {
            roleId=UUID.randomUUID();
            repository.insertRole(roleId, type);
        }
        else roleId=roles.get(0);
        repository.insertAssignment(UUID.randomUUID(), user, roleId, storeId, tenant);
    }
    @Transactional
    public void update(Authentication auth,String category,UUID id,UUID storeId,StaffInput input) {
        UUID tenant=scope(auth,storeId);
        UUID user=staff(id,storeId,tenant,role(category));
        if(repository.countOtherAssignments(user, id)>0) throw new ResponseStatusException(HttpStatus.CONFLICT,"This account has other role assignments and cannot be edited from this store.");
        validate(input,user,role(category));
        repository.updateUser(input.name().trim(), input.email().trim().toLowerCase(Locale.ROOT), input.mobileNo(), input.address().trim(), user);
        if(input.password()!=null&&!input.password().isBlank()) repository.updatePassword(passwords.encode(input.password()), user);
    }
    @Transactional
    public void remove(Authentication auth,String category,UUID id,UUID storeId) {
        UUID tenant=scope(auth,storeId);
        staff(id,storeId,tenant,role(category));
        repository.deleteStaffAssignment(id, storeId, tenant);
    }
}

