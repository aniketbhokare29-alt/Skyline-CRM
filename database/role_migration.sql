-- Safe role migration for the existing registeredusers table.
-- No tables or business records are deleted.
USE skyline_crm;

UPDATE registeredusers
SET role = 'ADMIN_OWNER'
WHERE role IN ('Admin', 'Admin Owner', 'admin_owner');

UPDATE registeredusers
SET role = 'AGENT'
WHERE LOWER(role) = 'agent';

UPDATE registeredusers
SET role = 'EMPLOYEE'
WHERE role IS NULL
   OR TRIM(role) = ''
   OR role NOT IN ('ADMIN_OWNER', 'EMPLOYEE', 'AGENT');

ALTER TABLE registeredusers
  MODIFY COLUMN role VARCHAR(32) NOT NULL DEFAULT 'EMPLOYEE';

-- Add the constraint only once. The backend validates the same set on older
-- MySQL versions that do not enforce CHECK constraints.
SET @role_constraint_sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE registeredusers ADD CONSTRAINT chk_registeredusers_role CHECK (role IN (''ADMIN_OWNER'', ''EMPLOYEE'', ''AGENT''))',
    'SELECT 1'
  )
  FROM information_schema.table_constraints
  WHERE constraint_schema = DATABASE()
    AND table_name = 'registeredusers'
    AND constraint_name = 'chk_registeredusers_role'
);
PREPARE role_constraint_stmt FROM @role_constraint_sql;
EXECUTE role_constraint_stmt;
DEALLOCATE PREPARE role_constraint_stmt;
