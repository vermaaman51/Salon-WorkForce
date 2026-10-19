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
    private final AdminRepository repository;
    private final ReportDates reportDates;
    private final PasswordEncoder passwords; private final TenantPlans plans;private final com.salon.repository.OrderPages pages;
    public AdminService(AdminRepository repository,PasswordEncoder passwords,TenantPlans plans,com.salon.repository.OrderPages pages,ReportDates reportDates) {
        this.repository=repository;this.reportDates=reportDates;
        this.passwords=passwords;this.plans=plans;this.pages=pages;
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
    public Map<String,Object> session(Authentication auth) {
        UUID t=tenant(auth);
        var result=new LinkedHashMap<String,Object>(repository.findSession(t, CurrentUser.id(auth)));result.put("plan",plans.metadata(t));return result;
    }
    public Object stores(Authentication auth) {
        return repository.findStores(tenant(auth));
    }
    public Object sales(Authentication auth,java.time.LocalDate requestedDate,java.time.LocalDate from,java.time.LocalDate to,UUID storeId) {return sales(auth,requestedDate,from,to,storeId,0);}
    public Object sales(Authentication auth,java.time.LocalDate requestedDate,java.time.LocalDate from,java.time.LocalDate to,UUID storeId,int page) {
        UUID tenantId=tenant(auth);
        var range=reportDates.resolve(requestedDate,from,to);from=range.from();to=range.to();var today=range.today();var minDate=range.minDate();var zone=range.zone();
        if(storeId!=null&&!repository.storeBelongsToTenant(storeId,tenantId))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Store not found.");
        var result=pages.list(tenantId,storeId,new com.salon.repository.OrderPages.Query(page,"total",null,null,null,null,null),true,from.atStartOfDay(),to.plusDays(1).atStartOfDay(),true);
        result.put("orders",result.remove("items"));result.put("date",from.toString());result.put("from",from.toString());result.put("to",to.toString());result.put("today",today.toString());result.put("minDate",minDate.toString());result.put("maxRangeDays",30);result.put("timezone",zone.getId());result.put("storeId",storeId==null?"":storeId.toString());return result;    }
    @Transactional
    public void addStore(Authentication auth,StoreInput input) {
        UUID t=tenant(auth);plans.reserve(t,"stores");repository.insertStore(UUID.randomUUID(), input.name().trim(), input.city().trim(), input.address().trim(), t,com.salon.utility.GstNumber.normalize(input.gstNumber()==null?repository.tenantGstNumber(t):input.gstNumber()));
    }
    @Transactional
    public void editStore(Authentication auth,UUID id,StoreInput input) {
        UUID t=tenant(auth);
        store(id,t);
        repository.updateStore(input.name().trim(), input.city().trim(), input.address().trim(), id, t,com.salon.utility.GstNumber.normalize(input.gstNumber()==null?repository.storeGstNumber(id,t):input.gstNumber()));
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
        plans.reserve(t,"STORE_MANAGER");
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
