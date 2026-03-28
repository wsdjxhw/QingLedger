# 用户认证模块设计文档

**项目**: QingLedger 智能记账系统
**模块**: 用户认证
**日期**: 2026-03-28
**状态**: 设计中

---

## 1. 概述

### 1.1 目标
实现完整的用户认证系统,支持多种登录方式,为后续业务功能提供安全可靠的用户身份验证基础。

### 1.2 功能范围
- 手机号注册/登录 (验证码)
- 邮箱注册/登录 (验证码)
- 密码登录 (支持手机号/邮箱)
- JWT双Token认证
- 账号绑定 (手机号绑定邮箱)
- 忘记密码
- 修改密码

### 1.3 技术选型
- **密码加密**: BCrypt
- **Token**: JWT (HS256)
- **缓存**: Redis (验证码、Refresh Token)
- **数据库**: MySQL
- **验证码发送**: 开发环境控制台输出,生产环境邮箱SMTP

---

## 2. 架构设计

### 2.1 分层架构

```
Controller层
    ↓
AuthService (认证业务逻辑)
    ↓
├──→ UserService (用户信息管理)
├──→ VerificationService (验证码服务)
├──→ TokenService (JWT服务)
└──→ UserAuthService (账号绑定关系)
```

### 2.2 组件职责

#### AuthService
协调各个Service完成认证操作:
- 手机号/邮箱验证码登录
- 密码登录
- 注册
- 绑定邮箱
- 重置密码
- 退出登录

#### UserService
用户基础信息管理:
- 创建用户
- 查询用户 (ID/手机号/邮箱)
- 更新密码
- 更新昵称

#### VerificationService
统一处理手机号和邮箱的验证码:
- 发送验证码
- 验证验证码
- 限制规则 (1分钟1次,1天10次)

#### TokenService
JWT生成、验证、刷新:
- 生成Access + Refresh Token
- 解析验证Access Token
- 刷新Access Token
- 废除Refresh Token

#### UserAuthService
管理用户的多种登录方式:
- 绑定新的登录方式
- 解绑登录方式
- 查询用户的所有登录方式

---

## 3. 接口设计

### 3.1 接口列表

| 接口 | 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|------|
| 发送验证码 | POST | `/api/v1/auth/code` | 发送手机/邮箱验证码 | 否 |
| 手机号注册 | POST | `/api/v1/auth/register/phone` | 手机号+密码注册 | 否 |
| 邮箱注册 | POST | `/api/v1/auth/register/email` | 邮箱+密码注册 | 否 |
| 手机号登录 | POST | `/api/v1/auth/login/phone` | 验证码登录 | 否 |
| 邮箱登录 | POST | `/api/v1/auth/login/email` | 验证码登录 | 否 |
| 密码登录 | POST | `/api/v1/auth/login/password` | 密码登录 | 否 |
| 刷新Token | POST | `/api/v1/auth/refresh` | 用Refresh Token换新Token | 否 |
| 退出登录 | POST | `/api/v1/auth/logout` | 废除Refresh Token | 是 |
| 绑定邮箱 | POST | `/api/v1/auth/bind/email` | 绑定邮箱到当前账号 | 是 |
| 重置密码 | POST | `/api/v1/auth/password/reset` | 忘记密码 | 否 |
| 修改密码 | POST | `/api/v1/auth/password/change` | 已登录用户修改密码 | 是 |

### 3.2 请求响应格式

#### 发送验证码
**请求**:
```json
{
  "type": "register",  // register/login/bind/reset
  "target": "13800138000"  // 手机号或邮箱
}
```

**响应**:
```json
{
  "code": 200,
  "message": "验证码已发送",
  "data": {
    "expireIn": 300
  }
}
```

#### 注册 (手机号/邮箱)
**请求**:
```json
{
  "phone": "13800138000",  // 或 email
  "code": "123456",
  "password": "abc123456"
}
```

**响应**:
```json
{
  "code": 200,
  "message": "注册成功",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "expireIn": 7200,
    "user": {
      "id": 1,
      "nickname": "用户1",
      "avatar": null
    }
  }
}
```

#### 登录 (密码)
**请求**:
```json
{
  "account": "13800138000",  // 手机号或邮箱
  "password": "abc123456"
}
```

**响应**: (同注册)

#### 刷新Token
**请求**:
```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**响应**:
```json
{
  "code": 200,
  "message": "刷新成功",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "expireIn": 7200
  }
}
```

---

## 4. 数据库设计

### 4.1 user 表

| 字段 | 类型 | 说明 | 约束 |
|------|------|------|------|
| id | BIGINT | 用户ID | PRIMARY KEY, AUTO_INCREMENT |
| phone | VARCHAR(20) | 手机号 | UNIQUE, NOT NULL |
| password | VARCHAR(128) | 加密密码 (BCrypt) | |
| nickname | VARCHAR(50) | 昵称 | |
| avatar | VARCHAR(255) | 头像URL | |
| status | TINYINT | 状态: 1正常 0禁用 | DEFAULT 1 |
| created_at | DATETIME | 创建时间 | DEFAULT CURRENT_TIMESTAMP |
| updated_at | DATETIME | 更新时间 | DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP |

**索引**:
- `idx_phone` - 手机号索引
- `idx_status` - 状态索引

### 4.2 user_auth 表

| 字段 | 类型 | 说明 | 约束 |
|------|------|------|------|
| id | BIGINT | 主键 | PRIMARY KEY, AUTO_INCREMENT |
| user_id | BIGINT | 用户ID | NOT NULL |
| auth_type | VARCHAR(20) | 类型: phone/email | NOT NULL |
| identifier | VARCHAR(128) | 标识符: 手机号/邮箱 | NOT NULL |
| bind_at | DATETIME | 绑定时间 | DEFAULT CURRENT_TIMESTAMP |

**索引**:
- `uk_auth` - (auth_type + identifier) 唯一索引
- `idx_user_id` - 用户ID索引

---

## 5. Redis数据结构

### 5.1 验证码
```
Key: verification:{type}:{target}
Value: {code}
TTL: 300秒 (5分钟)
```

**示例**:
```
verification:register:13800138000 -> "123456"
verification:bind:user@example.com -> "789012"
```

### 5.2 Refresh Token
```
Key: refresh_token:{userId}
Value: {refreshToken}
TTL: 604800秒 (7天)
```

**示例**:
```
refresh_token:1 -> "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

### 5.3 发送频率限制
```
Key: verification_limit:{target}
Value: {count}
TTL: 86400秒 (1天)
```

---

## 6. JWT设计

### 6.1 Access Token
**有效期**: 2小时
**Claims**:
```json
{
  "userId": 123,
  "type": "access",
  "exp": 1234567890
}
```

### 6.2 Refresh Token
**有效期**: 7天
**Claims**:
```json
{
  "userId": 123,
  "type": "refresh",
  "exp": 1234567890
}
```

### 6.3 配置
- **算法**: HS256
- **密钥**: 配置在`application.yml`
- **Access Token过期**: 7200秒 (2小时)
- **Refresh Token过期**: 604800秒 (7天)

---

## 7. 安全设计

### 7.1 密码安全
- 加密算法: BCrypt (strength=10)
- 存储方式: 只存储哈希值,不存储明文
- 验证方式: BCryptPasswordEncoder.matches()

### 7.2 Token安全
- Access Token存储在客户端 (内存/LocalStorage)
- Refresh Token存储在Redis,可主动废除
- Token包含用户ID和类型标识
- 验证签名和过期时间

### 7.3 验证码安全
- 格式: 6位数字
- 有效期: 5分钟
- 使用后立即失效
- 发送限制:
  - 同一目标1分钟内只能发1次
  - 同一目标1天最多发10次

### 7.4 接口限流
- 登录/注册接口: 同一IP 1分钟最多5次
- 发送验证码: 同一目标1分钟1次,1天10次

---

## 8. 错误处理

### 8.1 业务异常码

| 错误码 | 说明 | HTTP状态码 |
|--------|------|-----------|
| 1001 | 手机号已存在 | 400 |
| 1002 | 邮箱已存在 | 400 |
| 1003 | 用户不存在 | 404 |
| 1004 | 密码错误 | 401 |
| 1005 | 验证码错误 | 400 |
| 1006 | 验证码已过期 | 400 |
| 1007 | 验证码发送过于频繁 | 429 |
| 1008 | Token无效或已过期 | 401 |
| 1009 | Refresh Token无效 | 401 |
| 1010 | 账号已被禁用 | 403 |

### 8.2 异常处理
- 使用`@RestControllerAdvice`统一处理
- 返回格式统一为`Result<T>`
- 敏感信息不暴露给前端
- 记录异常日志

---

## 9. 开发计划

### Phase 1: 基础设施
- [ ] 创建实体类 (User, UserAuth)
- [ ] 创建Mapper接口
- [ ] 配置Redis
- [ ] 配置Spring Security

### Phase 2: 核心Service
- [ ] UserService - 用户CRUD
- [ ] VerificationService - 验证码(控制台版本)
- [ ] TokenService - JWT生成和验证
- [ ] UserAuthService - 账号绑定

### Phase 3: 认证Service
- [ ] AuthService - 各种登录注册逻辑

### Phase 4: Controller接口
- [ ] AuthController - 所有认证接口
- [ ] 集成测试

---

## 10. 后续扩展
- [ ] 邮箱SMTP发送 (替换控制台输出)
- [ ] 第三方登录 (微信、GitHub等)
- [ ] 设备管理 (记录登录设备)
- [ ] 登录日志 (记录登录历史)
- [ ] 异地登录提醒
