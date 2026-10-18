package com.salon;

import java.util.*;
import java.sql.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @Transactional
class OrderHistoryTests {
    @Autowired JdbcTemplate db;
    UUID tenant,store,order;
    @BeforeEach void setup(){
        tenant=UUID.randomUUID();store=UUID.randomUUID();order=UUID.randomUUID();
        db.update("INSERT INTO tenant(id,tenant_name) VALUES (?,'History test')",tenant);
        db.update("INSERT INTO store(id,tenant_id,name) VALUES (?,?,'History store')",store,tenant);
        db.update("INSERT INTO orders(id,tenant_id,store_id,order_type,customer_name,customer_mobile,details,service,amount,appointment_at) VALUES (?,?,?,'APPOINTMENT','Customer','9000000090','Original details','Hair, Nail',100.50,CURRENT_TIMESTAMP)",order,tenant,store);
    }
    @Test void snapshotsEveryChangeWithUniqueIdsAndPreservesDeletedOrder(){
        db.update("UPDATE orders SET details='Updated details',service='Facial',amount=200.75 WHERE id=?",order);
        db.update("DELETE FROM orders WHERE id=?",order);
        var snapshots=db.queryForList("SELECT * FROM order_history WHERE order_id=? ORDER BY history_sequence",order);
        assertThat(snapshots).hasSize(3);
        assertThat(snapshots.stream().map(r->r.get("id")).distinct().count()).isEqualTo(3);
        assertThat(snapshots.stream().map(r->r.get("order_id"))).containsOnly(order);
        assertThat(snapshots.stream().map(r->r.get("event_type"))).containsExactly("INSERT","UPDATE","DELETE");
        assertThat(snapshots.get(0)).containsEntry("details","Original details").containsEntry("service","Hair, Nail").containsEntry("tenant_id",tenant).containsEntry("store_id",store);
        assertThat(snapshots.get(1)).containsEntry("details","Updated details").containsEntry("service","Facial");
        assertThat(snapshots.get(2).get("amount")).isEqualTo(new java.math.BigDecimal("200.75"));
        assertThat(db.queryForObject("SELECT count(*) FROM orders WHERE id=?",Long.class,order)).isZero();
    }
    @Test void historyRejectsModificationAndDeletion(){
        assertThatThrownBy(()->db.update("UPDATE order_history SET details='Tampered' WHERE order_id=?",order)).hasMessageContaining("append-only");
        assertThatThrownBy(()->db.update("DELETE FROM order_history WHERE order_id=?",order)).hasMessageContaining("append-only");
        assertThat(db.queryForObject("SELECT details FROM order_history WHERE order_id=?",String.class,order)).isEqualTo("Original details");
    }
    @Test void snapshotRollsBackWithOrderWrite(){
        db.execute((ConnectionCallback<Void>) connection->{
            Savepoint point=connection.setSavepoint();
            try(var update=connection.prepareStatement("UPDATE orders SET details='Rolled back' WHERE id=?")){
                update.setObject(1,order);update.executeUpdate();
            }
            assertThat(db.queryForObject("SELECT count(*) FROM order_history WHERE order_id=?",Long.class,order)).isEqualTo(2);
            connection.rollback(point);return null;
        });
        assertThat(db.queryForObject("SELECT count(*) FROM order_history WHERE order_id=?",Long.class,order)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT details FROM orders WHERE id=?",String.class,order)).isEqualTo("Original details");
    }
}
