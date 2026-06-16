package com.qingledger.security;

import com.qingledger.entity.User;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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

    // JWT验证白名单（这些接口不需要JWT验证，即使携带了过期token也不验证）
    private static final List<String> JWT_WHITELIST = Arrays.asList(
        "/api/v1/auth/code",
        "/api/v1/auth/register",
        "/api/v1/auth/login/",
        "/api/v1/auth/refresh",
        "/api/v1/auth/password/reset"
    );

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

        String requestURI = request.getRequestURI();
        log.debug("处理请求URI: {}", requestURI);

        // 检查是否在JWT白名单中
        boolean isInWhitelist = JWT_WHITELIST.stream().anyMatch(path -> {
            boolean matches;
            if (path.endsWith("/")) {
                matches = requestURI.startsWith(path);
            } else {
                matches = requestURI.equals(path) || requestURI.startsWith(path + "/");
            }
            log.debug("白名单检查 - 路径: {}, 匹配结果: {}", path, matches);
            return matches;
        });

        log.debug("白名单检查最终结果: {}", isInWhitelist);

        if (isInWhitelist) {
            log.debug("请求在JWT白名单中，跳过JWT验证: {}", requestURI);
            filterChain.doFilter(request, response);
            return;
        }

        // 步骤1: 从请求头提取 Token
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken == null || !bearerToken.startsWith("Bearer ")) {
            // 没有 Token，继续过滤器链（AnonymousAuthenticationFilter 会处理）
            filterChain.doFilter(request, response);
            return;
        }

        String accessToken = bearerToken.substring(7);
        log.debug("开始JWT认证，Token长度: {}", accessToken.length());

        // 步骤2: 解析和验证 Token
        try {
            // 验证 Token 类型（必须是 Access Token，不能用 RefreshToken）
            if (!jwtUtil.isAccessToken(accessToken)) {
                write401Response(response, "Token类型错误，请使用 AccessToken");
                return;
            }

            // 提取用户ID
            Long userId = jwtUtil.getUserId(accessToken);

            if (userId == null) {
                write401Response(response, "Token 中没有用户ID信息");
                return;
            }

            // 查询用户信息
            User user = userMapper.selectById(userId);
            if (user == null) {
                write401Response(response, "用户不存在");
                return;
            }

            // 检查用户状态
            if (user.getStatus() == null || user.getStatus() != 1) {
                write401Response(response, "用户已被禁用");
                return;
            }

            // 步骤3: 设置到 UserContext（业务代码可以使用）
            UserContext.setUserId(userId);
            UserContext.setUser(user);

            // 步骤4: 设置到 Spring Security Context（Spring Security 需要）
            UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                    userId,
                    null,
                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
                );
            SecurityContextHolder.getContext().setAuthentication(authentication);

            log.debug("JWT 认证成功: userId={}", userId);

            // 步骤5: 继续过滤器链
            filterChain.doFilter(request, response);

        } catch (Exception e) {
            log.error("JWT 认证失败: {}", e.getMessage(), e);
            write401Response(response, "AccessToken已过期或无效");
        } finally {
            // 步骤6: 请求结束后清理 ThreadLocal（防止内存泄漏）
            UserContext.clear();
        }
    }

    /**
     * 写入 401 响应（HTTP 401 + Result JSON 格式）
     */
    private void write401Response(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        String json = String.format("{\"code\":%d,\"message\":\"%s\",\"data\":null}", 401, message);
        response.getWriter().write(json);
    }
}
