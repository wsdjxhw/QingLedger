package com.qingledger.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 配置类
 *
 * 使用 GenericJackson2JsonRedisSerializer 进行 JSON 序列化，这是 Spring Data Redis 推荐的方式。
 *
 * 优势：
 * - 自动处理类型信息，无需手动配置 activateDefaultTyping
 * - 内置安全的类型验证机制
 * - 兼容 Redis 3.2+ 版本
 * - 支持复杂对象的序列化和反序列化
 *
 * @author QingLedger Team
 */
@Configuration
public class RedisConfig {

    /**
     * Redis 模板配置
     *
     * 配置说明：
     * - Key 使用 String 序列化
     * - Value 和 HashValue 使用 GenericJackson2JsonRedisSerializer
     * - 自动处理类型信息，支持多态类型
     *
     * @param factory Redis 连接工厂
     * @return 配置好的 RedisTemplate
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        // 使用 Spring Data Redis 推荐的 GenericJackson2JsonRedisSerializer
        // 它会自动处理类型信息，无需手动配置 ObjectMapper
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer();

        // 配置序列化器
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(jsonSerializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }
}
