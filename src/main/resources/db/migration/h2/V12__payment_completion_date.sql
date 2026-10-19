ALTER TABLE orders ADD COLUMN payment_completed_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE order_history ADD COLUMN payment_completed_at TIMESTAMP WITH TIME ZONE;
DROP TRIGGER orders_history_capture;
DROP TRIGGER orders_modified_at;
CREATE TRIGGER orders_modified_at BEFORE INSERT, UPDATE ON orders FOR EACH ROW CALL 'com.salon.OrderModifiedTestTrigger';
CREATE TRIGGER orders_history_capture AFTER INSERT, UPDATE, DELETE ON orders FOR EACH ROW CALL 'com.salon.OrderHistoryTestTrigger';