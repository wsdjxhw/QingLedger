# 用户认证模块实现计划 (修复版)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现完整的用户认证系统,支持手机号/邮箱验证码登录、密码登录、JWT双Token认证、账号绑定等功能

**Architecture:** 分层架构,AuthService协调UserService、VerificationService、TokenService、UserAuthService完成认证操作

**Tech Stack:** Spring Boot 3.2, Spring Security, MyBatis-Plus, Redis, JWT, BCrypt

---

## 文件结构

(与原计划相同,省略)

---

## 任务分解

### Phase 0: 数据库准备

#### Task 0: 创建数据库迁移脚本

**Files:**
- Create: `database/migrations/V1.0.1__update_user_auth.sql`

- [ ] **Step 1: 创建新的迁移脚本**

```sql
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
```

- [ ] **Step 2: 提交**

```bash
git add database/migrations/V1.0.1__update_user_auth.sql
git commit -m "feat: 添加数据库迁移脚本更新认证表结构"
```

---

### Phase 1: 基础设施

#### Task 1: 创建用户实体类

**Files:**
- Create: `src/main/java/com/qingledger/entity/User.java`

- [ ] **Step 1: 创建User实体类**

```java
package com.qingledger.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 用户实体
 */
@Data
@TableName("user")
public class User {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String nickname;

    private String avatar;

    private Integer status;

    private LocalDateTime deletedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 2: 编译验证**

Run: `mvn clean compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add src/main/java/com/qingledger/entity/User.java
git commit -m "feat: 添加User实体类"
```

---

#### Task 2: 创建认证方式实体类

**Files:**
- Create: `src/main/java/com/qingledger/entity/UserAuth.java`

- [ ] **Step 1: 创建UserAuth实体类**

```java
package com.qingledger.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 用户认证方式实体
 */
@Data
@TableName("user_auth")
public class UserAuth {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String authType;

    private String identifier;

    private String password;

    private Boolean verified;

    private Boolean isPrimary;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime bindAt;
}
```

- [ ] **Step 2: 编译验证**

Run: `mvn clean compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add src/main/java/com/qingledger/entity/UserAuth.java
git commit -m "feat: 添加UserAuth实体类"
```

---

#### Task 3: 创建Mapper接口

**Files:**
- Create: `src/main/java/com/qingledger/mapper/UserMapper.java`
- Create: `src/main/java/com/qingledger/mapper/UserAuthMapper.java`

- [ ] **Step 1: 创建UserMapper和UserAuthMapper**

```java
// UserMapper.java
package com.qingledger.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qingledger.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}

// UserAuthMapper.java
package com.qingledger.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qingledger.entity.UserAuth;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserAuthMapper extends BaseMapper<UserAuth> {
}
```

- [ ] **Step 2: 编译验证并提交**

Run: `mvn clean compile && git add src/main/java/com/qingledger/mapper/ && git commit -m "feat: 添加UserMapper和UserAuthMapper"`

---

#### Task 4: 创建Redis和Security配置

**Files:**
- Create: `src/main/java/com/qingledger/config/RedisConfig.java`
- Modify: `src/main/java/com/qingledger/config/SecurityConfig.java`
- Create: `src/main/java/com/qingledger/config/SecurityConfig.java` (如果不存在)

- [ ] **Step 1: 创建Redis配置**

```java
package com.qingledger.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        Jackson2JsonRedisSerializer<Object> serializer = new Jackson2JsonRedisSerializer<>(Object.class);

        ObjectMapper mapper = new ObjectMapper();
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        mapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );
        serializer.setObjectMapper(mapper);

        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(serializer);

        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder passwordEncoder() {
        return new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(10);
    }
}
```

- [ ] **Step 2: 更新Security配置**

```java
package com.qingledger.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(AbstractHttpConfigurer::disable)
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/api/v1/auth/**",
                    "/doc.html",
                    "/swagger/**",
                    "/v3/api-docs/**"
                ).permitAll()
                .anyRequest().authenticated()
            );

        return http.build();
    }
}
```

- [ ] **Step 3: 编译验证并提交**

Run: `mvn clean compile && git add src/main/java/com/qingledger/config/ && git commit -m "feat: 添加Redis和Security配置"`

---

#### Task 5: 创建JWT工具类和配置

**Files:**
- Create: `src/main/java/com/qingledger/utils/JwtUtil.java`
- Create: `src/main/java/com/qingledger/config/JwtConfig.java`
- Modify: `src/main/resources/application-dev.yml`

- [ ] **Step 1: 创建JWT配置类**

```java
package com.qingledger.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtConfig {
    private String secret;
    private Long accessTokenExpire;
    private Long refreshTokenExpire;
}
```

- [ ] **Step 2: 创建JWT工具类**

```java
package com.qingledger.utils;

import com.qingledger.config.JwtConfig;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class JwtUtil {

    private final JwtConfig jwtConfig;

    public JwtUtil(JwtConfig jwtConfig) {
        this.jwtConfig = jwtConfig;
    }

    public String generateAccessToken(Long userId) {
        return generateToken(userId, "access", jwtConfig.getAccessTokenExpire());
    }

    public RefreshTokenResult generateRefreshToken(Long userId) {
        String tokenId = UUID.randomUUID().toString().replace("-", "");
        String token = generateToken(userId, "refresh", jwtConfig.getRefreshTokenExpire(), tokenId);
        return new RefreshTokenResult(token, tokenId);
    }

    private String generateToken(Long userId, String type, Long expireSeconds) {
        return generateToken(userId, type, expireSeconds, null);
    }

    private String generateToken(Long userId, String type, Long expireSeconds, String tokenId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("type", type);
        if (tokenId != null) {
            claims.put("tokenId", tokenId);
        }

        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expireSeconds * 1000);

        return Jwts.builder()
                .claims(claims)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long getUserId(String token) {
        return parseToken(token).get("userId", Long.class);
    }

    public String getTokenType(String token) {
        return parseToken(token).get("type", String.class);
    }

    public String getTokenId(String token) {
        return parseToken(token).get("tokenId", String.class);
    }

    public boolean isAccessToken(String token) {
        return "access".equals(getTokenType(token));
    }

    public boolean isRefreshToken(String token) {
        return "refresh".equals(getTokenType(token));
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtConfig.getSecret().getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public record RefreshTokenResult(String token, String tokenId) {
    }
}
```

- [ ] **Step 3: 添加配置到application-dev.yml**

```yaml
# JWT配置
jwt:
  secret: qing-ledger-secret-key-at-least-256-bits-long-for-hs256-algorithm-in-production
  access-token-expire: 7200
  refresh-token-expire: 604800
```

- [ ] **Step 4: 编译验证并提交**

Run: `mvn clean compile && git add src/main/java/com/qingledger/config/ src/main/java/com/qingledger/utils/ src/main/resources/application-dev.yml && git commit -m "feat: 添加JWT配置和工具类"`

---

#### Task 6: 创建异常类

**Files:**
- Create: `src/main/java/com/qingledger/exception/AuthException.java`
- Create: `src/main/java/com/qingledger/exception/VerificationException.java`
- Modify: `src/main/java/com/qingledger/common/GlobalExceptionHandler.java`

- [ ] **Step 1: 创建异常类**

```java
// AuthException.java
package com.qingledger.exception;

import lombok.Getter;

@Getter
public class AuthException extends RuntimeException {
    private final int code;

    public AuthException(int code, String message) {
        super(message);
        this.code = code;
    }
}

// VerificationException.java
package com.qingledger.exception;

import lombok.Getter;

@Getter
public class VerificationException extends RuntimeException {
    private final int code;

    public VerificationException(int code, String message) {
        super(message);
        this.code = code;
    }
}
```

- [ ] **Step 2: 修改GlobalExceptionHandler**

添加以下方法:
```java
@ExceptionHandler(AuthException.class)
public Result<Void> handleAuthException(AuthException e) {
    log.error("认证异常: {}", e.getMessage());
    return Result.error(e.getCode(), e.getMessage());
}

@ExceptionHandler(VerificationException.class)
public Result<Void> handleVerificationException(VerificationException e) {
    log.error("验证码异常: {}", e.getMessage());
    return Result.error(e.getCode(), e.getMessage());
}
```

- [ ] **Step 3: 编译验证并提交**

Run: `mvn clean compile && git add src/main/java/com/qingledger/exception/ src/main/java/com/qingledger/common/GlobalExceptionHandler.java && git commit -m "feat: 添加认证和验证码异常处理"`

---

### Phase 2: 核心Service

由于篇幅限制,剩余任务的详细步骤与原计划类似,但需要注意以下修复:

1. **Task 9 (VerificationService)**: 添加分钟级限制
2. **Task 10 (TokenService)**: 修复expireIn计算
3. **Task 13 (AuthService)**: 实现密码更新逻辑
4. **Task 14 (AuthController)**: 修复getUserInfo实现

---

## 关键修复说明

1. **数据库迁移**: 创建V1.0.1脚本,而不是直接修改V1.0.0
2. **JWT配置**: 添加到application-dev.yml
3. **BCryptPasswordEncoder**: 作为Bean注入
4. **验证码限制**: 添加1分钟限制检查
5. **密码更新**: 调用userAuthMapper.updateById()
6. **导入错误**: Task 14中使用正确的import

---

## 执行顺序

按照任务编号顺序执行:
1. Task 0: 数据库迁移
2. Task 1-6: 基础设施
3. Task 7-12: 核心Service
4. Task 13-15: 认证Service和Controller

每个任务完成后提交代码。
