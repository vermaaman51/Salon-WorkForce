-- Preserve role IDs, user-role assignments, order assignees and history references.
UPDATE role SET roletype='EXPERT' WHERE roletype='WORKER';
