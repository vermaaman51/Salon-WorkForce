CREATE TABLE user_role (
 id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
 user_id UUID NOT NULL REFERENCES users(id),
 role_id UUID NOT NULL REFERENCES role(id),
 store_id UUID REFERENCES store(id),
 tenant_id UUID NOT NULL REFERENCES tenant(id),
 CONSTRAINT unique_user_role_store UNIQUE(user_id,role_id,store_id),
 CONSTRAINT user_role_scope_unique UNIQUE(id,store_id,tenant_id)
);
CREATE INDEX user_role_role_idx ON user_role(role_id);
CREATE INDEX user_role_store_idx ON user_role(store_id);
CREATE INDEX user_role_tenant_idx ON user_role(tenant_id);
