package com.salon.repository;

import java.util.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ReceptionRepository {
    private final JdbcTemplate db;
    public ReceptionRepository(JdbcTemplate db) {
        this.db=db;
    }
    public List<UUID> findWorkflowTenants(UUID userId, UUID storeId,String role) {
        return db.queryForList("SELECT DISTINCT ur.tenant_id FROM user_role ur JOIN role r ON r.id=ur.role_id JOIN store s ON s.id=ur.store_id AND s.tenant_id=ur.tenant_id WHERE ur.user_id=? AND ur.store_id=? AND r.roletype=?", UUID.class, userId, storeId,role);
    }
    public List<Map<String,Object>> lockOrder(UUID orderId, UUID storeId, UUID tenantId) {
        return db.queryForList("SELECT * FROM orders WHERE id=? AND store_id=? AND tenant_id=? FOR UPDATE", orderId, storeId, tenantId);
    }
    public Map<String,Object> findUser(UUID userId) {
        return db.queryForMap("SELECT name,email FROM users WHERE id=?", userId);
    }
    public List<Map<String,Object>> findAssignedStores(UUID userId,String role) {
        return db.queryForList("SELECT DISTINCT s.id,s.name,t.tenant_name,t.segment_type FROM user_role ur JOIN role r ON r.id=ur.role_id JOIN store s ON s.id=ur.store_id AND s.tenant_id=ur.tenant_id JOIN tenant t ON t.id=s.tenant_id WHERE ur.user_id=? AND r.roletype=? ORDER BY s.name,s.id", userId,role);
    }
    public List<Map<String,Object>> findExperts(UUID storeId, UUID tenantId) {
        return db.queryForList("SELECT ur.id,u.name FROM user_role ur JOIN role r ON r.id=ur.role_id JOIN users u ON u.id=ur.user_id WHERE ur.store_id=? AND ur.tenant_id=? AND r.roletype='EXPERT' ORDER BY u.name,ur.id", storeId, tenantId);
    }
    public int insertOrder(UUID id, UUID tenantId, UUID storeId, String orderType, String customerName, String mobile, String details, String service, BigDecimal amount, LocalDateTime appointmentAt) {
        return db.update("INSERT INTO orders(id,tenant_id,store_id,order_type,customer_name,customer_mobile,details,service,amount,appointment_at) VALUES (?,?,?,?,?,?,?,?,?,?)", id, tenantId, storeId, orderType, customerName, mobile, details, service, amount, appointmentAt);
    }
    public List<Map<String,Object>> lockExpert(UUID assignmentId, UUID storeId, UUID tenantId) {
        return db.queryForList("SELECT ur.id FROM user_role ur JOIN role r ON r.id=ur.role_id WHERE ur.id=? AND ur.store_id=? AND ur.tenant_id=? AND r.roletype='EXPERT' FOR UPDATE", assignmentId, storeId, tenantId);
    }
    public int assignExpert(UUID assignee, UUID orderId) {
        return db.update("UPDATE orders SET assignee=?,order_status='ASSIGNED' WHERE id=?", assignee, orderId);
    }
    public int completeOrder(String paymentStatus,String paymentMode, UUID orderId) {
        return db.update("UPDATE orders SET order_status='COMPLETED',payment_status=?,payment_mode=? WHERE id=?", paymentStatus,paymentMode, orderId);
    }
    public int completePayment(String paymentMode,UUID orderId) {
        return db.update("UPDATE orders SET payment_status='COMPLETED',payment_mode=? WHERE id=?",paymentMode, orderId);
    }
}
