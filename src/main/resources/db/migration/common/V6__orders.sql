CREATE TABLE orders (
 id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
 tenant_id UUID NOT NULL REFERENCES tenant(id),
 store_id UUID NOT NULL,
 assignee UUID,
 order_type VARCHAR(20) NOT NULL CHECK(order_type IN ('QUERY','APPOINTMENT')),
 order_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK(order_status IN ('PENDING','ASSIGNED','COMPLETED')),
 payment_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK(payment_status IN ('PENDING','COMPLETED')),
 customer_name VARCHAR(255) NOT NULL,
 customer_mobile VARCHAR(16) NOT NULL,
 details VARCHAR(2000) NOT NULL,
 service VARCHAR(255),
 payment_mode VARCHAR(10) CHECK(payment_mode IN ('CASH','UPI')),
 amount NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK(amount>=0),
 appointment_at TIMESTAMP,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(store_id,tenant_id) REFERENCES store(id,tenant_id),
 FOREIGN KEY(assignee,store_id,tenant_id) REFERENCES user_role(id,store_id,tenant_id),
 CHECK(order_type<>'APPOINTMENT' OR appointment_at IS NOT NULL),
 CHECK(order_type<>'QUERY' OR (amount=0 AND assignee IS NULL AND order_status<>'ASSIGNED' AND payment_status='PENDING')),
 CHECK(order_status<>'ASSIGNED' OR assignee IS NOT NULL),
 CHECK(order_type<>'APPOINTMENT' OR order_status<>'COMPLETED' OR assignee IS NOT NULL),
 CHECK(payment_status<>'COMPLETED' OR order_status='COMPLETED')
);
CREATE INDEX orders_store_status_idx ON orders(tenant_id,store_id,order_status,created_at);
CREATE INDEX orders_assignee_idx ON orders(assignee);
