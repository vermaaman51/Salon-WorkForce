ALTER TABLE user_role ADD COLUMN tenant_id UUID REFERENCES tenant(id);
UPDATE user_role SET tenant_id = (SELECT tenant_id FROM store WHERE store.id = user_role.store_id);
ALTER TABLE user_role ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE user_role ALTER COLUMN store_id DROP NOT NULL;
CREATE INDEX user_role_tenant_idx ON user_role(tenant_id);
