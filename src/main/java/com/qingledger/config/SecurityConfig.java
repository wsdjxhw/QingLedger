package com.qingledger.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * 安全过滤器链配置
     *
     * 配置说明：
     * - 禁用 CSRF（使用 JWT 无需 CSRF 保护）
     * - 禁用 CORS（如有需要可单独配置 CorsFilter）
     * - 无状态会话管理（JWT 认证不需要 Session）
     * - 白名单路径：认证相关、API 文档、健康检查等
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
                    "/api/v1/auth/**",
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
            );

        return http.build();
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
