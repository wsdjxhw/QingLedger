-- =====================================================
-- 简化 user_auth 表结构
-- 版本: V1.0.2
-- 说明: 删除冗余的 open_id 和 union_id 字段，保留 identifier
-- =====================================================

-- 1. 确保 identifier 字段不能为空（因为 open_id 要被删除）
ALTER TABLE user_auth
MODIFY COLUMN identifier VARCHAR(128) NOT NULL COMMENT '账号标识(手机号/邮箱/第三方OpenID)';

-- 2. 更新唯一索引（从 auth_type + open_id 改为 auth_type + identifier）
ALTER TABLE user_auth DROP INDEX uk_auth;
ALTER TABLE user_auth
ADD UNIQUE KEY uk_auth (auth_type, identifier) COMMENT '认证类型+账号标识唯一索引';

-- 3. 删除冗余的 open_id 字段
ALTER TABLE user_auth DROP COLUMN open_id;

-- 4. 删除未使用的 union_id 字段
ALTER TABLE user_auth DROP COLUMN union_id;

-- 5. 更新表注释
ALTER TABLE user_auth COMMENT = '用户认证方式表-支持手机号/邮箱/第三方登录';
