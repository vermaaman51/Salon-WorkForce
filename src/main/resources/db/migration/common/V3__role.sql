CREATE TABLE role (
 id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
 roletype VARCHAR(255)
);
-- Current application roles: ADMIN, STORE_MANAGER, RECEPTIONIST, EXPERT.
