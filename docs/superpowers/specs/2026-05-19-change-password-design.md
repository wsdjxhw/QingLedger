# 修改密码功能 - 设计规格

## 概述

为已登录用户提供修改密码功能。用户输入旧密码进行二次身份验证后,设置新密码。修改成功后,该用户在其他设备上的会话将被强制下线,当前设备的会话保留。

## 设计决策

| 决策 | 选择 |
|------|------|
| 二次验证方式 | 旧密码（BCrypt）|
| 新密码格式 | 6-20 位（DTO 已配置 @Size）|
| 新旧密码相同 | 拦截,报错"新密码不能与旧密码相同" |
| 密码同步策略 | 该用户所有 UserAuth 记录的 password 字段一起更新 |
| 会话处理 | 踢掉其他设备的 RefreshToken,保留当前会话 |
| 错误信息粒度 | 明确区分（旧密码错误 / 用户不存在 / 新旧密码相同 等）|

## 接口

### 请求

```
POST /api/v1/auth/password/change
Authorization: Bearer <accessToken>
Content-Type: application/json

{
    "oldPassword": "旧密码",
    "newPassword": "新密码"
}
```

### 成功响应

```json
{
    "code": 200,
    "message": "success",
    "data": null
}
```

### 失败响应

```json
{
    "code": xxx,
    "message": "错误描述",
    "data": null
}
```

## 业务逻辑

### 前置条件

- 用户已登录（通过 JWT Filter 保护）
- accessToken 中携带 refreshTokenId(由 `JwtUtil.generateAccessToken(userId, tokenId)` 在登录时嵌入)

### Service 层流程: `changePassword(userId, oldPassword, newPassword)`

1. 校验新旧密码不相同（明文 equals 比对）, 相同则返回"新密码不能与旧密码相同"
2. 查询该用户所有 UserAuth 记录（`SELECT * FROM user_auth WHERE user_id = ?`)
3. 若记录列表为空 → 返回"用户不存在"
4. 用任意一条记录的 password 字段做 BCrypt 校验旧密码: `passwordEncoder.matches(oldPassword, anyAuth.getPassword())`,失败则返回"旧密码错误"
5. 对新密码进行 BCrypt 加密
6. 遍历所有 UserAuth 记录,更新 password 字段为新加密密码,逐条 `userAuthMapper.updateById()`
7. 返回 `Result.ok()`

### Controller 层流程: `changePassword(request, req)`

1. 从请求头提取 accessToken
2. 通过 `jwtUtil.getUserId(accessToken)` 取 userId
3. 通过 `jwtUtil.getRefreshTokenId(accessToken)` 取当前会话的 refreshTokenId
4. 调用 `authService.changePassword(userId, req.getOldPassword(), req.getNewPassword())`
5. 若 service 返回 ok → 调用 `tokenService.revokeAllRefreshTokensExcept(userId, currentTokenId)` 踢掉其他设备
6. 返回 service 的结果（业务失败时直接透传错误消息,不踢会话）
7. 整体用 try-catch 包裹,统一捕获异常返回"修改密码失败: ..."

### 流程图

```
[请求] → 提取 accessToken → 取 userId + refreshTokenId
    ↓
    Service: 新旧密码相同? → [是] → 报错
    ↓ 否
    查所有 UserAuth → 无记录? → [是] → 报错"用户不存在"
    ↓ 否
    旧密码匹配? → [否] → 报错"旧密码错误"
    ↓ 是
    加密新密码 → 更新所有 UserAuth.password
    ↓
    返回 ok
    ↓
    Controller: 踢其他设备会话(保留当前 refreshTokenId)
    ↓
    返回成功
```

## 会话踢线机制

### 关键事实

每次登录,`TokenServiceImpl.generateTokens()` 会先生成 refreshToken 拿到 tokenId(UUID),再用 `jwtUtil.generateAccessToken(userId, tokenId)` 把 tokenId 嵌入 accessToken claims。因此每个 accessToken 都能反查出自己对应的 refreshTokenId。

Redis 存储结构:
- `refresh_token:{userId}:{tokenId}` → refreshToken 字符串
- `refresh_token_info:{userId}:{tokenId}` → RefreshTokenInfo 对象

同一个 userId 可能在 Redis 中有多条 tokenId 记录(多设备登录)。

### 新增方法: `revokeAllRefreshTokensExcept(Long userId, String keepTokenId)`

实现思路:

1. 用 Redis `SCAN` 命令(`ScanOptions.match("refresh_token:" + userId + ":*")`)增量扫描该用户的所有 refreshToken key
2. 解析 key 末段拿到 tokenId
3. 跳过 `keepTokenId`,删除其他 key 及对应的 `refresh_token_info:{userId}:{tokenId}`

不使用 `KEYS` 命令,因为它在生产环境会阻塞 Redis。

### 会话踢线效果

| 设备 | 状态 |
|------|------|
| 当前设备 | refreshToken 保留,可正常刷新,不被踢 |
| 其他设备的 refreshToken | 已被删除,刷新时命中"RefreshToken 已失效"而被迫重新登录 |
| 其他设备已签发的 accessToken | 由于 JWT 无法吊销,会话最长保留至 accessToken 过期(约 2 小时),此为 JWT 方案的天然代价 |

## 涉及文件

| 文件 | 改动类型 | 说明 |
|------|---------|------|
| `service/auth/impl/AuthServiceImpl.java` | 修改 | 实现 `changePassword`,加 `@Transactional` |
| `service/auth/TokenService.java` | 修改 | 新增 `revokeAllRefreshTokensExcept(Long userId, String keepTokenId)` 接口方法 |
| `service/auth/impl/TokenServiceImpl.java` | 修改 | 实现新增方法,基于 RedisTemplate.scan() |
| `controller/AuthController.java` | 修改 | 替换 `password/change` 端点的"功能开发中"占位符 |

DTO `ChangePasswordRequest` 已存在(oldPassword + newPassword 6-20 位),无需改动。

## 错误码 / 错误消息

| 场景 | 消息 | 触发位置 |
|------|------|---------|
| oldPassword 为空 | 旧密码不能为空 | DTO `@NotBlank` |
| newPassword 为空 | 新密码不能为空 | DTO `@NotBlank` |
| newPassword 长度不合法 | 密码长度必须在6-20位之间 | DTO `@Size` |
| 新旧密码相同 | 新密码不能与旧密码相同 | Service 层 |
| 用户不存在 | 用户不存在 | Service 层 |
| 旧密码错误 | 旧密码错误 | Service 层 |
| 其他异常 | 修改密码失败: ... | Controller 层 try-catch |

## 边界情况

- **用户绑了多种登录方式(手机+邮箱)** → 所有 UserAuth 记录的 password 字段同步更新
- **用户只有一种登录方式** → 同样能修改,只更新一条记录
- **修改前后密码完全相同** → 拦截,引导用户使用真正的新密码
- **当前设备 accessToken 中无 refreshTokenId(老 token)** → Controller 调用 `revokeAllRefreshTokensExcept` 时 keepTokenId 可能为 null,实现需对 null 友好处理(此场景下扫描结果不会匹配 null,等同于全部踢掉)
- **Redis SCAN 期间有新会话产生** → SCAN 是增量游标,可能漏扫新增 key,但新登录的会话本就不应被踢,无影响
- **新旧密码相同但旧密码错误** → 由于"新旧密码相同"校验在前,会优先报"新密码不能与旧密码相同"。这是预期行为,无需调整顺序
- **DB 提交后 Redis 踢线失败** → 密码已成功修改,踢其他设备的 Redis 操作采用"best-effort"语义:失败时仅记日志,不回滚密码、不返回错误。已签发的 accessToken 也最多在 2 小时内自然过期

## 安全考虑

- 旧密码校验防止 accessToken 被盗后任意改密
- 改密后立即踢掉其他会话,即便攻击者持有其他设备的 refreshToken 也立即失效
- 当前会话保留,避免用户主动操作后被自己踢出导致体验问题
- 错误消息粒度高(明确区分"旧密码错误"等),由于本接口需 JWT 已登录,不存在用户枚举风险
