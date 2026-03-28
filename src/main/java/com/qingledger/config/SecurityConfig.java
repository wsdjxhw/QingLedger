package com.qingledger.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 配置类
 *
 * @author QingLedger Team
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * 配置安全过滤链
     * 开发环境暂时允许所有请求,后续添加认证授权
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 禁用 CSRF (开发环境)
                .csrf(AbstractHttpConfigurer::disable)
                // 允许所有请求
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll()
                )
                // 禁用 CORS (开发环境)
                .cors(AbstractHttpConfigurer::disable);

        return http.build();
    }
}
