# 修改密码功能 - 设计规格

## 概述

为已登录用户提供修改密码功能。用户输入旧密码进行二次身份验证后设置新密码。修改成功后,该用户在其他设备上的会话将被强制下线,当前设备会话保留。

## 范围边界

本任务范围:

- 修改密码核心逻辑（校验旧密码、更新新密码）。
- 踢掉其他设备 refreshToken（保留当前设备 refreshToken）。

非本任务范围:

- `passwordVersion/passwordChangedAt` 全局 token 失效机制。
- 限流基础设施（`userId + IP` 维度计数等）。
- 安全审计体系（审计表、审计服务）。
- `traceId` 响应字段体系改造。
- 异步补偿任务/调度器/消息队列方案。
- 全项目 HTTP 状态码风格改造。

## 设计决策

| 决策 | 选择 |
|------|------|
| 二次验证方式 | 旧密码（BCrypt） |
| 新密码格式 | 6-20 位（DTO 已配置 `@Size`） |
| 新旧密码相同 | 拦截,返回业务错误 |
| 密码同步策略 | 该用户所有 `UserAuth` 记录的 `password` 字段一起更新 |
| 会话处理 | 踢掉其他设备 `RefreshToken`,保留当前会话（`keepTokenId = null` 的老 token 场景除外） |
| 响应结构 | 沿用统一 `Result{code,message,data}` |
| HTTP 状态码 | 沿用现状: 接口统一返回 HTTP 200,业务结果看 `Result.code` |

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
  "message": "操作成功",
  "data": null
}
```

### 失败响应

```json
{
  "code": 500,
  "message": "旧密码错误",
  "data": null
}
```

## 业务逻辑

### 前置条件

- 用户已登录（通过 JWT Filter 保护）。
- accessToken 中携带 `refreshTokenId`（由 `JwtUtil.generateAccessToken(userId, tokenId)` 在登录时嵌入）。

### Service 层流程: `changePassword(userId, oldPassword, newPassword)`

1. 校验入参（DTO 校验）。
2. 校验新旧密码不相同（明文 equals 比对）,相同则返回业务错误。
3. 查询该用户所有 `UserAuth` 记录（`SELECT * FROM user_auth WHERE user_id = ?`）。
4. 若记录列表为空 → 返回“用户不存在”。
5. 固定使用查询结果第一条记录的 `password` 做 BCrypt 校验旧密码: `passwordEncoder.matches(oldPassword, userAuthList.get(0).getPassword())`。
6. 若旧密码错误 → 返回业务错误。
7. 对新密码进行 BCrypt 加密。
8. 更新该用户所有 `UserAuth.password`。
9. 返回 `Result.ok()`。

实现约束:

- `AuthServiceImpl.changePassword` 使用 `@Transactional`。
- 第 8 步采用单 SQL 条件更新,避免逐条 `updateById()` 造成部分成功:
`userAuthMapper.update(null, new UpdateWrapper<UserAuth>().set("password", encoded).eq("user_id", userId))`。

### Controller 层流程: `changePassword(request, req)`

1. 从请求头提取 accessToken。
2. 通过 `jwtUtil.getUserId(accessToken)` 取 `userId`。
3. 通过 `jwtUtil.getRefreshTokenId(accessToken)` 取当前会话 `refreshTokenId`。
4. 调用 `authService.changePassword(userId, req.getOldPassword(), req.getNewPassword())`。
5. 若 service 返回 ok → 调用 `tokenService.revokeAllRefreshTokensExcept(userId, currentTokenId)` 踢掉其他设备。
6. 若踢线失败: 仅记录日志,不回滚密码,不改变接口成功结果（best-effort）。
7. 返回 service 的结果。

### 流程图

```
[请求] -> 提取 accessToken -> 取 userId + refreshTokenId
    |
    v
Service: 校验入参 -> 新旧密码相同?
    | 是
    v
  返回业务错误
    | 否
    v
查询 UserAuth -> 空记录?
    | 是
    v
  返回"用户不存在"
    | 否
    v
旧密码匹配?
    | 否
    v
  返回"旧密码错误"
    | 是
    v
加密新密码 -> 条件更新该用户全部 UserAuth.password
    |
    v
返回 ok
    |
    v
Controller: 踢其他设备 refreshToken(保留当前会话)
    |
    v
返回成功
```

## 会话踢线机制

### 关键事实

每次登录,`TokenServiceImpl.generateTokens()` 会先生成 refreshToken 拿到 tokenId(UUID),再用 `jwtUtil.generateAccessToken(userId, tokenId)` 把 tokenId 嵌入 accessToken claims。因此每个 accessToken 都能反查出自己对应的 refreshTokenId。

Redis 存储结构:

- `refresh_token:{userId}:{tokenId}` -> refreshToken 字符串
- `refresh_token_info:{userId}:{tokenId}` -> RefreshTokenInfo 对象

同一个 `userId` 可能在 Redis 中有多条 `tokenId` 记录（多设备登录）。

### 新增方法: `revokeAllRefreshTokensExcept(Long userId, String keepTokenId)`

实现思路:

1. 用 Redis `SCAN` 命令（`ScanOptions.match("refresh_token:" + userId + ":*")`）增量扫描该用户所有 refreshToken key。
2. 解析 key 末段拿到 `tokenId`。
3. 跳过 `keepTokenId`,删除其他 key 及对应的 `refresh_token_info:{userId}:{tokenId}`。

不使用 `KEYS` 命令,因为它在生产环境会阻塞 Redis。

### 会话踢线效果

| 设备 | 状态 |
|------|------|
| 当前设备 | refreshToken 保留,可正常刷新,不被踢 |
| 其他设备的 refreshToken | 已被删除,刷新时命中“RefreshToken 已失效”并被迫重新登录 |
| 其他设备已签发的 accessToken | 由于 JWT 无法主动吊销,最长保留到 accessToken 自然过期（约 2 小时） |

## 错误码与响应约定

约束:

- 保持现有响应结构: `Result{code,message,data}`。
- 保持现有传输约定: HTTP 200 + 业务码。
- 保持与现有 `AuthServiceImpl` 一致: 业务分支直接 `return Result.fail(message)`。
- 改密业务失败（旧密码错误/新旧密码相同/用户不存在）统一返回 `code = 500` + 对应 `message`。
- `AuthException` 的 `1008-1012` 保持用于 token 相关异常,本接口不新增/不复用该码段。

## 边界情况

- 用户绑了多种登录方式（手机+邮箱） -> 所有 `UserAuth` 记录同步更新。
- 用户只有一种登录方式 -> 正常修改,仅更新一条。
- 修改前后密码完全相同 -> 拦截并返回业务错误。
- 当前设备 accessToken 中无 `refreshTokenId`（老 token） -> `keepTokenId = null` 时等同全量踢线（此场景下“保留当前会话”承诺不成立,当前设备也可能被踢出）。
- Redis SCAN 期间有新会话产生 -> 可能漏扫新 key,该行为接受（best-effort）。
- DB 提交后 Redis 踢线失败 -> 密码修改成功,仅记录日志。

## 安全考虑

- 旧密码校验防止 accessToken 被盗后任意改密。
- 改密后踢掉其他 refreshToken,降低长期会话被滥用风险。
- 保留当前会话,避免用户主动操作后被自己踢出。

## 涉及文件

| 文件 | 改动类型 | 说明 |
|------|---------|------|
| `service/auth/impl/AuthServiceImpl.java` | 修改 | 实现 `changePassword`,加 `@Transactional` |
| `service/auth/TokenService.java` | 修改 | 新增 `revokeAllRefreshTokensExcept(Long userId, String keepTokenId)` |
| `service/auth/impl/TokenServiceImpl.java` | 修改 | 基于 Redis `SCAN` 实现踢线 |
| `controller/AuthController.java` | 修改 | 实现 `password/change` 端点 |

DTO `ChangePasswordRequest` 已存在（`oldPassword + newPassword` 6-20 位）,无需改动。

## 测试与验收

最小测试矩阵:

1. 单元测试:
- 新旧密码相同返回业务错误。
- 旧密码错误返回业务错误。
- `keepTokenId = null` 时执行全量踢线。
2. 集成测试:
- 多设备登录后改密,仅当前设备可继续刷新。
- 其他设备 refreshToken 刷新失败。
- Redis 踢线失败时接口仍返回改密成功（并有错误日志）。
3. 并发测试:
- 同用户并发改密时,最终 `UserAuth` 密码一致。
- 并发改密语义采用 last-write-wins（后写覆盖前写）,该语义被接受。

验收标准:

- 功能: 改密成功后当前设备保留,其他设备 refreshToken 失效。
- 一致性: 不出现部分 `UserAuth` 更新成功、部分失败。
- 兼容性: 不改变现有 `Result` 结构与全局 HTTP 200 响应习惯。
