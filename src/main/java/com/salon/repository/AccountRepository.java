package com.salon.repository;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AccountRepository {
    private final JdbcTemplate db;
    public AccountRepository(JdbcTemplate db) {
        this.db=db;
    }
    public List<Map<String,Object>> findByMobile(String mobile) {
        return db.queryForList("SELECT id,password_hash FROM users WHERE mobileno=?",mobile);
    }
    public List<String> findRoles(UUID userId) {
        return db.queryForList("SELECT DISTINCT r.roletype FROM user_role ur JOIN role r ON r.id=ur.role_id WHERE ur.user_id=?",String.class,userId);
    }
    public void initializeAdminPassword(String hash) {
        db.update("UPDATE users SET password_hash=? WHERE email='aman@mail.com' AND password_hash IS NULL AND EXISTS (SELECT 1 FROM user_role ur JOIN role r ON r.id=ur.role_id WHERE ur.user_id=users.id AND r.roletype='ADMIN')",hash);
    }
}
