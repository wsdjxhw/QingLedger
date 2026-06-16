-- =====================================================
-- Category module constraints and integrity upgrades
-- Version: V1.0.3
-- Date: 2026-05-31
-- =====================================================

-- 1) Add unique index for user-defined category names under same type
ALTER TABLE category
ADD UNIQUE INDEX uk_name_type_user (name, type, user_id);

-- 2) Repair dirty transaction.category_id values before adding foreign key
UPDATE `transaction` t
LEFT JOIN category c ON c.id = t.category_id
SET t.category_id = NULL
WHERE t.category_id IS NOT NULL
  AND c.id IS NULL;

-- 3) Add foreign key protection for category reference
ALTER TABLE `transaction`
ADD CONSTRAINT fk_transaction_category
FOREIGN KEY (category_id) REFERENCES category(id)
ON DELETE RESTRICT
ON UPDATE RESTRICT;

-- 4) Keep category.is_system and category.user_id consistent
ALTER TABLE category
ADD CONSTRAINT chk_category_system_user
CHECK (
    (is_system = 1 AND user_id IS NULL)
    OR
    (is_system = 0 AND user_id IS NOT NULL)
);
