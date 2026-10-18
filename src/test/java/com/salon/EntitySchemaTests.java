package com.salon;

import com.salon.entity.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.open-in-view=false"})
@Transactional
class EntitySchemaTests {
    @Autowired EntityManager em;

    @Test void persistsUuidEntitiesAndForeignKeyIds() {
        Tenant tenant = new Tenant();
        tenant.setTenantName("Example salon");
        tenant.setUsername("owner");
        tenant.setCountry("India");
        em.persist(tenant);
        Store store = new Store();
        store.setTenantId(tenant.getId());
        store.setName("Main store");
        store.setCity("Mumbai");
        store.setAddress("Store address");
        em.persist(store);
        User user = new User();
        user.setName("Ganesh");
        user.setMobileNo("1234567890");
        user.setEmail("ganesh@example.test");
        user.setAddress("User address");
        em.persist(user);
        Role role = new Role();
        role.setRoleType("ADMIN");
        em.persist(role);
        UserRole assignment = new UserRole();
        assignment.setUserId(user.getId());
        assignment.setRoleId(role.getId());
        assignment.setStoreId(store.getId());
        assignment.setTenantId(tenant.getId());
        em.persist(assignment);
        Order order = new Order();
        order.setTenantId(tenant.getId());
        order.setStoreId(store.getId());
        order.setAssignee(assignment.getId());
        order.setOrderType(com.salon.constant.OrderType.APPOINTMENT);
        order.setOrderStatus(Order.OrderStatus.ASSIGNED);
        order.setCustomerName("Customer");
        order.setCustomerMobile("1234567891");
        order.setDetails("Haircut");
        order.setService("Hair, Nail");
        order.setAppointmentAt(java.time.LocalDateTime.now());
        em.persist(order);
        em.flush();
        UUID assignmentId = assignment.getId();
        assertThat(tenant.getId()).isNotNull();
        assertThat(store.getId()).isNotNull();
        assertThat(user.getId()).isNotNull();
        assertThat(role.getId()).isNotNull();
        assertThat(assignmentId).isNotNull();
        em.clear();
        Order savedOrder = em.find(Order.class, order.getId());
        assertThat(savedOrder.getService()).isEqualTo("Hair, Nail");
        assertThat(savedOrder.getDetails()).isEqualTo("Haircut");
        assertThat(savedOrder.getTenantId()).isEqualTo(tenant.getId());
        assertThat(savedOrder.getStoreId()).isEqualTo(store.getId());
        assertThat(savedOrder.getAssignee()).isEqualTo(assignmentId);
        UserRole saved = em.find(UserRole.class, assignmentId);
        assertThat(saved.getUserId()).isEqualTo(user.getId());
        assertThat(saved.getRoleId()).isEqualTo(role.getId());
        assertThat(saved.getStoreId()).isEqualTo(store.getId());
        assertThat(saved.getTenantId()).isEqualTo(tenant.getId());
        UserRole admin = new UserRole();
        admin.setUserId(saved.getUserId());
        admin.setRoleId(saved.getRoleId());
        admin.setTenantId(saved.getTenantId());
        em.persist(admin);
        em.flush();
        UUID rootId = admin.getId();
        em.clear();
        assertThat(em.find(UserRole.class, rootId).getStoreId()).isNull();
    }
}
