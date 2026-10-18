-- Replace the original workforce schema with the tenant/store identity model.
DROP TABLE appointments;
DROP TABLE leaves;
DROP TABLE queries;
DROP TABLE employees;
DROP TABLE staff_accounts;

CREATE TABLE tenant (
 id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
 tenant_name VARCHAR(255), username VARCHAR(255), country VARCHAR(255)
);
CREATE TABLE store (
 id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
 city VARCHAR(255), address VARCHAR(255), tenant_id UUID NOT NULL REFERENCES tenant(id), name VARCHAR(255)
);
-- USER is reserved in PostgreSQL, so use users as the physical table name.
CREATE TABLE users (
 id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
 name VARCHAR(255), mobileno VARCHAR(255), email VARCHAR(255), address VARCHAR(255)
);
CREATE TABLE role (
 id UUID DEFAULT gen_random_uuid() PRIMARY KEY, roletype VARCHAR(255)
);
CREATE TABLE user_role (
 id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
 user_id UUID NOT NULL REFERENCES users(id),
 role_id UUID NOT NULL REFERENCES role(id),
 store_id UUID NOT NULL REFERENCES store(id),
 CONSTRAINT unique_user_role_store UNIQUE (user_id, role_id, store_id)
);
CREATE INDEX store_tenant_idx ON store(tenant_id);
CREATE INDEX user_role_role_idx ON user_role(role_id);
CREATE INDEX user_role_store_idx ON user_role(store_id);
