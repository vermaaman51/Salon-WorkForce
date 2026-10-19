ALTER TABLE orders ADD COLUMN modified_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE order_history ADD COLUMN modified_at TIMESTAMP WITH TIME ZONE;
CREATE OR REPLACE FUNCTION capture_order_history() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE snapshot orders%ROWTYPE;
BEGIN
 IF TG_OP='DELETE' THEN snapshot:=OLD; ELSE snapshot:=NEW; END IF;
 INSERT INTO order_history(order_id,event_type,tenant_id,store_id,assignee,order_type,order_status,payment_status,customer_name,customer_mobile,details,service,amount,appointment_at,created_at,payment_mode,modified_at)
 VALUES(snapshot.id,TG_OP,snapshot.tenant_id,snapshot.store_id,snapshot.assignee,snapshot.order_type,snapshot.order_status,snapshot.payment_status,snapshot.customer_name,snapshot.customer_mobile,snapshot.details,snapshot.service,snapshot.amount,snapshot.appointment_at,snapshot.created_at,snapshot.payment_mode,snapshot.modified_at);
 RETURN NULL;
END;
$$;
UPDATE orders o SET modified_at=COALESCE((SELECT MAX(h.snapshot_at) FROM order_history h WHERE h.order_id=o.id),CURRENT_TIMESTAMP);
ALTER TABLE orders ALTER COLUMN modified_at SET DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE orders ALTER COLUMN modified_at SET NOT NULL;
CREATE FUNCTION set_order_modified_at() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN NEW.modified_at:=clock_timestamp(); RETURN NEW; END;
$$;
CREATE TRIGGER orders_modified_at BEFORE INSERT OR UPDATE ON orders FOR EACH ROW EXECUTE FUNCTION set_order_modified_at();
CREATE INDEX orders_created_at_idx ON orders(created_at);
CREATE INDEX orders_tenant_store_modified_idx ON orders(tenant_id,store_id,modified_at DESC,id DESC);
CREATE INDEX orders_tenant_modified_idx ON orders(tenant_id,modified_at DESC,id DESC);
