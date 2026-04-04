package com.qingledger.service.auth.impl;

import com.qingledger.exception.VerificationException;
import com.qingledger.service.auth.VerificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;

/**
 * 验证码服务实现类 - 负责手机号和邮箱验证码的发送、验证和管理
 *
 * 核心功能：
 * 1. 发送验证码：生成6位数字验证码，存储到Redis，并在控制台打印（开发环境）
 * 2. 验证验证码：比对用户输入与存储的验证码，验证成功后删除
 * 3. 删除验证码：从Redis删除指定的验证码
 *
 * 限制策略：
 * - 验证码1分钟内过期
 * - 1分钟内只能发送1次（防止频繁发送）
 * - 每天最多发送15次（防止滥用）
 * - 验证失败5次后验证码失效（防止暴力破解）
 *
 * Redis 数据结构：
 * - verification:{type}:{target} → 验证码值 (1分钟过期)
 * - verification_limit:{target}:{date} → 当日发送计数 (当天结束过期)
 * - verification_limit_minute:{target} → 1分钟限制标志 (65秒过期)
 * - verification_fail:{type}:{target} → 验证失败次数 (1分钟过期)
 *
 * Lua 脚本：
 * - send_code.lua: 原子化检查限制、存储验证码、更新计数
 *
 * @author QingLedger Team
 * @since 2026-04-04 完成回顾和注释
 */
@Slf4j
@Service
public class VerificationServiceImpl implements VerificationService {
    /**
     * 安全随机数生成器（比普通Random更安全）
     */
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 验证码长度：6位数字
     */
    private static final int CODE_LENGTH = 6;

    /**
     * 验证码过期时间：1分钟
     */
    private static final Duration CODE_EXPIRE = Duration.ofMinutes(1);

    /**
     * 1分钟限制时间：65秒（留5秒缓冲）
     */
    private static final Duration MINUTE_LIMIT = Duration.ofSeconds(65);

    /**
     * 每日最大发送次数：15次
     */
    //private static final int MAX_DAILY_COUNT = 15;

    /**
     * 发送验证码的 Lua 脚本
     * 原子化执行：检查限制 → 存储验证码 → 更新计数
     */
    private static final DefaultRedisScript<Long> SEND_CODE_REDIS_SCRIPT;

    // 静态初始化块：加载 Lua 脚本
    static {
        ClassPathResource resource = new ClassPathResource("lua/verification/send_code.lua");
        String scriptText;
        try {
            scriptText = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load Lua script", e);
        }

        SEND_CODE_REDIS_SCRIPT = new DefaultRedisScript<>();
        SEND_CODE_REDIS_SCRIPT.setScriptText(scriptText);
        SEND_CODE_REDIS_SCRIPT.setResultType(Long.class);
    }

    /**
     * Redis 模板（用于存储验证码和限制信息）
     */
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 构造器 - 通过依赖注入获取 RedisTemplate
     *
     * @param redisTemplate Redis 模板
     */
    public VerificationServiceImpl(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 发送验证码
     *
     * 流程：
     * 1. 生成6位数字验证码
     * 2. 准备Redis的key
     * 3. 使用Lua脚本原子化检查限制并存储验证码
     * 4. 开发环境：在控制台打印验证码
     *
     * @param type 验证码类型（PHONE/EMAIL）
     * @param target 发送目标（手机号/邮箱）
     * @throws VerificationException 1001-发送过于频繁, 1002-达到日上限
     */
    @Override
    public void sendCode(String type, String target) {
        // 步骤1: 生成6位数字验证码
        String code = generateCode();

        // 步骤2: 准备 Redis keys
        String codeKey = getCodeKey(type, target);           // 验证码存储key
        String limitKey = getDailyLimitKey(target);          // 日限制计数key
        String minuteKey = getMinuteLimitKey(target);        // 1分钟限制key
        long secondsUntilEndOfDay = calculateSecondsUntilEndOfDay();  // 距离今天结束的秒数

        // 步骤3: 使用Lua脚本原子化执行所有操作
        // Lua脚本会：检查1分钟限制 → 检查日限制 → 存储验证码 → 更新计数
        Long result = redisTemplate.execute(
                SEND_CODE_REDIS_SCRIPT,
                Arrays.asList(codeKey, limitKey, minuteKey),        // KEYS[1], KEYS[2], KEYS[3]
                code,                                              // ARGV[1]: 验证码值
                String.valueOf(CODE_EXPIRE.getSeconds()),          // ARGV[2]: 验证码过期秒数
                String.valueOf(secondsUntilEndOfDay),              // ARGV[3]: 日计数过期秒数
                String.valueOf(MINUTE_LIMIT.getSeconds())          // ARGV[4]: 1分钟限制秒数
        );

        // 步骤4: 处理Lua脚本返回的错误码
        if (result == -1) {
            throw new VerificationException(1001, "验证码发送过于频繁,请稍后再试");
        } else if (result == -2) {
            throw new VerificationException(1002, "今日验证码发送次数已达15次上限");
        }

        // 步骤5: 开发环境：在控制台打印验证码（生产环境会发送短信/邮件）
        log.info("========== 验证码 ==========");
        log.info("类型: {}", type);
        log.info("目标: {}", target);
        log.info("验证码: {}", code);
        log.info("过期时间: {} 秒", CODE_EXPIRE.getSeconds());
        log.info("============================");
    }

    /**
     * 验证验证码
     *
     * 流程：
     * 1. 从Redis获取存储的验证码
     * 2. 检查验证码是否过期
     * 3. 比对验证码是否正确
     * 4. 验证成功后删除验证码（一次性使用）
     * 5. 验证失败时记录失败次数
     *
     * 安全策略：
     * - 验证失败5次后，验证码失效（防止暴力破解）
     *
     * @param type 验证码类型（PHONE/EMAIL）
     * @param target 发送目标（手机号/邮箱）
     * @param code 用户输入的验证码
     * @return true-验证成功
     * @throws VerificationException 1005-验证码错误, 1006-验证码失效, 1015-失败次数过多
     */
    @Override
    public boolean verifyCode(String type, String target, String code) {
        String codeKey = getCodeKey(type, target);
        String storedCode = (String) redisTemplate.opsForValue().get(codeKey);

        if (storedCode == null) {
            throw new VerificationException(1006, "验证码已失效,请等待冷却时间结束后再次发送");
        }

        if (!storedCode.equals(code)) {
            // 增加失败计数
            String failKey = getFailKey(type, target);
            Long failCount = redisTemplate.opsForValue().increment(failKey);
            redisTemplate.expire(failKey, CODE_EXPIRE);

            if (failCount >= 5) {
                deleteCode(type, target);
                throw new VerificationException(1015, "验证码验证失败次数过多,请等待冷却时间结束后重新发送验证码");
            }
            throw new VerificationException(1005, "验证码错误");
        }

        // 验证成功,删除验证码
        deleteCode(type, target);
        return true;
    }

    /**
     * 删除验证码
     *
     * 功能：从Redis删除指定的验证码
     *
     * @param type 验证码类型（PHONE/EMAIL）
     * @param target 发送目标（手机号/邮箱）
     */
    @Override
    public void deleteCode(String type, String target) {
        String codeKey = getCodeKey(type, target);
        redisTemplate.delete(codeKey);
    }

    // ========== 私有辅助方法 ==========

    /**
     * 生成6位数字验证码
     *
     * @return 6位数字字符串
     */
    private String generateCode() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(RANDOM.nextInt(10));  // 生成0-9的随机数字
        }
        return code.toString();
    }

    /**
     * 生成验证码存储的Redis key
     *
     * 格式：verification:{type}:{target}
     * 例如：verification:PHONE:13800138000
     *
     * @param type 验证码类型
     * @param target 发送目标
     * @return Redis key
     */
    private String getCodeKey(String type, String target) {
        return "verification:" + type + ":" + target;
    }

    /**
     * 生成日限制计数的Redis key
     *
     * 格式：verification_limit:{target}:{date}
     * 例如：verification_limit:13800138000:2026-04-04
     *
     * @param target 发送目标
     * @return Redis key
     */
    private String getDailyLimitKey(String target) {
        return "verification_limit:" + target + ":" + java.time.LocalDate.now();
    }

    /**
     * 生成1分钟限制的Redis key
     *
     * 格式：verification_limit_minute:{target}
     *
     * @param target 发送目标
     * @return Redis key
     */
    private String getMinuteLimitKey(String target) {
        return "verification_limit_minute:" + target;
    }

    /**
     * 生成验证失败次数的Redis key
     *
     * 格式：verification_fail:{type}:{target}
     *
     * @param type 验证码类型
     * @param target 发送目标
     * @return Redis key
     */
    private String getFailKey(String type, String target) {
        return "verification_fail:" + type + ":" + target;
    }

    /**
     * 计算距离今天结束还有多少秒
     *
     * 用途：设置日限制计数的过期时间
     *
     * @return 距离今天结束的秒数
     */
    private long calculateSecondsUntilEndOfDay() {
        LocalDateTime endOfToday = LocalDateTime.now().with(LocalTime.MAX);  // 今天23:59:59
        return ChronoUnit.SECONDS.between(LocalDateTime.now(), endOfToday);
    }
}
