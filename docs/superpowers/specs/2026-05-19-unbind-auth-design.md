# 解绑登录方式功能 - 设计规格

## 概述

为用户提供解绑已绑定的手机号或邮箱的功能。解绑后该认证记录被彻底删除。

## 设计决策

| 决策 | 选择 |
|------|------|
| 二次验证方式 | 密码验证（BCrypt） |
| 主账号处理 | 自动转移到剩余认证方式 |
| 最后一条记录保护 | 拒绝解绑，提示至少保留一种方式 |
| 接口风格 | `DELETE /unbind` + `@RequestBody` DTO |

## 接口

### 请求

```
DELETE /api/v1/auth/unbind
Authorization: Bearer <accessToken>
Content-Type: application/json

{
    "authType": "PHONE",
    "password": "用户当前密码"
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
- `authType` 必须是大写 `PHONE` 或 `EMAIL`（与后端常量一致，不做大小写转换）

### 流程

1. 从 JWT 中提取 `userId`
2. 参数校验：`authType` 和 `password` 均不能为空
3. 查询该用户下 `authType` 对应的 `UserAuth` 记录，不存在则返回错误
4. 查询该用户所有 `UserAuth` 记录数量，若 `<= 1` 条则拒绝解绑（至少保留一种登录方式）
5. 用 `BCryptPasswordEncoder.matches(submittedPassword, targetAuth.getPassword())` 验证密码，不匹配则返回错误
6. 若待删除记录是 `isPrimary = true`，则将剩余第一条记录的 `isPrimary` 设为 `true`
7. 执行 `userAuthMapper.deleteById(id)` 删除记录
8. 返回成功

### 流程图

```
[请求] → 参数校验 → 认证记录存在? → [否] → 报错
    ↓ 是
    用户绑定数 > 1? → [否] → 报错"至少保留一种登录方式"
    ↓ 是
    密码匹配? → [否] → 报错"密码错误"
    ↓ 是
    是主账号? → [是] → 转移 primary 到剩余方式
    ↓ 否
    删除记录 → 返回成功
```

## 涉及文件

| 文件 | 改动类型 | 说明 |
|------|---------|------|
| `dto/request/UnbindRequest.java` | 新建 | DTO，含 authType + password + @NotBlank |
| `service/auth/AuthService.java` | 修改 | `unbind()` 签名改为 `unbind(Long userId, String authType, String password)` |
| `service/auth/impl/AuthServiceImpl.java` | 修改 | 实现完整逻辑，@Transactional |
| `controller/AuthController.java` | 修改 | 从 `@PathVariable` 改为 `@RequestBody` |

## 错误码 / 错误消息

| 场景 | 消息 |
|------|------|
| 认证类型无效 | 认证类型必须是 PHONE 或 EMAIL |
| 密码为空 | 密码不能为空 |
| 记录不存在 | 未找到该认证方式 |
| 最后一条 | 至少保留一种登录方式，请先绑定新的登录方式 |
| 密码错误 | 密码错误 |

## 边界情况

- **用户只有一条认证记录** → 拒绝解绑，返回明确提示
- **用户有多条记录，解绑的是主账号** → 自动转移 primary 到剩余记录
- **用户有多条记录，解绑的是非主账号** → 直接删除即可
- **authType 不合法** → 前端校验 + 后端校验双重保证
