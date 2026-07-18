-- V1.0.7 Add session title and soft delete support

DELIMITER $$

DROP PROCEDURE IF EXISTS upgrade_chat_session_title $$
CREATE PROCEDURE upgrade_chat_session_title()
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = DATABASE() AND table_name = 'chat_session'
    ) THEN
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'chat_session' AND column_name = 'title'
        ) THEN
            ALTER TABLE chat_session
                ADD COLUMN title VARCHAR(100) NULL COMMENT '会话标题' AFTER ledger_id;
        END IF;

        -- 扩展 status 枚举，支持软删除
        ALTER TABLE chat_session
            MODIFY COLUMN status ENUM('active', 'archived', 'deleted') DEFAULT 'active' COMMENT '状态';
    END IF;
END $$

CALL upgrade_chat_session_title() $$
DROP PROCEDURE upgrade_chat_session_title $$

DELIMITER ;
