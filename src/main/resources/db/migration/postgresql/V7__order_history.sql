CREATE TABLE order_history (
 id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
 history_sequence BIGINT GENERATED ALWAYS AS IDENTITY UNIQUE,
 order_id UUID NOT NULL,
 event_type VARCHAR(12) NOT NULL CHECK(event_type IN ('BASELINE','INSERT','UPDATE','DELETE')),
 snapshot_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 tenant_id UUID NOT NULL,
 store_id UUID NOT NULL,
 assignee UUID,
 order_type VARCHAR(20) NOT NULL,
 order_status VARCHAR(20) NOT NULL,
 payment_status VARCHAR(20) NOT NULL,
 customer_name VARCHAR(255) NOT NULL,
 customer_mobile VARCHAR(16) NOT NULL,
 details VARCHAR(2000) NOT NULL,
 service VARCHAR(255),
 payment_mode VARCHAR(10) CHECK(payment_mode IN ('CASH','UPI')),
 amount NUMERIC(12,2) NOT NULL,
 appointment_at TIMESTAMP,
 created_at TIMESTAMP
);
CREATE INDEX order_history_order_idx ON order_history(order_id,history_sequence);
CREATE INDEX order_history_tenant_store_idx ON order_history(tenant_id,store_id,snapshot_at);
-- No foreign keys: snapshots survive deletion of the order and its related records.

-- Capture a complete snapshot in the same transaction as every order change.
CREATE FUNCTION capture_order_history() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE snapshot orders%ROWTYPE;
BEGIN
 IF TG_OP='DELETE' THEN snapshot:=OLD; ELSE snapshot:=NEW; END IF;
 INSERT INTO order_history(order_id,event_type,tenant_id,store_id,assignee,order_type,order_status,payment_status,customer_name,customer_mobile,details,service,amount,appointment_at,created_at,payment_mode)
 VALUES(snapshot.id,TG_OP,snapshot.tenant_id,snapshot.store_id,snapshot.assignee,snapshot.order_type,snapshot.order_status,snapshot.payment_status,snapshot.customer_name,snapshot.customer_mobile,snapshot.details,snapshot.service,snapshot.amount,snapshot.appointment_at,snapshot.created_at,snapshot.payment_mode);
 RETURN NULL;
END;
$$;
CREATE TRIGGER orders_history_capture AFTER INSERT OR UPDATE OR DELETE ON orders
FOR EACH ROW EXECUTE FUNCTION capture_order_history();

CREATE FUNCTION prevent_order_history_changes() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 RAISE EXCEPTION 'order_history is append-only' USING ERRCODE='55000';
END;
$$;
CREATE TRIGGER order_history_immutable BEFORE UPDATE OR DELETE OR TRUNCATE ON order_history
FOR EACH STATEMENT EXECUTE FUNCTION prevent_order_history_changes();
-- A normal DELETE is captured; TRUNCATE would bypass the row-level capture trigger.
CREATE TRIGGER orders_prevent_truncate BEFORE TRUNCATE ON orders
FOR EACH STATEMENT EXECUTE FUNCTION prevent_order_history_changes();
