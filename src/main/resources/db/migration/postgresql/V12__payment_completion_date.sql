ALTER TABLE orders ADD COLUMN payment_completed_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE order_history ADD COLUMN payment_completed_at TIMESTAMP WITH TIME ZONE;
CREATE OR REPLACE FUNCTION set_order_modified_at() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 NEW.modified_at:=clock_timestamp();
 IF TG_OP='INSERT' THEN
  IF NEW.payment_status='COMPLETED' THEN NEW.payment_completed_at:=clock_timestamp(); ELSE NEW.payment_completed_at:=NULL; END IF;
 ELSIF NEW.payment_status='COMPLETED' AND OLD.payment_status IS DISTINCT FROM 'COMPLETED' THEN
  NEW.payment_completed_at:=clock_timestamp();
 ELSE
  NEW.payment_completed_at:=OLD.payment_completed_at;
 END IF;
 RETURN NEW;
END;
$$;
CREATE OR REPLACE FUNCTION capture_order_history() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE snapshot orders%ROWTYPE;
BEGIN
 IF TG_OP='DELETE' THEN snapshot:=OLD; ELSE snapshot:=NEW; END IF;
 INSERT INTO order_history(order_id,event_type,tenant_id,store_id,assignee,order_type,order_status,payment_status,customer_name,customer_mobile,details,service,amount,appointment_at,created_at,payment_mode,modified_at,payment_completed_at)
 VALUES(snapshot.id,TG_OP,snapshot.tenant_id,snapshot.store_id,snapshot.assignee,snapshot.order_type,snapshot.order_status,snapshot.payment_status,snapshot.customer_name,snapshot.customer_mobile,snapshot.details,snapshot.service,snapshot.amount,snapshot.appointment_at,snapshot.created_at,snapshot.payment_mode,snapshot.modified_at,snapshot.payment_completed_at);
 RETURN NULL;
END;
$$;
