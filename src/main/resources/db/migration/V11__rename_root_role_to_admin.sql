-- Preserve role IDs and all existing user-role assignments and account credentials.
UPDATE role SET roletype='ADMIN' WHERE roletype='ROOT';
