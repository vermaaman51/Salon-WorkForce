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

-- Test-only equivalent of the PostgreSQL trigger functions.
CREATE TRIGGER orders_history_capture AFTER INSERT, UPDATE, DELETE ON orders FOR EACH ROW CALL 'com.salon.OrderHistoryTestTrigger';
CREATE TRIGGER order_history_immutable BEFORE UPDATE, DELETE ON order_history FOR EACH ROW CALL 'com.salon.OrderHistoryTestTrigger';
