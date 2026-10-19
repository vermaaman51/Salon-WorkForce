package com.salon.service;
import java.util.*;
import com.salon.repository.*;
import com.salon.dto.AdminRequests.ManagerInput;
import com.salon.utility.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@Service @PreAuthorize("hasRole('ADMIN')")
public class AdminReceptionService {
 private final AdminRepository admins;private final StoreManagerRepository staff;private final TenantPlans plans;private final JdbcTemplate db;private final PasswordEncoder passwords;
 public AdminReceptionService(AdminRepository admins,StoreManagerRepository staff,TenantPlans plans,JdbcTemplate db,PasswordEncoder passwords){this.admins=admins;this.staff=staff;this.plans=plans;this.db=db;this.passwords=passwords;}
 private UUID tenant(Authentication auth){var rows=admins.findAdminTenants(CurrentUser.id(auth));if(rows.size()!=1)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"A single Admin tenant is required.");return rows.get(0);}
 public Object list(Authentication auth){return db.queryForList("SELECT ur.id,u.name,u.email,u.mobileno AS mobile_no,u.address,s.id AS store_id,s.name AS store_name FROM user_role ur JOIN users u ON u.id=ur.user_id JOIN role r ON r.id=ur.role_id JOIN store s ON s.id=ur.store_id AND s.tenant_id=ur.tenant_id WHERE ur.tenant_id=? AND r.roletype='RECEPTIONIST' ORDER BY u.name,ur.id",tenant(auth));}
 @Transactional public void save(Authentication auth,UUID assignment,ManagerInput input){UUID t=tenant(auth);if(assignment==null)plans.reserve(t,"RECEPTIONIST");if(admins.lockStore(input.storeId(),t).isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Store not found.");
  if(!PasswordPolicy.isValid(input.password()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Password requires 12+ characters and at most 72 UTF-8 bytes.");
  UUID user=null;if(assignment!=null){var rows=db.queryForList("SELECT ur.user_id FROM user_role ur JOIN role r ON r.id=ur.role_id WHERE ur.id=? AND ur.tenant_id=? AND r.roletype='RECEPTIONIST' FOR UPDATE",UUID.class,assignment,t);if(rows.size()!=1)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Receptionist not found.");user=rows.get(0);if(staff.countOtherAssignments(user,assignment)>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Shared account cannot be edited here.");}
  String email=input.email().trim().toLowerCase(Locale.ROOT);if(!(user==null?staff.findDuplicateIdentity(input.mobileNo(),email):staff.findOtherDuplicateIdentity(input.mobileNo(),email,user)).isEmpty())throw new ResponseStatusException(HttpStatus.CONFLICT,"Mobile or email already belongs to an account.");
  if(user==null){user=UUID.randomUUID();staff.insertUser(user,input.name().trim(),email,input.mobileNo(),input.address().trim(),passwords.encode(input.password()));var roles=staff.findRoles("RECEPTIONIST");UUID role=roles.isEmpty()?UUID.randomUUID():roles.get(0);if(roles.isEmpty())staff.insertRole(role,"RECEPTIONIST");staff.insertAssignment(UUID.randomUUID(),user,role,input.storeId(),t);}else{staff.updateUser(input.name().trim(),email,input.mobileNo(),input.address().trim(),user);staff.updatePassword(passwords.encode(input.password()),user);db.update("UPDATE user_role SET store_id=? WHERE id=? AND tenant_id=?",input.storeId(),assignment,t);}
 }
 @Transactional public void remove(Authentication auth,UUID id){db.update("DELETE FROM user_role WHERE id=? AND tenant_id=? AND role_id IN (SELECT id FROM role WHERE roletype='RECEPTIONIST')",id,tenant(auth));}
}
