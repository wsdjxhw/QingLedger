package com.qingledger.config;

import com.qingledger.mapper.UserMapper;
import com.qingledger.security.JwtAuthenticationFilter;
import com.qingledger.utils.JwtUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;

    public SecurityConfig(JwtUtil jwtUtil, UserMapper userMapper) {
        this.jwtUtil = jwtUtil;
        this.userMapper = userMapper;
    }

    /**
     * 安全过滤器链配置
     *
     * 配置说明：
     * - 禁用 CSRF（使用 JWT 无需 CSRF 保护）
     * - 禁用 CORS（如有需要可单独配置 CorsFilter）
     * - 无状态会话管理（JWT 认证不需要 Session）
     * - 白名单路径：认证相关、API 文档、健康检查等
     * - 添加 JWT 认证过滤器，验证 Access Token
     *
     * @param http HttpSecurity
     * @return SecurityFilterChain
     */
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
                    "/api/v1/auth/code",
                    "/api/v1/auth/register",
                    "/api/v1/auth/login/**",
                    "/api/v1/auth/refresh",
                    "/api/v1/auth/password/reset",
                    "/api/auth/**",
                    "/doc.html",
                    "/swagger/**",
                    "/swagger-ui/**",
                    "/v3/api-docs/**",
                    "/webjars/**",
                    "/favicon.ico",
                    "/swagger-resources/**",
                    "/knife4j/**",
                    "/actuator/**",
                    "/error"
                ).permitAll()
                .anyRequest().authenticated()
            )
            // 添加 JWT 认证过滤器，在 UsernamePasswordAuthenticationFilter 之前执行
            .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * JWT 认证过滤器
     *
     * 功能：
     * - 从请求头提取并验证 JWT Token
     * - 提取用户信息并设置到上下文
     * - 支持业务代码通过 UserContext 获取用户信息
     *
     * @return JwtAuthenticationFilter
     */
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtUtil, userMapper);
    }

    /**
     * BCrypt 密码编码器
     *
     * 强度：10（默认值，安全性和性能的平衡）
     *
     * @return BCryptPasswordEncoder
     */
    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }
}
