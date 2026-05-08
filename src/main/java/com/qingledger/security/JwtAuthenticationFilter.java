package com.qingledger.security;

import com.qingledger.entity.User;
import com.qingledger.exception.AuthException;
import com.qingledger.mapper.UserMapper;
import com.qingledger.utils.UserContext;
import com.qingledger.utils.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * JWT 认证过滤器
 *
 * 功能说明：
 * - 拦截所有 HTTP 请求（除白名单外）
 * - 从请求头提取并验证 JWT Token
 * - 提取用户信息并设置到上下文
 * - 支持业务代码通过 UserContext 获取用户信息
 *
 * 工作流程：
 * 1. 从请求头提取 Authorization: Bearer <token>
 * 2. 验证 Token 是否为 Access Token
 * 3. 解析 Token 获取用户ID
 * 4. 查询数据库获取完整用户信息
 * 5. 设置到 Spring Security Context（供 Spring Security 使用）
 * 6. 设置到 UserContext（供业务代码使用）
 * 7. 请求结束后清理 UserContext（防止内存泄漏）
 *
 * 错误处理：
 * - Token 无效或过期：不设置认证信息，由 Spring Security 返回 401
 * - 用户不存在：记录警告日志，不设置认证信息
 * - 其他异常：记录错误日志，不影响过滤器链继续执行
 *
 * 使用方式：
 * 在 SecurityConfig 中注册：
 * <pre>
 * http.addFilterBefore(jwtAuthenticationFilter(),
 *                     UsernamePasswordAuthenticationFilter.class);
 * </pre>
 *
 * @author QingLedger Team
 * @since 2026-05-04
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;

    /**
     * 构造函数
     *
     * @param jwtUtil JWT 工具类，用于解析和验证 Token
     * @param userMapper 用户数据访问层，用于查询用户信息
     */
    public JwtAuthenticationFilter(JwtUtil jwtUtil, UserMapper userMapper) {
        this.jwtUtil = jwtUtil;
        this.userMapper = userMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        try {
            // 步骤1: 从请求头提取 Token
            String bearerToken = request.getHeader("Authorization");
            if (bearerToken == null || !bearerToken.startsWith("Bearer ")) {
                // 没有 Token，继续过滤器链（Spring Security 会处理未认证的请求）
                filterChain.doFilter(request, response);
                return;
            }

            String accessToken = bearerToken.substring(7);

            // 步骤2: 验证 Token 类型（必须是 Access Token，不能用 RefreshToken）
            if (!jwtUtil.isAccessToken(accessToken)) {
                log.debug("Token 类型错误，不是 Access Token");
                filterChain.doFilter(request, response);
                return;
            }

            // 步骤3: 提取用户ID
            Long userId = jwtUtil.getUserId(accessToken);

            if (userId == null) {
                log.warn("Token 中没有用户ID信息");
                filterChain.doFilter(request, response);
                return;
            }

            // 步骤4: 查询用户信息
            User user = userMapper.selectById(userId);
            if (user == null) {
                log.warn("用户不存在: userId={}", userId);
                filterChain.doFilter(request, response);
                return;
            }

            // 检查用户状态
            if (user.getStatus() == null || user.getStatus() != 1) {
                log.warn("用户已被禁用: userId={}", userId);
                filterChain.doFilter(request, response);
                return;
            }

            // 步骤5: 设置到 UserContext（业务代码可以使用）
            UserContext.setUserId(userId);
            UserContext.setUser(user);

            // 步骤6: 设置到 Spring Security Context（Spring Security 需要）
            UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                    userId,
                    null,
                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
                );
            SecurityContextHolder.getContext().setAuthentication(authentication);

            log.debug("JWT 认证成功: userId={}", userId);

            // 步骤7: 继续过滤器链
            filterChain.doFilter(request, response);

        } catch (AuthException e) {
            // Token 验证失败（过期、无效等）
            log.debug("JWT Token 验证失败: {}", e.getMessage());
            // 即使 Token 验证失败，也继续过滤器链（让 Spring Security 处理）
            filterChain.doFilter(request, response);
        } catch (Exception e) {
            // 其他异常（如数据库查询失败）
            log.error("JWT 认证过程中发生异常", e);
            // 发生异常时，仍然继续过滤器链
            filterChain.doFilter(request, response);
        } finally {
            // 步骤8: 请求结束后清理 ThreadLocal（防止内存泄漏）
            UserContext.clear();
        }
    }
}
