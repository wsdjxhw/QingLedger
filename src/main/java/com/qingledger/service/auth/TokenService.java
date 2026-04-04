package com.qingledger.service.auth;

import com.qingledger.dto.ClientInfo;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Token 服务 - 负责管理 Access Token 和 RefreshToken 的完整生命周期
 *
 * 功能概述：
 * - 生成双Token（Access Token短期 + RefreshToken长期）
 * - 刷新Token（带设备安全验证）
 * - 废除Token（用户登出时）
 * - 解析Token（从Token中提取用户信息）
 * - 提取客户端信息（IP、设备指纹、User-Agent等）
 *
 * 安全特性：
 * - RefreshToken 存储在 Redis，关联设备信息
 * - 刷新时验证设备一致性（Mobile严格，Web宽松）
 * - 支持 Web（HttpOnly Cookie）和 Mobile（响应体）两种返回方式
 *
 * @author QingLedger Team
 * @since 2026-04-04 完成回顾和注释
 */
public interface TokenService {

    /**
     * 生成 Token 对（简化版本，不记录设备信息）
     *
     * 功能：
     * - 生成 Access Token（有效期2小时）
     * - 生成 RefreshToken（有效期7天）
     * - 将 RefreshToken 存储到 Redis（不关联设备信息）
     *
     * 使用场景：
     * - 向后兼容旧代码
     * - 不关心设备信息的内部调用
     *
     * @param userId 用户ID
     * @return TokenPairResult 包含 accessToken、refreshToken、expireIn
     */
    TokenPairResult generateTokens(Long userId);

    /**
     * 生成 Token 对（完整版本，记录设备信息用于安全验证）
     *
     * 功能：
     * - 生成 Access Token（有效期2小时）
     * - 生成 RefreshToken（有效期7天）
     * - 将 RefreshToken 和设备信息存储到 Redis
     * - 设备信息包括：IP地址、设备指纹、城市、User-Agent
     *
     * 存储结构：
     * - refresh_token:{userId}:{tokenId} → RefreshToken值
     * - refresh_token_info:{userId}:{tokenId} → RefreshTokenInfo JSON
     *
     * @param userId 用户ID
     * @param clientInfo 客户端信息（IP、设备类型、设备指纹等）
     * @return TokenPairResult 包含 accessToken、refreshToken、expireIn
     */
    TokenPairResult generateTokens(Long userId, ClientInfo clientInfo);

    /**
     * 刷新 Access Token（简化版本，不进行设备验证）
     *
     * 功能：
     * - 验证 RefreshToken 有效性
     * - 从 Redis 获取存储的 Token 信息
     * - 生成新的 Token 对
     * - 废除旧的 RefreshToken
     *
     * 安全提示：
     * - 此方法不验证设备信息，存在安全风险
     * - 建议使用带设备验证的版本
     *
     * @param refreshToken 刷新令牌
     * @return TokenPairResult 新的 Token 对
     * @throws AuthException RefreshToken无效或已过期
     */
    TokenPairResult refreshAccessToken(String refreshToken);

    /**
     * 刷新 Access Token（完整版本，带设备安全验证）
     *
     * 功能：
     * 1. 验证 RefreshToken 格式和有效性
     * 2. 从 Redis 获取存储的设备信息
     * 3. 🔐 验证当前设备是否与创建Token时的设备一致
     *    - Mobile端：设备ID必须严格匹配
     *    - Web端：宽松验证（IP段匹配或同城市即可）
     * 4. 设备验证通过后，废除旧Token，生成新Token
     *
     * 安全策略：
     * - 设备验证失败时，立即删除 RefreshToken（防止被盗用）
     * - 不同城市刷新会被拒绝
     *
     * @param refreshToken 刷新令牌
     * @param clientInfo 当前客户端信息
     * @return TokenPairResult 新的 Token 对
     * @throws AuthException 1009-RefreshToken无效, 1010-已失效, 1011-解析失败, 1012-设备验证失败
     */
    TokenPairResult refreshAccessToken(String refreshToken, ClientInfo clientInfo);

    /**
     * 废除 RefreshToken（用户登出时调用）
     *
     * 功能：
     * - 从 Redis 删除指定的 RefreshToken
     * - 同时删除关联的设备信息
     *
     * 使用场景：
     * - 用户主动登出
     * - 检测到异常登录时强制下线
     * - Token 刷新成功后废除旧Token
     *
     * @param userId 用户ID
     * @param tokenId Token唯一标识（UUID）
     */
    void revokeRefreshToken(Long userId, String tokenId);

    /**
     * 解析 Access Token 获取用户ID
     *
     * 功能：
     * - 验证 Token 类型（必须是 Access Token）
     * - 验证 Token 签名和有效期
     * - 提取用户ID
     *
     * 使用场景：
     * - API 请求认证：从请求头获取 Token 并解析用户信息
     * - 权限验证：判断请求者身份
     *
     * @param accessToken 访问令牌
     * @return 用户ID
     * @throws AuthException 1008-Token类型错误、无效或已过期
     */
    Long parseAccessToken(String accessToken);

    /**
     * 从 HTTP 请求中提取客户端信息
     *
     * 功能：
     * - 提取 IP 地址（支持代理：X-Forwarded-For、X-Real-IP）
     * - 查询 IP 归属地城市
     * - 提取 User-Agent
     * - 判断客户端类型（WEB/MOBILE）
     * - 生成设备指纹
     *   - Mobile: 设备ID（从 X-Device-ID 请求头获取）
     *   - Web: IP段 + User-Agent
     *
     * 支持的请求头：
     * - X-Client-Type: WEB 或 MOBILE
     * - X-Device-ID: 移动端设备唯一标识
     * - X-Forwarded-For: 代理转发的原始IP
     * - X-Real-IP: 真实IP
     * - User-Agent: 浏览器/客户端标识
     *
     * @param request HTTP 请求对象
     * @return ClientInfo 客户端信息
     */
    ClientInfo extractClientInfo(HttpServletRequest request);

    /**
     * Token 返回结果（不可变记录类）
     *
     * 包含内容：
     * - accessToken: 访问令牌（短期，2小时）
     * - refreshToken: 刷新令牌（长期，7天）
     * - expireIn: Access Token 过期时间（秒）
     */
    record TokenPairResult(String accessToken, String refreshToken, Integer expireIn) {
    }
}
