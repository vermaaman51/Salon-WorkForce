package com.salon.service;

import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import com.salon.utility.PasswordPolicy;

@Service
public class TenantSubscriptions {
    private final JdbcTemplate db;
    private final PasswordEncoder passwords;
    public TenantSubscriptions(JdbcTemplate db, PasswordEncoder passwords) { this.db=db; this.passwords=passwords; }
    public boolean isRoot(UUID id) { return db.queryForObject("SELECT count(*) FROM platform_root WHERE user_id=?",Long.class,id)>0; }
    public boolean allowed(UUID id) {
        return isRoot(id)||db.queryForObject("SELECT count(*) FROM user_role ur JOIN tenant t ON t.id=ur.tenant_id WHERE ur.user_id=? AND (t._is_active=FALSE OR (t.subscription_expires_at IS NOT NULL AND t.subscription_expires_at<=CURRENT_TIMESTAMP))",Long.class,id)==0;
    }
    public List<Map<String,Object>> subscriptions(UUID id) { return db.queryForList("SELECT DISTINCT t.id,t.tenant_name,t.segment_type,t.subscription_expires_at FROM tenant t JOIN user_role ur ON ur.tenant_id=t.id WHERE ur.user_id=?",id); }
    public List<Map<String,Object>> tenants() { return db.queryForList("SELECT * FROM tenant ORDER BY tenant_name,id"); }
    public record TenantInput(String tenantName,String country,Boolean active,String segmentType,Instant expiresAt,String username,String gstNumber) {
        public TenantInput(String name,String country,Boolean active,String segment,Instant expiry,String username) { this(name,country,active,segment,expiry,username,null); }
        public TenantInput(String name,String country,Boolean active,String segment,Instant expiry) { this(name,country,active,segment,expiry,null,null); }
    }
    public record AdminInput(String name,String email,String mobileNo,String address,String password,UUID tenantId) {
        public AdminInput(String name,String email,String mobile,String address,String password) { this(name,email,mobile,address,password,null); }
    }
    private boolean validText(String value) { return value!=null&&!value.isBlank()&&value.length()<=255; }
    private void validate(TenantInput v) {
        if(!validText(v.tenantName())||v.country()==null||v.country().length()>255||v.active()==null||
            (v.username()!=null&&v.username().length()>255)||!Set.of("BASE","PRO","PRO_PLUS").contains(Objects.toString(v.segmentType(),"")))
            bad("Check tenant name, username, country, active status and segment.");
    }
    @Transactional public UUID save(UUID id,TenantInput v) {
        validate(v);
        String gst=com.salon.utility.GstNumber.normalize(v.gstNumber());
        var expiry=v.expiresAt()==null?null:OffsetDateTime.ofInstant(v.expiresAt(),ZoneOffset.UTC);
        if(id==null) {
            id=UUID.randomUUID();
            db.update("INSERT INTO tenant(id,tenant_name,username,country,_is_active,segment_type,subscription_expires_at,gst_number) VALUES (?,?,?,?,?,?,?,?)",id,v.tenantName().trim(),v.username(),v.country(),v.active(),v.segmentType(),expiry,gst);
        } else if(db.update("UPDATE tenant SET tenant_name=?,username=COALESCE(?,username),country=?,_is_active=?,segment_type=?,subscription_expires_at=?,gst_number=? WHERE id=?",v.tenantName().trim(),v.username(),v.country(),v.active(),v.segmentType(),expiry,gst,id)==0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Tenant not found");
        return id;
    }
    public List<Map<String,Object>> allAdmins() {
        return db.queryForList("SELECT ur.id AS assignment_id,u.id,u.name,u.email,u.mobileno,u.address,t.id AS tenant_id,t.tenant_name,t._is_active,t.subscription_expires_at FROM users u JOIN user_role ur ON ur.user_id=u.id JOIN role r ON r.id=ur.role_id JOIN tenant t ON t.id=ur.tenant_id WHERE r.roletype='ADMIN' ORDER BY u.name,t.tenant_name,ur.id");
    }
    public List<Map<String,Object>> admins(UUID tenant) {
        return db.queryForList("SELECT u.id,u.name,u.email,u.mobileno,u.address FROM users u JOIN user_role ur ON ur.user_id=u.id JOIN role r ON r.id=ur.role_id WHERE ur.tenant_id=? AND r.roletype='ADMIN'",tenant);
    }
    @Transactional public void admin(UUID tenant,UUID id,AdminInput v) {
        boolean changePassword=v.password()!=null&&!v.password().isBlank();
        if(!validText(v.name())||!validText(v.email())||!v.email().matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")||v.mobileNo()==null||!v.mobileNo().matches("[0-9]{10}")||!validText(v.address())||
            (id==null&&!changePassword)||(changePassword&&!PasswordPolicy.isValid(v.password())))
            bad("Provide name, email, 10-digit mobile and address. Passwords require 12+ characters (maximum 72 bytes).");
        UUID target=v.tenantId()==null?tenant:v.tenantId();
        // Lock in stable order when moving an assignment between tenants.
        for(UUID scope:new TreeSet<>(List.of(tenant,target))) {
            if(db.queryForList("SELECT id FROM tenant WHERE id=? FOR UPDATE",scope).isEmpty())
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Tenant not found");
        }
        if(id==null) {
            if(!target.equals(tenant))bad("Selected tenant does not match the create request.");
            id=UUID.randomUUID();
            db.update("INSERT INTO users(id,name,email,mobileno,address,password_hash) VALUES (?,?,?,?,?,?)",id,v.name().trim(),v.email().trim(),v.mobileNo(),v.address().trim(),passwords.encode(v.password()));
            var roles=db.queryForList("SELECT id FROM role WHERE roletype='ADMIN'",UUID.class);
            UUID role=roles.isEmpty()?UUID.randomUUID():roles.get(0);
            if(roles.isEmpty())db.update("INSERT INTO role(id,roletype) VALUES (?,'ADMIN')",role);
            db.update("INSERT INTO user_role(id,user_id,role_id,tenant_id) VALUES (?,?,?,?)",UUID.randomUUID(),id,role,tenant);
        } else {
            UUID existingId=id;
            if(admins(tenant).stream().noneMatch(a->a.get("id").equals(existingId)))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Admin not found");
            if(isRoot(id))bad("Cannot modify ROOT here");
            if(db.queryForObject("SELECT count(*) FROM user_role WHERE user_id=? AND tenant_id<>?",Long.class,id,tenant)>0)
                bad("This account is shared with another tenant and cannot be edited here.");
            if(!target.equals(tenant)) {
                if(db.queryForObject("SELECT count(*) FROM user_role ur JOIN role r ON r.id=ur.role_id WHERE ur.user_id=? AND (r.roletype<>'ADMIN' OR ur.store_id IS NOT NULL)",Long.class,id)>0)
                    bad("Remove this admin's other store or staff assignments before changing tenant.");
                db.update("UPDATE user_role SET tenant_id=? WHERE user_id=? AND tenant_id=? AND role_id IN (SELECT id FROM role WHERE roletype='ADMIN')",target,id,tenant);
            }
            db.update("UPDATE users SET name=?,email=?,mobileno=?,address=? WHERE id=?",v.name().trim(),v.email().trim(),v.mobileNo(),v.address().trim(),id);
            if(changePassword)db.update("UPDATE users SET password_hash=? WHERE id=?",passwords.encode(v.password()),id);
        }
    }
    @Transactional public void removeAdmin(UUID tenant,UUID id) {
        if(isRoot(id))bad("Cannot remove ROOT");
        db.update("DELETE FROM user_role WHERE tenant_id=? AND user_id=? AND role_id IN (SELECT id FROM role WHERE roletype='ADMIN')",tenant,id);
    }
    private static void bad(String message) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }
}
