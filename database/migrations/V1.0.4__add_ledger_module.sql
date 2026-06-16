-- =============================================================
-- V1.0.4: 账本模块（invitation_code 表 + ledger_member 外键/索引）
-- =============================================================
-- 执行前检查：
--   SELECT COUNT(*) FROM ledger_member WHERE ledger_id NOT IN (SELECT id FROM ledger);
--   SELECT COUNT(*) FROM ledger_member WHERE user_id NOT IN (SELECT id FROM user);
-- 若返回值 > 0，需先清理或补充对应主表记录后再执行迁移。
-- =============================================================

-- 1) ledger_member 外键
ALTER TABLE ledger_member
  ADD CONSTRAINT fk_member_ledger FOREIGN KEY (ledger_id) REFERENCES ledger(id) ON DELETE CASCADE;
ALTER TABLE ledger_member
  ADD CONSTRAINT fk_member_user FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE RESTRICT;

-- 2) ledger_member 补充索引
ALTER TABLE ledger_member ADD INDEX idx_ledger_role (ledger_id, role);

-- 3) invitation_code 表（新增）
CREATE TABLE invitation_code (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    code       VARCHAR(12) NOT NULL COMMENT '邀请码短码',
    ledger_id  BIGINT NOT NULL COMMENT '关联账本',
    creator_id BIGINT NOT NULL COMMENT '生成邀请码的用户',
    max_uses   INT DEFAULT 1 COMMENT '最大使用次数',
    use_count  INT DEFAULT 0 COMMENT '已使用次数（原子更新）',
    expires_at DATETIME COMMENT '过期时间，null=不过期',
    status     ENUM('active', 'revoked') DEFAULT 'active' COMMENT '状态：active-有效, revoked-已失效',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_code (code),
    INDEX idx_ledger (ledger_id),
    CONSTRAINT fk_invitation_ledger FOREIGN KEY (ledger_id) REFERENCES ledger(id) ON DELETE CASCADE,
    CONSTRAINT fk_invitation_creator FOREIGN KEY (creator_id) REFERENCES user(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='邀请码表';
