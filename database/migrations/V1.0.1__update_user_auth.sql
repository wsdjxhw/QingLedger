-- =====================================================
-- 更新用户表和认证方式表结构
-- 版本: V1.0.1
-- 说明: 修改user表移除phone字段,更新user_auth表支持密码和多种认证方式
-- =====================================================

-- 修改user表,移除phone和password字段
ALTER TABLE user DROP COLUMN phone;
ALTER TABLE user DROP COLUMN password;

-- 修改user_auth表,添加密码和验证字段
ALTER TABLE user_auth ADD COLUMN password VARCHAR(128) COMMENT '加密密码(BCrypt)';
ALTER TABLE user_auth ADD COLUMN verified BOOLEAN DEFAULT FALSE COMMENT '是否已验证';
ALTER TABLE user_auth ADD COLUMN is_primary BOOLEAN DEFAULT FALSE COMMENT '是否为主要登录方式';

-- 更新user_auth表注释
ALTER TABLE user_auth COMMENT = '用户认证方式表';

-- 为现有数据设置默认值(如果有)
UPDATE user_auth SET verified = TRUE WHERE verified IS NULL;
UPDATE user_auth SET is_primary = TRUE WHERE is_primary IS NULL;
