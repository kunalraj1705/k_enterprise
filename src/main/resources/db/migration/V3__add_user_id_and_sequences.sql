-- 1. Add the new business identifier column.
ALTER TABLE users
    ADD COLUMN user_id VARCHAR(8);

-- 2. Add the per-role sequence table.
CREATE TABLE user_id_sequence (
    user_role VARCHAR(20) PRIMARY KEY,
    next_value BIGINT NOT NULL
);

-- 3. Initialize one sequence for each supported role.
INSERT INTO user_id_sequence (user_role, next_value)
VALUES
    ('CUSTOMER', 1),
    ('ADMIN', 1),
    ('OPERATIONS', 1);

-- 4. Assign a business ID to existing users.
UPDATE users
SET user_id = CASE role
    WHEN 'CUSTOMER' THEN 'U0000001'
    WHEN 'ADMIN' THEN 'A0000001'
    WHEN 'OPERATIONS' THEN 'E0000001'
END;

-- 5. Advance the sequence for roles that already have users.
UPDATE user_id_sequence
SET next_value = next_value + 1
WHERE user_role IN (
    SELECT DISTINCT role
    FROM users
);

-- 6. Enforce the final constraints.
ALTER TABLE users
    ALTER COLUMN user_id SET NOT NULL;

ALTER TABLE users
    ADD CONSTRAINT uk_users_user_id UNIQUE (user_id);