package com.salon.service;

import com.salon.repository.AdminRepository;
import com.salon.dto.AdminRequests.*;
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
@PreAuthorize("hasRole('ADMIN')")
public class AdminService {
    @org.springframework.beans.factory.annotation.Value("${TZ:Asia/Kolkata}")
    private String salesTimezone;
    private final AdminRepository repository;
    private final PasswordEncoder passwords;
    public AdminService(AdminRepository repository,PasswordEncoder passwords) {
        this.repository=repository;
        this.passwords=passwords;
    }
    private UUID tenant(Authentication auth) {
        var tenants=repository.findAdminTenants(CurrentUser.id(auth));
        if(tenants.size()!=1)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"A single ADMIN tenant assignment is required.");
        return tenants.get(0);
    }
    private void store(UUID id,UUID tenant) {
        if(repository.lockStore(id, tenant).isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Store not found.");
    }
    private Map<String,Object> manager(UUID id,UUID tenant) {
        var rows=repository.lockManager(id, tenant);
        if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Manager not found.");
        return rows.get(0);
    }
    private void password(String value,boolean required) {
        if(value==null||value.isBlank()) {
            if(required)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"A password of at least 12 characters is required.");
            return;
        }
        if(!PasswordPolicy.isValid(value))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Password must be at least 12 characters and at most 72 UTF-8 bytes.");
    }
    private void uniqueMobile(String mobile,UUID user) {
        Long count = user==null
        ? repository.countMobile(mobile.trim())
        : repository.countOtherMobile(mobile.trim(), user);
        if(count>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"That mobile number already belongs to an account.");
    }
    public Object session(Authentication auth) {
        UUID t=tenant(auth);
        return repository.findSession(t, CurrentUser.id(auth));
    }
    public Object stores(Authentication auth) {
        return repository.findStores(tenant(auth));
    }
    public Object sales(Authentication auth,java.time.LocalDate requestedDate,java.time.LocalDate from,java.time.LocalDate to,UUID storeId) {
        UUID tenantId=tenant(auth);
        var zone=java.time.ZoneId.of(salesTimezone);
        var today=java.time.LocalDate.now(zone);
        var minDate=today.minusMonths(1);
        if(requestedDate!=null&&(from!=null||to!=null))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Use either a date or a From/To range.");
        if((from==null)!=(to==null))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Select both From and To dates.");
        if(from==null){from=requestedDate==null?today:requestedDate;to=from;}
        if(from.isBefore(minDate)||from.isAfter(to)||to.isAfter(today)||java.time.temporal.ChronoUnit.DAYS.between(from,to)>=30)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose dates within the past month and a range of up to 30 days, including both dates.");
        if(storeId!=null&&!repository.storeBelongsToTenant(storeId,tenantId))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Store not found.");
        return Map.of("date",from.toString(),"from",from.toString(),"to",to.toString(),"today",today.toString(),"minDate",minDate.toString(),"maxRangeDays",30,"timezone",zone.getId(),"storeId",storeId==null?"":storeId.toString(),"orders",
            repository.findDailySales(tenantId,from.atStartOfDay(),to.plusDays(1).atStartOfDay(),storeId));
    }
    public void addStore(Authentication auth,StoreInput input) {
        repository.insertStore(UUID.randomUUID(), input.name().trim(), input.city().trim(), input.address().trim(), tenant(auth));
    }
    @Transactional
    public void editStore(Authentication auth,UUID id,StoreInput input) {
        UUID t=tenant(auth);
        store(id,t);
        repository.updateStore(input.name().trim(), input.city().trim(), input.address().trim(), id, t);
    }
    @Transactional
    public void deleteStore(Authentication auth,UUID id) {
        UUID t=tenant(auth);
        store(id,t);
        if(repository.countStoreAssignments(id)>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Reassign or remove this store's managers before deleting it.");
        repository.deleteStore(id, t);
    }
    public Object managers(Authentication auth) {
        return repository.findManagers(tenant(auth));
    }
    @Transactional
    public void addManager(Authentication auth,ManagerInput input) {
        UUID t=tenant(auth);
        store(input.storeId(),t);
        password(input.password(),true);
        uniqueMobile(input.mobileNo(),null);
        String email=input.email().trim().toLowerCase(Locale.ROOT);
        if(repository.countEmail(email)>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"That email already belongs to an account.");
        UUID user=UUID.randomUUID();
        repository.insertUser(user, input.name().trim(), email, input.mobileNo().trim(), input.address().trim(), passwords.encode(input.password()));
        var roles=repository.findManagerRoles();
        UUID role;
        if(roles.isEmpty()) {
            role=UUID.randomUUID();
            repository.insertManagerRole(role);
        }
        else role=roles.get(0);
        repository.insertAssignment(UUID.randomUUID(), user, role, input.storeId(), t);
    }
    @Transactional
    public void editManager(Authentication auth,UUID id,ManagerInput input) {
        UUID t=tenant(auth);
        store(input.storeId(),t);
        var existing=manager(id,t);
        UUID user=(UUID)existing.get("user_id");
        if(repository.countOtherTenantAssignments(user, t)>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"This account is shared with another tenant and cannot be edited here.");
        String email=input.email().trim().toLowerCase(Locale.ROOT);
        if(repository.countOtherEmail(email, user)>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"That email already belongs to an account.");
        password(input.password(),true);
        uniqueMobile(input.mobileNo(),user);
        repository.updateUser(input.name().trim(), email, input.mobileNo().trim(), input.address().trim(), user);
        if(input.password()!=null&&!input.password().isBlank())repository.updatePassword(passwords.encode(input.password()), user);
        repository.updateManagerStore(input.storeId(), id, t);
    }
    @Transactional
    public void deleteManager(Authentication auth,UUID id) {
        UUID t=tenant(auth);
        UUID user=(UUID)manager(id,t).get("user_id");
        repository.deleteManagerAssignment(id, t);
        repository.deleteUnassignedUser(user);
    }
}

