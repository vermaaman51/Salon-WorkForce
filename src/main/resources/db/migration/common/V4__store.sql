CREATE TABLE store (
 id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
 city VARCHAR(255),
 address VARCHAR(255),
 tenant_id UUID NOT NULL REFERENCES tenant(id),
 name VARCHAR(255),
 CONSTRAINT store_id_tenant_unique UNIQUE(id,tenant_id)
);
CREATE INDEX store_tenant_idx ON store(tenant_id);
