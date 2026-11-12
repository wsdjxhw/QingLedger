-- V1.0.8 Mutation-tool idempotency: generated column + unique index on chat_tool_execution
-- 1) 将历史重复的 create_transaction 行改名为 create_transaction_dup(保留审计)
-- 2) 将存量 create_transaction 行的 idempotent_key 重写为新格式 requestId:toolName
-- 3) 增加 STORED 生成列 mutation_idempotent_scope + 唯一索引(仅约束变更类工具)
-- 要求 MySQL 5.7+(STORED 生成列)

DELIMITER $$

DROP PROCEDURE IF EXISTS upgrade_mutation_tool_idempotency $$
CREATE PROCEDURE upgrade_mutation_tool_idempotency()
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = DATABASE() AND table_name = 'chat_tool_execution'
    ) THEN
        -- 1. 清理历史重复行:同 request 下多条 create_transaction,保留 id 最大的一条
        UPDATE chat_tool_execution ct
        JOIN (
            SELECT request_id, MAX(id) AS keep_id
            FROM chat_tool_execution
            WHERE tool_name = 'create_transaction'
            GROUP BY request_id
            HAVING COUNT(*) > 1
        ) dup ON ct.request_id = dup.request_id AND ct.id < dup.keep_id
        SET ct.tool_name = 'create_transaction_dup';

        -- 2. 重写存量 create_transaction 的 idempotent_key 为新格式
        UPDATE chat_tool_execution
        SET idempotent_key = CONCAT(request_id, ':create_transaction')
        WHERE tool_name = 'create_transaction'
          AND idempotent_key <> CONCAT(request_id, ':create_transaction');

        -- 3. 生成列 + 唯一索引
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'chat_tool_execution'
              AND column_name = 'mutation_idempotent_scope'
        ) THEN
            ALTER TABLE chat_tool_execution
                ADD COLUMN mutation_idempotent_scope BIGINT
                    GENERATED ALWAYS AS (
                        IF(tool_name = 'create_transaction', request_id, NULL)
                    ) STORED COMMENT '变更类工具幂等作用域';
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM information_schema.statistics
            WHERE table_schema = DATABASE() AND table_name = 'chat_tool_execution'
              AND index_name = 'uk_mutation_scope'
        ) THEN
            ALTER TABLE chat_tool_execution
                ADD UNIQUE INDEX uk_mutation_scope (mutation_idempotent_scope);
        END IF;
    END IF;
END $$

CALL upgrade_mutation_tool_idempotency() $$
DROP PROCEDURE upgrade_mutation_tool_idempotency $$

DELIMITER ;
