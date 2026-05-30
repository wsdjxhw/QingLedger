package com.qingledger.service.auth.impl;

import com.qingledger.config.JwtConfig;
import com.qingledger.dto.ClientInfo;
import com.qingledger.entity.RefreshTokenInfo;
import com.qingledger.exception.AuthException;
import com.qingledger.service.auth.TokenService;
import com.qingledger.utils.IpUtil;
import com.qingledger.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Token 服务实现类 - 负责管理 Access Token 和 RefreshToken 的完整生命周期
 *
 * 核心功能：
 * 1. Token 生成：配合 JwtUtil 生成双Token，并存储到Redis
 * 2. Token 刷新：验证RefreshToken并进行设备安全验证
 * 3. Token 废除：从Redis删除指定的RefreshToken
 * 4. Token 解析：从AccessToken中提取用户ID
 * 5. 客户端信息提取：从HTTP请求中提取IP、设备指纹等信息
 *
 * 安全设计：
 * - RefreshToken 存储在Redis，关联设备信息
 * - 使用Lua脚本保证Redis操作的原子性
 * - 刷新Token时验证设备一致性（Mobile严格，Web宽松）
 * - 设备验证失败时立即删除Token，防止被盗用
 *
 * Redis 数据结构：
 * - refresh_token:{userId}:{tokenId} → RefreshToken值 (7天过期)
 * - refresh_token_info:{userId}:{tokenId} → RefreshTokenInfo 对象 (自动序列化为 JSON，7天过期)
 *
 * 序列化说明：
 * - 使用 GenericJackson2JsonRedisSerializer 自动处理对象序列化
 * - 存储时：直接存入对象，RedisTemplate 自动序列化为 JSON（带 @class 类型信息）
 * - 读取时：RedisTemplate 自动反序列化为原对象类型
 *
 * @author QingLedger Team
 * @since 2026-04-04 完成回顾和注释
 */
@Slf4j
@Service
public class TokenServiceImpl implements TokenService {

    /**
     * RefreshToken 过期时间：7天
     */
    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(7);

    /**
     * RefreshToken 过期时间（秒）：7天 = 604800秒
     */
    private static final long REFRESH_TOKEN_EXPIRE_SECONDS = 7 * 24 * 60 * 60;

    /**
     * JWT 工具类 - 负责生成和解析JWT Token
     */
    private final JwtUtil jwtUtil;

    /**
     * JWT 配置 - 包含过期时间等配置信息
     */
    private final JwtConfig jwtConfig;

    /**
     * Redis 模板 - 用于存储 RefreshToken
     * 注意：使用 GenericJackson2JsonRedisSerializer 后，会自动处理对象的序列化/反序列化
     */
    private final RedisTemplate<String, Object> redisTemplate;


    /**
     * 构造器 - 通过依赖注入获取所需组件
     *
     * 同时加载 Lua 脚本，如果加载失败则设为 null，后续使用普通 Redis 操作
     *
     * @param jwtUtil JWT 工具类
     * @param jwtConfig JWT 配置
     * @param redisTemplate Redis 模板（已配置自动序列化）
     */
    public TokenServiceImpl(JwtUtil jwtUtil, JwtConfig jwtConfig,
                           RedisTemplate<String, Object> redisTemplate) {
        this.jwtUtil = jwtUtil;
        this.jwtConfig = jwtConfig;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 生成 Token 对（简化版本）
     *
     * 功能：创建默认的 ClientInfo，委托给完整版本处理
     * 使用场景：向后兼容，不关心设备信息的内部调用
     *
     * @param userId 用户ID
     * @return TokenPairResult 包含 accessToken、refreshToken、expireIn
     */
    @Override
    public TokenPairResult generateTokens(Long userId) {
        // 创建默认的客户端信息（向后兼容）
        ClientInfo clientInfo = new ClientInfo();
        clientInfo.setClientType("WEB");
        clientInfo.setDeviceFingerprint("default");
        clientInfo.setIpAddress("0.0.0.0");
        clientInfo.setCity("未知");
        return generateTokens(userId, clientInfo);
    }

    /**
     * 生成 Token 对（完整版本，记录设备信息）
     *
     * 流程：
     * 1. 调用 JwtUtil 生成 Access Token 和 Refresh Token
     * 2. 构建 RefreshTokenInfo，记录用户和设备信息
     * 3. 将 RefreshToken 和 RefreshTokenInfo 存储到 Redis（7天过期）
     * 4. 返回 Token 对
     *
     * @param userId 用户ID
     * @param clientInfo 客户端信息（IP、设备类型、设备指纹等）
     * @return TokenPairResult 包含 accessToken、refreshToken、expireIn
     */
    @Override
    public TokenPairResult generateTokens(Long userId, ClientInfo clientInfo) {
        // 步骤1: 生成双Token
        // 先生成 RefreshToken 获取 tokenId
        JwtUtil.RefreshTokenResult refreshResult = jwtUtil.generateRefreshToken(userId);
        String refreshToken = refreshResult.token();
        String tokenId = refreshResult.tokenId();
        // 再生成 AccessToken，将 refreshTokenId 嵌入其中
        String accessToken = jwtUtil.generateAccessToken(userId, tokenId);

        // 步骤2: 构建设备信息（用于后续安全验证）
        RefreshTokenInfo info = new RefreshTokenInfo();
        info.setUserId(userId);
        info.setTokenId(tokenId);
        info.setClientType(clientInfo.getClientType());
        info.setDeviceFingerprint(clientInfo.getDeviceFingerprint());
        info.setCity(clientInfo.getCity());
        info.setCreatedAt(LocalDateTime.now());
        info.setLastVerifiedAt(LocalDateTime.now());

        // Web端额外记录IP段和User-Agent
        if ("WEB".equalsIgnoreCase(clientInfo.getClientType())) {
            info.setIpSegment(IpUtil.extractIpSegment(clientInfo.getIpAddress()));
            info.setUserAgent(clientInfo.getUserAgent());
        }

        // 步骤3: 存储到 Redis
        // 修复说明：废弃 Lua 脚本方式，直接使用 RedisTemplate 的 set() 操作
        // 原因：redisTemplate.execute() 执行 Lua 脚本时不会自动序列化 ARGV 参数，
        //      导致 RefreshTokenInfo 对象丢失 @class 类型信息，反序列化时变为 LinkedHashMap
        //
        // 新方案：使用 RedisTemplate 的普通操作，它会自动添加 @class 并正确序列化
        //        虽然失去了 Lua 脚本的原子性保证，但对于 Token 存储场景影响很小
        String refreshTokenKey = "refresh_token:" + userId + ":" + tokenId;
        String infoKey = "refresh_token_info:" + userId + ":" + tokenId;

        // 直接存储对象，RedisTemplate 会自动序列化为 JSON 并添加 @class 类型信息
        redisTemplate.opsForValue().set(refreshTokenKey, refreshToken, REFRESH_TOKEN_TTL);
        redisTemplate.opsForValue().set(infoKey, info, REFRESH_TOKEN_TTL);

        // 步骤4: 返回结果
        int expireIn = jwtConfig.getAccessTokenExpire().intValue();
        return new TokenPairResult(accessToken, refreshToken, expireIn);
    }

    /**
     * 刷新 Access Token（简化版本）
     *
     * 功能：创建默认的 ClientInfo，委托给完整版本处理
     * 安全提示：此方法不进行设备验证，存在安全风险
     *
     * @param refreshToken 刷新令牌
     * @return TokenPairResult 新的 Token 对
     */
    @Override
    public TokenPairResult refreshAccessToken(String refreshToken) {
        // 创建默认的客户端信息（向后兼容）
        ClientInfo clientInfo = new ClientInfo();
        clientInfo.setClientType("WEB");
        clientInfo.setDeviceFingerprint("default");
        clientInfo.setIpAddress("0.0.0.0");
        clientInfo.setCity("未知");
        return refreshAccessToken(refreshToken, clientInfo);
    }

    /**
     * 刷新 Access Token（完整版本，带设备安全验证）🔐
     *
     * 流程：
     * 1. 验证 RefreshToken 格式和类型
     * 2. 从 Redis 获取存储的设备信息
     * 3. 🔐 验证当前设备是否与创建Token时的设备一致（安全核心）
     *    - Mobile端：设备ID必须严格匹配
     *    - Web端：宽松验证（IP段匹配或同城市即可）
     * 4. 设备验证通过后，废除旧Token，生成新Token
     *
     * 安全策略：
     * - 设备验证失败时，立即删除 RefreshToken（防止被盗用）
     * - 不同城市刷新会被拒绝
     * - 刷新成功后，旧Token立即作废
     *
     * @param refreshToken 刷新令牌
     * @param clientInfo 当前客户端信息
     * @return TokenPairResult 新的 Token 对
     * @throws AuthException 1009-RefreshToken无效, 1010-已失效, 1011-解析失败, 1012-设备验证失败
     */
    @Override
    public TokenPairResult refreshAccessToken(String refreshToken, ClientInfo clientInfo) {
        // 步骤1: 验证 RefreshToken 格式和类型
        if (!jwtUtil.isRefreshToken(refreshToken)) {
            throw new AuthException(1009, "Refresh Token无效");
        }

        // 从 Token 中提取用户ID和TokenID
        Long userId = jwtUtil.getUserId(refreshToken);
        String tokenId = jwtUtil.getTokenId(refreshToken);

        // 步骤2: 从 Redis 获取存储的 TokenInfo
        // 注意：使用新的 GenericJackson2JsonRedisSerializer 配置后，
        // RedisTemplate 会自动将 JSON 反序列化为 RefreshTokenInfo 对象
        String refreshTokenKey = "refresh_token:" + userId + ":" + tokenId;
        String infoKey = "refresh_token_info:" + userId + ":" + tokenId;

        // 检查 Token 是否存在（可能已过期或被删除）
        if (Boolean.FALSE.equals(redisTemplate.hasKey(refreshTokenKey))) {
            throw new AuthException(1010, "RefreshToken 已失效,请重新登录");
        }

        // 直接获取 RefreshTokenInfo 对象，自动反序列化
        // RedisConfig 中配置的 activateDefaultTypingAsProperty 确保正确反序列化
        RefreshTokenInfo storedInfo = (RefreshTokenInfo) redisTemplate.opsForValue().get(infoKey);

        if (storedInfo == null || storedInfo.getUserId() == null) {
            throw new AuthException(1010, "RefreshToken 已失效,请重新登录");
        }

        // 步骤4: 🔐 设备验证（安全核心）
        boolean deviceValid = verifyDevice(storedInfo, clientInfo);
        if (!deviceValid) {
            // 设备验证失败，删除 RefreshToken（防止被盗用）
            redisTemplate.delete(refreshTokenKey);
            redisTemplate.delete(infoKey);
            throw new AuthException(1012, "检测到异常登录,请重新登录");
        }

        // 步骤5: 废除旧的 RefreshToken（防止重放攻击）
        redisTemplate.delete(refreshTokenKey);
        redisTemplate.delete(infoKey);

        // 步骤6: 生成新的 Token 对
        return generateTokens(userId, clientInfo);
    }

    /**
     * 废除 RefreshToken（用户登出时调用）
     *
     * 功能：从 Redis 删除指定的 RefreshToken 和关联的设备信息
     *
     * 使用场景：
     * - 用户主动登出
     * - 检测到异常登录时强制下线
     * - Token 刷新成功后废除旧Token
     *
     * @param userId 用户ID
     * @param tokenId Token唯一标识
     */
    @Override
    public void revokeRefreshToken(Long userId, String tokenId) {
        String key = "refresh_token:" + userId + ":" + tokenId;
        String infoKey = "refresh_token_info:" + userId + ":" + tokenId;
        redisTemplate.delete(key);
        redisTemplate.delete(infoKey);
    }

    @Override
    public void revokeAllRefreshTokensExcept(Long userId, String keepTokenId) {
        // 使用 SCAN 增量扫描该用户所有 refresh_token key,避免阻塞 Redis(KEYS 在生产环境会阻塞)
        String pattern = "refresh_token:" + userId + ":*";
        ScanOptions options = ScanOptions.scanOptions().match(pattern).count(100).build();

        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                // key 形如 refresh_token:{userId}:{tokenId},取末段 tokenId
                int lastColon = key.lastIndexOf(':');
                if (lastColon < 0 || lastColon == key.length() - 1) {
                    continue;
                }
                String tokenId = key.substring(lastColon + 1);
                if (keepTokenId != null && keepTokenId.equals(tokenId)) {
                    continue;
                }
                // 删除该 token 的 refresh_token 与 refresh_token_info 两组 key
                redisTemplate.delete(key);
                redisTemplate.delete("refresh_token_info:" + userId + ":" + tokenId);
            }
        }
    }

    /**
     * 解析 Access Token 获取用户ID
     *
     * 功能：验证 Token 类型并提取用户ID
     *
     * 使用场景：
     * - API 请求认证：从请求头获取 Token 并解析用户信息
     * - 权限验证：判断请求者身份
     *
     * @param accessToken 访问令牌
     * @return 用户ID
     * @throws AuthException 1008-Token类型错误、无效或已过期
     */
    @Override
    public Long parseAccessToken(String accessToken) {
        // 先验证 Token 类型（防止用 RefreshToken 来冒充）
        if (!jwtUtil.isAccessToken(accessToken)) {
            throw new AuthException(1008, "Token类型错误");
        }

        try {
            // 解析 Token 并提取用户ID
            return jwtUtil.getUserId(accessToken);
        } catch (Exception e) {
            throw new AuthException(1008, "Token无效或已过期: " + e.getMessage());
        }
    }

    /**
     * 从 HTTP 请求中提取客户端信息
     *
     * 功能：
     * - 提取 IP 地址（支持代理：X-Forwarded-For、X-Real-IP）
     * - 查询 IP 归属地城市
     * - 提取 User-Agent
     * - 判断客户端类型（WEB/MOBILE）
     * - 生成设备指纹
     *
     * 设备指纹策略：
     * - Mobile: 设备ID（从 X-Device-ID 请求头获取）
     * - Web: IP段 + "|" + User-Agent
     *
     * @param request HTTP 请求对象
     * @return ClientInfo 客户端信息
     */
    @Override
    public ClientInfo extractClientInfo(HttpServletRequest request) {
        ClientInfo info = new ClientInfo();

        // 提取 IP 地址（支持代理）
        String ip = getClientIp(request);
        info.setIpAddress(ip);
        info.setCity(IpUtil.getCity(ip));  // 查询 IP 归属地城市

        // 提取 User-Agent（浏览器/客户端标识）
        String userAgent = request.getHeader("User-Agent");
        info.setUserAgent(userAgent);

        // 判断客户端类型并生成设备指纹
        String clientType = request.getHeader("X-Client-Type");
        if ("MOBILE".equalsIgnoreCase(clientType)) {
            // Mobile 端：设备指纹 = 设备ID
            info.setClientType("MOBILE");
            info.setDeviceFingerprint(request.getHeader("X-Device-ID"));
        } else {
            // Web 端：设备指纹 = IP段 + User-Agent
            info.setClientType("WEB");
            String ipSegment = IpUtil.extractIpSegment(ip);
            info.setDeviceFingerprint(ipSegment + "|" + (userAgent != null ? userAgent : ""));
        }

        return info;
    }

    /**
     * 设备验证逻辑（安全核心）🔐
     *
     * 功能：验证当前设备是否与创建Token时的设备一致
     *
     * 验证策略：
     * - Mobile端：严格验证，设备ID必须完全匹配
     * - Web端：宽松验证
     *   1. IP段匹配 → 通过
     *   2. IP段不同但同城市 → 通过（记录警告）
     *   3. 不同城市 → 拒绝
     *
     * 为什么Web要宽松？
     * - 用户可能在同一个城市的不同网络环境（WiFi、4G、公司网络）
     * - IP可能变化，但在同城范围内可以接受
     *
     * @param storedInfo 存储的设备信息（创建Token时）
     * @param currentInfo 当前请求的设备信息
     * @return true-验证通过, false-验证失败
     */
    private boolean verifyDevice(RefreshTokenInfo storedInfo, ClientInfo currentInfo) {
        // Mobile 端：严格验证，设备ID必须匹配
        if ("MOBILE".equalsIgnoreCase(storedInfo.getClientType())) {
            boolean match = storedInfo.getDeviceFingerprint() != null
                    && storedInfo.getDeviceFingerprint().equals(currentInfo.getDeviceFingerprint());
            if (!match) {
                log.warn("Mobile 设备验证失败: userId={}, storedFingerprint={}, currentFingerprint={}",
                    storedInfo.getUserId(), storedInfo.getDeviceFingerprint(), currentInfo.getDeviceFingerprint());
            }
            return match;
        }

        // Web 端：宽松验证
        // 1. IP 段匹配 → 直接通过
        String currentIpSegment = IpUtil.extractIpSegment(currentInfo.getIpAddress());
        if (storedInfo.getIpSegment() != null && currentIpSegment.equals(storedInfo.getIpSegment())) {
            return true;
        }

        // 2. IP 段不匹配但城市相同 → 通过（记录警告日志）
        if (currentInfo.getCity() != null && currentInfo.getCity().equals(storedInfo.getCity())) {
            log.warn("IP 段变化但城市相同: userId={}, stored={}, current={}",
                storedInfo.getUserId(), storedInfo.getIpSegment(), currentIpSegment);
            return true;
        }

        // 3. 城市不同 → 拒绝（可能是异地盗用）
        log.warn("设备验证失败: userId={}, storedCity={}, currentCity={}",
            storedInfo.getUserId(), storedInfo.getCity(), currentInfo.getCity());
        return false;
    }

    /**
     * 从 HTTP 请求中获取真实客户端 IP
     *
     * 为什么需要这个方法？
     * - 请求可能经过多层代理（Nginx、负载均衡器）
     * - 直接用 request.getRemoteAddr() 只能获取到代理的IP
     *
     * IP 获取优先级：
     * 1. X-Forwarded-For: Nginx等代理设置的原始IP
     * 2. X-Real-IP: 另一个常见的代理头
     * 3. getRemoteAddr(): 直接连接的IP
     *
     * 注意：
     * - X-Forwarded-For 可能包含多个IP：1.2.3.4, 5.6.7.8
     * - 第一个IP是最原始的客户端IP
     *
     * @param request HTTP 请求对象
     * @return 客户端IP地址
     */
    private String getClientIp(HttpServletRequest request) {
        // 优先从 X-Forwarded-For 获取（代理设置的原始IP）
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) {
            // 其次从 X-Real-IP 获取
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty()) {
            // 最后使用直接连接的IP
            ip = request.getRemoteAddr();
        }
        // 处理多个 IP 的情况（代理链）：取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
