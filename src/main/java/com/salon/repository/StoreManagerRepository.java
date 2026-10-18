package com.salon.repository;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class StoreManagerRepository {
    private final JdbcTemplate db;
    public StoreManagerRepository(JdbcTemplate db) {
        this.db=db;
    }
    public List<UUID> findManagerTenants(UUID userId, UUID storeId) {
        return db.queryForList("SELECT DISTINCT ur.tenant_id FROM user_role ur JOIN role r ON r.id=ur.role_id JOIN store s ON s.id=ur.store_id AND s.tenant_id=ur.tenant_id WHERE ur.user_id=? AND ur.store_id=? AND r.roletype='STORE_MANAGER'", UUID.class, userId, storeId);
    }
    public List<UUID> lockStaffUser(UUID assignmentId, UUID storeId, UUID tenantId, String role) {
        return db.queryForList("SELECT ur.user_id FROM user_role ur JOIN role r ON r.id=ur.role_id WHERE ur.id=? AND ur.store_id=? AND ur.tenant_id=? AND r.roletype=? FOR UPDATE", UUID.class, assignmentId, storeId, tenantId, role);
    }
    public List<Map<String,Object>> findDuplicateIdentity(String mobile, String email) {
        return db.queryForList("SELECT id FROM users WHERE mobileno=? OR lower(email)=?", mobile, email);
    }
    public List<Map<String,Object>> findOtherDuplicateIdentity(String mobile, String email, UUID userId) {
        return db.queryForList("SELECT id FROM users WHERE (mobileno=? OR lower(email)=?) AND id<>?", mobile, email, userId);
    }
    public Map<String,Object> findUser(UUID userId) {
        return db.queryForMap("SELECT name,email FROM users WHERE id=?", userId);
    }
    public List<Map<String,Object>> findAssignedStores(UUID userId) {
        return db.queryForList("SELECT DISTINCT s.id,s.name,s.city,t.tenant_name FROM user_role ur JOIN role r ON r.id=ur.role_id JOIN store s ON s.id=ur.store_id AND s.tenant_id=ur.tenant_id JOIN tenant t ON t.id=s.tenant_id WHERE ur.user_id=? AND r.roletype='STORE_MANAGER' ORDER BY s.name,s.id", userId);
    }
    public List<Map<String,Object>> findStaff(UUID storeId, UUID tenantId, String role) {
        return db.queryForList("SELECT ur.id,u.name,u.email,u.mobileno AS mobile_no,u.address FROM user_role ur JOIN role r ON r.id=ur.role_id JOIN users u ON u.id=ur.user_id WHERE ur.store_id=? AND ur.tenant_id=? AND r.roletype=? ORDER BY u.name,ur.id", storeId, tenantId, role);
    }
    public List<Map<String,Object>> findOrders(UUID storeId, UUID tenantId) {
        return db.queryForList("SELECT id,customer_name,service AS service_name,amount,order_status,payment_status,assignee FROM orders WHERE store_id=? AND tenant_id=? AND order_type='APPOINTMENT' ORDER BY created_at DESC,id", storeId, tenantId);
    }
    public int insertUser(UUID id, String name, String email, String mobile, String address, String passwordHash) {
        return db.update("INSERT INTO users(id,name,email,mobileno,address,password_hash) VALUES (?,?,?,?,?,?)", id, name, email, mobile, address, passwordHash);
    }
    public List<UUID> findRoles(String role) {
        return db.queryForList("SELECT id FROM role WHERE roletype=? ORDER BY id", UUID.class, role);
    }
    public int insertRole(UUID id, String role) {
        return db.update("INSERT INTO role(id,roletype) VALUES (?,?)", id, role);
    }
    public int insertAssignment(UUID id, UUID userId, UUID roleId, UUID storeId, UUID tenantId) {
        return db.update("INSERT INTO user_role(id,user_id,role_id,store_id,tenant_id) VALUES (?,?,?,?,?)", id, userId, roleId, storeId, tenantId);
    }
    public Long countOtherAssignments(UUID userId, UUID assignmentId) {
        return db.queryForObject("SELECT count(*) FROM user_role WHERE user_id=? AND id<>?", Long.class, userId, assignmentId);
    }
    public int updateUser(String name, String email, String mobile, String address, UUID userId) {
        return db.update("UPDATE users SET name=?,email=?,mobileno=?,address=? WHERE id=?", name, email, mobile, address, userId);
    }
    public int updatePassword(String passwordHash, UUID userId) {
        return db.update("UPDATE users SET password_hash=? WHERE id=?", passwordHash, userId);
    }
    public int deleteStaffAssignment(UUID assignmentId, UUID storeId, UUID tenantId) {
        return db.update("DELETE FROM user_role WHERE id=? AND store_id=? AND tenant_id=?", assignmentId, storeId, tenantId);
    }
}
