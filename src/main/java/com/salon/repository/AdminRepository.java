package com.salon.repository;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdminRepository {
    private final JdbcTemplate db;
    public AdminRepository(JdbcTemplate db) {
        this.db=db;
    }
    public List<UUID> findAdminTenants(UUID userId) {
        return db.queryForList("SELECT DISTINCT ur.tenant_id FROM user_role ur JOIN role r ON r.id=ur.role_id WHERE ur.user_id=? AND r.roletype='ADMIN'", UUID.class, userId);
    }
    public List<Map<String,Object>> lockStore(UUID storeId, UUID tenantId) {
        return db.queryForList("SELECT id FROM store WHERE id=? AND tenant_id=? FOR UPDATE", storeId, tenantId);
    }
    public List<Map<String,Object>> lockManager(UUID assignmentId, UUID tenantId) {
        return db.queryForList("SELECT ur.user_id, ur.store_id FROM user_role ur JOIN role r ON r.id=ur.role_id WHERE ur.id=? AND ur.tenant_id=? AND r.roletype='STORE_MANAGER' FOR UPDATE", assignmentId, tenantId);
    }
    public Long countMobile(String mobile) {
        return db.queryForObject("SELECT count(*) FROM users WHERE mobileno=?", Long.class, mobile);
    }
    public Long countOtherMobile(String mobile, UUID userId) {
        return db.queryForObject("SELECT count(*) FROM users WHERE mobileno=? AND id<>?", Long.class, mobile, userId);
    }
    public Map<String,Object> findSession(UUID tenantId, UUID userId) {
        return db.queryForMap("SELECT t.id AS tenant_id,t.tenant_name,t.gst_number,u.name,u.email FROM tenant t CROSS JOIN users u WHERE t.id=? AND u.id=?", tenantId, userId);
    }
    public List<Map<String,Object>> findStores(UUID tenantId) {
        return db.queryForList("SELECT id,name,city,address,gst_number FROM store WHERE tenant_id=? ORDER BY name,id", tenantId);
    }
    public boolean storeBelongsToTenant(UUID storeId,UUID tenantId) {
        return db.queryForObject("SELECT count(*) FROM store WHERE id=? AND tenant_id=?",Long.class,storeId,tenantId)==1;
    }
    public List<Map<String,Object>> findDailySales(UUID tenantId, java.time.LocalDateTime start, java.time.LocalDateTime end,UUID storeId) {
        String sql="SELECT id,store_id,customer_name,service AS service_name,amount,order_status,payment_status FROM orders WHERE tenant_id=? AND order_type='APPOINTMENT' AND appointment_at>=? AND appointment_at<?";
        var args=new ArrayList<Object>(List.of(tenantId,start,end));
        if(storeId!=null){sql+=" AND store_id=?";args.add(storeId);}
        return db.queryForList(sql+" ORDER BY appointment_at DESC,id",args.toArray());
    }
    public String tenantGstNumber(UUID tenantId){return db.queryForObject("SELECT gst_number FROM tenant WHERE id=?",String.class,tenantId);}
    public String storeGstNumber(UUID id,UUID tenantId){return db.queryForObject("SELECT gst_number FROM store WHERE id=? AND tenant_id=?",String.class,id,tenantId);}
    public int insertStore(UUID id, String name, String city, String address, UUID tenantId,String gstNumber) {
        return db.update("INSERT INTO store(id,name,city,address,tenant_id,gst_number) VALUES (?,?,?,?,?,?)", id, name, city, address, tenantId,gstNumber);
    }
    public int updateStore(String name, String city, String address, UUID id, UUID tenantId,String gstNumber) {
        return db.update("UPDATE store SET name=?,city=?,address=?,gst_number=? WHERE id=? AND tenant_id=?", name, city, address,gstNumber, id, tenantId);
    }
    public Long countStoreAssignments(UUID storeId) {
        return db.queryForObject("SELECT count(*) FROM user_role WHERE store_id=?", Long.class, storeId);
    }
    public int deleteStore(UUID storeId, UUID tenantId) {
        return db.update("DELETE FROM store WHERE id=? AND tenant_id=?", storeId, tenantId);
    }
    public List<Map<String,Object>> findManagers(UUID tenantId) {
        return db.queryForList("SELECT ur.id,u.name,u.email,u.mobileno AS mobile_no,u.address,s.id AS store_id,s.name AS store_name FROM user_role ur JOIN role r ON r.id=ur.role_id JOIN users u ON u.id=ur.user_id JOIN store s ON s.id=ur.store_id AND s.tenant_id=ur.tenant_id WHERE ur.tenant_id=? AND r.roletype='STORE_MANAGER' ORDER BY u.name,ur.id", tenantId);
    }
    public Long countEmail(String email) {
        return db.queryForObject("SELECT count(*) FROM users WHERE lower(email)=?", Long.class, email);
    }
    public int insertUser(UUID id, String name, String email, String mobile, String address, String passwordHash) {
        return db.update("INSERT INTO users(id,name,email,mobileno,address,password_hash) VALUES (?,?,?,?,?,?)", id, name, email, mobile, address, passwordHash);
    }
    public List<UUID> findManagerRoles() {
        return db.queryForList("SELECT id FROM role WHERE roletype='STORE_MANAGER' ORDER BY id", UUID.class);
    }
    public int insertManagerRole(UUID id) {
        return db.update("INSERT INTO role(id,roletype) VALUES (?,'STORE_MANAGER')", id);
    }
    public int insertAssignment(UUID id, UUID userId, UUID roleId, UUID storeId, UUID tenantId) {
        return db.update("INSERT INTO user_role(id,user_id,role_id,store_id,tenant_id) VALUES (?,?,?,?,?)", id, userId, roleId, storeId, tenantId);
    }
    public Long countOtherTenantAssignments(UUID userId, UUID tenantId) {
        return db.queryForObject("SELECT count(*) FROM user_role WHERE user_id=? AND tenant_id<>?", Long.class, userId, tenantId);
    }
    public Long countOtherEmail(String email, UUID userId) {
        return db.queryForObject("SELECT count(*) FROM users WHERE lower(email)=? AND id<>?", Long.class, email, userId);
    }
    public int updateUser(String name, String email, String mobile, String address, UUID userId) {
        return db.update("UPDATE users SET name=?,email=?,mobileno=?,address=? WHERE id=?", name, email, mobile, address, userId);
    }
    public int updatePassword(String passwordHash, UUID userId) {
        return db.update("UPDATE users SET password_hash=? WHERE id=?", passwordHash, userId);
    }
    public int updateManagerStore(UUID storeId, UUID assignmentId, UUID tenantId) {
        return db.update("UPDATE user_role SET store_id=? WHERE id=? AND tenant_id=?", storeId, assignmentId, tenantId);
    }
    public int deleteManagerAssignment(UUID assignmentId, UUID tenantId) {
        return db.update("DELETE FROM user_role WHERE id=? AND tenant_id=?", assignmentId, tenantId);
    }
    public int deleteUnassignedUser(UUID userId) {
        return db.update("DELETE FROM users WHERE id=? AND NOT EXISTS (SELECT 1 FROM user_role WHERE user_id=?)", userId, userId);
    }
}
