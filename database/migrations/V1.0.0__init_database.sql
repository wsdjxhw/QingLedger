-- =====================================================
-- QingLedger 智能记账系统 - 初始化数据库脚本
-- 版本: V1.0.0
-- 创建时间: 2026-03-28
-- 说明: 创建所有核心业务表
-- =====================================================

-- 创建数据库
CREATE DATABASE IF NOT EXISTS qing_ledger DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE qing_ledger;

-- =====================================================
-- 1. 用户相关表
-- =====================================================

-- 用户主表
CREATE TABLE user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
    phone VARCHAR(20) UNIQUE NOT NULL COMMENT '手机号',
    password VARCHAR(128) COMMENT '加密密码(BCrypt)',
    nickname VARCHAR(50) COMMENT '昵称',
    avatar VARCHAR(255) COMMENT '头像URL',
    status TINYINT DEFAULT 1 COMMENT '状态:1正常,0禁用',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_phone (phone),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 第三方绑定表
CREATE TABLE user_auth (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    auth_type VARCHAR(20) NOT NULL COMMENT '认证类型:wechat,github等',
    open_id VARCHAR(128) NOT NULL COMMENT '第三方OpenID',
    union_id VARCHAR(128) COMMENT '第三方UnionID',
    bind_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '绑定时间',
    UNIQUE KEY uk_auth (auth_type, open_id),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='第三方登录绑定表';

-- 用户人设配置表
CREATE TABLE user_persona (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    persona_type VARCHAR(20) DEFAULT 'cute_pet' COMMENT '人设类型',
    custom_prompt TEXT COMMENT '自定义Prompt(扩展用)',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户AI人设配置表';

-- =====================================================
-- 2. 记账相关表
-- =====================================================

-- 账本表
CREATE TABLE ledger (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(50) NOT NULL COMMENT '账本名称',
    description VARCHAR(200) COMMENT '账本描述',
    owner_id BIGINT NOT NULL COMMENT '创建者ID',
    type ENUM('personal', 'shared') DEFAULT 'personal' COMMENT '账本类型',
    icon VARCHAR(50) COMMENT '账本图标',
    color VARCHAR(20) COMMENT '账本颜色',
    status TINYINT DEFAULT 1 COMMENT '状态:1正常,0归档',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_owner (owner_id),
    INDEX idx_type (type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='账本表';

-- 账本成员表(共享账本用)
CREATE TABLE ledger_member (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    ledger_id BIGINT NOT NULL COMMENT '账本ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    role ENUM('owner', 'admin', 'member') DEFAULT 'member' COMMENT '角色',
    joined_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
    UNIQUE KEY uk_ledger_user (ledger_id, user_id),
    INDEX idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='账本成员表';

-- 交易记录表
CREATE TABLE transaction (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    ledger_id BIGINT NOT NULL COMMENT '账本ID',
    user_id BIGINT NOT NULL COMMENT '记账用户ID',
    type ENUM('income', 'expense') NOT NULL COMMENT '类型',
    amount DECIMAL(10, 2) NOT NULL COMMENT '金额',
    category_id INT COMMENT '分类ID',
    tags VARCHAR(200) COMMENT '标签,逗号分隔',
    occur_date DATE NOT NULL COMMENT '交易日期',
    note TEXT COMMENT '备注',
    images JSON COMMENT '图片URL数组',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_ledger_date (ledger_id, occur_date),
    INDEX idx_user_date (user_id, occur_date),
    INDEX idx_category (category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='交易记录表';

-- 分类表
CREATE TABLE category (
    id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(30) NOT NULL COMMENT '分类名称',
    type ENUM('income', 'expense') NOT NULL COMMENT '类型',
    icon VARCHAR(50) COMMENT '图标',
    color VARCHAR(20) COMMENT '颜色',
    sort_order INT DEFAULT 0 COMMENT '排序',
    is_system TINYINT DEFAULT 1 COMMENT '是否系统预设',
    user_id BIGINT COMMENT '用户自定义分类时关联',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_type (type),
    INDEX idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='分类表';

-- =====================================================
-- 3. AI对话相关表
-- =====================================================

-- 对话会话表
CREATE TABLE chat_session (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    ledger_id BIGINT COMMENT '关联账本ID(可为空)',
    persona_type VARCHAR(20) COMMENT '使用的人设类型',
    status ENUM('active', 'archived') DEFAULT 'active' COMMENT '状态',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_user_status (user_id, status),
    INDEX idx_ledger (ledger_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对话会话表';

-- 对话消息表
CREATE TABLE chat_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id BIGINT NOT NULL COMMENT '会话ID',
    role ENUM('user', 'assistant', 'system') NOT NULL COMMENT '角色',
    content TEXT NOT NULL COMMENT '消息内容',
    msg_type ENUM('text', 'transaction', 'analysis') DEFAULT 'text' COMMENT '消息类型',
    transaction_id BIGINT COMMENT '关联的交易ID',
    tokens_used INT COMMENT '使用的Token数',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_session_time (session_id, created_at),
    INDEX idx_transaction (transaction_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对话消息表';

-- =====================================================
-- 4. 系统辅助表
-- =====================================================

-- 验证码表
CREATE TABLE verification_code (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    phone VARCHAR(20) NOT NULL COMMENT '手机号',
    code VARCHAR(10) NOT NULL COMMENT '验证码',
    type ENUM('login', 'register', 'reset') NOT NULL COMMENT '类型',
    expire_at DATETIME NOT NULL COMMENT '过期时间',
    used TINYINT DEFAULT 0 COMMENT '是否已使用',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_phone_type (phone, type),
    INDEX idx_expire (expire_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='验证码表';

-- 人设模板表
CREATE TABLE persona_template (
    id INT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(20) UNIQUE NOT NULL COMMENT '人设代码',
    name VARCHAR(30) NOT NULL COMMENT '人设名称',
    description VARCHAR(200) COMMENT '描述',
    system_prompt TEXT NOT NULL COMMENT '系统Prompt',
    avatar VARCHAR(100) COMMENT '头像',
    is_active TINYINT DEFAULT 1 COMMENT '是否启用',
    sort_order INT DEFAULT 0 COMMENT '排序',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI人设模板表';

-- =====================================================
-- 5. 初始化数据
-- =====================================================

-- 插入默认分类数据
INSERT INTO category (name, type, icon, color, sort_order, is_system) VALUES
-- 支出分类
('餐饮', 'expense', 'food', '#FF6B6B', 1, 1),
('交通', 'expense', 'transport', '#4ECDC4', 2, 1),
('购物', 'expense', 'shopping', '#45B7D1', 3, 1),
('娱乐', 'expense', 'entertainment', '#FFA07A', 4, 1),
('居住', 'expense', 'home', '#98D8C8', 5, 1),
('医疗', 'expense', 'medical', '#F7DC6F', 6, 1),
('教育', 'expense', 'education', '#BB8FCE', 7, 1),
('其他支出', 'expense', 'other', '#95A5A6', 99, 1),
-- 收入分类
('工资', 'income', 'salary', '#2ECC71', 1, 1),
('奖金', 'income', 'bonus', '#F39C12', 2, 1),
('理财', 'income', 'investment', '#3498DB', 3, 1),
('其他收入', 'income', 'other', '#95A5A6', 99, 1);

-- 插入默认人设模板
INSERT INTO persona_template (code, name, description, system_prompt, avatar, is_active, sort_order) VALUES
('cute_pet', '萌宠小账本', '可爱萌宠风格,温柔鼓励', '你是一只名叫"小账本"的萌宠,性格活泼可爱。当用户记账时,你要:1)用可爱的语气回应;2)适当给予鼓励;3)如果消费过高,要温柔地提醒。使用emoji和~符号增加可爱感。', '🐱', 1, 1),
('financial_advisor', '理财顾问', '专业理性的理财建议', '你是一位专业的理财顾问,说话理性客观。当用户记账时,你要:1)分析消费行为的合理性;2)提供数据驱动的建议;3)引用财务概念(如预算控制、收支比等)。保持专业但不失亲和力。', '👔', 1, 2),
('funny_friend', '毒舌损友', '幽默吐槽但真心关心', '你是用户的损友,说话幽默风趣,偶尔会吐槽用户的消费行为,但内心是真心关心用户的财务健康。可以适当开玩笑,但不要过分刻薄。如果用户消费合理,也要真诚夸奖。', '😏', 1, 3),
('warm_companion', '暖心陪伴', '温柔体贴的情感支持', '你是用户的暖心陪伴者,说话温柔体贴,关注用户的情绪。当用户记账时,你要:1)表达理解和共情;2)给予积极的心理暗示;3)在用户压力大时给予安慰。语气温暖,像知心朋友。', '🌸', 1, 4);

-- =====================================================
-- 6. 创建优化索引(根据查询场景优化)
-- =====================================================

-- 复合索引:查询用户在某个账本的交易
CREATE INDEX idx_transaction_ledger_user ON transaction(ledger_id, user_id, occur_date DESC);

-- 复合索引:查询用户的活跃会话
CREATE INDEX idx_session_user_active ON chat_session(user_id, status, updated_at DESC);

-- =====================================================
-- 7. 数据库权限配置(可选,根据环境调整)
-- =====================================================

-- 创建应用专用数据库用户
-- CREATE USER 'qingledger_app'@'%' IDENTIFIED BY 'your_secure_password';
-- GRANT SELECT, INSERT, UPDATE, DELETE ON qing_ledger.* TO 'qingledger_app'@'%';
-- FLUSH PRIVILEGES;
