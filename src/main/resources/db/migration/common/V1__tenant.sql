CREATE TABLE tenant (
 id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
 tenant_name VARCHAR(255),
 username VARCHAR(255),
 country VARCHAR(255)
);
