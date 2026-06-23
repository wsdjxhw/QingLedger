-- V1.0.6 chat agent schema upgrade

DELIMITER $$

DROP PROCEDURE IF EXISTS upgrade_chat_agent_schema $$
CREATE PROCEDURE upgrade_chat_agent_schema()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = DATABASE() AND table_name = 'chat_request'
    ) THEN
        CREATE TABLE chat_request (
            id BIGINT PRIMARY KEY AUTO_INCREMENT,
            session_id BIGINT NOT NULL COMMENT 'Session ID',
            user_id BIGINT NOT NULL COMMENT 'User ID',
            client_request_id VARCHAR(64) NOT NULL COMMENT 'Client request id',
            lease_token VARCHAR(36) NULL COMMENT 'Processing lease token',
            persona_type VARCHAR(20) NOT NULL COMMENT 'Persona code',
            status ENUM('processing', 'success', 'error') NOT NULL DEFAULT 'processing',
            user_content TEXT NOT NULL COMMENT 'Original user content',
            reply TEXT NULL COMMENT 'Final assistant reply',
            transaction_id BIGINT NULL COMMENT 'Created transaction id',
            msg_type ENUM('text', 'transaction') NULL COMMENT 'Final message type',
            error_message TEXT NULL COMMENT 'Error message',
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            UNIQUE KEY uk_session_request (session_id, client_request_id),
            INDEX idx_user_created (user_id, created_at),
            CONSTRAINT fk_chat_request_session FOREIGN KEY (session_id) REFERENCES chat_session(id) ON DELETE CASCADE,
            CONSTRAINT fk_chat_request_user FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE RESTRICT
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Chat requests';
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = DATABASE() AND table_name = 'chat_request'
    ) THEN
        ALTER TABLE chat_request
            MODIFY COLUMN error_message TEXT NULL COMMENT 'Error message';

        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'chat_request' AND column_name = 'lease_token'
        ) THEN
            ALTER TABLE chat_request
                ADD COLUMN lease_token VARCHAR(36) NULL COMMENT 'Processing lease token' AFTER client_request_id;
        END IF;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = DATABASE() AND table_name = 'chat_tool_execution'
    ) THEN
        CREATE TABLE chat_tool_execution (
            id BIGINT PRIMARY KEY AUTO_INCREMENT,
            request_id BIGINT NOT NULL COMMENT 'Related chat request',
            tool_name VARCHAR(64) NOT NULL COMMENT 'Tool name',
            arguments_hash CHAR(64) NOT NULL COMMENT 'SHA-256 of normalized arguments JSON',
            idempotent_key VARCHAR(160) NOT NULL COMMENT 'requestId+toolName+argumentsHash',
            lease_token VARCHAR(36) NULL COMMENT 'Processing lease token',
            transaction_id BIGINT NULL COMMENT 'Created transaction id',
            result_json TEXT NULL COMMENT 'Tool result JSON',
            status ENUM('processing', 'success', 'error') NOT NULL DEFAULT 'processing',
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            UNIQUE KEY uk_request_tool_args (request_id, tool_name, arguments_hash),
            UNIQUE KEY uk_idempotent_key (idempotent_key),
            INDEX idx_request_status (request_id, status),
            CONSTRAINT fk_tool_execution_request FOREIGN KEY (request_id) REFERENCES chat_request(id) ON DELETE CASCADE
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Chat tool executions';
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = DATABASE() AND table_name = 'chat_tool_execution'
    ) THEN
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'chat_tool_execution' AND column_name = 'lease_token'
        ) THEN
            ALTER TABLE chat_tool_execution
                ADD COLUMN lease_token VARCHAR(36) NULL COMMENT 'Processing lease token' AFTER idempotent_key;
        END IF;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = DATABASE() AND table_name = 'chat_session'
    ) THEN
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'chat_session' AND column_name = 'current_request_id'
        ) THEN
            ALTER TABLE chat_session
                ADD COLUMN current_request_id BIGINT NULL COMMENT 'Current executing request id' AFTER status;
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'chat_session' AND column_name = 'current_request_lease_token'
        ) THEN
            ALTER TABLE chat_session
                ADD COLUMN current_request_lease_token VARCHAR(36) NULL COMMENT 'Current request lease token' AFTER current_request_id;
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'chat_session' AND column_name = 'current_request_heartbeat_at'
        ) THEN
            ALTER TABLE chat_session
                ADD COLUMN current_request_heartbeat_at DATETIME NULL COMMENT 'Current request heartbeat time' AFTER current_request_lease_token;
        END IF;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = DATABASE() AND table_name = 'chat_message'
    ) THEN
        ALTER TABLE chat_message
            MODIFY COLUMN role ENUM('user', 'assistant', 'system', 'tool') NOT NULL COMMENT 'Role',
            MODIFY COLUMN content TEXT NULL COMMENT 'Message content',
            MODIFY COLUMN msg_type ENUM('text', 'transaction', 'analysis', 'tool_call', 'tool_result') DEFAULT 'text' COMMENT 'Message type';

        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'chat_message' AND column_name = 'client_request_id'
        ) THEN
            ALTER TABLE chat_message
                ADD COLUMN client_request_id VARCHAR(64) NULL COMMENT 'Client request id' AFTER tokens_used;
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'chat_message' AND column_name = 'dedupe_key'
        ) THEN
            ALTER TABLE chat_message
                ADD COLUMN dedupe_key VARCHAR(200) NULL COMMENT 'Message dedupe key' AFTER client_request_id;
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'chat_message' AND column_name = 'tool_calls_json'
        ) THEN
            ALTER TABLE chat_message
                ADD COLUMN tool_calls_json TEXT NULL COMMENT 'Assistant raw tool_calls JSON' AFTER dedupe_key;
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'chat_message' AND column_name = 'tool_call_id'
        ) THEN
            ALTER TABLE chat_message
                ADD COLUMN tool_call_id VARCHAR(64) NULL COMMENT 'LLM tool_call_id' AFTER tool_calls_json;
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'chat_message' AND column_name = 'tool_name'
        ) THEN
            ALTER TABLE chat_message
                ADD COLUMN tool_name VARCHAR(64) NULL COMMENT 'Tool name' AFTER tool_call_id;
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'chat_message' AND column_name = 'tool_arguments'
        ) THEN
            ALTER TABLE chat_message
                ADD COLUMN tool_arguments TEXT NULL COMMENT 'Tool arguments JSON' AFTER tool_name;
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'chat_message' AND column_name = 'tool_result'
        ) THEN
            ALTER TABLE chat_message
                ADD COLUMN tool_result TEXT NULL COMMENT 'Tool result JSON' AFTER tool_arguments;
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'chat_message' AND column_name = 'tool_status'
        ) THEN
            ALTER TABLE chat_message
                ADD COLUMN tool_status VARCHAR(16) NULL COMMENT 'Tool execution status' AFTER tool_result;
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM information_schema.statistics
            WHERE table_schema = DATABASE() AND table_name = 'chat_message' AND index_name = 'uk_message_dedupe_key'
        ) THEN
            ALTER TABLE chat_message
                ADD UNIQUE KEY uk_message_dedupe_key (dedupe_key);
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM information_schema.statistics
            WHERE table_schema = DATABASE() AND table_name = 'chat_message' AND index_name = 'idx_tool_call'
        ) THEN
            ALTER TABLE chat_message
                ADD INDEX idx_tool_call (tool_call_id);
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM information_schema.statistics
            WHERE table_schema = DATABASE() AND table_name = 'chat_message' AND index_name = 'idx_client_request'
        ) THEN
            ALTER TABLE chat_message
                ADD INDEX idx_client_request (session_id, client_request_id, created_at);
        END IF;
    END IF;

END $$

CALL upgrade_chat_agent_schema() $$
DROP PROCEDURE upgrade_chat_agent_schema $$

DELIMITER ;
