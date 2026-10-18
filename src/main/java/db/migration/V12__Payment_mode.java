package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V12__Payment_mode extends BaseJavaMigration {
    @Override public void migrate(Context context) throws Exception {
        var connection=context.getConnection();
        try(var statement=connection.createStatement()) {
            statement.execute("ALTER TABLE orders ADD COLUMN payment_mode VARCHAR(10) CHECK(payment_mode IN ('CASH','UPI'))");
            statement.execute("ALTER TABLE order_history ADD COLUMN payment_mode VARCHAR(10) CHECK(payment_mode IN ('CASH','UPI'))");
            if(connection.getMetaData().getDatabaseProductName().equals("PostgreSQL")) {
                statement.execute("""
                    CREATE OR REPLACE FUNCTION capture_order_history() RETURNS trigger LANGUAGE plpgsql AS $$
                    DECLARE snapshot orders%ROWTYPE;
                    BEGIN
                        IF TG_OP='DELETE' THEN snapshot:=OLD; ELSE snapshot:=NEW; END IF;
                        INSERT INTO order_history(order_id,event_type,tenant_id,store_id,assignee,order_type,order_status,payment_status,customer_name,customer_mobile,details,service,amount,appointment_at,created_at,payment_mode)
                        VALUES(snapshot.id,TG_OP,snapshot.tenant_id,snapshot.store_id,snapshot.assignee,snapshot.order_type,snapshot.order_status,snapshot.payment_status,snapshot.customer_name,snapshot.customer_mobile,snapshot.details,snapshot.service,snapshot.amount,snapshot.appointment_at,snapshot.created_at,snapshot.payment_mode);
                        RETURN NULL;
                    END; $$
                    """);
            } else if(connection.getMetaData().getDatabaseProductName().equals("H2")) {
                statement.execute("DROP TRIGGER orders_history_capture");
                statement.execute("CREATE TRIGGER orders_history_capture AFTER INSERT, UPDATE, DELETE ON orders FOR EACH ROW CALL 'com.salon.OrderHistoryTestTrigger'");
            } else throw new IllegalStateException("Unsupported database for payment mode snapshots.");
        }
    }
}
