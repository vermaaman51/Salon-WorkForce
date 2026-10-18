CREATE TABLE users (
 id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
 name VARCHAR(255),
 mobileno VARCHAR(255),
 email VARCHAR(255),
 address VARCHAR(255),
 password_hash VARCHAR(100)
);
CREATE UNIQUE INDEX users_email_unique ON users(email);
CREATE UNIQUE INDEX users_mobile_unique ON users(mobileno);
