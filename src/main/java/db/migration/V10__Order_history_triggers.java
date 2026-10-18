package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Snapshot at the database boundary, including writes made outside the application. */
public class V10__Order_history_triggers extends BaseJavaMigration {
    @Override public void migrate(Context context) throws Exception {
        var connection=context.getConnection();
        try(var statement=connection.createStatement()) {
            if(connection.getMetaData().getDatabaseProductName().equals("PostgreSQL")) {
                statement.execute("""
                    CREATE FUNCTION capture_order_history() RETURNS trigger LANGUAGE plpgsql AS $$
                    DECLARE snapshot orders%ROWTYPE;
                    BEGIN
                        IF TG_OP='DELETE' THEN snapshot:=OLD; ELSE snapshot:=NEW; END IF;
                        INSERT INTO order_history(order_id,event_type,tenant_id,store_id,assignee,order_type,order_status,payment_status,customer_name,customer_mobile,details,service,amount,appointment_at,created_at)
                        VALUES(snapshot.id,TG_OP,snapshot.tenant_id,snapshot.store_id,snapshot.assignee,snapshot.order_type,snapshot.order_status,snapshot.payment_status,snapshot.customer_name,snapshot.customer_mobile,snapshot.details,snapshot.service,snapshot.amount,snapshot.appointment_at,snapshot.created_at);
                        RETURN NULL;
                    END; $$
                    """);
                statement.execute("CREATE TRIGGER orders_history_capture AFTER INSERT OR UPDATE OR DELETE ON orders FOR EACH ROW EXECUTE FUNCTION capture_order_history()");
                statement.execute("""
                    CREATE FUNCTION prevent_order_history_changes() RETURNS trigger LANGUAGE plpgsql AS $$
                    BEGIN RAISE EXCEPTION 'order_history is append-only' USING ERRCODE='55000'; END; $$
                    """);
                statement.execute("CREATE TRIGGER order_history_immutable BEFORE UPDATE OR DELETE OR TRUNCATE ON order_history FOR EACH STATEMENT EXECUTE FUNCTION prevent_order_history_changes()");
                // TRUNCATE bypasses row-level DELETE triggers; require regular DELETE for recoverable removal.
                statement.execute("CREATE TRIGGER orders_prevent_truncate BEFORE TRUNCATE ON orders FOR EACH STATEMENT EXECUTE FUNCTION prevent_order_history_changes()");
            } else if(connection.getMetaData().getDatabaseProductName().equals("H2")) {
                statement.execute("CREATE TRIGGER orders_history_capture AFTER INSERT, UPDATE, DELETE ON orders FOR EACH ROW CALL 'com.salon.OrderHistoryTestTrigger'");
                statement.execute("CREATE TRIGGER order_history_immutable BEFORE UPDATE, DELETE ON order_history FOR EACH ROW CALL 'com.salon.OrderHistoryTestTrigger'");
            } else {
                throw new IllegalStateException("Order history requires PostgreSQL (or H2 in tests).");
            }
        }
    }
}
